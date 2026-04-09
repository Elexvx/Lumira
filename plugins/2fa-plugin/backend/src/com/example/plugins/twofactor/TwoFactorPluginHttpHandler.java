package com.example.plugins.twofactor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHttpHandler;

import java.util.Map;

public class TwoFactorPluginHttpHandler implements PluginHttpHandler {

    private static final TypeReference<Map<String, String>> BODY_TYPE = new TypeReference<>() {
    };
    private final TwoFactorPluginSecondFactorProvider provider = new TwoFactorPluginSecondFactorProvider();

    @Override
    public PluginHttpResponse handle(PluginHttpRequest request, PluginRuntimeContext context) throws Exception {
        return switch (request.path()) {
            case "/", "/health" -> PluginHttpResponse.json(200, Map.of("pluginCode", "2fa", "status", "ok"));
            case "/profile" -> PluginHttpResponse.json(200, toProfile(context, request));
            case "/bind" -> {
                UserContact contact = loadContact(context, request.userId());
                yield PluginHttpResponse.json(200, provider.bind(context, request.tenantId(), request.userId(), contact.email(), contact.mobile()));
            }
            case "/challenge" -> PluginHttpResponse.json(200, provider.prepareChallenge(context, request.tenantId(), request.userId()));
            case "/verify" -> PluginHttpResponse.json(200, verify(context, request));
            case "/unbind" -> {
                provider.unbind(context, request.tenantId(), request.userId());
                yield PluginHttpResponse.json(200, Map.of("success", true));
            }
            default -> PluginHttpResponse.json(404, Map.of("message", "路径不存在"));
        };
    }

    @Override
    public String requiredPermission(PluginHttpRequest request) {
        if ("GET".equalsIgnoreCase(request.method()) && ("/".equals(request.path()) || "/health".equals(request.path()) || "/profile".equals(request.path()))) {
            return "plugin:2fa:view";
        }
        return "plugin:2fa:manage";
    }

    private PluginSecondFactorProfile toProfile(PluginRuntimeContext context, PluginHttpRequest request) {
        return provider.profile(context, request.tenantId(), request.userId());
    }

    private PluginSecondFactorVerification verify(PluginRuntimeContext context, PluginHttpRequest request) throws Exception {
        Map<String, String> body = context.getObjectMapper().readValue(request.body(), BODY_TYPE);
        return provider.verify(context, body.getOrDefault("challengeId", ""), body.getOrDefault("verificationCode", ""));
    }

    private UserContact loadContact(PluginRuntimeContext context, Long userId) {
        Map<String, Object> row = context.getJdbcTemplate().queryForMap(
                "select email, mobile from sys_user where id = ? and deleted = 0",
                userId
        );
        return new UserContact(
                row.get("email") == null ? "" : String.valueOf(row.get("email")),
                row.get("mobile") == null ? "" : String.valueOf(row.get("mobile"))
        );
    }

    private record UserContact(String email, String mobile) {
    }
}
