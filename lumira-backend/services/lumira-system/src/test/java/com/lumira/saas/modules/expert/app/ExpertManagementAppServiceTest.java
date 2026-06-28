package com.lumira.saas.modules.expert.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertManagementAppServiceTest {

    @Test
    void createExpertStartsApprovalWorkflowWithoutCreatingAccount() {
        ExpertSql sql = new ExpertSql();
        WorkflowAppService workflowAppService = mock(WorkflowAppService.class);
        when(workflowAppService.startWorkflow(any(CurrentUser.class), eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION), eq(501L), eq("exp-001"), eq("Ada Expert"), any(Map.class))).thenReturn(7001L);
        ExpertManagementAppService service = new ExpertManagementAppService(sql, workflowAppService);

        ExpertVO.Expert expert = service.createExpert(admin(), expertRequest());

        assertThat(expert.getUserId()).isNull();
        assertThat(expert.getStatus()).isEqualTo("inactive");
        assertThat(expert.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(expert.getApprovalInstanceId()).isEqualTo(7001L);
        verify(workflowAppService).startWorkflow(any(CurrentUser.class), eq(WorkflowAppService.BUSINESS_EXPERT_APPLICATION), eq(501L), eq("exp-001"), eq("Ada Expert"), any(Map.class));
        assertThat(sql.workflowInstanceUpdates).isEqualTo(1);
    }

    private ExpertDTO.ExpertUpsertRequest expertRequest() {
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
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setPermissions(Set.of("*"));
        return currentUser;
    }

    private static final class ExpertSql extends MyBatisQueryOperations {
        private final Map<String, Object> expert = new LinkedHashMap<>();
        private int workflowInstanceUpdates;

        @Override
        public int update(String sql, Object... args) {
            String normalized = sql.toLowerCase();
            if (normalized.contains("insert into aiadc_expert")) {
                expert.put("id", 501L);
                expert.put("code", args[1]);
                expert.put("name", args[2]);
                expert.put("title", args[3]);
                expert.put("organization", args[4]);
                expert.put("position", args[5]);
                expert.put("expertise", args[6]);
                expert.put("phone", args[7]);
                expert.put("mobile", args[8]);
                expert.put("idCardNumber", args[9]);
                expert.put("email", args[10]);
                expert.put("avatarUrl", args[11]);
                expert.put("bio", args[12]);
                expert.put("tags", args[13]);
                expert.put("status", args[14]);
                expert.put("approvalStatus", "PENDING");
                expert.put("sort", args[15]);
                expert.put("createdAt", LocalDateTime.now());
                expert.put("updatedAt", LocalDateTime.now());
                return 1;
            }
            if (normalized.contains("set approval_instance_id = ?")) {
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

        private <T> T map(RowMapper<T> rowMapper, Map<String, Object> values) {
            try {
                return rowMapper.mapRow(new SqlRow(values), 0);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
