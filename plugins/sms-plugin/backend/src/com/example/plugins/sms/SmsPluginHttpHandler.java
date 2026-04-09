package com.example.plugins.sms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeContext;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpRequest;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginHttpResponse;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginHttpHandler;

import java.util.Map;

public class SmsPluginHttpHandler implements PluginHttpHandler {

    private static final TypeReference<Map<String, String>> BODY_TYPE = new TypeReference<>() {
    };
    private final SmsPluginSecondFactorProvider provider = new SmsPluginSecondFactorProvider();

    @Override
    public PluginHttpResponse handle(PluginHttpRequest request, PluginRuntimeContext context) throws Exception {
        return switch (request.path()) {
            case "/", "/health" -> PluginHttpResponse.json(200, Map.of("pluginCode", "sms", "status", "ok"));
            case "/profile" -> PluginHttpResponse.json(200, provider.profile(context, request.tenantId(), request.userId()));
            case "/config" -> handleConfig(request, context);
            case "/bind" -> {
                SmsPluginSecondFactorProvider.BindingContact contact = loadContact(context, request.userId());
                yield PluginHttpResponse.json(200, provider.bind(context, request.tenantId(), request.userId(), contact.email(), contact.mobile()));
            }
            case "/challenge" -> PluginHttpResponse.json(200, provider.prepareChallenge(context, request.tenantId(), request.userId()));
            case "/verify" -> PluginHttpResponse.json(200, verify(request, context));
            case "/unbind" -> {
                provider.unbind(context, request.tenantId(), request.userId());
                yield PluginHttpResponse.json(200, Map.of("success", true));
            }
            default -> PluginHttpResponse.json(404, Map.of("message", "路径不存在"));
        };
    }

    @Override
    public String requiredPermission(PluginHttpRequest request) {
        if ("GET".equalsIgnoreCase(request.method()) && ("/".equals(request.path()) || "/health".equals(request.path()) || "/profile".equals(request.path()) || "/config".equals(request.path()))) {
            return "plugin:sms:view";
        }
        return "plugin:sms:manage";
    }

    private PluginHttpResponse handleConfig(PluginHttpRequest request, PluginRuntimeContext context) throws Exception {
        if ("GET".equalsIgnoreCase(request.method())) {
            return PluginHttpResponse.json(200, provider.getGatewayConfigView(context, request.tenantId()));
        }
        if ("PUT".equalsIgnoreCase(request.method()) || "POST".equalsIgnoreCase(request.method())) {
            SmsPluginSecondFactorProvider.SmsGatewayConfig payload = context.getObjectMapper().readValue(request.body(), SmsPluginSecondFactorProvider.SmsGatewayConfig.class);
            return PluginHttpResponse.json(200, provider.saveGatewayConfig(context, request.tenantId(), payload));
        }
        return PluginHttpResponse.json(405, Map.of("message", "请求方法不支持"));
    }

    private PluginSecondFactorVerification verify(PluginHttpRequest request, PluginRuntimeContext context) throws Exception {
        Map<String, String> body = context.getObjectMapper().readValue(request.body(), BODY_TYPE);
        return provider.verify(
                context,
                body.getOrDefault("challengeId", ""),
                body.getOrDefault("verificationCode", "")
        );
    }

    private SmsPluginSecondFactorProvider.BindingContact loadContact(PluginRuntimeContext context, Long userId) {
        Map<String, Object> row = context.getJdbcTemplate().queryForMap(
                "select email, mobile from sys_user where id = ? and deleted = 0",
                userId
        );
        return new SmsPluginSecondFactorProvider.BindingContact(
                row.get("email") == null ? "" : String.valueOf(row.get("email")),
                row.get("mobile") == null ? "" : String.valueOf(row.get("mobile"))
        );
    }
}
