package com.lumira.saas.modules.iam.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.common.security.authorization.DelegationGrantDecision;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDelegationGrantEvaluatorTest {

    @Test
    void deniesAiAgentWhenDelegationGrantIsMissing() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.grants = List.of();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(jdbc);

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", false, false));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_GRANT_NOT_FOUND");
    }

    @Test
    void requiresConfirmAndApprovalFromMatchedGrant() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.grants = List.of(grant("file.object.search", "system:file:view", "TENANT", "HIGH", true, true));
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(jdbc);

        assertThat(evaluator.evaluate(request("file.object.search", "system:file:view", "HIGH", false, false)).verdict())
                .isEqualTo(AuthorizationVerdict.REQUIRE_APPROVAL);
        assertThat(evaluator.evaluate(request("file.object.search", "system:file:view", "HIGH", false, true)).verdict())
                .isEqualTo(AuthorizationVerdict.REQUIRE_CONFIRM);
        assertThat(evaluator.evaluate(request("file.object.search", "system:file:view", "HIGH", true, true)).verdict())
                .isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void filtersByToolAndPermissionAndPrefersMostSpecificGrant() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.grants = List.of(
                grant(null, "*", "SELF", "LOW", false, false),
                grant("file.object.search", "system:file:view", "TENANT", "MEDIUM", false, false)
        );
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(jdbc);

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "MEDIUM", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(decision.scopeType()).isEqualTo("TENANT");
        assertThat(decision.maxRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void failsClosedWhenSubjectsCannotBeResolved() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.humanSubjectId = null;
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(jdbc);

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_NOT_FOUND");
    }

    private AuthorizationRequest request(String toolCode, String permissionKey, String riskLevel, boolean confirmed, boolean approvalGranted) {
        CurrentUser user = new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of(permissionKey));
        return AuthorizationRequest.aiTool(user, 300L, toolCode, permissionKey, riskLevel, confirmed, approvalGranted, Map.of());
    }

    private Map<String, Object> grant(String toolCode, String permissionKey, String scopeType, String maxRiskLevel,
                                      boolean requireConfirm, boolean requireApproval) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("resourceCode", "ai_tool");
        row.put("actionCode", "execute");
        row.put("permissionKey", permissionKey);
        row.put("toolCode", toolCode);
        row.put("scopeType", scopeType);
        row.put("maxRiskLevel", maxRiskLevel);
        row.put("requireConfirm", requireConfirm ? 1 : 0);
        row.put("requireApproval", requireApproval ? 1 : 0);
        return row;
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private Long humanSubjectId = 10L;
        private Long employeeSubjectId = 20L;
        private List<Map<String, Object>> grants = List.of();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("from iam_subject")) {
                String subjectType = String.valueOf(args[1]);
                Long value = "HUMAN_USER".equals(subjectType) ? humanSubjectId : employeeSubjectId;
                return value == null ? null : requiredType.cast(value);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (!sql.contains("from iam_delegation_grant")) {
                return List.of();
            }
            String toolCode = args[3] == null ? null : String.valueOf(args[3]);
            String permissionKey = args[4] == null ? null : String.valueOf(args[4]);
            String resourceCode = args[5] == null ? null : String.valueOf(args[5]);
            String actionCode = args[6] == null ? null : String.valueOf(args[6]);
            return grants.stream()
                    .filter(row -> matches(row.get("toolCode"), toolCode))
                    .filter(row -> matches(row.get("permissionKey"), permissionKey))
                    .filter(row -> matches(row.get("resourceCode"), resourceCode))
                    .filter(row -> matches(row.get("actionCode"), actionCode))
                    .toList();
        }

        private boolean matches(Object grantValue, String requestValue) {
            if (grantValue == null) {
                return true;
            }
            String value = String.valueOf(grantValue);
            return "*".equals(value) || value.equals(requestValue);
        }
    }
}
