package com.lumira.common.web;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class FeignHeaderForwardingConfigTest {

    @Test
    void shouldNotInjectGlobalTokenWhenInternalRequestHasNoScopedToken() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("release-job-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri("/payment/internal/jobs/outbox/relay");
        template.header("X-Job-Token", "stale-token");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Job-Token");
    }

    @Test
    void shouldInjectScopedTokenForMatchingInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                null,
                null,
                null,
                null,
                null,
                "payment-token-1234567890",
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/payment/orders/order-1");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("payment-token-1234567890");
    }

    @Test
    void shouldInjectTeamScopedTokenForTeamInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "team-token-1234567890",
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/team/teams/21");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("team-token-1234567890");
    }

    @Test
    void shouldUseDedicatedAuthSystemTokenForWechatLoginSecretSettings() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/system/verification/wechat-settings");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("auth-system-token-1234567890");
    }

    @Test
    void shouldUseDedicatedAuthSystemTokenForLoginUserSnapshot() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/system/users/login/alice");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("auth-system-token-1234567890");
    }

    @Test
    void shouldUseDedicatedAuthSystemTokenForPasskeyCredentialInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/system/passkeys/assertion?credentialId=credential-1");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("auth-system-token-1234567890");
    }

    @Test
    void shouldUseDedicatedAuthSystemTokenForPasskeySettingsInternalRequest() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/system/verification/passkey-settings");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("auth-system-token-1234567890");
    }

    @Test
    void shouldUseDedicatedAuthSystemTokenForOtherAuthOwnedInternalSystemRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "GET", "/internal/system/security/settings", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/verification/providers/totp", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "POST", "/internal/system/captcha/validate", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "POST", "/internal/system/audit/login", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "POST", "/internal/system/users/wechat-login", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/menus/ai-visible?userId=42&userUuid=user-uuid-42", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/permissions/snapshot?userId=42&userUuid=user-uuid-42", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/read-model-version?contextName=platform&scope=public-bootstrap", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/read-model-version?contextName=platform&scope=runtime-appearance", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/profile", "auth-system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/requires-password-change?userUuid=user-uuid-42", "auth-system-token-1234567890");
    }

    @Test
    void shouldUseMessageScopedTokenForNotificationRuntimeSecretSettings() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/system/config/notification-runtime-values");

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Job-Token");
        assertThat(values).containsExactly("message-token-1234567890");
    }

    @Test
    void shouldUseMessageScopedTokenForMessageRuntimeSecretSettings() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "GET", "/internal/system/config/runtime/smtp", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/config/runtime/wechat-official", "message-token-1234567890");
    }

    @Test
    void shouldUseMessageScopedTokenForSystemRecipientInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/target-uuid", "message-token-1234567890");
        assertUsesJobToken(interceptor, "POST", "/internal/system/audit/operation", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/permissions/role-snapshot?userId=42&userUuid=user-uuid-42", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/identities-by-ids?ids=42&ids=43", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/email-recipients?ids=1", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/roles/7/wechat-recipients", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/platform/email-recipients", "message-token-1234567890");
    }

    @Test
    void shouldUseMessageScopedTokenForSystemRecipientLookupInternalRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "GET", "/internal/system/roles/names-by-ids?ids=7", "message-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/roles/7/identities", "message-token-1234567890");
    }

    @Test
    void shouldUseMessageScopedTokenForUnreadReadModelVersionBump() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(
                interceptor,
                "POST",
                "/internal/system/read-model-version/bump?contextName=message&scope=unread&eventKey=message.unread",
                "message-token-1234567890"
        );
    }

    @Test
    void shouldUseMessageScopedTokenForUnreadReadModelVersionRead() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                "message-token-1234567890",
                null,
                null,
                null
        ).requestInterceptor();

        assertUsesJobToken(
                interceptor,
                "GET",
                "/internal/system/read-model-version?contextName=message&scope=unread",
                "message-token-1234567890"
        );
    }

    @Test
    void shouldUsePluginScopedTokenForPluginPermissionRegistration() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                null,
                null,
                null,
                null,
                null,
                "plugin-token-1234567890",
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "POST", "/internal/system/permissions/plugin", "plugin-token-1234567890");
        assertUsesJobToken(interceptor, "POST", "/internal/system/permissions/invalidate", "plugin-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/menus/builtin", "plugin-token-1234567890");
        assertUsesJobToken(
                interceptor,
                "POST",
                "/internal/system/read-model-version/bump?contextName=plugin&scope=bootstrap&eventKey=plugin.enabled",
                "plugin-token-1234567890"
        );
        assertUsesJobToken(
                interceptor,
                "GET",
                "/internal/system/read-model-version?contextName=plugin&scope=bootstrap",
                "plugin-token-1234567890"
        );
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/email-available?userUuid=user-uuid-42", "plugin-token-1234567890");
    }

    @Test
    void shouldNotOvermatchSystemUserScopedTokensForLookalikePaths() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                "system-token-1234567890",
                "auth-token-1234567890",
                "auth-system-token-1234567890",
                null,
                null,
                null,
                "plugin-token-1234567890",
                null
        ).requestInterceptor();

        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/profile-picture", "system-token-1234567890");
        assertUsesJobToken(interceptor, "GET", "/internal/system/users/42/email-available-history", "system-token-1234567890");
    }

    @Test
    void shouldNotInjectJobTokenForPublicRequests() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("release-job-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/api/v2/payment/providers");
        template.header("X-Job-Token", "stale-token");
        template.header("X-Internal-Token", "stale-token");
        template.header("X-Forwarded-Internal-Token", "stale-token");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Job-Token");
        assertThat(template.headers()).doesNotContainKey("X-Internal-Token");
        assertThat(template.headers()).doesNotContainKey("X-Forwarded-Internal-Token");
    }

    @Test
    void shouldNotInjectJobTokenForAbsoluteExternalInternalLookingUrl() {
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("release-job-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.target("https://example.com");
        template.uri("/internal/system/permissions/snapshot");
        template.header("X-Job-Token", "stale-token");
        template.header("X-Internal-Token", "stale-token");
        template.header("X-Forwarded-Internal-Token", "stale-token");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Job-Token");
        assertThat(template.headers()).doesNotContainKey("X-Internal-Token");
        assertThat(template.headers()).doesNotContainKey("X-Forwarded-Internal-Token");
    }

    @Test
    void shouldStripUserAuthorizationAndCookieForInternalRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("Cookie", "refresh_token=user-refresh");
        request.addHeader("X-Request-Id", "request-1");
        request.addHeader("X-Trace-Id", "trace-1");
        request.setCookies(new Cookie("refresh_token", "user-refresh"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig(
                null,
                "auth-token-1234567890",
                null,
                null,
                null,
                null,
                null,
                null
        ).requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/internal/auth/sessions/session-1/current-user");
        template.header("Authorization", "Bearer stale-service-token");
        template.header("Cookie", "stale=session");
        template.header("X-Job-Token", "stale-token");
        template.header("X-Internal-Token", "stale-token");
        template.header("X-Forwarded-Internal-Token", "stale-token");

        try {
            interceptor.apply(template);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(template.headers()).doesNotContainKey("Authorization");
        assertThat(template.headers()).doesNotContainKey("Cookie");
        assertThat(template.headers()).doesNotContainKey("X-Internal-Token");
        assertThat(template.headers()).doesNotContainKey("X-Forwarded-Internal-Token");
        assertThat(template.headers().get("X-Job-Token")).containsExactly("auth-token-1234567890");
        assertThat(template.headers().get("X-Request-Id")).containsExactly("request-1");
        assertThat(template.headers().get("X-Trace-Id")).containsExactly("trace-1");
    }

    @Test
    void shouldReplaceRatherThanAppendAuthorizationForPublicRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("internal-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.uri("/api/v2/payment/providers");
        template.header("Authorization", "Bearer stale-token");

        try {
            interceptor.apply(template);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer user-token");
    }

    @Test
    void shouldNotForwardUserAuthorizationToAbsoluteExternalRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("Cookie", "refresh_token=user-refresh");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestInterceptor interceptor = new FeignHeaderForwardingConfig("internal-token-1234567890").requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.target("https://external.example");
        template.uri("/api/status");
        template.header("Authorization", "Bearer stale-token");
        template.header("Cookie", "stale=session");

        try {
            interceptor.apply(template);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(template.headers()).doesNotContainKey("Authorization");
        assertThat(template.headers()).doesNotContainKey("Cookie");
    }

    private void assertUsesJobToken(RequestInterceptor interceptor, String method, String uri, String expectedToken) {
        RequestTemplate template = new RequestTemplate();
        template.method(method);
        template.uri(uri);

        interceptor.apply(template);

        assertThat(template.headers().get("X-Job-Token")).containsExactly(expectedToken);
    }
}
