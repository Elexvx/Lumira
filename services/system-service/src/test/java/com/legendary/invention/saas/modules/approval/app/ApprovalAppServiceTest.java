package com.legendary.invention.saas.modules.approval.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.task.app.TaskCenterAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApprovalAppServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TaskCenterAppService taskCenterAppService;

    @Mock
    private OperationAuditService operationAuditService;

    private ApprovalAppService approvalAppService;

    @BeforeEach
    void setUp() {
        approvalAppService = new ApprovalAppService(jdbcTemplate, taskCenterAppService, operationAuditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listInstances_shouldScopeRegularUsersToSubmittedOrAssignedInstances() {
        doReturn(List.of())
                .when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));
        doReturn(0L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));

        approvalAppService.listInstances(currentUser(), null, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("applicant_id = ?")
                .contains("approval_task t")
                .contains("t.assignee_user_id = ?")
                .contains("t.handled_by = ?")
                .contains("sys_user_role ur");
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 1, true, Set.of("approval:view"));
    }
}
