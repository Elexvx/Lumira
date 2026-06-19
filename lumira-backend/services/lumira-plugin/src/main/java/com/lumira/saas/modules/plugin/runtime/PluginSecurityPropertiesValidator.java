package com.lumira.saas.modules.plugin.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

@Component
public class PluginSecurityPropertiesValidator implements ApplicationRunner {

    static final Set<String> UNSAFE_SIGNATURE_SECRETS = Set.of(
            "saas-plugin-signature-secret-dev-only",
            "change-me-plugin-signature-secret"
    );

    private final PluginProperties pluginProperties;
    private final Environment environment;

    public PluginSecurityPropertiesValidator(PluginProperties pluginProperties, Environment environment) {
        this.pluginProperties = pluginProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfileActive()) {
            return;
        }
        String signatureSecret = pluginProperties.getSignatureSecret();
        if (!StringUtils.hasText(signatureSecret) || UNSAFE_SIGNATURE_SECRETS.contains(signatureSecret.trim())) {
            throw new IllegalStateException("生产环境必须配置安全的 PLUGIN_SIGNATURE_SECRET，不能使用默认插件签名密钥");
        }
        if (signatureSecret.trim().length() < 32) {
            throw new IllegalStateException("生产环境 PLUGIN_SIGNATURE_SECRET 长度不能少于 32 个字符");
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }
}
