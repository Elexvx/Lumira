package com.lumira.saas.modules.iam.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.common.security.authorization.DelegationGrantDecision;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.infrastructure.JdbcDelegationGrantRepository;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDelegationGrantEvaluatorTest {

    @Test
    void deniesAiAgentWhenDelegationGrantIsMissing() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.grants = List.of();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", false, false));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_GRANT_NOT_FOUND");
    }

    @Test
    void requiresConfirmAndApprovalFromMatchedGrant() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.grants = List.of(grant("file.object.search", "system:file:view", "TENANT", "HIGH", true, true));
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));

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
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "MEDIUM", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.ALLOW);
        assertThat(decision.scopeType()).isEqualTo("TENANT");
        assertThat(decision.maxRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void failsClosedWhenSubjectsCannotBeResolved() {
        StubQueryOperations jdbc = new StubQueryOperations();
        jdbc.humanSubjectId = null;
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_NOT_FOUND");
    }

    @Test
    void refusesToInferHumanUserIdFromUntrustedCurrentUser() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));
        CurrentUser currentUser = new CurrentUser(100L, "admin", 1001L, "session-1", null, true, Set.of("system:file:view"));

        DelegationGrantDecision decision = evaluator.evaluate(
                AuthorizationRequest.aiTool(currentUser, 300L, "file.object.search", "system:file:view", "LOW", true, true, Map.of())
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_CONTEXT_INCOMPLETE");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    @Test
    void refusesExplicitHumanUserIdThatDoesNotMatchTrustedCurrentUser() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));
        CurrentUser currentUser = trustedUser(100L, "system:file:view");

        DelegationGrantDecision decision = evaluator.evaluate(
                explicitHumanRequest(currentUser, 200L)
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_UNTRUSTED");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    @Test
    void refusesExplicitHumanUserUuidThatDoesNotMatchTrustedCurrentUser() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));
        CurrentUser currentUser = trustedUser(100L, "system:file:view");

        DelegationGrantDecision decision = evaluator.evaluate(
                explicitHumanRequest(currentUser, 100L, "other-user-uuid")
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_UNTRUSTED");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    @Test
    void refusesExplicitHumanUserIdWhenCurrentUserIsNotFullyTrusted() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc));
        CurrentUser currentUser = trustedUser(100L, "system:file:view");
        currentUser.setPermissionsVersion(null);

        DelegationGrantDecision decision = evaluator.evaluate(
                explicitHumanRequest(currentUser, 100L)
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_UNTRUSTED");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    @Test
    void revokedSessionTicketMakesDelegationContextFailClosed() {
        StubQueryOperations jdbc = new StubQueryOperations();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 100L, "user-uuid-100", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc), sessionAuthenticationService);

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_UNTRUSTED");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    @Test
    void missingTrustedUserResolverFailsClosedInStrictMode() {
        StubQueryOperations jdbc = new StubQueryOperations();
        DefaultDelegationGrantEvaluator evaluator = new DefaultDelegationGrantEvaluator(new JdbcDelegationGrantRepository(jdbc), null);

        DelegationGrantDecision decision = evaluator.evaluate(request("file.object.search", "system:file:view", "LOW", true, true));

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.DENY);
        assertThat(decision.reasonCode()).isEqualTo("DELEGATION_HUMAN_SUBJECT_UNTRUSTED");
        assertThat(jdbc.subjectLookupCount).isZero();
    }

    private AuthorizationRequest request(String toolCode, String permissionKey, String riskLevel, boolean confirmed, boolean approvalGranted) {
        CurrentUser user = trustedUser(100L, permissionKey);
        return AuthorizationRequest.aiTool(user, 300L, toolCode, permissionKey, riskLevel, confirmed, approvalGranted, Map.of());
    }

    private AuthorizationRequest explicitHumanRequest(CurrentUser currentUser, Long humanUserId) {
        return explicitHumanRequest(currentUser, humanUserId, currentUser == null ? null : currentUser.getUserUuid());
    }

    private AuthorizationRequest explicitHumanRequest(CurrentUser currentUser, Long humanUserId, String humanUserUuid) {
        return new AuthorizationRequest(
                com.lumira.common.security.authorization.SubjectRef.humanUser(humanUserId),
                com.lumira.common.security.authorization.SubjectRef.digitalEmployee(300L),
                humanUserId,
                humanUserUuid,
                300L,
                "ai_tool",
                "execute",
                "system:file:view",
                "file.object.search",
                "LOW",
                null,
                Map.of(),
                true,
                true,
                "AI_AGENT",
                "req-1",
                "trace-1",
                currentUser
        );
    }

    private CurrentUser trustedUser(Long userId, String permissionKey) {
        CurrentUser user = new CurrentUser(userId, "admin", 1001L, "session-1", 1, true, Set.of(permissionKey));
        user.setUserUuid("user-uuid-" + userId);
        user.setPermissionsVersion("permissions-1");
        return user;
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
        private int subjectLookupCount;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("from iam_subject")) {
                subjectLookupCount++;
                String subjectType = String.valueOf(args[0]);
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
            String toolCode = args[2] == null ? null : String.valueOf(args[2]);
            String permissionKey = args[3] == null ? null : String.valueOf(args[3]);
            String resourceCode = args[4] == null ? null : String.valueOf(args[4]);
            String actionCode = args[5] == null ? null : String.valueOf(args[5]);
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
