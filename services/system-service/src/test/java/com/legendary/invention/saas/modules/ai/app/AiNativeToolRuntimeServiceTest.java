package com.legendary.invention.saas.modules.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiNativeToolRuntimeServiceTest {

    @Test
    void executesPermissionSnapshotAndRecordsAuditLog() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiSkillPermissionChecker permissionChecker = mock(AiSkillPermissionChecker.class);
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, permissionChecker);

        AiVO.ToolExecuteResultVO result = service.execute(currentUser(), request("system.permission.snapshot", Map.of()));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(result.getData()).containsEntry("username", "admin");
        assertThat(result.getData().get("permissions").toString()).contains("ai:tool:execute");
        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("system.permission.snapshot");
        assertThat(jdbcTemplate.lastUpdateArgs[5]).isEqualTo("allow");
        assertThat(jdbcTemplate.lastUpdateArgs[8]).isEqualTo("SUCCESS");
        verify(permissionChecker).verifyAllowed(anyLong(), anyLong(), anyList(), anyBoolean());
    }

    @Test
    void blocksSensitiveConfigAccessAndAuditsFailure() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        assertThatThrownBy(() -> service.execute(currentUser(), request("system.config.read", Map.of("configKey", "jwt.secret"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("敏感配置不允许");

        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_tool_audit_log");
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("system.config.read");
        assertThat(jdbcTemplate.lastUpdateArgs[5]).isEqualTo("deny");
        assertThat(jdbcTemplate.lastUpdateArgs[8]).isEqualTo("FAIL");
    }

    @Test
    void searchesUsersWithMaskedContactFields() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        DefaultAiNativeToolRuntimeService service = newService(jdbcTemplate, mock(AiSkillPermissionChecker.class));

        AiVO.ToolExecuteResultVO result = service.execute(currentUser(), request("system.user.search", Map.of("keyword", "admin")));

        assertThat(result.getResultStatus()).isEqualTo("SUCCESS");
        assertThat(result.getData()).containsEntry("total", 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) result.getData().get("items");
        assertThat(users).hasSize(1);
        assertThat(users.get(0)).containsEntry("mobile", "138****8000");
        assertThat(users.get(0)).containsEntry("email", "a***@example.com");
        assertThat(jdbcTemplate.lastUpdateArgs[3]).isEqualTo("system.user.search");
    }

    private DefaultAiNativeToolRuntimeService newService(
            MyBatisQueryOperations jdbcTemplate,
            AiSkillPermissionChecker permissionChecker
    ) {
        return new DefaultAiNativeToolRuntimeService(
                jdbcTemplate,
                new PermissionGuard(),
                permissionChecker,
                new ObjectMapper()
        );
    }

    private CurrentUser currentUser() {
        return new CurrentUser(
                100L,
                "admin",
                1001L,
                "session-1",
                1,
                true,
                Set.of("ai:tool:execute", "system:config:view", "system:menu:view", "system:user:view", "system:file:view", "audit:view")
        );
    }

    private AiDTO.ToolExecuteRequest request(String toolCode, Map<String, Object> arguments) {
        AiDTO.ToolExecuteRequest request = new AiDTO.ToolExecuteRequest();
        request.setEmployeeId(1L);
        request.setToolCode(toolCode);
        request.setArguments(arguments);
        return request;
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("from ai_employee")) {
                return requiredType.cast(1L);
            }
            if (sql.contains("from sys_user u") && sql.contains("count(1)")) {
                return requiredType.cast(1L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from sys_config")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("configKey", args[1]);
                row.put("configName", "站点名称");
                row.put("configValue", "SaaS Foundation");
                return List.of(row);
            }
            if (sql.contains("from sys_user u")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", 100L);
                row.put("username", "admin");
                row.put("mobile", "13800008000");
                row.put("email", "admin@example.com");
                row.put("status", "ENABLED");
                return List.of(row);
            }
            return List.of();
        }
    }
}
