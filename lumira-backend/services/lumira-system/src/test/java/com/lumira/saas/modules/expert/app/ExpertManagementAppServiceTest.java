package com.lumira.saas.modules.expert.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertRepository;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertManagementAppServiceTest {

    private static ExpertRepository repository(MyBatisQueryOperations sql) {
        return new JdbcExpertRepository(sql);
    }

    @Test
    void createExpertStartsApprovalWorkflowWithoutCreatingAccount() throws Exception {
        ExpertSql sql = new ExpertSql();
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        when(workflowAppService.startWorkflow(any(CurrentUser.class), eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION), eq(501L), eq("exp-001"), eq("Ada Expert"), any(Map.class))).thenReturn(7001L);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);

        ExpertVO.Expert expert = service.createExpert(admin(), expertRequest());

        assertThat(expert.getUserId()).isNull();
        assertThat(expert.getStatus()).isEqualTo("inactive");
        assertThat(expert.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(expert.getApprovalInstanceId()).isEqualTo(7001L);
        verify(workflowAppService).startWorkflow(any(CurrentUser.class), eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION), eq(501L), eq("exp-001"), eq("Ada Expert"), any(Map.class));
        assertThat(sql.workflowInstanceUpdates).isEqualTo(1);
        assertThat(sql.insertSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.workflowInstanceUpdateSql)
                .contains("updated_by_uuid")
                .contains("and code = ?")
                .contains("and status = ?")
                .contains("and approval_status = ?");
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/expert/app/ExpertManagementAppService.java"));
        assertThat(source).contains("Expert application changed, please retry");
        assertThat(sql.insertArgs).contains(1001L, "user-uuid-1001");
        assertThat(sql.workflowInstanceUpdateArgs).contains(1001L, "user-uuid-1001", "exp-001", "inactive", "PENDING");
    }

    @Test
    void createExpertShouldRejectWhenInsertMissesBeforeWorkflowStart() {
        ExpertSql sql = new ExpertSql();
        sql.insertResult = 0;
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);

        assertThatThrownBy(() -> service.createExpert(admin(), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Expert application changed, please retry");
                });

        assertThat(sql.lastInsertIdQueries).isZero();
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void updateExpertShouldRequireUpdatePermissionAtServiceLayer() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);

        assertThatThrownBy(() -> service.updateExpert(user("expert:view"), 501L, expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
        verify(sql, never()).update(anyString(), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void listExpertsShouldRequireViewPermissionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        assertThatThrownBy(() -> service.listExperts(user("expert:update"), null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(sql, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
    }

    @Test
    void listExpertsShouldRejectBlankUsernameBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        assertThatThrownBy(() -> service.listExperts(blankUsernameUser(), null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
    }

    @Test
    void listExpertsShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        assertThatThrownBy(() -> service.listExperts(missingSessionVersionUser(), null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
    }

    @Test
    void listExpertsShouldRejectMissingUserUuidBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));
        CurrentUser currentUser = user("expert:view");
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.listExperts(currentUser, null, null, null, 1, 10))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
    }

    @Test
    void createExpertShouldRejectMissingPermissionsVersionBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);
        CurrentUser currentUser = user("expert:create");
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.createExpert(currentUser, expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).queryForObject(anyString(), any(Class.class), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRejectWhenLiveSnapshotRevokesCreatePermissionBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("expert:view")));
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService, permissionSnapshotService);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).queryForObject(anyString(), any(Class.class), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRejectDisabledTrustedIdentityBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verify(sql, never()).update(anyString(), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRejectBlankLiveUsernameBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService, permissionSnapshotService, systemInternalApi, null);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verify(sql, never()).update(anyString(), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRefreshLiveUsernameBeforeWorkflowStart() throws Exception {
        ExpertSql sql = new ExpertSql();
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        when(workflowAppService.startWorkflow(any(CurrentUser.class), eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION), eq(501L), eq("exp-001"), eq("Ada Expert"), any(Map.class)))
                .thenReturn(7001L);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " admin-live ", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("*", "expert:create")));
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService, permissionSnapshotService, systemInternalApi, null);
        CurrentUser currentUser = admin();
        currentUser.setUsername("admin-stale");

        service.createExpert(currentUser, expertRequest());

        verify(workflowAppService).startWorkflow(
                argThat(user -> "admin-live".equals(user.getUsername())),
                eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION),
                eq(501L),
                eq("exp-001"),
                eq("Ada Expert"),
                any(Map.class)
        );
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
    }

    @Test
    void createExpertShouldRejectRevokedSessionTicketBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        ExpertManagementAppService service =
                new ExpertManagementAppService(repository(sql), workflowAppService, null, sessionAuthenticationService);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).queryForObject(anyString(), any(Class.class), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service =
                new ExpertManagementAppService(repository(sql), workflowAppService, null, (SessionAuthenticationService) null);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).queryForObject(anyString(), any(Class.class), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void createExpertShouldRejectNullRequestBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).queryForObject(anyString(), any(Class.class), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void expertResourceOperationsShouldRejectInvalidIdsBeforeDatabaseAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        assertThatThrownBy(() -> service.getExpert(user("expert:view"), 0L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateExpert(user("expert:update"), -1L, expertRequest()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.deleteExpert(user("expert:delete"), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
        verify(sql, never()).update(anyString(), any());
    }

    @Test
    void createExpertShouldRejectUnsafeFieldsBeforeDatabaseOrWorkflowAccess() {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService);
        ExpertDTO.ExpertUpsertRequest unsafeAvatar = expertRequest();
        unsafeAvatar.setAvatarUrl("javascript:alert(1)");
        ExpertDTO.ExpertUpsertRequest oversizedBio = expertRequest();
        oversizedBio.setBio("x".repeat(1001));
        ExpertDTO.ExpertUpsertRequest invalidEmail = expertRequest();
        invalidEmail.setEmail("not-an-email");
        ExpertDTO.ExpertUpsertRequest invalidMobile = expertRequest();
        invalidMobile.setMobile("123");

        assertThatThrownBy(() -> service.createExpert(user("expert:create"), unsafeAvatar))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createExpert(user("expert:create"), oversizedBio))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createExpert(user("expert:create"), invalidEmail))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createExpert(user("expert:create"), invalidMobile))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(sql, never()).update(anyString(), any());
        verify(sql, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ExpertVO.Expert>>any(), any());
        verify(workflowAppService, never()).startWorkflow(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void updateExpertShouldBindOriginalCodeStatusAndApprovalStatusInFinalWrite() {
        ExpertSql sql = new ExpertSql();
        sql.seedExpert();
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        service.updateExpert(admin(), 501L, expertRequest());

        assertThat(sql.lastUpdateSql)
                .contains("where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0");
        assertThat(sql.lastUpdateArgs).containsSubsequence(501L, "exp-001", "inactive", "PENDING");
    }

    @Test
    void deleteExpertShouldBindOriginalCodeStatusAndApprovalStatusInFinalWrite() {
        ExpertSql sql = new ExpertSql();
        sql.seedExpert();
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), mock(WorkflowAppService.class));

        service.deleteExpert(admin(), 501L);

        assertThat(sql.lastUpdateSql)
                .contains("where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0");
        assertThat(sql.lastUpdateArgs).containsSubsequence(501L, "exp-001", "inactive", "PENDING");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        MyBatisQueryOperations sql = mock(MyBatisQueryOperations.class);
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("expert:view")));
        ExpertManagementAppService service = new ExpertManagementAppService(repository(sql), workflowAppService, permissionSnapshotService);
        CurrentUser currentUser = user("expert:view");
        currentUser.setSimulatedRoleId(0L);

        Method method = ExpertManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);
        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(1001L, "user-uuid-1001", 0L);
    }

    private static ExpertDTO.ExpertUpsertRequest expertRequest() {
        ExpertDTO.ExpertUpsertRequest request = new ExpertDTO.ExpertUpsertRequest();
        request.setCode("exp-001");
        request.setName("Ada Expert");
        request.setTitle("教授");
        request.setOrganization("Lumira University");
        request.setPosition("导师");
        request.setExpertise("AI");
        request.setMobile("13800000000");
        request.setEmail("ada@example.com");
        request.setStatus("active");
        request.setSort(10);
        return request;
    }

    private CurrentUser admin() {
        return user("*");
    }

    private CurrentUser user(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private CurrentUser blankUsernameUser() {
        CurrentUser currentUser = user("expert:view");
        currentUser.setUsername(" ");
        return currentUser;
    }

    private CurrentUser missingSessionVersionUser() {
        CurrentUser currentUser = user("expert:view");
        currentUser.setSessionVersion(null);
        return currentUser;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
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
        );
    }

    private static final class ExpertSql extends MyBatisQueryOperations {
        private final Map<String, Object> expert = new LinkedHashMap<>();
        private int workflowInstanceUpdates;
        private String insertSql;
        private List<Object> insertArgs = List.of();
        private String workflowInstanceUpdateSql;
        private List<Object> workflowInstanceUpdateArgs = List.of();
        private String lastUpdateSql;
        private List<Object> lastUpdateArgs = List.of();
        private int insertResult = 1;
        private int lastInsertIdQueries;

        private void seedExpert() {
            expert.put("id", 501L);
            expert.put("code", "exp-001");
            expert.put("name", "Ada Expert");
            expert.put("title", "教授");
            expert.put("organization", "Lumira University");
            expert.put("position", "导师");
            expert.put("expertise", "AI");
            expert.put("mobile", "13800000000");
            expert.put("email", "ada@example.com");
            expert.put("status", "inactive");
            expert.put("approvalStatus", "PENDING");
            expert.put("sort", 10);
            expert.put("createdAt", LocalDateTime.now());
            expert.put("updatedAt", LocalDateTime.now());
        }

        @Override
        public int update(String sql, Object... args) {
            String normalized = sql.toLowerCase();
            lastUpdateSql = sql;
            lastUpdateArgs = Arrays.asList(args);
            if (normalized.contains("insert into aiadc_expert")) {
                insertSql = sql;
                insertArgs = Arrays.asList(args);
                expert.put("id", 501L);
                expert.put("code", args[0]);
                expert.put("name", args[1]);
                expert.put("title", args[2]);
                expert.put("organization", args[3]);
                expert.put("position", args[4]);
                expert.put("expertise", args[5]);
                expert.put("phone", args[6]);
                expert.put("mobile", args[7]);
                expert.put("idCardNumber", args[8]);
                expert.put("email", args[9]);
                expert.put("avatarUrl", args[10]);
                expert.put("bio", args[11]);
                expert.put("tags", args[12]);
                expert.put("status", args[13]);
                expert.put("approvalStatus", args[14]);
                expert.put("sort", args[15]);
                expert.put("createdAt", LocalDateTime.now());
                expert.put("updatedAt", LocalDateTime.now());
                return insertResult;
            }
            if (normalized.contains("set approval_instance_id = ?")) {
                workflowInstanceUpdateSql = sql;
                workflowInstanceUpdateArgs = Arrays.asList(args);
                expert.put("approvalInstanceId", args[0]);
                workflowInstanceUpdates += 1;
                return 1;
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(501L);
            }
            if (normalized.contains("from sys_dict_type") || normalized.contains("from sys_dict_item")) {
                return requiredType.cast(0L);
            }
            if (normalized.contains("from sys_role")) {
                return requiredType.cast(1003L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.toLowerCase().contains("from aiadc_expert")) {
                return List.of(map(rowMapper, expert));
            }
            return List.of();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (args.length == 0) return List.of();
            return switch (String.valueOf(args[0])) {
                case "aiadc_expert_title" -> List.of(Map.of("itemValue", expertRequest().getTitle()));
                case "aiadc_expert_position" -> List.of(Map.of("itemValue", expertRequest().getPosition()));
                case "aiadc_expert_expertise" -> List.of(Map.of("itemValue", expertRequest().getExpertise()));
                case "aiadc_expert_tag" -> List.of(Map.of("itemValue", "TAG"));
                case "aiadc_expert_status" -> List.of(Map.of("itemValue", "active"), Map.of("itemValue", "inactive"));
                case "aiadc_expert_initial_status" -> List.of(Map.of("itemValue", "inactive"));
                case "aiadc_expert_approval_status" -> List.of(
                        Map.of("itemValue", "PENDING"), Map.of("itemValue", "RUNNING"),
                        Map.of("itemValue", "APPROVED"), Map.of("itemValue", "REJECTED"));
                default -> List.of();
            };
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
