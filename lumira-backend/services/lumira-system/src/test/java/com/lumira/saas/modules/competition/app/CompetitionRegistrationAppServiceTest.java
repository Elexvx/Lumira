package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetitionRegistrationAppServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createRegistrationCalculatesTeamFeeAndPersistsSnapshots() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.competitionFeeMode = "TEAM";
        sql.competitionEntryFeeMinor = 12_300L;
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 2));

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), registrationRequest());

        assertThat(registration.getCompetitionId()).isEqualTo(11L);
        assertThat(registration.getTeamId()).isEqualTo(21L);
        assertThat(registration.getProjectId()).isEqualTo(31L);
        assertThat(registration.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(registration.getFeeMode()).isEqualTo("TEAM");
        assertThat(registration.getMemberCount()).isEqualTo(2);
        assertThat(registration.getPayableAmountMinor()).isEqualTo(12_300L);
        assertThat(objectMapper.readTree(registration.getTeamSnapshotJson()).path("teamName").asText()).isEqualTo("AI Team");
        assertThat(objectMapper.readTree(registration.getMemberSnapshotJson())).hasSize(2);
        assertThat(objectMapper.readTree(registration.getProjectSnapshotJson()).path("title").asText()).isEqualTo("AI Project");
        assertThat(sql.wroteTeamTables).isFalse();
    }

    @Test
    void createRegistrationCalculatesMemberFeeFromActiveTeamMembers() {
        RegistrationSql sql = new RegistrationSql();
        sql.competitionFeeMode = "MEMBER";
        sql.competitionEntryFeeMinor = 5_000L;
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 3));

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), registrationRequest());

        assertThat(registration.getMemberCount()).isEqualTo(3);
        assertThat(registration.getPayableAmountMinor()).isEqualTo(15_000L);
    }

    @Test
    void createRegistrationPersistsCollectedMembersWithoutTeamModuleWrites() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.competitionFeeMode = "MEMBER";
        sql.competitionEntryFeeMinor = 5_000L;
        CompetitionRegistrationAppService service = service(sql, teamApiRejectingLookup());

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), inlineRegistrationRequest());

        assertThat(registration.getTeamId()).isZero();
        assertThat(registration.getMemberCount()).isEqualTo(2);
        assertThat(registration.getPayableAmountMinor()).isEqualTo(10_000L);
        assertThat(objectMapper.readTree(registration.getTeamSnapshotJson()).path("teamName").asText()).isEqualTo("Collected Team");
        JsonNode members = objectMapper.readTree(registration.getMemberSnapshotJson());
        assertThat(members).hasSize(2);
        assertThat(members.get(0).path("memberName").asText()).isEqualTo("Alice");
        assertThat(members.get(0).path("extraValues").path("mobile").asText()).isEqualTo("13800138000");
        assertThat(members.get(0).has("userId")).isFalse();
        assertThat(sql.wroteTeamTables).isFalse();
    }

    @Test
    void createRegistrationDoesNotRequireApplicantToBeActiveTeamMember() {
        RegistrationSql sql = new RegistrationSql();
        CompetitionRegistrationAppService service = service(sql, teamApiRejectingMembershipCheck(2001L, 2));

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), registrationRequest());

        assertThat(registration.getTeamId()).isEqualTo(21L);
        assertThat(registration.getMemberCount()).isEqualTo(2);
    }

    @Test
    void requiredMaterialFieldsMustBeSubmittedBeforePayment() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.stageForm = Map.of(
                "id", 81L,
                "competitionId", 11L,
                "stageId", 71L,
                "formName", "Preliminary",
                "formSchemaJson", """
                        {"fields":[{"key":"project_plan","label":"Project plan","type":"file","required":true}]}
                        """,
                "version", 1,
                "status", "ENABLED"
        );
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CompetitionRegistrationDTO.MaterialSubmitRequest request = new CompetitionRegistrationDTO.MaterialSubmitRequest();
        request.setStageId(71L);

        assertThatThrownBy(() -> service.submitMaterials(student(), 1L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Required material field is missing");
        assertThat(sql.materialValueInserts).isZero();
    }

    @Test
    void listMaterialsReturnsSavedSubmissionValuesForOwnedRegistration() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.materialSubmissions = List.of(Map.of(
                "id", 91L,
                "registrationId", 1L,
                "competitionId", 11L,
                "stageId", 71L,
                "formVersion", 1,
                "submitterUserId", 1001L,
                "status", "SUBMITTED",
                "submittedAt", LocalDateTime.now()
        ));
        sql.materialValues = List.of(Map.of(
                "id", 101L,
                "submissionId", 91L,
                "fieldKey", "project_intro",
                "fieldType", "textarea",
                "textValue", "A practical AI project."
        ));
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));

        List<CompetitionRegistrationVO.MaterialSubmission> materials = service.listMaterials(student(), 1L);

        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).getStatus()).isEqualTo("SUBMITTED");
        assertThat(materials.get(0).getValues()).hasSize(1);
        assertThat(materials.get(0).getValues().get(0).getFieldKey()).isEqualTo("project_intro");
        assertThat(materials.get(0).getValues().get(0).getTextValue()).isEqualTo("A practical AI project.");
    }

    @Test
    void paymentOrderCreationIsIdempotentAndCarriesRegistrationMetadata() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.preliminaryStageId = 71L;
        sql.submittedMaterialCount = 1L;
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));

        CompetitionRegistrationVO.PaymentOrder first = service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());
        CompetitionRegistrationVO.PaymentOrder second = service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());

        assertThat(first.getOrderNo()).isEqualTo(second.getOrderNo());
        assertThat(first.getAmountMinor()).isEqualTo(8_800L);
        assertThat(sql.paymentOrderInserts).isEqualTo(1);
        assertThat(sql.registration.get("paymentOrderNo")).isEqualTo(first.getOrderNo());
        JsonNode metadata = objectMapper.readTree(sql.paymentRequestJson).path("metadata");
        assertThat(metadata.path("bizType").asText()).isEqualTo("competition_registration");
        assertThat(metadata.path("registrationId").asLong()).isEqualTo(1L);
        assertThat(metadata.path("competitionId").asLong()).isEqualTo(11L);
        assertThat(metadata.path("teamId").asLong()).isEqualTo(21L);
        assertThat(metadata.path("projectId").asLong()).isEqualTo(31L);
    }

    @Test
    void listPaymentRecordsConnectsRegistrationAndPaymentContext() {
        RegistrationSql sql = new RegistrationSql();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("registrationId", 1L);
        row.put("registrationNo", "REG-TEST");
        row.put("competitionId", 11L);
        row.put("competitionCode", "AIADC2026");
        row.put("competitionTitle", "AIADC 2026");
        row.put("teamId", 21L);
        row.put("teamName", "AI Team");
        row.put("projectId", 31L);
        row.put("projectTitle", "AI Project");
        row.put("ownerUserId", 1001L);
        row.put("registrationStatus", "PENDING_PAYMENT");
        row.put("participantNo", null);
        row.put("memberCount", 2);
        row.put("payableAmountMinor", 8_800L);
        row.put("orderNo", "REG-1-ABCD");
        row.put("providerCode", "manual");
        row.put("providerOrderNo", "manual-REG-1-ABCD");
        row.put("subject", "Competition registration REG-TEST");
        row.put("amountMinor", 8_800L);
        row.put("currency", "CNY");
        row.put("paymentStatus", "PENDING");
        row.put("paymentUrl", "/payment/orders/REG-1-ABCD");
        row.put("registrationCreatedAt", LocalDateTime.now());
        row.put("updatedAt", LocalDateTime.now());
        sql.paymentRecordRows = List.of(row);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));

        PageResponse<CompetitionRegistrationVO.PaymentRecord> page = service.listPaymentRecords(
                paymentAdmin(),
                1,
                10,
                "AIADC",
                "PENDING",
                null,
                "manual"
        );

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        CompetitionRegistrationVO.PaymentRecord record = page.getRecords().get(0);
        assertThat(record.getOrderNo()).isEqualTo("REG-1-ABCD");
        assertThat(record.getRegistrationNo()).isEqualTo("REG-TEST");
        assertThat(record.getTeamName()).isEqualTo("AI Team");
        assertThat(record.getProjectTitle()).isEqualTo("AI Project");
        assertThat(record.getAmountMinor()).isEqualTo(8_800L);
        assertThat(record.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(sql.lastPaymentRecordCountSql)
                .contains("left join payment_order po on\npo.order_no collate utf8mb4_unicode_ci = cr.payment_order_no collate utf8mb4_unicode_ci")
                .doesNotContain("utf8mb4_unicode_ciand")
                .doesNotContain("onpo.");
        assertThat(sql.lastPaymentRecordQuerySql)
                .contains("left join payment_order po on\npo.order_no collate utf8mb4_unicode_ci = cr.payment_order_no collate utf8mb4_unicode_ci")
                .doesNotContain("utf8mb4_unicode_ciand")
                .doesNotContain("onpo.");
    }

    @Test
    void markPaidFromPaymentOrderConfirmsRegistrationOnceAndAssignsParticipantNo() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", "REG-1-ABCD", 8_800L);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));

        service.markPaidFromPaymentOrder("REG-1-ABCD");
        service.markPaidFromPaymentOrder("REG-1-ABCD");

        assertThat(sql.registration.get("status")).isEqualTo("CONFIRMED");
        assertThat(sql.registration.get("participantNo")).isEqualTo("AIADC2026-0001");
        assertThat(sql.confirmUpdates).isEqualTo(1);
    }

    private CompetitionRegistrationAppService service(RegistrationSql sql, TeamInternalApi teamInternalApi) {
        return new CompetitionRegistrationAppService(
                sql,
                objectMapper,
                objectProvider(teamInternalApi)
        );
    }

    private CompetitionRegistrationDTO.RegistrationCreateRequest registrationRequest() {
        CompetitionRegistrationDTO.RegistrationCreateRequest request = new CompetitionRegistrationDTO.RegistrationCreateRequest();
        request.setCompetitionId(11L);
        request.setTeamId(21L);
        request.setProjectId(31L);
        return request;
    }

    private CompetitionRegistrationDTO.RegistrationCreateRequest inlineRegistrationRequest() {
        CompetitionRegistrationDTO.RegistrationCreateRequest request = new CompetitionRegistrationDTO.RegistrationCreateRequest();
        request.setCompetitionId(11L);
        request.setProjectId(31L);
        CompetitionRegistrationDTO.TeamSnapshotRequest team = new CompetitionRegistrationDTO.TeamSnapshotRequest();
        team.setTeamName("Collected Team");
        team.setTeamType("COMPETITION");
        request.setTeamSnapshot(team);
        CompetitionRegistrationDTO.MemberSnapshotRequest first = new CompetitionRegistrationDTO.MemberSnapshotRequest();
        first.setMemberName("Alice");
        first.setRole("MEMBER");
        first.setExtraValues(Map.of("mobile", "13800138000"));
        CompetitionRegistrationDTO.MemberSnapshotRequest second = new CompetitionRegistrationDTO.MemberSnapshotRequest();
        second.setMemberName("Bob");
        second.setRole("MEMBER");
        second.setExtraValues(Map.of("mobile", "13900139000"));
        request.setMembers(List.of(first, second));
        return request;
    }

    private CurrentUser student() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("student");
        currentUser.setPermissions(Set.of("aiadc:registration:create", "aiadc:registration:pay"));
        return currentUser;
    }

    private CurrentUser paymentAdmin() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1002L);
        currentUser.setUsername("payment-admin");
        currentUser.setPermissions(Set.of("payment:order:view"));
        return currentUser;
    }

    private TeamInternalApi teamApiWithMembers(Long userId, int memberCount) {
        return new TeamInternalApi() {
            @Override
            public TeamSummaryDTO getTeam(Long teamId) {
                TeamSummaryDTO team = new TeamSummaryDTO();
                team.setId(teamId);
                team.setTeamCode("TEAM-001");
                team.setTeamName("AI Team");
                team.setTeamType("competition");
                team.setVisibility("PRIVATE");
                team.setOwnerUserId(userId);
                team.setStatus("ACTIVE");
                return team;
            }

            @Override
            public List<TeamMemberDTO> listActiveMembers(Long teamId) {
                List<TeamMemberDTO> members = new ArrayList<>();
                for (int i = 0; i < memberCount; i += 1) {
                    TeamMemberDTO member = new TeamMemberDTO();
                    member.setId(100L + i);
                    member.setTeamId(teamId);
                    member.setUserId(userId + i);
                    member.setRole(i == 0 ? "OWNER" : "MEMBER");
                    member.setStatus("ACTIVE");
                    member.setJoinedAt(LocalDateTime.now());
                    members.add(member);
                }
                return members;
            }

            @Override
            public TeamMemberDTO requireActiveMember(Long teamId, Long userId) {
                TeamMemberDTO member = new TeamMemberDTO();
                member.setTeamId(teamId);
                member.setUserId(userId);
                member.setStatus("ACTIVE");
                return member;
            }

            @Override public boolean isTeamOwner(Long teamId, Long userId) { return true; }
            @Override public boolean isTeamAdmin(Long teamId, Long userId) { return true; }
            @Override public boolean isTeamManager(Long teamId, Long userId) { return true; }
        };
    }

    private TeamInternalApi teamApiRejectingMembershipCheck(Long userId, int memberCount) {
        return new TeamInternalApi() {
            private final TeamInternalApi delegate = teamApiWithMembers(userId, memberCount);

            @Override
            public TeamSummaryDTO getTeam(Long teamId) {
                return delegate.getTeam(teamId);
            }

            @Override
            public List<TeamMemberDTO> listActiveMembers(Long teamId) {
                return delegate.listActiveMembers(teamId);
            }

            @Override
            public TeamMemberDTO requireActiveMember(Long teamId, Long userId) {
                throw new AssertionError("Registration must not require applicant team membership");
            }

            @Override public boolean isTeamOwner(Long teamId, Long userId) { return delegate.isTeamOwner(teamId, userId); }
            @Override public boolean isTeamAdmin(Long teamId, Long userId) { return delegate.isTeamAdmin(teamId, userId); }
            @Override public boolean isTeamManager(Long teamId, Long userId) { return delegate.isTeamManager(teamId, userId); }
        };
    }

    private TeamInternalApi teamApiRejectingLookup() {
        return new TeamInternalApi() {
            @Override public TeamSummaryDTO getTeam(Long teamId) { throw new AssertionError("Inline registration must not read Team module"); }
            @Override public List<TeamMemberDTO> listActiveMembers(Long teamId) { throw new AssertionError("Inline registration must not read Team members"); }
            @Override public TeamMemberDTO requireActiveMember(Long teamId, Long userId) { throw new AssertionError("Inline registration must not require team membership"); }
            @Override public boolean isTeamOwner(Long teamId, Long userId) { return false; }
            @Override public boolean isTeamAdmin(Long teamId, Long userId) { return false; }
            @Override public boolean isTeamManager(Long teamId, Long userId) { return false; }
        };
    }

    private ObjectProvider<TeamInternalApi> objectProvider(TeamInternalApi teamInternalApi) {
        return new ObjectProvider<>() {
            @Override public TeamInternalApi getObject(Object... args) { return teamInternalApi; }
            @Override public TeamInternalApi getIfAvailable() { return teamInternalApi; }
            @Override public TeamInternalApi getIfUnique() { return teamInternalApi; }
            @Override public TeamInternalApi getObject() { return teamInternalApi; }
            @Override public Iterator<TeamInternalApi> iterator() { return List.of(teamInternalApi).iterator(); }
            @Override public Stream<TeamInternalApi> stream() { return Stream.of(teamInternalApi); }
            @Override public Stream<TeamInternalApi> orderedStream() { return stream(); }
        };
    }

    private static final class RegistrationSql extends MyBatisQueryOperations {
        private String competitionFeeMode = "TEAM";
        private Long competitionEntryFeeMinor = 0L;
        private Map<String, Object> registration;
        private Map<String, Object> stageForm;
        private Long lastInsertedId = 1L;
        private Long preliminaryStageId;
        private Long submittedMaterialCount = 0L;
        private int paymentOrderInserts;
        private int materialValueInserts;
        private int confirmUpdates;
        private boolean wroteTeamTables;
        private String paymentRequestJson;
        private String paymentOrderNo;
        private String lastPaymentRecordCountSql;
        private String lastPaymentRecordQuerySql;
        private List<Map<String, Object>> materialSubmissions = List.of();
        private List<Map<String, Object>> materialValues = List.of();
        private List<Map<String, Object>> paymentRecordRows = List.of();

        void seedRegistration(Long id, String status, String paymentOrderNo, Long payableAmountMinor) {
            registration = newRegistration(id, status, paymentOrderNo, payableAmountMinor);
            this.paymentOrderNo = paymentOrderNo;
        }

        @Override
        public int update(String sql, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("insert into team")
                    || normalized.contains("update team")
                    || normalized.contains("delete from team")
                    || normalized.contains("insert into team_member")
                    || normalized.contains("update team_member")
                    || normalized.contains("delete from team_member")) {
                wroteTeamTables = true;
            }
            if (normalized.contains("insert into competition_registration")) {
                registration = newRegistration(lastInsertedId, String.valueOf(args[5]), null, ((Number) args[9]).longValue());
                registration.put("registrationNo", args[0]);
                registration.put("competitionId", args[1]);
                registration.put("teamId", args[2]);
                registration.put("projectId", args[3]);
                registration.put("ownerUserId", args[4]);
                registration.put("feeMode", args[6]);
                registration.put("entryFeeMinor", args[7]);
                registration.put("memberCount", args[8]);
                registration.put("currency", args[10]);
                registration.put("teamSnapshotJson", args[11]);
                registration.put("projectSnapshotJson", args[12]);
                registration.put("memberSnapshotJson", args[13]);
                return 1;
            }
            if (normalized.contains("insert into registration_material_submission")) {
                lastInsertedId = 91L;
                return 1;
            }
            if (normalized.contains("insert into registration_material_value")) {
                materialValueInserts += 1;
                return 1;
            }
            if (normalized.contains("insert into payment_order")) {
                paymentOrderInserts += 1;
                paymentOrderNo = String.valueOf(args[0]);
                paymentRequestJson = String.valueOf(args[10]);
                return 1;
            }
            if (normalized.contains("update competition_registration set payment_order_no")) {
                registration.put("paymentOrderNo", args[0]);
                return 1;
            }
            if (normalized.contains("set status = 'confirmed'")) {
                if (registration.get("participantNo") == null) {
                    registration.put("status", "CONFIRMED");
                    registration.put("participantNo", args[0]);
                    if (args[1] != null) {
                        registration.put("paymentOrderNo", args[1]);
                    }
                    confirmUpdates += 1;
                    return 1;
                }
                return 0;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("last_insert_id")) {
                return requiredType.cast(lastInsertedId);
            }
            if (normalized.contains("from competition_stage")) {
                return requiredType.cast(preliminaryStageId);
            }
            if (normalized.contains("from registration_material_submission")) {
                return requiredType.cast(submittedMaterialCount);
            }
            if (normalized.contains("from competition_registration cr")) {
                lastPaymentRecordCountSql = sql;
                return requiredType.cast((long) paymentRecordRows.size());
            }
            if (normalized.contains("count(1) + 1 from competition_registration")) {
                return requiredType.cast(1L);
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("from aiadc_competition")) {
                return map(rowMapper, Map.of(
                        "id", 11L,
                        "code", "AIADC2026",
                        "feeMode", competitionFeeMode,
                        "entryFeeMinor", competitionEntryFeeMinor,
                        "currency", "CNY"
                ));
            }
            if (normalized.contains("from competition_registration")) {
                if (registration == null) {
                    return null;
                }
                if (normalized.contains("where payment_order_no") && args.length > 0 && !String.valueOf(args[0]).equals(registration.get("paymentOrderNo"))) {
                    return null;
                }
                return map(rowMapper, registration);
            }
            if (normalized.contains("from competition_stage_form")) {
                return stageForm == null ? null : map(rowMapper, stageForm);
            }
            if (normalized.contains("from payment_order")) {
                if (paymentOrderNo == null || args.length > 0 && !paymentOrderNo.equals(args[0])) {
                    return null;
                }
                return map(rowMapper, Map.of(
                        "orderNo", paymentOrderNo,
                        "amountMinor", registration.get("payableAmountMinor"),
                        "currency", registration.get("currency"),
                        "status", "PENDING",
                        "paymentUrl", "/payment/orders/" + paymentOrderNo
                ));
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("from aiadc_project")) {
                return List.of(new LinkedHashMap<>(Map.of(
                        "id", 31L,
                        "code", "PROJ-001",
                        "locale", "zh",
                        "title", "AI Project",
                        "category", "ai",
                        "description", "Project description",
                        "status", "draft"
                )));
            }
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("from registration_material_submission")) {
                return materialSubmissions.stream().map((row) -> map(rowMapper, row)).toList();
            }
            if (normalized.contains("from registration_material_value")) {
                return materialValues.stream().map((row) -> map(rowMapper, row)).toList();
            }
            if (normalized.contains("from competition_registration cr")) {
                lastPaymentRecordQuerySql = sql;
                return paymentRecordRows.stream().map((row) -> map(rowMapper, row)).toList();
            }
            return super.query(sql, rowMapper, args);
        }

        private Map<String, Object> newRegistration(Long id, String status, String paymentOrderNo, Long payableAmountMinor) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("registrationNo", "REG-TEST");
            row.put("competitionId", 11L);
            row.put("teamId", 21L);
            row.put("projectId", 31L);
            row.put("ownerUserId", 1001L);
            row.put("status", status);
            row.put("feeMode", competitionFeeMode);
            row.put("entryFeeMinor", competitionEntryFeeMinor);
            row.put("memberCount", 1);
            row.put("payableAmountMinor", payableAmountMinor);
            row.put("currency", "CNY");
            row.put("paymentOrderNo", paymentOrderNo);
            row.put("participantNo", null);
            row.put("teamSnapshotJson", "{}");
            row.put("projectSnapshotJson", "{}");
            row.put("memberSnapshotJson", "[]");
            row.put("createdAt", LocalDateTime.now());
            row.put("updatedAt", LocalDateTime.now());
            return row;
        }

        private <T> T map(RowMapper<T> rowMapper, Map<String, Object> values) {
            try {
                return rowMapper.mapRow(new SqlRow(values), 0);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
