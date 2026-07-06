package com.lumira.saas.modules.plugin.runtime;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginDeclaredPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;

class PluginRuntimeSecurityPolicyTest {

    private PluginProperties properties;
    private PluginRuntimeSecurityPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new PluginProperties();
        properties.setMaxGatewayBodyBytes(16);
        properties.setRequireHttpPermission(true);
        policy = new PluginRuntimeSecurityPolicy(properties);
    }

    @Test
    void filterHeaders_shouldRemoveSensitiveHeaders() {
        Map<String, String> filtered = policy.filterHeaders(Map.ofEntries(
                entry("Authorization", "Bearer token"),
                entry("Cookie", "sid=1"),
                entry("X-Job-Token", "internal-token"),
                entry("X-Internal-Token", "internal-token"),
                entry("X-Forwarded-Internal-Token", "internal-token"),
                entry("Host", "evil.example.com"),
                entry("Forwarded", "for=198.51.100.7;host=evil.example.com"),
                entry("X-Forwarded-For", "198.51.100.7"),
                entry("X-Forwarded-Host", "evil.example.com"),
                entry("X-Forwarded-Proto", "https"),
                entry("X-Real-IP", "198.51.100.7"),
                entry("X-Request-Id", "req-1")
        ));

        assertThat(filtered).containsEntry("X-Request-Id", "req-1");
        assertThat(filtered)
                .doesNotContainKeys(
                        "Authorization",
                        "Cookie",
                        "X-Job-Token",
                        "X-Internal-Token",
                        "X-Forwarded-Internal-Token",
                        "Host",
                        "Forwarded",
                        "X-Forwarded-For",
                        "X-Forwarded-Host",
                        "X-Forwarded-Proto",
                        "X-Real-IP"
                );
    }

    @Test
    void normalizePluginPath_shouldRejectTraversal() {
        assertThat(policy.normalizePluginPath("orders/list")).isEqualTo("/orders/list");
        assertThatThrownBy(() -> policy.normalizePluginPath("/../system"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validatePluginCode_shouldRejectTraversalAndBlankCodes() {
        assertThat(policy.validatePluginCode("sms.plugin-1")).isEqualTo("sms.plugin-1");
        assertThatThrownBy(() -> policy.validatePluginCode("../system"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> policy.validatePluginCode(""))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateBodySize_shouldRejectOversizedRequest() {
        policy.validateBodySize(16);
        assertThatThrownBy(() -> policy.validateBodySize(17))
                .isInstanceOf(BizException.class);
        assertThat(policy.maxGatewayBodyBytes()).isEqualTo(16);
    }

    @Test
    void validateRequiredPermission_shouldRejectBlankPermissionWhenRequired() {
        assertThatThrownBy(() -> policy.validateRequiredPermission(null))
                .isInstanceOf(BizException.class);
        policy.validateRequiredPermission("plugin:demo:view");
    }

    @Test
    void validateRequiredPermission_shouldRejectBlankPermissionEvenWhenLegacyToggleIsDisabled() {
        properties.setRequireHttpPermission(false);

        assertThatThrownBy(() -> policy.validateRequiredPermission(""))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateRequiredPermission_shouldRequirePluginNamespaceAndDeclaration() {
        List<PluginDeclaredPermission> declared = List.of(
                new PluginDeclaredPermission("plugin:demo:view", "View demo", "demo")
        );

        assertThat(policy.validateRequiredPermission("demo", "plugin:demo:view", declared))
                .isEqualTo("plugin:demo:view");
        assertThatThrownBy(() -> policy.validateRequiredPermission("demo", "system:user:delete", declared))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> policy.validateRequiredPermission("demo", "plugin:demo:delete", declared))
                .isInstanceOf(BizException.class);
    }
}
