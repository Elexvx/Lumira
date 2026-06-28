package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CompetitionRegistrationAppService {

    private static final String PAYMENT_ORDER_JOIN_ON =
            "po.order_no collate utf8mb4_unicode_ci = cr.payment_order_no collate utf8mb4_unicode_ci ";
    private static final Set<String> REGISTRATION_STATUSES = Set.of("DRAFT", "PENDING_PAYMENT", "PAID", "CONFIRMED", "CANCELLED");
    private static final Set<String> STAGE_CODES = Set.of("PRELIMINARY", "FINAL");
    private static final Set<String> STAGE_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED", "CLOSED");
    private static final Set<String> FORM_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> FIELD_TYPES = Set.of("input", "textarea", "file");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TeamInternalApi> teamInternalApiProvider;

    public CompetitionRegistrationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.teamInternalApiProvider = teamInternalApiProvider;
    }

    public PageResponse<CompetitionRegistrationVO.Registration> listRegistrations(CurrentUser currentUser, long pageNo, long pageSize) {
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        String where = " from competition_registration where deleted = 0";
        if (!canViewAllRegistrations(currentUser)) {
            where += " and owner_user_id = ?";
            params.add(requireUserId(currentUser));
        }
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((safePageNo - 1) * safePageSize);
        selectParams.add(safePageSize);
        List<CompetitionRegistrationVO.Registration> records = jdbcTemplate.query(
                registrationSelect() + where + " order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                selectParams.toArray()
        );
        PageResponse<CompetitionRegistrationVO.Registration> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        response.setHasMore(safePageNo * safePageSize < response.getTotal());
        return response;
    }

    public PageResponse<CompetitionRegistrationVO.PaymentRecord> listPaymentRecords(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            String keyword,
            String paymentStatus,
            String registrationStatus,
            String providerCode
    ) {
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                from competition_registration cr
                left join payment_order po on
                """ + PAYMENT_ORDER_JOIN_ON + """
                and po.deleted = 0
                left join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                left join aiadc_project p on p.id = cr.project_id and p.deleted = 0
                left join team t on t.id = cr.team_id and t.deleted = 0
                where cr.deleted = 0
                """);
        if (!canViewAllPaymentRecords(currentUser)) {
            where.append(" and cr.owner_user_id = ?");
            params.add(requireUserId(currentUser));
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                         cr.registration_no like ?
                         or cr.participant_no like ?
                         or cr.payment_order_no like ?
                         or po.provider_order_no like ?
                         or c.code like ?
                         or c.title like ?
                         or t.team_name like ?
                         or p.title like ?
                     )
                    """);
            String likeKeyword = "%" + normalizedKeyword + "%";
            for (int i = 0; i < 8; i += 1) {
                params.add(likeKeyword);
            }
        }
        String normalizedPaymentStatus = trimToNull(paymentStatus);
        if (normalizedPaymentStatus != null) {
            where.append(" and coalesce(po.status, cr.status) = ?");
            params.add(normalizedPaymentStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedRegistrationStatus = trimToNull(registrationStatus);
        if (normalizedRegistrationStatus != null) {
            where.append(" and cr.status = ?");
            params.add(normalizedRegistrationStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedProviderCode = trimToNull(providerCode);
        if (normalizedProviderCode != null) {
            where.append(" and po.provider_code = ?");
            params.add(normalizedProviderCode);
        }

        Long total = jdbcTemplate.queryForObject("select count(1) " + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((safePageNo - 1) * safePageSize);
        selectParams.add(safePageSize);
        List<CompetitionRegistrationVO.PaymentRecord> records = jdbcTemplate.query(
                paymentRecordSelect()
                        + where
                        + """
                         order by coalesce(po.created_at, cr.updated_at, cr.created_at) desc, cr.id desc
                         limit ?, ?
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.PaymentRecord.class),
                selectParams.toArray()
        );
        PageResponse<CompetitionRegistrationVO.PaymentRecord> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        response.setHasMore(safePageNo * safePageSize < response.getTotal());
        return response;
    }

    public CompetitionRegistrationVO.Registration getRegistration(CurrentUser currentUser, Long id) {
        CompetitionRegistrationVO.Registration registration = findRegistration(id);
        if (registration == null || !canAccessRegistration(currentUser, registration)) {
            throw biz(ErrorCode.NOT_FOUND, "Registration not found");
        }
        return registration;
    }

    public List<CompetitionRegistrationVO.MaterialSubmission> listMaterials(CurrentUser currentUser, Long registrationId) {
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        List<CompetitionRegistrationVO.MaterialSubmission> submissions = jdbcTemplate.query(
                """
                        select id, registration_id as registrationId, competition_id as competitionId,
                               stage_id as stageId, form_version as formVersion, submitter_user_id as submitterUserId,
                               status, submitted_at as submittedAt, locked_at as lockedAt
                        from registration_material_submission
                        where registration_id = ? and competition_id = ? and deleted = 0
                        order by stage_id asc, id desc
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.MaterialSubmission.class),
                                registrationId,
                registration.getCompetitionId()
        );
        for (CompetitionRegistrationVO.MaterialSubmission submission : submissions) {
            submission.setValues(jdbcTemplate.query(
                    """
                            select id, submission_id as submissionId, field_key as fieldKey, field_type as fieldType,
                                   text_value as textValue, file_id as fileId, json_value as jsonValue
                            from registration_material_value
                            where submission_id = ? and deleted = 0
                            order by id asc
                            """,
                    new BeanPropertyRowMapper<>(CompetitionRegistrationVO.MaterialValue.class),
                                        submission.getId()
            ));
        }
        return submissions;
    }

    @Transactional
    public CompetitionRegistrationVO.Registration createRegistration(CurrentUser currentUser, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        Long userId = requireUserId(currentUser);
        CompetitionRow competition = requireCompetition(request.getCompetitionId());
        TeamSnapshot team = resolveTeamSnapshot(request.getTeamId());
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId());
        int memberCount = team.members().size();
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        jdbcTemplate.update(
                """
                        insert into competition_registration (
                            registration_no, competition_id, team_id, project_id, owner_user_id,
                            status, fee_mode, entry_fee_minor, member_count, payable_amount_minor, currency,
                            team_snapshot_json, project_snapshot_json, member_snapshot_json, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                                generateRegistrationNo(),
                competition.id(),
                team.teamId(),
                project.projectId(),
                userId,
                "PENDING_PAYMENT",
                competition.feeMode(),
                competition.entryFeeMinor(),
                memberCount,
                payableAmountMinor,
                competition.currency(),
                serialize(team.summary()),
                serialize(project.summary()),
                serialize(team.members()),
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getRegistration(currentUser, id);
    }

    @Transactional
    public CompetitionRegistrationVO.Registration updateRegistration(CurrentUser currentUser, Long id, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        CompetitionRegistrationVO.Registration existing = getRegistration(currentUser, id);
        CompetitionRow competition = requireCompetition(request.getCompetitionId());
        TeamSnapshot team = resolveTeamSnapshot(request.getTeamId());
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId());
        int memberCount = team.members().size();
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        if (!Set.of("DRAFT", "PENDING_PAYMENT").contains(existing.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Paid registrations cannot be changed");
        }
        jdbcTemplate.update(
                """
                        update competition_registration
                        set competition_id = ?, team_id = ?, project_id = ?, fee_mode = ?, entry_fee_minor = ?,
                            member_count = ?, payable_amount_minor = ?, currency = ?, team_snapshot_json = ?,
                            project_snapshot_json = ?, member_snapshot_json = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                competition.id(),
                team.teamId(),
                project.projectId(),
                competition.feeMode(),
                competition.entryFeeMinor(),
                memberCount,
                payableAmountMinor,
                competition.currency(),
                serialize(team.summary()),
                serialize(project.summary()),
                serialize(team.members()),
                requireUserId(currentUser),
                LocalDateTime.now(),
                                id
        );
        return getRegistration(currentUser, id);
    }

    public List<CompetitionRegistrationVO.Stage> listStages(CurrentUser currentUser, Long competitionId) {
        requireCompetition(competitionId);
        return jdbcTemplate.query(
                stageSelect() + " from competition_stage where competition_id = ? and deleted = 0 order by sort asc, id asc",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                                competitionId
        );
    }

    @Transactional
    public CompetitionRegistrationVO.Stage createStage(CurrentUser currentUser, Long competitionId, CompetitionRegistrationDTO.StageUpsertRequest request) {
        requireCompetition(competitionId);
        String stageCode = normalizeEnum(request.getStageCode(), null, STAGE_CODES, "Invalid stage code");
        jdbcTemplate.update(
                """
                        insert into competition_stage (
                            competition_id, stage_code, stage_name, status, sort, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                                competitionId,
                stageCode,
                trimRequired(request.getStageName(), "Stage name is required"),
                normalizeEnum(request.getStatus(), "DRAFT", STAGE_STATUSES, "Invalid stage status"),
                request.getSort() == null ? 100 : request.getSort(),
                requireUserId(currentUser),
                requireUserId(currentUser)
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return findStage(id);
    }

    public CompetitionRegistrationVO.StageForm getStageForm(CurrentUser currentUser, Long stageId) {
        CompetitionRegistrationVO.StageForm form = findStageForm(stageId);
        if (form == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage form not found");
        }
        return form;
    }

    @Transactional
    public CompetitionRegistrationVO.StageForm upsertStageForm(CurrentUser currentUser, Long stageId, CompetitionRegistrationDTO.StageFormUpsertRequest request) {
        CompetitionRegistrationVO.Stage stage = findStage(stageId);
        if (stage == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage not found");
        }
        validateFormSchema(request.getFormSchemaJson());
        CompetitionRegistrationVO.StageForm existing = findStageForm(stageId);
        int version = request.getVersion() == null || request.getVersion() <= 0 ? 1 : request.getVersion();
        String status = normalizeEnum(request.getStatus(), "ENABLED", FORM_STATUSES, "Invalid form status");
        if (existing == null) {
            jdbcTemplate.update(
                    """
                            insert into competition_stage_form (
                                competition_id, stage_id, form_name, form_schema_json, version, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                                        stage.getCompetitionId(),
                    stageId,
                    trimRequired(request.getFormName(), "Form name is required"),
                    request.getFormSchemaJson(),
                    version,
                    status,
                    requireUserId(currentUser),
                    requireUserId(currentUser)
            );
        } else {
            jdbcTemplate.update(
                    """
                            update competition_stage_form
                            set form_name = ?, form_schema_json = ?, version = ?, status = ?, updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    trimRequired(request.getFormName(), "Form name is required"),
                    request.getFormSchemaJson(),
                    version,
                    status,
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                                        existing.getId()
            );
        }
        return getStageForm(currentUser, stageId);
    }

    @Transactional
    public CompetitionRegistrationVO.Registration submitMaterials(CurrentUser currentUser, Long registrationId, CompetitionRegistrationDTO.MaterialSubmitRequest request) {
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        CompetitionRegistrationVO.StageForm form = findStageForm(request.getStageId());
        if (form == null || !form.getCompetitionId().equals(registration.getCompetitionId())) {
            throw biz(ErrorCode.NOT_FOUND, "Stage form not found");
        }
        validateMaterialValues(form.getFormSchemaJson(), request.getValues());
        Long submissionId = jdbcTemplate.queryForObject(
                "select id from registration_material_submission where registration_id = ? and stage_id = ? and deleted = 0 limit 1",
                Long.class,
                                registrationId,
                request.getStageId()
        );
        if (submissionId == null) {
            jdbcTemplate.update(
                    """
                            insert into registration_material_submission (
                                registration_id, competition_id, stage_id, form_version, submitter_user_id,
                                status, submitted_at, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, 0)
                            """,
                                        registrationId,
                    registration.getCompetitionId(),
                    request.getStageId(),
                    form.getVersion(),
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                    requireUserId(currentUser),
                    requireUserId(currentUser)
            );
            submissionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            jdbcTemplate.update(
                    "update registration_material_submission set status = 'SUBMITTED', submitted_at = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    LocalDateTime.now(),
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                                        submissionId
            );
            jdbcTemplate.update("delete from registration_material_value where submission_id = ?", submissionId);
        }
        for (CompetitionRegistrationDTO.MaterialValueRequest value : request.getValues()) {
            jdbcTemplate.update(
                    """
                            insert into registration_material_value (
                                submission_id, field_key, field_type, text_value, file_id, json_value, deleted
                            ) values (?, ?, ?, ?, ?, ?, 0)
                            """,
                                        submissionId,
                    trimRequired(value.getFieldKey(), "Material field key is required"),
                    normalizeFieldType(value.getFieldType()),
                    trimToNull(value.getTextValue()),
                    value.getFileId(),
                    trimToNull(value.getJsonValue())
            );
        }
        return getRegistration(currentUser, registrationId);
    }

    @Transactional
    public CompetitionRegistrationVO.PaymentOrder createPaymentOrder(CurrentUser currentUser, Long registrationId, CompetitionRegistrationDTO.PaymentOrderRequest request) {
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        if (!"PENDING_PAYMENT".equals(registration.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only pending-payment registrations can create payment orders");
        }
        requireSubmittedPreliminaryMaterials(registration);
        if (registration.getPayableAmountMinor() == null || registration.getPayableAmountMinor() == 0L) {
            confirmPaidRegistration(registration.getId(), null);
            CompetitionRegistrationVO.PaymentOrder freeOrder = new CompetitionRegistrationVO.PaymentOrder();
            freeOrder.setOrderNo(null);
            freeOrder.setAmountMinor(0L);
            freeOrder.setCurrency(registration.getCurrency());
            freeOrder.setStatus("CONFIRMED");
            return freeOrder;
        }
        String existingOrderNo = registration.getPaymentOrderNo();
        if (StringUtils.hasText(existingOrderNo)) {
            CompetitionRegistrationVO.PaymentOrder existing = findPaymentOrder(existingOrderNo);
            if (existing != null) {
                return existing;
            }
        }
        String orderNo = "REG-" + registration.getId() + "-" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36).toUpperCase(Locale.ROOT);
        String providerCode = StringUtils.hasText(request.getProviderCode()) ? request.getProviderCode().trim() : "manual";
        String idempotencyKey = "competition-registration-" + registration.getId();
        Map<String, Object> metadata = Map.of(
                "bizType", "competition_registration",
                "registrationId", registration.getId(),
                "competitionId", registration.getCompetitionId(),
                "teamId", registration.getTeamId(),
                "projectId", registration.getProjectId()
        );
        jdbcTemplate.update(
                """
                        insert into payment_order (
                            order_no, provider_code, provider_order_no, subject, amount_minor, currency,
                            status, payment_url, client_ip, notify_url, return_url, request_json, response_json,
                            idempotency_key, expires_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                orderNo,
                providerCode,
                providerCode + "-" + orderNo,
                "Competition registration " + registration.getRegistrationNo(),
                registration.getPayableAmountMinor(),
                registration.getCurrency(),
                "/payment/orders/" + orderNo,
                trimToNull(request.getClientIp()),
                trimToNull(request.getNotifyUrl()),
                trimToNull(request.getReturnUrl()),
                serialize(Map.of(
                        "providerCode", providerCode,
                        "orderNo", orderNo,
                        "subject", "Competition registration " + registration.getRegistrationNo(),
                        "amountMinor", registration.getPayableAmountMinor(),
                        "currency", registration.getCurrency(),
                        "metadata", metadata
                )),
                serialize(Map.of("providerCode", providerCode, "providerOrderNo", providerCode + "-" + orderNo, "paymentUrl", "/payment/orders/" + orderNo)),
                idempotencyKey,
                LocalDateTime.now().plusHours(2),
                requireUserId(currentUser),
                requireUserId(currentUser)
        );
        jdbcTemplate.update(
                "update competition_registration set payment_order_no = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                orderNo,
                requireUserId(currentUser),
                LocalDateTime.now(),
                registration.getId()
        );
        return findPaymentOrder(orderNo);
    }

    public CompetitionRegistrationVO.PaymentOrder getPaymentStatus(CurrentUser currentUser, Long registrationId) {
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        if (!StringUtils.hasText(registration.getPaymentOrderNo())) {
            CompetitionRegistrationVO.PaymentOrder order = new CompetitionRegistrationVO.PaymentOrder();
            order.setAmountMinor(registration.getPayableAmountMinor());
            order.setCurrency(registration.getCurrency());
            order.setStatus(registration.getStatus());
            return order;
        }
        CompetitionRegistrationVO.PaymentOrder order = findPaymentOrder(registration.getPaymentOrderNo());
        if (order == null) {
            throw biz(ErrorCode.NOT_FOUND, "Payment order not found");
        }
        return order;
    }

    @Transactional
    public void markPaidFromPaymentOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        CompetitionRegistrationVO.Registration registration = findRegistrationByPaymentOrder(orderNo);
        if (registration != null) {
            confirmPaidRegistration(registration.getId(), orderNo);
        }
    }

    @Transactional
    public CompetitionRegistrationVO.PaymentOrder simulatePayment(CurrentUser currentUser, Long registrationId) {
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        if (!Set.of("PENDING_PAYMENT", "PAID", "CONFIRMED").contains(registration.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only pending-payment registrations can be paid");
        }

        String orderNo = registration.getPaymentOrderNo();
        if (!StringUtils.hasText(orderNo)) {
            orderNo = "MOCK-REG-" + registration.getId() + "-" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L * 36L), 36).toUpperCase(Locale.ROOT);
            String providerOrderNo = "mock-" + orderNo;
            jdbcTemplate.update(
                    """
                            insert into payment_order (
                                order_no, provider_code, provider_order_no, subject, amount_minor, currency,
                                status, payment_url, request_json, response_json, idempotency_key,
                                expires_at, paid_at, created_by, updated_by, deleted
                            ) values (?, 'mock', ?, ?, ?, ?, 'PAID', ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    orderNo,
                    providerOrderNo,
                    "Mock competition registration " + registration.getRegistrationNo(),
                    registration.getPayableAmountMinor() == null ? 0L : registration.getPayableAmountMinor(),
                    registration.getCurrency(),
                    "/payment/mock/orders/" + orderNo,
                    serialize(Map.of("providerCode", "mock", "orderNo", orderNo, "registrationId", registration.getId())),
                    serialize(Map.of("providerCode", "mock", "providerOrderNo", providerOrderNo, "status", "PAID")),
                    "mock-competition-registration-" + registration.getId(),
                    LocalDateTime.now().plusHours(2),
                    LocalDateTime.now(),
                    requireUserId(currentUser),
                    requireUserId(currentUser)
            );
            jdbcTemplate.update(
                    "update competition_registration set payment_order_no = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    orderNo,
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                    registration.getId()
            );
        } else {
            jdbcTemplate.update(
                    """
                            update payment_order
                            set status = 'PAID', paid_at = coalesce(paid_at, ?), updated_by = ?, updated_at = ?
                            where order_no = ? and deleted = 0
                            """,
                    LocalDateTime.now(),
                    requireUserId(currentUser),
                    LocalDateTime.now(),
                    orderNo
            );
        }

        confirmPaidRegistration(registration.getId(), orderNo);
        return findPaymentOrder(orderNo);
    }

    private void confirmPaidRegistration(Long registrationId, String orderNo) {
        CompetitionRegistrationVO.Registration registration = findRegistration(registrationId);
        if (registration == null || StringUtils.hasText(registration.getParticipantNo())) {
            return;
        }
        String participantNo = generateParticipantNo(registration.getCompetitionId());
        jdbcTemplate.update(
                """
                        update competition_registration
                        set status = 'CONFIRMED', participant_no = ?, payment_order_no = coalesce(?, payment_order_no),
                            updated_by = 0, updated_at = ?
                        where id = ? and deleted = 0
                          and participant_no is null
                        """,
                participantNo,
                orderNo,
                LocalDateTime.now(),
                                registrationId
        );
    }

    private void requireSubmittedPreliminaryMaterials(CompetitionRegistrationVO.Registration registration) {
                Long preliminaryStageId = jdbcTemplate.queryForObject(
                """
                        select id from competition_stage
                        where competition_id = ? and stage_code = 'PRELIMINARY' and deleted = 0
                        order by id asc limit 1
                        """,
                Long.class,
                                registration.getCompetitionId()
        );
        if (preliminaryStageId == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from registration_material_submission
                        where registration_id = ? and stage_id = ? and status in ('SUBMITTED', 'LOCKED') and deleted = 0
                        """,
                Long.class,
                                registration.getId(),
                preliminaryStageId
        );
        if (count == null || count == 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Preliminary materials must be submitted before payment");
        }
    }

    private TeamSnapshot resolveTeamSnapshot(Long teamId) {
        TeamInternalApi api = teamInternalApiProvider.getIfAvailable();
        if (api != null) {
            TeamSummaryDTO team = api.getTeam(teamId);
            if (team == null) {
                throw biz(ErrorCode.NOT_FOUND, "Team not found");
            }
            List<TeamMemberDTO> members = api.listActiveMembers(teamId);
            return new TeamSnapshot(teamId, team, members == null ? List.of() : members);
        }
        Map<String, Object> team = singleRow(
                """
                        select id, team_code as teamCode, team_name as teamName,
                               team_type as teamType, visibility, owner_user_id as ownerUserId, status
                        from team
                        where id = ? and deleted = 0
                        limit 1
                        """,
                                teamId
        );
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        List<Map<String, Object>> members = jdbcTemplate.queryForList(
                """
                        select id, team_id as teamId, user_id as userId, role, status,
                               extra_values_json as extraValuesJson, joined_at as joinedAt
                        from team_member
                        where team_id = ? and status = 'ACTIVE' and deleted = 0
                        order by id asc
                        """,
                                teamId
        );
        return new TeamSnapshot(teamId, team, members);
    }

    private ProjectSnapshot requireProjectSnapshot(Long projectId) {
        Map<String, Object> project = singleRow(
                """
                        select id, code, locale, title, category, description,
                               image_url as imageUrl, owner_name as ownerName, status, tags
                        from aiadc_project
                        where id = ? and deleted = 0
                        limit 1
                        """,
                                projectId
        );
        if (project == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return new ProjectSnapshot(projectId, project);
    }

    private CompetitionRow requireCompetition(Long competitionId) {
        CompetitionRow row = jdbcTemplate.queryForObject(
                """
                        select id, code, fee_mode as feeMode, entry_fee_minor as entryFeeMinor, currency
                        from aiadc_competition
                        where id = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRow.class),
                                competitionId
        );
        if (row == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return row;
    }

    private long calculatePayableAmount(String feeMode, Long entryFeeMinor, int memberCount) {
        long fee = entryFeeMinor == null ? 0L : entryFeeMinor;
        if (fee < 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Entry fee cannot be negative");
        }
        return "MEMBER".equalsIgnoreCase(feeMode) ? fee * Math.max(0, memberCount) : fee;
    }

    private void validateFormSchema(String schemaJson) {
        try {
            JsonNode fields = objectMapper.readTree(schemaJson).path("fields");
            if (!fields.isArray()) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Form schema fields must be an array");
            }
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String type = field.path("type").asText("");
                if (!StringUtils.hasText(key) || !FIELD_TYPES.contains(type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form field schema");
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form schema json");
        }
    }

    private void validateMaterialValues(String schemaJson, List<CompetitionRegistrationDTO.MaterialValueRequest> values) {
        Map<String, CompetitionRegistrationDTO.MaterialValueRequest> valueMap = new LinkedHashMap<>();
        for (CompetitionRegistrationDTO.MaterialValueRequest value : values == null ? List.<CompetitionRegistrationDTO.MaterialValueRequest>of() : values) {
            valueMap.put(trimRequired(value.getFieldKey(), "Material field key is required"), value);
            normalizeFieldType(value.getFieldType());
        }
        try {
            JsonNode fields = objectMapper.readTree(schemaJson).path("fields");
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String type = field.path("type").asText("");
                if (!FIELD_TYPES.contains(type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported material field type");
                }
                if (field.path("required").asBoolean(false) && isBlankMaterialValue(valueMap.get(key), type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Required material field is missing: " + key);
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form schema json");
        }
    }

    private boolean isBlankMaterialValue(CompetitionRegistrationDTO.MaterialValueRequest value, String type) {
        if (value == null) {
            return true;
        }
        if ("file".equals(type)) {
            return value.getFileId() == null && !StringUtils.hasText(value.getJsonValue());
        }
        return !StringUtils.hasText(value.getTextValue()) && !StringUtils.hasText(value.getJsonValue());
    }

    private String normalizeFieldType(String value) {
        String normalized = trimRequired(value, "Material field type is required");
        if (!FIELD_TYPES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported material field type");
        }
        return normalized;
    }

    private String generateParticipantNo(Long competitionId) {
        CompetitionRow competition = requireCompetition(competitionId);
        Long next = jdbcTemplate.queryForObject(
                "select count(1) + 1 from competition_registration where competition_id = ? and participant_no is not null and deleted = 0",
                Long.class,
                                competitionId
        );
        return competition.code().toUpperCase(Locale.ROOT) + "-" + String.format("%04d", next == null ? 1L : next);
    }

    private String generateRegistrationNo() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L), 36).toUpperCase(Locale.ROOT);
        return "REG-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-" + random;
    }

    private CompetitionRegistrationVO.Registration findRegistration(Long id) {
        return jdbcTemplate.queryForObject(
                registrationSelect() + " from competition_registration where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                                id
        );
    }

    private CompetitionRegistrationVO.Registration findRegistrationByPaymentOrder(String orderNo) {
        return jdbcTemplate.queryForObject(
                registrationSelect() + " from competition_registration where payment_order_no = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                                orderNo
        );
    }

    private CompetitionRegistrationVO.Stage findStage(Long id) {
        return jdbcTemplate.queryForObject(
                stageSelect() + " from competition_stage where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                                id
        );
    }

    private CompetitionRegistrationVO.StageForm findStageForm(Long stageId) {
        return jdbcTemplate.queryForObject(
                """
                        select id, competition_id as competitionId, stage_id as stageId,
                               form_name as formName, form_schema_json as formSchemaJson, version, status
                        from competition_stage_form
                        where stage_id = ? and status = 'ENABLED' and deleted = 0
                        order by version desc, id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.StageForm.class),
                                stageId
        );
    }

    private CompetitionRegistrationVO.PaymentOrder findPaymentOrder(String orderNo) {
        return jdbcTemplate.queryForObject(
                """
                        select order_no as orderNo, amount_minor as amountMinor, currency, status, payment_url as paymentUrl
                        from payment_order
                        where order_no = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.PaymentOrder.class),
                                orderNo
        );
    }

    private Map<String, Object> singleRow(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean canAccessRegistration(CurrentUser currentUser, CompetitionRegistrationVO.Registration registration) {
        return canViewAllRegistrations(currentUser) || requireUserId(currentUser).equals(registration.getOwnerUserId());
    }

    private boolean canViewAllPaymentRecords(CurrentUser currentUser) {
        return canViewAllRegistrations(currentUser) || hasPermission(currentUser, "payment:order:view");
    }

    private boolean canViewAllRegistrations(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains("aiadc:registration:manage")
                || currentUser.getPermissions().contains("aiadc:registration:update"));
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        return currentUser != null
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(permissionKey));
    }


    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw biz(ErrorCode.BIZ_ERROR, "Failed to serialize registration data");
        }
    }

    private String registrationSelect() {
        return """
                select id, registration_no as registrationNo, competition_id as competitionId,
                       team_id as teamId, project_id as projectId, owner_user_id as ownerUserId, status,
                       fee_mode as feeMode, entry_fee_minor as entryFeeMinor, member_count as memberCount,
                       payable_amount_minor as payableAmountMinor, currency, payment_order_no as paymentOrderNo,
                       participant_no as participantNo, team_snapshot_json as teamSnapshotJson,
                       project_snapshot_json as projectSnapshotJson, member_snapshot_json as memberSnapshotJson,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private String stageSelect() {
        return """
                select id, competition_id as competitionId, stage_code as stageCode,
                       stage_name as stageName, status, sort
                """;
    }

    private String paymentRecordSelect() {
        return """
                select cr.id as registrationId, cr.registration_no as registrationNo,
                       cr.competition_id as competitionId, c.code as competitionCode, c.title as competitionTitle,
                       cr.team_id as teamId, t.team_name as teamName,
                       cr.project_id as projectId, p.title as projectTitle,
                       cr.owner_user_id as ownerUserId, cr.status as registrationStatus,
                       cr.participant_no as participantNo, cr.member_count as memberCount,
                       cr.payable_amount_minor as payableAmountMinor,
                       coalesce(po.order_no, cr.payment_order_no) as orderNo,
                       po.provider_code as providerCode, po.provider_order_no as providerOrderNo,
                       po.subject as subject, coalesce(po.amount_minor, cr.payable_amount_minor) as amountMinor,
                       coalesce(po.currency, cr.currency) as currency,
                       coalesce(po.status, cr.status) as paymentStatus,
                       po.payment_url as paymentUrl, po.failure_code as failureCode, po.failure_message as failureMessage,
                       po.created_at as orderCreatedAt, po.paid_at as paidAt,
                       cr.created_at as registrationCreatedAt,
                       case when po.updated_at is null or po.updated_at < cr.updated_at then cr.updated_at else po.updated_at end as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private record TeamSnapshot(Long teamId, Object summary, List<?> members) {
    }

    private record ProjectSnapshot(Long projectId, Object summary) {
    }

    public static class CompetitionRow {
        private Long id;
        private String code;
        private String feeMode;
        private Long entryFeeMinor;
        private String currency;

        Long id() { return id; }
        String code() { return code == null ? "" : code; }
        String feeMode() { return feeMode == null ? "TEAM" : feeMode; }
        Long entryFeeMinor() { return entryFeeMinor == null ? 0L : entryFeeMinor; }
        String currency() { return currency == null ? "CNY" : currency; }
        public void setId(Long id) { this.id = id; }
        public void setCode(String code) { this.code = code; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}
