package com.lumira.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenAuthFilterTest {

    @Test
    void recognizesRootAndContextPrefixedInternalPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/file/internal/jobs/processing/run")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/payment/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/plugin/internal/jobs/outbox/relay")).isTrue();
    }

    @Test
    void ignoresPublicAndApplicationPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath(null)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/api/v2/platform/public/bootstrap")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/api/v2/notices")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/settings/internalization")).isFalse();
    }

    @Test
    void leavesRuntimeControlPathsToTheirDedicatedTokenFilter() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/internal/runtime/drain")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/async/internal/runtime/drain")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/internal/system/runtime")).isTrue();
    }

    @Test
    void authorizesOnlyExactInternalToken() {
        String internalToken = "strong-internal-service-token-2026";

        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken, internalToken)).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(null, internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("   ", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("a".repeat(513), internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("token\nvalue", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("strong-internal-service-token-202X", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken + " ", internalToken)).isFalse();
    }

    @Test
    void internalPathRequiresTokenEvenWhenAuthenticationAlreadyExists() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "jwt", List.of())
        );
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "strong-internal-service-token-2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user");
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalPathUsesInternalPrincipalOnlyForCurrentInvocation() throws Exception {
        var previousAuthentication = new UsernamePasswordAuthenticationToken("user", "jwt", List.of());
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                null,
                null,
                null,
                null,
                "strong-internal-service-token-2026",
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/message/internal/jobs/outbox/relay");
        request.addHeader("X-Job-Token", "strong-internal-service-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isInstanceOf(CurrentUser.class);
            CurrentUser principal = (CurrentUser) authentication.getPrincipal();
            assertThat(principal.getUsername()).isEqualTo("internal-service");
            assertThat(principal.isAuthenticated()).isFalse();
            assertThat(principal.getPermissions()).isEmpty();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainInvoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopedAuthSystemTokenOverridesGlobalSystemTokenForPermissionSnapshotPath() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/permissions/snapshot");
        request.addHeader("X-Job-Token", "global-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void scopedAuthSystemTokenAllowsPermissionSnapshotPath() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/permissions/snapshot");
        request.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void wechatLoginSecretSettingsRequireDedicatedAuthSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/verification/wechat-settings");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("GET", "/internal/system/verification/wechat-settings");
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    @Test
    void loginUserSnapshotRequiresDedicatedAuthSystemTokenBecauseItContainsPasswordHash() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/users/login/alice");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("GET", "/internal/system/users/login/alice");
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    @Test
    void passkeyCredentialInternalEndpointsRequireDedicatedAuthSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/passkeys/assertion");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("GET", "/internal/system/passkeys/assertion");
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    @Test
    void passkeySettingsInternalEndpointRequiresDedicatedAuthSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/verification/passkey-settings");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("GET", "/internal/system/verification/passkey-settings");
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    @Test
    void authOwnedInternalSystemEndpointsRequireDedicatedAuthSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                null,
                null
        );

        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/security/settings");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/verification/providers/totp");
        assertRequiresAuthSystemScopedToken(filter, "POST", "/internal/system/captcha/validate");
        assertRequiresAuthSystemScopedToken(filter, "POST", "/internal/system/audit/login");
        assertRequiresAuthSystemScopedToken(filter, "POST", "/internal/system/users/wechat-login");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/menus/ai-visible?userId=42&userUuid=user-uuid-42");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/permissions/snapshot");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/users/42");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/users/42/profile");
        assertRequiresAuthSystemScopedToken(filter, "GET", "/internal/system/users/42/requires-password-change");
        assertRequiresAuthSystemScopedToken(
                filter,
                "GET",
                "/internal/system/read-model-version",
                "contextName=platform&scope=public-bootstrap"
        );
        assertRequiresAuthSystemScopedToken(
                filter,
                "GET",
                "/internal/system/read-model-version",
                "contextName=platform&scope=runtime-appearance"
        );
    }

    @Test
    void notificationRuntimeSecretSettingsRequireMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/config/notification-runtime-values");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("GET", "/internal/system/config/notification-runtime-values");
        authorizedRequest.addHeader("X-Job-Token", "message-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    @Test
    void messageRuntimeSecretSettingsRequireMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );

        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/config/runtime/smtp");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/config/runtime/wechat-official");
    }

    @Test
    void systemRecipientInternalEndpointsRequireMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );

        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/users/42/target-uuid");
        assertRequiresMessageScopedToken(filter, "POST", "/internal/system/audit/operation");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/permissions/role-snapshot");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/users/identities-by-ids?ids=42&ids=43");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/users/email-recipients");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/roles/8/wechat-recipients");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/platform/email-recipients");
    }

    @Test
    void systemRecipientLookupInternalEndpointsRequireMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );

        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/roles/names-by-ids?ids=8");
        assertRequiresMessageScopedToken(filter, "GET", "/internal/system/roles/8/identities");
    }

    @Test
    void messageUnreadReadModelVersionBumpRequiresMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );

        assertRequiresMessageScopedToken(
                filter,
                "POST",
                "/internal/system/read-model-version/bump",
                "contextName=message&scope=unread&eventKey=message.unread"
        );
    }

    @Test
    void messageUnreadReadModelVersionReadRequiresMessageScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                "message-internal-token-2026",
                null,
                null,
                null
        );

        assertRequiresMessageScopedToken(
                filter,
                "GET",
                "/internal/system/read-model-version",
                "contextName=message&scope=unread"
        );
    }

    @Test
    void pluginPermissionRegistrationRequiresPluginScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                "plugin-internal-token-2026",
                null
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/system/permissions/plugin");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest("POST", "/internal/system/permissions/plugin");
        authorizedRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);

        MockHttpServletRequest invalidateRequest = new MockHttpServletRequest("POST", "/internal/system/permissions/invalidate");
        invalidateRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse invalidateResponse = new MockHttpServletResponse();
        MockFilterChain invalidateChain = new MockFilterChain();

        filter.doFilterInternal(invalidateRequest, invalidateResponse, invalidateChain);

        assertThat(invalidateResponse.getStatus()).isEqualTo(401);
        assertThat(invalidateChain.getRequest()).isNull();

        MockHttpServletRequest authorizedInvalidateRequest = new MockHttpServletRequest("POST", "/internal/system/permissions/invalidate");
        authorizedInvalidateRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedInvalidateResponse = new MockHttpServletResponse();
        MockFilterChain authorizedInvalidateChain = new MockFilterChain();

        filter.doFilterInternal(authorizedInvalidateRequest, authorizedInvalidateResponse, authorizedInvalidateChain);

        assertThat(authorizedInvalidateResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedInvalidateChain.getRequest()).isSameAs(authorizedInvalidateRequest);

        MockHttpServletRequest builtinMenusRequest = new MockHttpServletRequest("GET", "/internal/system/menus/builtin");
        builtinMenusRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse builtinMenusResponse = new MockHttpServletResponse();
        MockFilterChain builtinMenusChain = new MockFilterChain();

        filter.doFilterInternal(builtinMenusRequest, builtinMenusResponse, builtinMenusChain);

        assertThat(builtinMenusResponse.getStatus()).isEqualTo(401);
        assertThat(builtinMenusChain.getRequest()).isNull();

        MockHttpServletRequest authorizedBuiltinMenusRequest = new MockHttpServletRequest("GET", "/internal/system/menus/builtin");
        authorizedBuiltinMenusRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedBuiltinMenusResponse = new MockHttpServletResponse();
        MockFilterChain authorizedBuiltinMenusChain = new MockFilterChain();

        filter.doFilterInternal(authorizedBuiltinMenusRequest, authorizedBuiltinMenusResponse, authorizedBuiltinMenusChain);

        assertThat(authorizedBuiltinMenusResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedBuiltinMenusChain.getRequest()).isSameAs(authorizedBuiltinMenusRequest);

        MockHttpServletRequest pluginReadModelRequest = new MockHttpServletRequest("POST", "/internal/system/read-model-version/bump");
        pluginReadModelRequest.setQueryString("contextName=plugin&scope=bootstrap&eventKey=plugin.enabled");
        pluginReadModelRequest.addParameter("contextName", "plugin");
        pluginReadModelRequest.addParameter("scope", "bootstrap");
        pluginReadModelRequest.addParameter("eventKey", "plugin.enabled");
        pluginReadModelRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse pluginReadModelResponse = new MockHttpServletResponse();
        MockFilterChain pluginReadModelChain = new MockFilterChain();

        filter.doFilterInternal(pluginReadModelRequest, pluginReadModelResponse, pluginReadModelChain);

        assertThat(pluginReadModelResponse.getStatus()).isEqualTo(401);
        assertThat(pluginReadModelChain.getRequest()).isNull();

        MockHttpServletRequest authorizedPluginReadModelRequest = new MockHttpServletRequest("POST", "/internal/system/read-model-version/bump");
        authorizedPluginReadModelRequest.setQueryString("contextName=plugin&scope=bootstrap&eventKey=plugin.enabled");
        authorizedPluginReadModelRequest.addParameter("contextName", "plugin");
        authorizedPluginReadModelRequest.addParameter("scope", "bootstrap");
        authorizedPluginReadModelRequest.addParameter("eventKey", "plugin.enabled");
        authorizedPluginReadModelRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedPluginReadModelResponse = new MockHttpServletResponse();
        MockFilterChain authorizedPluginReadModelChain = new MockFilterChain();

        filter.doFilterInternal(authorizedPluginReadModelRequest, authorizedPluginReadModelResponse, authorizedPluginReadModelChain);

        assertThat(authorizedPluginReadModelResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedPluginReadModelChain.getRequest()).isSameAs(authorizedPluginReadModelRequest);

        MockHttpServletRequest pluginReadModelReadRequest = new MockHttpServletRequest("GET", "/internal/system/read-model-version");
        pluginReadModelReadRequest.setQueryString("contextName=plugin&scope=bootstrap");
        pluginReadModelReadRequest.addParameter("contextName", "plugin");
        pluginReadModelReadRequest.addParameter("scope", "bootstrap");
        pluginReadModelReadRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse pluginReadModelReadResponse = new MockHttpServletResponse();
        MockFilterChain pluginReadModelReadChain = new MockFilterChain();

        filter.doFilterInternal(pluginReadModelReadRequest, pluginReadModelReadResponse, pluginReadModelReadChain);

        assertThat(pluginReadModelReadResponse.getStatus()).isEqualTo(401);
        assertThat(pluginReadModelReadChain.getRequest()).isNull();

        MockHttpServletRequest authorizedPluginReadModelReadRequest = new MockHttpServletRequest("GET", "/internal/system/read-model-version");
        authorizedPluginReadModelReadRequest.setQueryString("contextName=plugin&scope=bootstrap");
        authorizedPluginReadModelReadRequest.addParameter("contextName", "plugin");
        authorizedPluginReadModelReadRequest.addParameter("scope", "bootstrap");
        authorizedPluginReadModelReadRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedPluginReadModelReadResponse = new MockHttpServletResponse();
        MockFilterChain authorizedPluginReadModelReadChain = new MockFilterChain();

        filter.doFilterInternal(authorizedPluginReadModelReadRequest, authorizedPluginReadModelReadResponse, authorizedPluginReadModelReadChain);

        assertThat(authorizedPluginReadModelReadResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedPluginReadModelReadChain.getRequest()).isSameAs(authorizedPluginReadModelReadRequest);

        MockHttpServletRequest emailAvailableRequest = new MockHttpServletRequest("GET", "/internal/system/users/42/email-available");
        emailAvailableRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse emailAvailableResponse = new MockHttpServletResponse();
        MockFilterChain emailAvailableChain = new MockFilterChain();

        filter.doFilterInternal(emailAvailableRequest, emailAvailableResponse, emailAvailableChain);

        assertThat(emailAvailableResponse.getStatus()).isEqualTo(401);
        assertThat(emailAvailableChain.getRequest()).isNull();

        MockHttpServletRequest authorizedEmailAvailableRequest = new MockHttpServletRequest("GET", "/internal/system/users/42/email-available");
        authorizedEmailAvailableRequest.addHeader("X-Job-Token", "plugin-internal-token-2026");
        MockHttpServletResponse authorizedEmailAvailableResponse = new MockHttpServletResponse();
        MockFilterChain authorizedEmailAvailableChain = new MockFilterChain();

        filter.doFilterInternal(authorizedEmailAvailableRequest, authorizedEmailAvailableResponse, authorizedEmailAvailableChain);

        assertThat(authorizedEmailAvailableResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedEmailAvailableChain.getRequest()).isSameAs(authorizedEmailAvailableRequest);
    }

    @Test
    void lookalikeSystemUserPathsRemainOnBroadSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                "auth-internal-token-2026",
                "auth-system-internal-token-2026",
                null,
                null,
                null,
                "plugin-internal-token-2026",
                null
        );

        assertAuthorized(filter, "GET", "/internal/system/users/42/profile-picture", "system-internal-token-2026");
        assertAuthorized(filter, "GET", "/internal/system/users/42/email-available-history", "system-internal-token-2026");
    }

    @Test
    void serviceInternalJobPathsRequireMatchingServiceScopedToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                "file-internal-token-2026",
                "message-internal-token-2026",
                "payment-internal-token-2026",
                "plugin-internal-token-2026",
                "job-internal-token-2026"
        );

        assertAuthorized(filter, "/message/internal/jobs/outbox/relay", "message-internal-token-2026");
        assertAuthorized(filter, "/file/internal/jobs/outbox/relay", "file-internal-token-2026");
        assertAuthorized(filter, "/payment/internal/jobs/outbox/relay", "payment-internal-token-2026");
        assertAuthorized(filter, "/plugin/internal/jobs/outbox/relay", "plugin-internal-token-2026");
    }

    @Test
    void teamInternalPathsRequireDedicatedTeamToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                null,
                "team-internal-token-2026",
                null
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/team/teams/21");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        assertAuthorized(filter, "GET", "/internal/team/teams/21", "team-internal-token-2026");
    }

    @Test
    void systemInternalJobPathRequiresDedicatedJobTokenBeforeSystemToken() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                null,
                "job-internal-token-2026"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/jobs/outbox/relay");
        request.addHeader("X-Job-Token", "job-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);

        MockHttpServletRequest systemTokenRequest = new MockHttpServletRequest("POST", "/internal/jobs/outbox/relay");
        systemTokenRequest.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse systemTokenResponse = new MockHttpServletResponse();
        MockFilterChain systemTokenChain = new MockFilterChain();

        filter.doFilterInternal(systemTokenRequest, systemTokenResponse, systemTokenChain);

        assertThat(systemTokenResponse.getStatus()).isEqualTo(401);
        assertThat(systemTokenChain.getRequest()).isNull();
    }

    @Test
    void systemInternalJobPathRejectsSystemTokenWhenDedicatedJobTokenMissing() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/jobs/outbox/relay");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonInternalPathKeepsExistingAuthenticationWithoutToken() throws Exception {
        var previousAuthentication = new UsernamePasswordAuthenticationToken("user", "jwt", List.of());
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter("strong-internal-service-token-2026");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/notices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        SecurityContextHolder.clearContext();
    }

    private void assertAuthorized(InternalServiceTokenAuthFilter filter, String path, String token) throws Exception {
        assertAuthorized(filter, "POST", path, token);
    }

    private void assertAuthorized(InternalServiceTokenAuthFilter filter, String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Job-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private void assertRequiresAuthSystemScopedToken(InternalServiceTokenAuthFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest(method, path);
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    private void assertRequiresAuthSystemScopedToken(
            InternalServiceTokenAuthFilter filter,
            String method,
            String path,
            String queryString
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setQueryString(queryString);
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest(method, path);
        authorizedRequest.setQueryString(queryString);
        authorizedRequest.addHeader("X-Job-Token", "auth-system-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    private void assertRequiresMessageScopedToken(InternalServiceTokenAuthFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest(method, path);
        authorizedRequest.addHeader("X-Job-Token", "message-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }

    private void assertRequiresMessageScopedToken(InternalServiceTokenAuthFilter filter, String method, String path, String queryString) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setQueryString(queryString);
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest authorizedRequest = new MockHttpServletRequest(method, path);
        authorizedRequest.setQueryString(queryString);
        authorizedRequest.addHeader("X-Job-Token", "message-internal-token-2026");
        MockHttpServletResponse authorizedResponse = new MockHttpServletResponse();
        MockFilterChain authorizedChain = new MockFilterChain();

        filter.doFilterInternal(authorizedRequest, authorizedResponse, authorizedChain);

        assertThat(authorizedResponse.getStatus()).isEqualTo(200);
        assertThat(authorizedChain.getRequest()).isSameAs(authorizedRequest);
    }
}
