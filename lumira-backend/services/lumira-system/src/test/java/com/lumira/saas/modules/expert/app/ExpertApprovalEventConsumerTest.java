package com.lumira.saas.modules.expert.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.expert.infrastructure.JdbcExpertApprovalRepository;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExpertApprovalEventConsumerTest {

    @Test
    void consumeRejectsEventWithoutTrustedOperatorBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, mock(SystemUserManagementAppService.class), mock(AccountActivationService.class));
        PlatformEventOutboxEntity event = event(null);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted operator");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void consumeRejectsMismatchedEventTypeBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, mock(SystemUserManagementAppService.class), mock(AccountActivationService.class));
        PlatformEventOutboxEntity event = event(42L);
        event.setEventType("NOTICE_CREATED");

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type mismatch");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void consumeCreatesExpertAccountUsingOperatorPermissionSnapshot() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        SystemVO.UserDetailVO createdUser = new SystemVO.UserDetailVO();
        createdUser.setId(9001L);
        createdUser.setUserUuid("user-uuid-9001");
        when(userManagementAppService.createUserFromTrustedSnapshot(any(CurrentUser.class), any(SystemDTO.UserUpsertRequest.class)))
                .thenReturn(createdUser);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot(
                        "perm-v42",
                        Set.of("system:user:create", "system:user:view"),
                        Set.of(11L),
                        21L,
                        Set.of(21L),
                        Set.of(21L, 22L),
                        List.of(),
                        "/system/users"
                )
        );
        stubExpert(jdbcTemplate, null, null);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("EXPERT")))
                .thenReturn(List.of(3001L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("expert_EXP001"))).thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        AccountActivationService activationService = mock(AccountActivationService.class);
        when(activationService.createActivationToken(9001L, 1001L, 42L, "user-uuid-42")).thenReturn("token");
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        consumer.consume(event(42L));

        var userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(userManagementAppService).createUserFromTrustedSnapshot(userCaptor.capture(), any(SystemDTO.UserUpsertRequest.class));
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(userCaptor.getValue().getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("reviewer");
        assertThat(userCaptor.getValue().getSessionId()).isEqualTo("internal-workflow-event-20001");
        assertThat(userCaptor.getValue().getSessionVersion()).isEqualTo(1);
        assertThat(userCaptor.getValue().getPermissionsVersion()).isEqualTo("perm-v42");
        assertThat(userCaptor.getValue().getPermissions()).containsExactlyInAnyOrder("system:user:create", "system:user:view");
        assertThat(userCaptor.getValue().getRoleIds()).containsExactly(11L);
        assertThat(userCaptor.getValue().getPrimaryDeptId()).isEqualTo(21L);
        assertThat(userCaptor.getValue().getDeptIds()).containsExactly(21L);
        assertThat(userCaptor.getValue().getDescendantDeptIds()).containsExactlyInAnyOrder(21L, 22L);
        assertThat(userCaptor.getValue().getDefaultHomePath()).isEqualTo("/system/users");
        verify(activationService).sendActivationEmail("alice@example.test", "expert_EXP001", "token");
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("where id = ?")
                                && sql.contains("code = ?")
                                && sql.contains("approval_instance_id = ?")
                                && sql.contains("approval_status = 'APPROVED'")
                                && sql.contains("user_id is null")
                                && sql.contains("user_uuid is null or user_uuid = ''")),
                eq(9001L),
                eq("user-uuid-9001"),
                eq(42L),
                eq("user-uuid-42"),
                any(LocalDateTime.class),
                eq(1001L),
                eq("EXP001"),
                eq(7001L)
        );
    }

    @Test
    void consumeRejectsConcurrentExpertAccountBindingBeforeActivationEmail() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        SystemVO.UserDetailVO createdUser = new SystemVO.UserDetailVO();
        createdUser.setId(9001L);
        createdUser.setUserUuid("user-uuid-9001");
        when(userManagementAppService.createUserFromTrustedSnapshot(any(CurrentUser.class), any(SystemDTO.UserUpsertRequest.class)))
                .thenReturn(createdUser);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:create"))
        );
        stubExpert(jdbcTemplate, null, null);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("EXPERT")))
                .thenReturn(List.of(3001L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("expert_EXP001"))).thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        AccountActivationService activationService = mock(AccountActivationService.class);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("binding changed");

        verify(activationService, never()).createActivationToken(any(), any(), any(), any());
        verify(activationService, never()).sendActivationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void consumeDoesNotCreateUserWhenExpertAlreadyHasAccount() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:create"))
        );
        stubExpert(jdbcTemplate, 9001L, "user-uuid-9001", "expert_alice");
        when(activationService.createActivationToken(9001L, 1001L, 42L, "user-uuid-42")).thenReturn("token");
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        consumer.consume(event(42L));

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verify(activationService).sendActivationEmail("alice@example.test", "expert_alice", "token");
    }

    @Test
    void consumeCreatesExpertAccountUsingSimulatedRolePermissionSnapshotWhenPresent() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        SystemVO.UserDetailVO createdUser = new SystemVO.UserDetailVO();
        createdUser.setId(9001L);
        createdUser.setUserUuid("user-uuid-9001");
        when(userManagementAppService.createUserFromTrustedSnapshot(any(CurrentUser.class), any(SystemDTO.UserUpsertRequest.class)))
                .thenReturn(createdUser);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:view")),
                new PermissionSnapshotService.PermissionSnapshot("perm-role-9", Set.of("system:user:create", "system:user:view"))
        );
        stubExpert(jdbcTemplate, null, null);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq("EXPERT")))
                .thenReturn(List.of(3001L));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("expert_EXP001"))).thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        AccountActivationService activationService = mock(AccountActivationService.class);
        when(activationService.createActivationToken(9001L, 1001L, 42L, "user-uuid-42")).thenReturn("token");
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        consumer.consume(event(42L, 9L));

        var userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(userManagementAppService).createUserFromTrustedSnapshot(userCaptor.capture(), any(SystemDTO.UserUpsertRequest.class));
        assertThat(userCaptor.getValue().getSimulatedRoleId()).isEqualTo(9L);
        assertThat(userCaptor.getValue().getPermissionsVersion()).isEqualTo("perm-role-9");
        assertThat(userCaptor.getValue().getPermissions()).containsExactlyInAnyOrder("system:user:create", "system:user:view");
        verify(permissionSnapshotService).loadGrantedRoleSnapshot(42L, "user-uuid-42", 9L);
        verify(permissionSnapshotService, never()).loadSnapshot(42L, "user-uuid-42");
        verify(activationService).sendActivationEmail("alice@example.test", "expert_EXP001", "token");
    }

    @Test
    void consumeRejectsDisabledOperatorBeforeCreatingUser() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "DISABLED");
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator is disabled");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorWithoutTrustedStatusBeforeCreatingUser() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", " ");
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator is disabled");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorUuidMismatchBeforeCreatingUser() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:create"))
        );
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);
        PlatformEventOutboxEntity event = event(42L);
        event.setPayloadJson("""
                {"aggregateId":1001,"workflowInstanceId":7001,"businessUuid":"EXP001","userUuid":"other-user-uuid"}
                """);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator uuid mismatch");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsPayloadUuidMismatchWithOutboxOperatorBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, userManagementAppService, activationService);
        PlatformEventOutboxEntity event = event(42L);
        event.setPayloadJson("""
                {"aggregateId":1001,"workflowInstanceId":7001,"businessUuid":"EXP001","userUuid":"other-user-uuid"}
                """);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator uuid mismatch");

        verifyNoInteractions(jdbcTemplate);
        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsEventKeyMismatchBeforeCreatingUser() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, userManagementAppService, activationService);
        PlatformEventOutboxEntity event = event(42L);
        event.setEventKey(WorkflowAppService.EVENT_EXPERT_APPROVED + ":expert:9999");

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event key mismatch");

        verifyNoInteractions(jdbcTemplate);
        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeBindsApprovedExpertLookupToBusinessUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:create"))
        );
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(1001L), eq("EXP001"), eq(7001L)))
                .thenReturn(List.of());
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expert not found");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorWithoutCreateUserPermission() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:view"))
        );
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lacks required permission");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorWhenPermissionSnapshotPermissionsAreUnavailable() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot("perm-v42", null)
        );
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lacks required permission");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorWhenPermissionSnapshotIsUnavailable() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(null);
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permission snapshot is unavailable");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    @Test
    void consumeRejectsOperatorWhenPermissionSnapshotVersionIsBlank() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        AccountActivationService activationService = mock(AccountActivationService.class);
        stubOperator(jdbcTemplate, 42L, "reviewer", "ENABLED");
        PermissionSnapshotService permissionSnapshotService = permissionSnapshotService(
                new PermissionSnapshotService.PermissionSnapshot(" ", Set.of("system:user:create"))
        );
        ExpertApprovalEventConsumer consumer = consumer(jdbcTemplate, permissionSnapshotService, userManagementAppService, activationService);

        assertThatThrownBy(() -> consumer.consume(event(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permission snapshot is unavailable");

        verify(userManagementAppService, never()).createUserFromTrustedSnapshot(any(), any());
        verifyNoInteractions(activationService);
    }

    private ExpertApprovalEventConsumer consumer(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SystemUserManagementAppService userManagementAppService,
            AccountActivationService activationService
    ) {
        return new ExpertApprovalEventConsumer(
                new JdbcExpertApprovalRepository(jdbcTemplate),
                new ObjectMapper(),
                permissionSnapshotService,
                userManagementAppService,
                activationService
        );
    }

    private ExpertApprovalEventConsumer consumer(
            MyBatisQueryOperations jdbcTemplate,
            SystemUserManagementAppService userManagementAppService,
            AccountActivationService activationService
    ) {
        return consumer(
                jdbcTemplate,
                permissionSnapshotService(new PermissionSnapshotService.PermissionSnapshot("perm-v42", Set.of("system:user:create"))),
                userManagementAppService,
                activationService
        );
    }

    private PlatformEventOutboxEntity event(Long userId) {
        return event(userId, null);
    }

    private PlatformEventOutboxEntity event(Long userId, Long simulatedRoleId) {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(20001L);
        event.setUserId(userId);
        event.setUserUuid("user-uuid-" + userId);
        event.setSourceType(PlatformEventTypes.SOURCE_SYSTEM);
        event.setEventType(WorkflowAppService.EVENT_EXPERT_APPROVED);
        event.setEventKey(WorkflowAppService.EVENT_EXPERT_APPROVED + ":expert:1001");
        String payload = simulatedRoleId == null
                ? """
                {"aggregateId":1001,"workflowInstanceId":7001,"businessUuid":"EXP001","userUuid":"user-uuid-42"}
                """
                : """
                {"aggregateId":1001,"workflowInstanceId":7001,"businessUuid":"EXP001","userUuid":"user-uuid-42","simulatedRoleId":%d}
                """.formatted(simulatedRoleId);
        event.setPayloadJson(payload);
        return event;
    }

    private void stubExpert(MyBatisQueryOperations jdbcTemplate, Long userId, String username) {
        stubExpert(jdbcTemplate, userId, userId == null ? null : "user-uuid-" + userId, username);
    }

    private void stubExpert(MyBatisQueryOperations jdbcTemplate, Long userId, String userUuid, String username) {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(1001L), eq("EXP001"), eq(7001L)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertThat(sql).contains("e.approval_status = 'APPROVED'");
                    assertThat(sql).contains("e.code = ?");
                    RowMapper<?> mapper = invocation.getArgument(1);
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("id", 1001L);
                    values.put("code", "EXP001");
                    values.put("name", "Expert Alice");
                    values.put("mobile", "13800000000");
                    values.put("email", "alice@example.test");
                    values.put("approvalInstanceId", 7001L);
                    values.put("userId", userId);
                    values.put("userUuid", userUuid);
                    values.put("username", username);
                    SqlRow row = new SqlRow(values);
                    return List.of(mapper.mapRow(row, 0));
                });
    }

    private void stubOperator(MyBatisQueryOperations jdbcTemplate, Long userId, String username, String status) {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(userId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("id", userId);
                    values.put("uuid", "user-uuid-" + userId);
                    values.put("username", username);
                    values.put("status", status);
                    SqlRow row = new SqlRow(values);
                    return List.of(mapper.mapRow(row, 0));
                });
    }

    private PermissionSnapshotService permissionSnapshotService(PermissionSnapshotService.PermissionSnapshot snapshot) {
        return permissionSnapshotService(snapshot, snapshot);
    }

    private PermissionSnapshotService permissionSnapshotService(
            PermissionSnapshotService.PermissionSnapshot snapshot,
            PermissionSnapshotService.PermissionSnapshot simulatedRoleSnapshot
    ) {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);
        when(permissionSnapshotService.loadGrantedRoleSnapshot(42L, "user-uuid-42", 9L)).thenReturn(simulatedRoleSnapshot);
        return permissionSnapshotService;
    }
}
