package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        assertThat(sql.lastRegistrationInsertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastRegistrationInsertArgs).contains(1001L, "user-uuid-1001");
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
    void createRegistrationPersistsRegistrationAndProjectExtraValues() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.competitionFeeMode = "MEMBER";
        sql.competitionEntryFeeMinor = 5_000L;
        CompetitionRegistrationAppService service = service(sql, teamApiRejectingLookup());
        CompetitionRegistrationDTO.RegistrationCreateRequest request = inlineRegistrationRequest();
        request.setRegistrationExtraValues(Map.of("contactName", "张三", "school", "AIADC University"));
        CompetitionRegistrationDTO.ProjectSnapshotRequest projectSnapshot = new CompetitionRegistrationDTO.ProjectSnapshotRequest();
        projectSnapshot.setExtraValues(Map.of("advisor", "李老师"));
        request.setProjectSnapshot(projectSnapshot);

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), request);

        JsonNode team = objectMapper.readTree(registration.getTeamSnapshotJson());
        JsonNode project = objectMapper.readTree(registration.getProjectSnapshotJson());
        assertThat(team.path("registrationExtraValues").path("contactName").asText()).isEqualTo("张三");
        assertThat(team.path("registrationExtraValues").path("school").asText()).isEqualTo("AIADC University");
        assertThat(project.path("extraValues").path("advisor").asText()).isEqualTo("李老师");
    }

    @Test
    void createRegistrationShouldRejectOversizedInlineTeamBeforeDatabaseOrTeamLookup() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, teamApiRejectingLookup());
        CompetitionRegistrationDTO.RegistrationCreateRequest request = inlineRegistrationRequest();
        request.getTeamSnapshot().setExtraValues(Map.of("payload", "x".repeat(10_001)));

        assertThatThrownBy(() -> service.createRegistration(student(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = new CompetitionRegistrationAppService(
                sql,
                objectMapper,
                objectProvider(teamApiRejectingLookup()),
                objectProvider((PaymentInternalApi) null),
                objectProvider((SystemInternalApi) null),
                null,
                null
        );

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        CompetitionRegistrationAppService service = new CompetitionRegistrationAppService(
                sql,
                objectMapper,
                objectProvider(teamApiRejectingLookup()),
                objectProvider((PaymentInternalApi) null),
                objectProvider((SystemInternalApi) null),
                permissionSnapshotService,
                null
        );

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectWhenLiveIdentityUserUuidMismatchesTrustedUser() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(new SystemUserSnapshotDTO(
                1001L,
                "user-uuid-mismatch",
                "student",
                null,
                "ENABLED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        CompetitionRegistrationAppService service =
                service(sql, teamApiRejectingLookup(), null, permissionSnapshotService, null, systemInternalApi);

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRefreshUsernameFromLiveIdentityBeforeUsingPermissionSnapshot() {
        RegistrationSql sql = new RegistrationSql();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        PermissionSnapshotService.PermissionSnapshot snapshot = mock(PermissionSnapshotService.PermissionSnapshot.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(new SystemUserSnapshotDTO(
                1001L,
                "user-uuid-1001",
                "student-live",
                null,
                "ENABLED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(snapshot);
        when(snapshot.getPermissions()).thenReturn(Set.of("aiadc:registration:create", "aiadc:registration:pay"));
        when(snapshot.getRoleIds()).thenReturn(Set.of());
        when(snapshot.getDeptIds()).thenReturn(Set.of());
        when(snapshot.getDescendantDeptIds()).thenReturn(Set.of());
        when(snapshot.getDataScopes()).thenReturn(List.of());
        when(snapshot.getVersion()).thenReturn("permissions-2");

        CompetitionRegistrationAppService service =
                service(sql, teamApiWithMembers(1001L, 1), null, permissionSnapshotService, null, systemInternalApi);
        CurrentUser currentUser = student();
        currentUser.setUsername("student-stale");

        service.createRegistration(currentUser, registrationRequest());

        assertThat(currentUser.getUsername()).isEqualTo("student-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(new SystemUserSnapshotDTO(
                        1001L,
                        "user-uuid-1001",
                        "student-live",
                        null,
                        "ENABLED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("aiadc:registration:create")));
        CompetitionRegistrationAppService service =
                service(sql, mock(TeamInternalApi.class), null, permissionSnapshotService, null, systemInternalApi);
        CurrentUser currentUser = student();
        currentUser.setSimulatedRoleId(0L);
        Method method = CompetitionRegistrationAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    @Test
    void createRegistrationShouldRejectTooManyInlineMembersBeforeDatabaseOrTeamLookup() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, teamApiRejectingLookup());
        CompetitionRegistrationDTO.RegistrationCreateRequest request = inlineRegistrationRequest();
        List<CompetitionRegistrationDTO.MemberSnapshotRequest> members = new ArrayList<>();
        for (int i = 0; i < 21; i += 1) {
            CompetitionRegistrationDTO.MemberSnapshotRequest member = new CompetitionRegistrationDTO.MemberSnapshotRequest();
            member.setMemberName("member-" + i);
            members.add(member);
        }
        request.setMembers(members);

        assertThatThrownBy(() -> service.createRegistration(student(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationReadsTeamSnapshotAsApplicant() {
        RegistrationSql sql = new RegistrationSql();
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 2));

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), registrationRequest());

        assertThat(registration.getTeamId()).isEqualTo(21L);
        assertThat(registration.getMemberCount()).isEqualTo(2);
    }

    @Test
    void createRegistrationShouldRejectWhenInsertMissesBeforeLastInsertId() {
        RegistrationSql sql = new RegistrationSql();
        sql.updateResults.add(0);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 2));

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Registration changed, please retry");
                });

        assertThat(sql.lastRegistrationInsertSql).contains("insert into competition_registration");
        assertThat(sql.lastInsertIdQueries).isZero();
    }

    @Test
    void updateRegistrationShouldRejectWhenFinalWriteMisses() {
        RegistrationSql sql = new RegistrationSql();
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 2));
        service.createRegistration(student(), registrationRequest());
        sql.updateResults.add(0);

        assertThatThrownBy(() -> service.updateRegistration(student(), 1L, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Registration changed, please retry");
                });

        assertThat(sql.lastRegistrationUpdateSql).contains("update competition_registration");
        assertThat(sql.lastRegistrationUpdateArgs).containsSubsequence(
                1L,
                sql.registration.get("registrationNo"),
                1001L,
                "user-uuid-1001",
                "PENDING_PAYMENT"
        );
    }

    @Test
    void updateRegistrationShouldRequireUpdatePermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:registration:create"));

        assertThatThrownBy(() -> service.updateRegistration(currentUser, 1L, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationFallbackTeamSnapshotKeepsUserUuids() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.competitionFeeMode = "MEMBER";
        sql.competitionEntryFeeMinor = 5_000L;
        CompetitionRegistrationAppService service = service(sql, (TeamInternalApi) null);

        CompetitionRegistrationVO.Registration registration = service.createRegistration(student(), registrationRequest());

        JsonNode team = objectMapper.readTree(registration.getTeamSnapshotJson());
        JsonNode members = objectMapper.readTree(registration.getMemberSnapshotJson());
        assertThat(team.path("ownerUserId").asLong()).isEqualTo(1001L);
        assertThat(team.path("ownerUserUuid").asText()).isEqualTo("user-uuid-1001");
        assertThat(members).hasSize(2);
        assertThat(members.get(0).path("userId").asLong()).isEqualTo(1001L);
        assertThat(members.get(0).path("userUuid").asText()).isEqualTo("user-uuid-1001");
    }

    @Test
    void listRegistrationsKeepsEditorsScopedToTheirOwnRecords() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "CONFIRMED", "REG-1-ABCD", 0L);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CurrentUser editor = new CurrentUser();
        editor.setUserId(1002L);
        editor.setUserUuid("user-uuid-1002");
        editor.setUsername("editor");
        editor.setSessionId("session-editor");
        editor.setSessionVersion(1);
        editor.setPermissionsVersion("permissions-1");
        editor.setAuthenticated(true);
        editor.setPermissions(Set.of("aiadc:registration:view", "aiadc:registration:update"));
        editor.setDataScopes(List.of(new DataPermissionRule("competition:registration", DataScopeType.SELF, List.of(), List.of())));

        PageResponse<CompetitionRegistrationVO.Registration> page = service.listRegistrations(editor, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(sql.lastRegistrationCountSql).contains("owner_user_id = ?");
        assertThat(sql.lastRegistrationQuerySql).contains("owner_user_id = ?");
    }

    @Test
    void listRegistrationsRespectsAllDataScopeFromRoleConfiguration() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "CONFIRMED", "REG-1-ABCD", 0L);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CurrentUser manager = new CurrentUser();
        manager.setUserId(1002L);
        manager.setUserUuid("user-uuid-1002");
        manager.setUsername("manager");
        manager.setSessionId("session-manager");
        manager.setSessionVersion(1);
        manager.setPermissionsVersion("permissions-1");
        manager.setAuthenticated(true);
        manager.setPermissions(Set.of("aiadc:registration:view"));
        manager.setDataScopes(List.of(new DataPermissionRule("competition:registration", DataScopeType.ALL, List.of(), List.of())));

        PageResponse<CompetitionRegistrationVO.Registration> page = service.listRegistrations(manager, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(sql.lastRegistrationCountSql).doesNotContain("owner_user_id = ?");
        assertThat(sql.lastRegistrationQuerySql).doesNotContain("owner_user_id = ?");
    }

    @Test
    void listRegistrationsShouldRejectBlankUsernameEvenWithAllDataScopeBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser manager = new CurrentUser();
        manager.setUserId(1002L);
        manager.setUsername(" ");
        manager.setAuthenticated(true);
        manager.setPermissions(Set.of("*"));
        manager.setDataScopes(List.of(new DataPermissionRule("competition:registration", DataScopeType.ALL, List.of(), List.of())));

        assertThatThrownBy(() -> service.listRegistrations(manager, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void listRegistrationsShouldRequireRegistrationReadAccessBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:competition:update"));

        assertThatThrownBy(() -> service.listRegistrations(currentUser, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void getRegistrationShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setUserUuid(null);

        assertThatThrownBy(() -> service.getRegistration(currentUser, 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void getRegistrationShouldRequireRegistrationReadAccessBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:competition:update"));

        assertThatThrownBy(() -> service.getRegistration(currentUser, 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void getPaymentStatusShouldRejectUntrustedUserBeforeDrainingPaymentQueue() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PaymentInternalApi paymentInternalApi = mock(PaymentInternalApi.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class), paymentInternalApi);
        CurrentUser currentUser = student();
        currentUser.setPermissionsVersion(null);

        assertThatThrownBy(() -> service.getPaymentStatus(currentUser, 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
        verifyNoInteractions(paymentInternalApi);
    }

    @Test
    void getPaymentStatusShouldRequireRegistrationReadAccessBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PaymentInternalApi paymentInternalApi = mock(PaymentInternalApi.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class), paymentInternalApi);
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:competition:update"));

        assertThatThrownBy(() -> service.getPaymentStatus(currentUser, 1L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
        verifyNoInteractions(paymentInternalApi);
    }

    @Test
    void listPaymentRecordsShouldRequirePaymentViewPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));

        assertThatThrownBy(() -> service.listPaymentRecords(student(), 1, 10, null, null, null, null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
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
    void submitMaterialsShouldCarryTrustedUserUuidWithNumericAuditFields() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.stageForm = Map.of(
                "id", 81L,
                "competitionId", 11L,
                "stageId", 71L,
                "formName", "Preliminary",
                "formSchemaJson", """
                        {"fields":[{"key":"project_intro","label":"Project intro","type":"textarea","required":false}]}
                        """,
                "version", 1,
                "status", "ENABLED"
        );
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CompetitionRegistrationDTO.MaterialValueRequest value = new CompetitionRegistrationDTO.MaterialValueRequest();
        value.setFieldKey("project_intro");
        value.setFieldType("textarea");
        value.setTextValue("A practical AI project.");
        CompetitionRegistrationDTO.MaterialSubmitRequest request = new CompetitionRegistrationDTO.MaterialSubmitRequest();
        request.setStageId(71L);
        request.setValues(List.of(value));

        service.submitMaterials(student(), 1L, request);

        assertThat(sql.lastMaterialSubmissionInsertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastMaterialSubmissionInsertArgs).contains(1001L, "user-uuid-1001");
        assertThat(sql.materialValueInserts).isEqualTo(1);
    }

    @Test
    void submitMaterialsShouldRequireMaterialSubmitPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:registration:view"));
        CompetitionRegistrationDTO.MaterialSubmitRequest request = new CompetitionRegistrationDTO.MaterialSubmitRequest();
        request.setStageId(71L);

        assertThatThrownBy(() -> service.submitMaterials(currentUser, 1L, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void submitMaterialsShouldConstrainExistingSubmissionByOwnerUuid() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.existingMaterialSubmissionId = 91L;
        sql.stageForm = Map.of(
                "id", 81L,
                "competitionId", 11L,
                "stageId", 71L,
                "formName", "Preliminary",
                "formSchemaJson", """
                        {"fields":[{"key":"project_intro","label":"Project intro","type":"textarea","required":false}]}
                        """,
                "version", 1,
                "status", "ENABLED"
        );
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CompetitionRegistrationDTO.MaterialValueRequest value = new CompetitionRegistrationDTO.MaterialValueRequest();
        value.setFieldKey("project_intro");
        value.setFieldType("textarea");
        value.setTextValue("A practical AI project.");
        CompetitionRegistrationDTO.MaterialSubmitRequest request = new CompetitionRegistrationDTO.MaterialSubmitRequest();
        request.setStageId(71L);
        request.setValues(List.of(value));

        service.submitMaterials(student(), 1L, request);

        assertThat(sql.lastMaterialSubmissionLookupSql)
                .contains("r.owner_user_id = ?")
                .contains("r.owner_user_uuid = ?");
        assertThat(sql.lastMaterialSubmissionUpdateSql)
                .contains("r.owner_user_id = ?")
                .contains("r.owner_user_uuid = ?");
        assertThat(sql.lastMaterialValueDeleteSql)
                .contains("update registration_material_value")
                .contains("set deleted = 1")
                .contains("r.owner_user_id = ?")
                .contains("r.owner_user_uuid = ?")
                .doesNotContain("delete from registration_material_value");
    }

    @Test
    void createRegistrationShouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setAuthenticated(false);
        currentUser.setPermissions(Set.of("*", "aiadc:registration:create"));

        assertThatThrownBy(() -> service.createRegistration(currentUser, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRequireCreatePermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:registration:view"));

        assertThatThrownBy(() -> service.createRegistration(currentUser, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectBlankUsernameBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.createRegistration(currentUser, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.createRegistration(currentUser, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectWhenLiveSnapshotMarksUserInactiveBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(false);
        CompetitionRegistrationAppService service =
                service(sql, mock(TeamInternalApi.class), null, permissionSnapshotService);

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
    }

    @Test
    void createRegistrationShouldRejectRevokedSessionTicketBeforeDatabaseOrTeamLookup() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        TeamInternalApi teamInternalApi = mock(TeamInternalApi.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        CompetitionRegistrationAppService service =
                service(sql, teamInternalApi, null, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createRegistration(student(), registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sql);
        verifyNoInteractions(teamInternalApi);
    }

    @Test
    void registrationMutationsShouldRejectNullRequestsAndInvalidIdsBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));

        assertThatThrownBy(() -> service.createRegistration(student(), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateRegistration(student(), 0L, registrationRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.submitMaterials(student(), -1L, new CompetitionRegistrationDTO.MaterialSubmitRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createPaymentOrder(student(), 0L, null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(sql);
    }

    @Test
    void stageOperationsShouldRejectNullRequestsAndInvalidIdsBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser manager = student();
        manager.setPermissions(Set.of("*"));

        assertThatThrownBy(() -> service.createStage(manager, 0L, new CompetitionRegistrationDTO.StageUpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createStage(manager, 11L, null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.upsertStageForm(manager, -1L, new CompetitionRegistrationDTO.StageFormUpsertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.upsertStageForm(manager, 71L, null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(sql);
    }

    @Test
    void createStageShouldRequireManagePermissionAtServiceLayer() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        CompetitionRegistrationDTO.StageUpsertRequest request = new CompetitionRegistrationDTO.StageUpsertRequest();
        request.setStageCode("PRELIMINARY");
        request.setStageName("Preliminary");

        assertThatThrownBy(() -> service.createStage(student(), 11L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");

        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any());
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void upsertStageFormShouldRequireManagePermissionAtServiceLayer() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        CompetitionRegistrationDTO.StageFormUpsertRequest request = new CompetitionRegistrationDTO.StageFormUpsertRequest();
        request.setFormName("Preliminary form");
        request.setFormSchemaJson("{\"fields\":[]}");

        assertThatThrownBy(() -> service.upsertStageForm(student(), 71L, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");

        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any());
        verify(jdbcTemplate, never()).update(anyString(), any());
    }

    @Test
    void listStagesShouldRequireReadPermissionBeforeLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:competition:update"));

        assertThatThrownBy(() -> service.listStages(currentUser, 11L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getStageFormShouldRequireReadPermissionBeforeLookup() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:competition:update"));

        assertThatThrownBy(() -> service.getStageForm(currentUser, 71L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getStageFormShouldAllowRegistrationPayPermissionWithoutCreatePermission() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:registration:pay"));
        when(jdbcTemplate.queryForObject(
                contains("join competition_stage s"),
                org.mockito.ArgumentMatchers.<RowMapper<CompetitionRegistrationVO.StageForm>>any(),
                eq(71L)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getStageForm(currentUser, 71L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void getStageFormShouldHideDraftStageFromRegistrationPermission() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(jdbcTemplate, teamApiWithMembers(1001L, 1));
        when(jdbcTemplate.queryForObject(
                contains("join aiadc_competition"),
                org.mockito.ArgumentMatchers.<RowMapper<CompetitionRegistrationVO.StageForm>>any(),
                eq(71L)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getStageForm(student(), 71L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void stageWritesShouldCarryTrustedUserUuidWithNumericAuditFields() {
        RegistrationSql sql = new RegistrationSql();
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));
        CurrentUser manager = student();
        manager.setPermissions(Set.of("*"));
        CompetitionRegistrationDTO.StageUpsertRequest stageRequest = new CompetitionRegistrationDTO.StageUpsertRequest();
        stageRequest.setStageCode("PRELIMINARY");
        stageRequest.setStageName("Preliminary");
        CompetitionRegistrationDTO.StageFormUpsertRequest formRequest = new CompetitionRegistrationDTO.StageFormUpsertRequest();
        formRequest.setFormName("Preliminary form");
        formRequest.setFormSchemaJson("{\"fields\":[]}");

        service.createStage(manager, 11L, stageRequest);
        service.upsertStageForm(manager, 71L, formRequest);

        assertThat(sql.lastStageInsertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastStageInsertArgs).contains(1001L, "user-uuid-1001");
        assertThat(sql.lastStageFormInsertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastStageFormInsertArgs).contains(1001L, "user-uuid-1001");
    }

    @Test
    void registrationAndMaterialWritesShouldBindOriginalBusinessAndOwnerContext() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CompetitionRegistrationAppService.java"));

        assertThat(source).contains(
                "where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?",
                "and status = ? and deleted = 0",
                "where id = ? and competition_id = ? and stage_id = ? and status = ? and deleted = 0",
                "where id = ? and registration_id = ? and stage_id = ? and form_version = ? and deleted = 0",
                "and s.registration_id = ?",
                "and s.stage_id = ?",
                "and s.form_version = ?",
                "Material submission changed, please retry",
                "Registration changed, please retry",
                "Competition stage changed, please retry",
                "Competition stage form changed, please retry",
                "requireRegistrationWrite(inserted, \"Registration changed, please retry\")",
                "requireRegistrationWrite(inserted, \"Competition stage changed, please retry\")",
                "requireRegistrationWrite(inserted, \"Competition stage form changed, please retry\")",
                "requireRegistrationWrite(inserted, \"Material submission changed, please retry\")",
                "update registration_material_value",
                "set deleted = 1"
        );
        assertThat(source).doesNotContain("delete from registration_material_value");
    }

    @Test
    void paymentOrderCreationIsIdempotentAndCarriesRegistrationMetadata() throws Exception {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.preliminaryStageId = 71L;
        sql.submittedMaterialCount = 1L;
        PaymentInternalApi paymentInternalApi = new PaymentInternalApi() {
            @Override
            public PaymentOrderDTO createOrder(Long operatorId, String operatorUuid, Long simulatedRoleId, PaymentCreateOrderRequestDTO request) {
                assertThat(operatorId).isEqualTo(1001L);
                assertThat(operatorUuid).isEqualTo("user-uuid-1001");
                assertThat(simulatedRoleId).isNull();
                sql.paymentOrderInserts += 1;
                sql.paymentOrderNo = request.orderNo();
                try {
                    sql.paymentRequestJson = objectMapper.writeValueAsString(request.metadata());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                sql.registration.put("paymentOrderNo", request.orderNo());
                return paymentOrder(request.orderNo(), request.amountMinor(), request.currency());
            }

            @Override
            public PaymentOrderDTO getOrder(Long operatorId, String operatorUuid, Long simulatedRoleId, String orderNo) {
                assertThat(operatorId).isEqualTo(1001L);
                assertThat(operatorUuid).isEqualTo("user-uuid-1001");
                assertThat(simulatedRoleId).isNull();
                return paymentOrder(orderNo, 8_800L, "CNY");
            }
        };
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1), paymentInternalApi);

        CompetitionRegistrationVO.PaymentOrder first = service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());
        CompetitionRegistrationVO.PaymentOrder second = service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());

        assertThat(first.getOrderNo()).isEqualTo(second.getOrderNo());
        assertThat(first.getAmountMinor()).isEqualTo(8_800L);
        assertThat(sql.paymentOrderInserts).isEqualTo(1);
        assertThat(sql.registration.get("paymentOrderNo")).isEqualTo(first.getOrderNo());
        assertThat(sql.registration.get("ownerUserUuid")).isEqualTo("user-uuid-1001");
        assertThat(sql.paymentOrderTask.get("ownerUserUuid")).isEqualTo("user-uuid-1001");
        assertThat(sql.lastPaymentTaskInsertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastPaymentTaskInsertSql)
                .doesNotContain("owner_user_uuid = values(owner_user_uuid),")
                .contains("registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then values(provider_code)")
                .contains("registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) and status in ('FAILED', 'DEAD')");
        assertThat(sql.lastPaymentTaskInsertArgs).contains(1001L, "user-uuid-1001");
        assertThat(sql.lastPaymentOrderAttachSql)
                .contains("registration_no = ?")
                .contains("owner_user_id = ?")
                .contains("owner_user_uuid = ?")
                .contains("payable_amount_minor = ?")
                .contains("currency = ?");
        assertThat(sql.lastPaymentOrderAttachArgs)
                .contains("REG-TEST", 1001L, "user-uuid-1001", 8_800L, "CNY");
        JsonNode metadata = objectMapper.readTree(sql.paymentRequestJson);
        assertThat(metadata.path("bizType").asText()).isEqualTo("competition_registration");
        assertThat(metadata.path("registrationId").asLong()).isEqualTo(1L);
        assertThat(metadata.path("competitionId").asLong()).isEqualTo(11L);
        assertThat(metadata.path("teamId").asLong()).isEqualTo(21L);
        assertThat(metadata.path("projectId").asLong()).isEqualTo(31L);
    }

    @Test
    void paymentOrderQueueShouldPreserveSimulatedRoleScope() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.preliminaryStageId = 71L;
        sql.submittedMaterialCount = 1L;
        PaymentInternalApi paymentInternalApi = new PaymentInternalApi() {
            @Override
            public PaymentOrderDTO createOrder(Long operatorId, String operatorUuid, Long simulatedRoleId, PaymentCreateOrderRequestDTO request) {
                assertThat(operatorId).isEqualTo(1001L);
                assertThat(operatorUuid).isEqualTo("user-uuid-1001");
                assertThat(simulatedRoleId).isEqualTo(9L);
                sql.registration.put("paymentOrderNo", request.orderNo());
                return paymentOrder(request.orderNo(), request.amountMinor(), request.currency());
            }

            @Override
            public PaymentOrderDTO getOrder(Long operatorId, String operatorUuid, Long simulatedRoleId, String orderNo) {
                assertThat(operatorId).isEqualTo(1001L);
                assertThat(operatorUuid).isEqualTo("user-uuid-1001");
                assertThat(simulatedRoleId).isEqualTo(9L);
                return paymentOrder(orderNo, 8_800L, "CNY");
            }
        };
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1), paymentInternalApi);
        CurrentUser simulatedStudent = student();
        simulatedStudent.setSimulatedRoleId(9L);

        CompetitionRegistrationVO.PaymentOrder order = service.createPaymentOrder(simulatedStudent, 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());

        assertThat(order.getStatus()).isIn("PENDING", "QUEUED");
        assertThat(sql.paymentOrderTask.get("simulatedRoleId")).isEqualTo(9L);
        assertThat(sql.lastPaymentTaskInsertSql).contains("simulated_role_id");
    }

    @Test
    void paymentOrderCreationShouldRejectWhenTaskInsertMisses() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.preliminaryStageId = 71L;
        sql.submittedMaterialCount = 1L;
        sql.updateResults.add(0);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1));

        assertThatThrownBy(() -> service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Payment order task changed, please retry");
                });

        assertThat(sql.lastPaymentTaskInsertSql).contains("insert into competition_payment_order_task");
        assertThat(sql.paymentOrderTask).isNotNull();
    }

    @Test
    void createPaymentOrderShouldRequirePayPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        CompetitionRegistrationAppService service = service(sql, mock(TeamInternalApi.class));
        CurrentUser currentUser = student();
        currentUser.setPermissions(Set.of("aiadc:registration:create"));

        assertThatThrownBy(() -> service.createPaymentOrder(currentUser, 1L, new CompetitionRegistrationDTO.PaymentOrderRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(sql);
    }

    @Test
    void paymentOrderTaskClaimShouldRequireOwnerUuidBoundToRegistrationAndUser() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/competition/app/CompetitionRegistrationAppService.java"));

        assertThat(source)
                .contains("r.owner_user_uuid = t.owner_user_uuid")
                .contains("u.id = r.owner_user_id")
                .contains("u.uuid = r.owner_user_uuid")
                .contains("t.owner_user_uuid is not null")
                .contains("owner_user_uuid as ownerUserUuid")
                .contains("and registration_id = ?")
                .contains("and owner_user_uuid = ?")
                .contains("and status = 'RUNNING'")
                .contains("select retry_count")
                .contains("Payment order task changed, please retry")
                .contains("Registration payment state changed, please retry");
    }

    @Test
    void drainPaymentOrderQueueShouldMarkTaskFailedWhenPaymentOwnerResolverIsUnavailable() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.seedPaymentOrderTask(1L, "user-uuid-1001");
        PaymentInternalApi paymentInternalApi = mock(PaymentInternalApi.class);
        CompetitionRegistrationAppService service = new CompetitionRegistrationAppService(
                sql,
                objectMapper,
                objectProvider(teamApiWithMembers(1001L, 1)),
                objectProvider(paymentInternalApi),
                objectProvider((SystemInternalApi) null),
                null
        );
        service.createPaymentOrder(student(), 1L, new CompetitionRegistrationDTO.PaymentOrderRequest());

        int processed = service.drainPaymentOrderQueue(5);

        assertThat(processed).isZero();
        assertThat(sql.paymentOrderTask).isNotNull();
        assertThat(sql.paymentOrderTask.get("status")).isEqualTo("FAILED");
        assertThat(sql.paymentOrderTask.get("processMessage")).isEqualTo("Trusted payment owner resolver is unavailable");
        verifyNoInteractions(paymentInternalApi);
    }

    @Test
    void drainPaymentOrderQueueShouldMarkTaskFailedWhenTaskOwnerUuidMismatchesRegistration() {
        RegistrationSql sql = new RegistrationSql();
        sql.seedRegistration(1L, "PENDING_PAYMENT", null, 8_800L);
        sql.seedPaymentOrderTask(1L, "user-uuid-9999");
        PaymentInternalApi paymentInternalApi = mock(PaymentInternalApi.class);
        CompetitionRegistrationAppService service = service(sql, teamApiWithMembers(1001L, 1), paymentInternalApi);

        int processed = service.drainPaymentOrderQueue(5);

        assertThat(processed).isZero();
        assertThat(sql.paymentOrderTask).isNotNull();
        assertThat(sql.paymentOrderTask.get("status")).isEqualTo("FAILED");
        assertThat(String.valueOf(sql.paymentOrderTask.get("processMessage"))).contains("Payment order task owner userUuid mismatch");
        verifyNoInteractions(paymentInternalApi);
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
        assertThat(sql.registration.get("updatedBy")).isEqualTo(1001L);
        assertThat(sql.confirmUpdates).isEqualTo(1);
        assertThat(sql.lastConfirmRegistrationSql)
                .contains("owner_user_id = ? and owner_user_uuid = ?");
    }

    private CompetitionRegistrationAppService service(RegistrationSql sql, TeamInternalApi teamInternalApi) {
        return service(sql, teamInternalApi, null);
    }

    private CompetitionRegistrationAppService service(RegistrationSql sql, TeamInternalApi teamInternalApi, PaymentInternalApi paymentInternalApi) {
        return service((MyBatisQueryOperations) sql, teamInternalApi, paymentInternalApi);
    }

    private CompetitionRegistrationAppService service(MyBatisQueryOperations sql, TeamInternalApi teamInternalApi) {
        return service(sql, teamInternalApi, null);
    }

    private CompetitionRegistrationAppService service(MyBatisQueryOperations sql, TeamInternalApi teamInternalApi, PaymentInternalApi paymentInternalApi) {
        return service(sql, teamInternalApi, paymentInternalApi, null);
    }

    private CompetitionRegistrationAppService service(
            MyBatisQueryOperations sql,
            TeamInternalApi teamInternalApi,
            PaymentInternalApi paymentInternalApi,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return service(sql, teamInternalApi, paymentInternalApi, permissionSnapshotService, null);
    }

    private CompetitionRegistrationAppService service(
            MyBatisQueryOperations sql,
            TeamInternalApi teamInternalApi,
            PaymentInternalApi paymentInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return service(
                sql,
                teamInternalApi,
                paymentInternalApi,
                permissionSnapshotService,
                sessionAuthenticationService,
                systemInternalApi(1001L)
        );
    }

    private CompetitionRegistrationAppService service(
            MyBatisQueryOperations sql,
            TeamInternalApi teamInternalApi,
            PaymentInternalApi paymentInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi
    ) {
        try {
            Constructor<CompetitionRegistrationAppService> constructor = CompetitionRegistrationAppService.class.getDeclaredConstructor(
                    MyBatisQueryOperations.class,
                    ObjectMapper.class,
                    ObjectProvider.class,
                    ObjectProvider.class,
                    ObjectProvider.class,
                    PermissionSnapshotService.class,
                    SessionAuthenticationService.class,
                    boolean.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    sql,
                    objectMapper,
                    objectProvider(teamInternalApi),
                    objectProvider(paymentInternalApi),
                    objectProvider(systemInternalApi),
                    permissionSnapshotService,
                    sessionAuthenticationService,
                    false
            );
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create lenient CompetitionRegistrationAppService", ex);
        }
    }

    private PaymentOrderDTO paymentOrder(String orderNo, Long amountMinor, String currency) {
        return new PaymentOrderDTO(orderNo, "alipay", "provider-" + orderNo, "Competition registration", amountMinor, currency, "PENDING", "/payment/orders/" + orderNo, null, null, null, Map.of(), null, null, null, null, null);
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
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("student");
        currentUser.setSessionId("session-1001");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(
                "aiadc:registration:view",
                "aiadc:registration:create",
                "aiadc:registration:update",
                "aiadc:registration:pay",
                "aiadc:material:submit"
        ));
        return currentUser;
    }

    private CurrentUser paymentAdmin() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1002L);
        currentUser.setUserUuid("user-uuid-1002");
        currentUser.setUsername("payment-admin");
        currentUser.setSessionId("session-1002");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of("payment:order:view"));
        return currentUser;
    }

    private TeamInternalApi teamApiWithMembers(Long userId, int memberCount) {
        return new TeamInternalApi() {
            @Override
            public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) {
                assertThat(requesterUserId).isEqualTo(userId);
                assertThat(requesterUserUuid).isEqualTo("user-uuid-" + userId);
                TeamSummaryDTO team = new TeamSummaryDTO();
                team.setId(teamId);
                team.setTeamCode("TEAM-001");
                team.setTeamName("AI Team");
                team.setTeamType("competition");
                team.setVisibility("PRIVATE");
                team.setOwnerUserId(userId);
                team.setOwnerUserUuid("user-uuid-" + userId);
                team.setStatus("ACTIVE");
                return team;
            }

            @Override
            public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) {
                assertThat(requesterUserId).isEqualTo(userId);
                assertThat(requesterUserUuid).isEqualTo("user-uuid-" + userId);
                List<TeamMemberDTO> members = new ArrayList<>();
                for (int i = 0; i < memberCount; i += 1) {
                    TeamMemberDTO member = new TeamMemberDTO();
                    member.setId(100L + i);
                    member.setTeamId(teamId);
                    member.setUserId(userId + i);
                    member.setUserUuid("user-uuid-" + (userId + i));
                    member.setRole(i == 0 ? "OWNER" : "MEMBER");
                    member.setStatus("ACTIVE");
                    member.setJoinedAt(LocalDateTime.now());
                    members.add(member);
                }
                return members;
            }

            @Override
            public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) {
                TeamMemberDTO member = new TeamMemberDTO();
                member.setTeamId(teamId);
                member.setUserId(userId);
                member.setUserUuid(userUuid);
                member.setStatus("ACTIVE");
                return member;
            }

            @Override public boolean isTeamOwner(Long teamId, Long userId, String userUuid) { return true; }
            @Override public boolean isTeamAdmin(Long teamId, Long userId, String userUuid) { return true; }
            @Override public boolean isTeamManager(Long teamId, Long userId, String userUuid) { return true; }
        };
    }

    private TeamInternalApi teamApiRejectingMembershipCheck(Long userId, int memberCount) {
        return new TeamInternalApi() {
            private final TeamInternalApi delegate = teamApiWithMembers(userId, memberCount);

            @Override
            public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) {
                return delegate.getTeam(requesterUserId, requesterUserUuid, teamId);
            }

            @Override
            public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) {
                return delegate.listActiveMembers(requesterUserId, requesterUserUuid, teamId);
            }

            @Override
            public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) {
                throw new AssertionError("Registration must not require applicant team membership");
            }

            @Override public boolean isTeamOwner(Long teamId, Long userId, String userUuid) { return delegate.isTeamOwner(teamId, userId, userUuid); }
            @Override public boolean isTeamAdmin(Long teamId, Long userId, String userUuid) { return delegate.isTeamAdmin(teamId, userId, userUuid); }
            @Override public boolean isTeamManager(Long teamId, Long userId, String userUuid) { return delegate.isTeamManager(teamId, userId, userUuid); }
        };
    }

    private TeamInternalApi teamApiRejectingLookup() {
        return new TeamInternalApi() {
            @Override public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) { throw new AssertionError("Inline registration must not read Team module"); }
            @Override public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) { throw new AssertionError("Inline registration must not read Team members"); }
            @Override public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) { throw new AssertionError("Inline registration must not require team membership"); }
            @Override public boolean isTeamOwner(Long teamId, Long userId, String userUuid) { return false; }
            @Override public boolean isTeamAdmin(Long teamId, Long userId, String userUuid) { return false; }
            @Override public boolean isTeamManager(Long teamId, Long userId, String userUuid) { return false; }
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

    private ObjectProvider<PaymentInternalApi> objectProvider(PaymentInternalApi paymentInternalApi) {
        return new ObjectProvider<>() {
            @Override public PaymentInternalApi getObject(Object... args) { return paymentInternalApi; }
            @Override public PaymentInternalApi getIfAvailable() { return paymentInternalApi; }
            @Override public PaymentInternalApi getIfUnique() { return paymentInternalApi; }
            @Override public PaymentInternalApi getObject() { return paymentInternalApi; }
            @Override public Iterator<PaymentInternalApi> iterator() { return paymentInternalApi == null ? List.<PaymentInternalApi>of().iterator() : List.of(paymentInternalApi).iterator(); }
            @Override public Stream<PaymentInternalApi> stream() { return paymentInternalApi == null ? Stream.empty() : Stream.of(paymentInternalApi); }
            @Override public Stream<PaymentInternalApi> orderedStream() { return stream(); }
        };
    }

    private ObjectProvider<SystemInternalApi> objectProvider(SystemInternalApi systemInternalApi) {
        return new ObjectProvider<>() {
            @Override public SystemInternalApi getObject(Object... args) { return systemInternalApi; }
            @Override public SystemInternalApi getIfAvailable() { return systemInternalApi; }
            @Override public SystemInternalApi getIfUnique() { return systemInternalApi; }
            @Override public SystemInternalApi getObject() { return systemInternalApi; }
            @Override public Iterator<SystemInternalApi> iterator() { return systemInternalApi == null ? List.<SystemInternalApi>of().iterator() : List.of(systemInternalApi).iterator(); }
            @Override public Stream<SystemInternalApi> stream() { return systemInternalApi == null ? Stream.empty() : Stream.of(systemInternalApi); }
            @Override public Stream<SystemInternalApi> orderedStream() { return stream(); }
        };
    }

    private SystemInternalApi systemInternalApi(Long userId) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        org.mockito.Mockito.when(systemInternalApi.findUserIdentityById(userId)).thenReturn(new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
                "student",
                null,
                "ENABLED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return systemInternalApi;
    }

    private static final class RegistrationSql extends MyBatisQueryOperations {
        private String competitionFeeMode = "TEAM";
        private Long competitionEntryFeeMinor = 0L;
        private Map<String, Object> registration;
        private Map<String, Object> stageForm;
        private Long lastInsertedId = 1L;
        private Long preliminaryStageId;
        private Long submittedMaterialCount = 0L;
        private Long existingMaterialSubmissionId;
        private int paymentOrderInserts;
        private int materialValueInserts;
        private int confirmUpdates;
        private boolean wroteTeamTables;
        private String paymentRequestJson;
        private String paymentOrderNo;
        private String lastRegistrationCountSql;
        private String lastRegistrationQuerySql;
        private String lastRegistrationInsertSql;
        private Object[] lastRegistrationInsertArgs = new Object[0];
        private String lastRegistrationUpdateSql;
        private List<Object> lastRegistrationUpdateArgs = List.of();
        private String lastStageInsertSql;
        private List<Object> lastStageInsertArgs = List.of();
        private String lastStageFormInsertSql;
        private List<Object> lastStageFormInsertArgs = List.of();
        private String lastMaterialSubmissionInsertSql;
        private List<Object> lastMaterialSubmissionInsertArgs = List.of();
        private String lastMaterialSubmissionLookupSql;
        private String lastMaterialSubmissionUpdateSql;
        private String lastMaterialValueDeleteSql;
        private String lastPaymentTaskInsertSql;
        private List<Object> lastPaymentTaskInsertArgs = List.of();
        private String lastPaymentOrderAttachSql;
        private List<Object> lastPaymentOrderAttachArgs = List.of();
        private String lastPaymentRecordCountSql;
        private String lastPaymentRecordQuerySql;
        private List<Map<String, Object>> materialSubmissions = List.of();
        private List<Map<String, Object>> materialValues = List.of();
        private List<Map<String, Object>> paymentRecordRows = List.of();
        private Map<String, Object> paymentOrderTask;
        private String lastConfirmRegistrationSql;
        private int lastInsertIdQueries;
        private final Queue<Integer> updateResults = new java.util.ArrayDeque<>();

        void seedRegistration(Long id, String status, String paymentOrderNo, Long payableAmountMinor) {
            registration = newRegistration(id, status, paymentOrderNo, payableAmountMinor);
            this.paymentOrderNo = paymentOrderNo;
        }

        void seedPaymentOrderTask(Long registrationId, String ownerUserUuid) {
            seedPaymentOrderTask(registrationId, ownerUserUuid, null);
        }

        void seedPaymentOrderTask(Long registrationId, String ownerUserUuid, Long simulatedRoleId) {
            paymentOrderTask = new LinkedHashMap<>();
            paymentOrderTask.put("id", 301L);
            paymentOrderTask.put("registrationId", registrationId);
            paymentOrderTask.put("providerCode", "alipay");
            paymentOrderTask.put("clientIp", null);
            paymentOrderTask.put("notifyUrl", null);
            paymentOrderTask.put("returnUrl", null);
            paymentOrderTask.put("ownerUserUuid", ownerUserUuid);
            paymentOrderTask.put("simulatedRoleId", simulatedRoleId);
            paymentOrderTask.put("status", "PENDING");
            paymentOrderTask.put("retryCount", 0);
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
                lastRegistrationInsertSql = sql;
                lastRegistrationInsertArgs = args;
                registration = newRegistration(lastInsertedId, String.valueOf(args[6]), null, ((Number) args[10]).longValue());
                registration.put("registrationNo", args[0]);
                registration.put("competitionId", args[1]);
                registration.put("teamId", args[2]);
                registration.put("projectId", args[3]);
                registration.put("ownerUserId", args[4]);
                registration.put("ownerUserUuid", args[5]);
                registration.put("feeMode", args[7]);
                registration.put("entryFeeMinor", args[8]);
                registration.put("memberCount", args[9]);
                registration.put("currency", args[11]);
                registration.put("teamSnapshotJson", args[12]);
                registration.put("projectSnapshotJson", args[13]);
                registration.put("memberSnapshotJson", args[14]);
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("update competition_registration")
                    && normalized.contains("set competition_id = ?")) {
                lastRegistrationUpdateSql = sql;
                lastRegistrationUpdateArgs = Arrays.asList(args);
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("insert into competition_stage_form")) {
                lastStageFormInsertSql = sql;
                lastStageFormInsertArgs = Arrays.asList(args);
                stageForm = Map.of(
                        "id", 81L,
                        "competitionId", args[0],
                        "stageId", args[1],
                        "formName", args[2],
                        "formSchemaJson", args[3],
                        "version", args[4],
                        "status", args[5],
                        "createdAt", LocalDateTime.now(),
                        "updatedAt", LocalDateTime.now()
                );
                lastInsertedId = 81L;
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("insert into competition_stage (")) {
                lastStageInsertSql = sql;
                lastStageInsertArgs = Arrays.asList(args);
                lastInsertedId = 71L;
                preliminaryStageId = 71L;
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("insert into registration_material_submission")) {
                lastMaterialSubmissionInsertSql = sql;
                lastMaterialSubmissionInsertArgs = Arrays.asList(args);
                lastInsertedId = 91L;
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("update registration_material_submission")) {
                lastMaterialSubmissionUpdateSql = sql;
                return 1;
            }
            if (normalized.contains("update registration_material_value")) {
                lastMaterialValueDeleteSql = sql;
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
            if (normalized.contains("insert into competition_payment_order_task")) {
                lastPaymentTaskInsertSql = sql;
                lastPaymentTaskInsertArgs = Arrays.asList(args);
                paymentOrderTask = new LinkedHashMap<>();
                paymentOrderTask.put("id", 301L);
                paymentOrderTask.put("registrationId", args[0]);
                paymentOrderTask.put("providerCode", args[1]);
                paymentOrderTask.put("clientIp", args[2]);
                paymentOrderTask.put("notifyUrl", args[3]);
                paymentOrderTask.put("returnUrl", args[4]);
                paymentOrderTask.put("ownerUserUuid", args[5]);
                paymentOrderTask.put("simulatedRoleId", args[6]);
                paymentOrderTask.put("status", "PENDING");
                paymentOrderTask.put("retryCount", 0);
                return updateResults.isEmpty() ? 1 : updateResults.remove();
            }
            if (normalized.contains("update competition_payment_order_task")
                    && normalized.contains("set status = 'running'")) {
                if (paymentOrderTask != null && !"SUCCEEDED".equals(paymentOrderTask.get("status"))) {
                    paymentOrderTask.put("status", "RUNNING");
                    paymentOrderTask.put("claimToken", args[0]);
                }
                return 1;
            }
            if (normalized.contains("update competition_payment_order_task")
                    && normalized.contains("set status = 'succeeded'")) {
                if (paymentOrderTask != null
                        && Objects.equals(paymentOrderTask.get("registrationId"), args[3])
                        && Objects.equals(paymentOrderTask.get("ownerUserUuid"), args[4])
                        && Objects.equals(paymentOrderTask.get("claimToken"), args[5])) {
                    paymentOrderTask.put("status", "SUCCEEDED");
                    paymentOrderTask.put("processMessage", args[0]);
                    paymentOrderTask.put("claimToken", null);
                    return 1;
                }
                return 0;
            }
            if (normalized.contains("update competition_payment_order_task")
                    && normalized.contains("set status = ?")
                    && normalized.contains("retry_count = ?")) {
                if (paymentOrderTask != null
                        && Objects.equals(paymentOrderTask.get("registrationId"), args[6])
                        && Objects.equals(paymentOrderTask.get("ownerUserUuid"), args[7])
                        && Objects.equals(paymentOrderTask.get("claimToken"), args[8])) {
                    paymentOrderTask.put("status", args[0]);
                    paymentOrderTask.put("retryCount", args[1]);
                    paymentOrderTask.put("processMessage", args[3]);
                    paymentOrderTask.put("claimToken", null);
                    return 1;
                }
                return 0;
            }
            if (normalized.contains("update competition_registration")
                    && normalized.contains("set payment_order_no")) {
                lastPaymentOrderAttachSql = sql;
                lastPaymentOrderAttachArgs = Arrays.asList(args);
                if (!Objects.equals(registration.get("id"), args[4])
                        || !Objects.equals(registration.get("registrationNo"), args[5])
                        || !Objects.equals(registration.get("ownerUserId"), args[6])
                        || !Objects.equals(registration.get("ownerUserUuid"), args[7])
                        || !Objects.equals(registration.get("payableAmountMinor"), args[8])
                        || !Objects.equals(registration.get("currency"), args[9])) {
                    return 0;
                }
                registration.put("paymentOrderNo", args[0]);
                return 1;
            }
            if (normalized.contains("set status = 'confirmed'")) {
                lastConfirmRegistrationSql = sql;
                if (registration.get("participantNo") == null) {
                    registration.put("status", "CONFIRMED");
                    registration.put("participantNo", args[0]);
                    if (args[1] != null) {
                        registration.put("paymentOrderNo", args[1]);
                    }
                    registration.put("updatedBy", args[2]);
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
                lastInsertIdQueries += 1;
                return requiredType.cast(lastInsertedId);
            }
            if (normalized.contains("from competition_stage") && !normalized.contains("from competition_stage_form")) {
                return requiredType.cast(preliminaryStageId);
            }
            if (normalized.contains("from registration_material_submission")
                    && normalized.contains("select s.id")) {
                lastMaterialSubmissionLookupSql = sql;
                return requiredType.cast(existingMaterialSubmissionId);
            }
            if (normalized.contains("from registration_material_submission")) {
                return requiredType.cast(submittedMaterialCount);
            }
            if (normalized.contains("from competition_registration cr")) {
                lastPaymentRecordCountSql = sql;
                return requiredType.cast((long) paymentRecordRows.size());
            }
            if (normalized.contains("select retry_count")
                    && normalized.contains("from competition_payment_order_task")) {
                if (paymentOrderTask == null
                        || !"RUNNING".equals(paymentOrderTask.get("status"))
                        || !Objects.equals(paymentOrderTask.get("id"), args[0])
                        || !Objects.equals(paymentOrderTask.get("registrationId"), args[1])
                        || !Objects.equals(paymentOrderTask.get("ownerUserUuid"), args[2])
                        || !Objects.equals(paymentOrderTask.get("claimToken"), args[3])) {
                    return null;
                }
                Object retryCount = paymentOrderTask.get("retryCount");
                return retryCount == null ? null : requiredType.cast(((Number) retryCount).intValue());
            }
            if (normalized.contains("from competition_registration where deleted = 0")) {
                lastRegistrationCountSql = sql;
                return requiredType.cast(registration == null ? 0L : 1L);
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
            if (normalized.contains("from competition_stage") && !normalized.contains("from competition_stage_form")) {
                return map(rowMapper, Map.of(
                        "id", 71L,
                        "competitionId", 11L,
                        "stageCode", "PRELIMINARY",
                        "stageName", "Preliminary",
                        "status", "DRAFT",
                        "sort", 100,
                        "createdAt", LocalDateTime.now(),
                        "updatedAt", LocalDateTime.now()
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
            if (normalized.contains("from competition_payment_order_task")) {
                if (paymentOrderTask == null
                        || !"RUNNING".equals(paymentOrderTask.get("status"))
                        || args.length == 0
                        || !Objects.equals(paymentOrderTask.get("claimToken"), args[0])) {
                    return List.of();
                }
                return List.of(new LinkedHashMap<>(paymentOrderTask));
            }
            if (normalized.contains("from team")
                    && normalized.contains("owner_user_uuid")
                    && normalized.contains("exists")) {
                return List.of(new LinkedHashMap<>(Map.of(
                        "id", 21L,
                        "teamCode", "TEAM-001",
                        "teamName", "AI Team",
                        "teamType", "competition",
                        "visibility", "PRIVATE",
                        "ownerUserId", 1001L,
                        "ownerUserUuid", "user-uuid-1001",
                        "status", "ACTIVE"
                )));
            }
            if (normalized.contains("from team_member")
                    && normalized.contains("user_uuid as useruuid")) {
                Map<String, Object> owner = new LinkedHashMap<>();
                owner.put("id", 101L);
                owner.put("teamId", 21L);
                owner.put("userId", 1001L);
                owner.put("userUuid", "user-uuid-1001");
                owner.put("role", "OWNER");
                owner.put("status", "ACTIVE");
                owner.put("extraValuesJson", "{}");
                owner.put("joinedAt", LocalDateTime.now());
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("id", 102L);
                member.put("teamId", 21L);
                member.put("userId", 1002L);
                member.put("userUuid", "user-uuid-1002");
                member.put("role", "MEMBER");
                member.put("status", "ACTIVE");
                member.put("extraValuesJson", "{}");
                member.put("joinedAt", LocalDateTime.now());
                return List.of(owner, member);
            }
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
            if (normalized.contains("from competition_registration where deleted = 0")) {
                lastRegistrationQuerySql = sql;
                return registration == null ? List.of() : List.of(map(rowMapper, registration));
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
            row.put("ownerUserUuid", "user-uuid-1001");
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
