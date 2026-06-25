package com.lumira.saas.modules.expert.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertManagementAppServiceTest {

    @Test
    void createExpertDelegatesSystemUserCreationAndStoresUserId() {
        ExpertSql sql = new ExpertSql();
        SystemUserManagementAppService userManagementAppService = mock(SystemUserManagementAppService.class);
        SystemVO.UserDetailVO createdUser = new SystemVO.UserDetailVO();
        createdUser.setId(9001L);
        when(userManagementAppService.createUser(any(CurrentUser.class), any(SystemDTO.UserUpsertRequest.class))).thenReturn(createdUser);
        ExpertManagementAppService service = new ExpertManagementAppService(sql, userManagementAppService);

        ExpertVO.Expert expert = service.createExpert(admin(), expertRequest());

        assertThat(expert.getUserId()).isEqualTo(9001L);
        assertThat(expert.getAccountStatus()).isEqualTo("ENABLED");
        assertThat(expert.getInitialPasswordResetRequired()).isTrue();
        ArgumentCaptor<SystemDTO.UserUpsertRequest> captor = ArgumentCaptor.forClass(SystemDTO.UserUpsertRequest.class);
        verify(userManagementAppService).createUser(any(CurrentUser.class), captor.capture());
        SystemDTO.UserUpsertRequest userRequest = captor.getValue();
        assertThat(userRequest.getUsername()).isEqualTo("expert_exp-001");
        assertThat(userRequest.getPassword()).isNotBlank();
        assertThat(expert.getInitialPassword()).isEqualTo(userRequest.getPassword());
        assertThat(userRequest.getRealName()).isEqualTo("Ada Expert");
        assertThat(userRequest.getMobile()).isEqualTo("13800000000");
        assertThat(userRequest.getEmail()).isEqualTo("ada@example.com");
        assertThat(userRequest.getStatus()).isEqualTo("ENABLED");
        assertThat(userRequest.getRoleIds()).containsExactly(1003L);
        assertThat(sql.expertUserIdUpdates).isEqualTo(1);
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
        private int expertUserIdUpdates;

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
                expert.put("sort", args[15]);
                expert.put("createdAt", LocalDateTime.now());
                expert.put("updatedAt", LocalDateTime.now());
                return 1;
            }
            if (normalized.contains("set user_id = ?")) {
                expert.put("userId", args[0]);
                expert.put("accountStatus", "ENABLED");
                expert.put("initialPasswordResetRequired", 1);
                expertUserIdUpdates += 1;
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
