package com.lumira.saas.modules.plugin.runtime;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Map<String, String> filtered = policy.filterHeaders(Map.of(
                "Authorization", "Bearer token",
                "Cookie", "sid=1",
                "X-Request-Id", "req-1"
        ));

        assertThat(filtered).containsEntry("X-Request-Id", "req-1");
        assertThat(filtered).doesNotContainKeys("Authorization", "Cookie");
    }

    @Test
    void normalizePluginPath_shouldRejectTraversal() {
        assertThat(policy.normalizePluginPath("orders/list")).isEqualTo("/orders/list");
        assertThatThrownBy(() -> policy.normalizePluginPath("/../system"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateBodySize_shouldRejectOversizedRequest() {
        policy.validateBodySize(16);
        assertThatThrownBy(() -> policy.validateBodySize(17))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateRequiredPermission_shouldRejectBlankPermissionWhenRequired() {
        assertThatThrownBy(() -> policy.validateRequiredPermission(null))
                .isInstanceOf(BizException.class);
        policy.validateRequiredPermission("plugin:demo:view");
    }
}
