package com.lumira.common.security;

import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationTrustSupportTest {

    @Test
    void canReuseTrustedCurrentUserAuthentication() {
        CurrentUser currentUser = trustedUser();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, "token", Set.of());

        assertThat(AuthenticationTrustSupport.canReuse(authentication)).isTrue();
    }

    @Test
    void canReuseInternalServiceTokenAuthenticationWithoutTreatingItAsUserLogin() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", "internal", 0, false, Set.of());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(internalService, "token", Set.of());

        assertThat(AuthenticationTrustSupport.canReuse(authentication)).isTrue();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(authentication)).isTrue();
        assertThat(AuthenticationTrustSupport.isTrustedCurrentUser(internalService)).isFalse();
    }

    @Test
    void rejectsUntrustedPreAuthenticatedPrincipals() {
        UsernamePasswordAuthenticationToken stringPrincipal =
                new UsernamePasswordAuthenticationToken("alice", "token", Set.of());
        CurrentUser missingSession = trustedUser();
        missingSession.setSessionId(null);
        UsernamePasswordAuthenticationToken missingSessionPrincipal =
                new UsernamePasswordAuthenticationToken(missingSession, "token", Set.of());
        CurrentUser missingUserUuid = trustedUser();
        missingUserUuid.setUserUuid(null);
        UsernamePasswordAuthenticationToken missingUserUuidPrincipal =
                new UsernamePasswordAuthenticationToken(missingUserUuid, "token", Set.of());
        CurrentUser missingPermissionsVersion = trustedUser();
        missingPermissionsVersion.setPermissionsVersion(null);
        UsernamePasswordAuthenticationToken missingPermissionsVersionPrincipal =
                new UsernamePasswordAuthenticationToken(missingPermissionsVersion, "token", Set.of());
        CurrentUser unsafeSessionId = trustedUser();
        unsafeSessionId.setSessionId("../session");
        UsernamePasswordAuthenticationToken unsafeSessionPrincipal =
                new UsernamePasswordAuthenticationToken(unsafeSessionId, "token", Set.of());

        assertThat(AuthenticationTrustSupport.canReuse(stringPrincipal)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(stringPrincipal)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(missingSessionPrincipal)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(missingUserUuidPrincipal)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(missingPermissionsVersionPrincipal)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(unsafeSessionPrincipal)).isFalse();
    }

    @Test
    void rejectsMalformedInternalServicePrincipals() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", "internal", 0, false, Set.of());
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken(internalService, "token");
        CurrentUser privilegedInternalService = new CurrentUser(0L, "internal-service", "internal", 0, false, Set.of("*"));
        UsernamePasswordAuthenticationToken privileged =
                new UsernamePasswordAuthenticationToken(privilegedInternalService, "token", Set.of());
        CurrentUser internalWithUserUuid = new CurrentUser(0L, "internal-service", "internal", 0, false, Set.of());
        internalWithUserUuid.setUserUuid("user-uuid-1");
        UsernamePasswordAuthenticationToken withUserUuid =
                new UsernamePasswordAuthenticationToken(internalWithUserUuid, "token", Set.of());
        CurrentUser internalWithPermissionsVersion = new CurrentUser(0L, "internal-service", "internal", 0, false, Set.of());
        internalWithPermissionsVersion.setPermissionsVersion("permissions-1");
        UsernamePasswordAuthenticationToken withPermissionsVersion =
                new UsernamePasswordAuthenticationToken(internalWithPermissionsVersion, "token", Set.of());
        CurrentUser internalWithHumanScopes = new CurrentUser(
                0L,
                "internal-service",
                "internal",
                0,
                false,
                Set.of(),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L),
                List.of(new DataPermissionRule("dept", DataScopeType.CUSTOM, List.of(9L), List.of()))
        );
        UsernamePasswordAuthenticationToken withHumanScopes =
                new UsernamePasswordAuthenticationToken(internalWithHumanScopes, "token", Set.of());

        assertThat(AuthenticationTrustSupport.canReuse(unauthenticated)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(unauthenticated)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(privileged)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(privileged)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(withUserUuid)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(withUserUuid)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(withPermissionsVersion)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(withPermissionsVersion)).isFalse();
        assertThat(AuthenticationTrustSupport.canReuse(withHumanScopes)).isFalse();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(withHumanScopes)).isFalse();
    }

    private CurrentUser trustedUser() {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", "session-1", 1, true, Set.of("system:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
