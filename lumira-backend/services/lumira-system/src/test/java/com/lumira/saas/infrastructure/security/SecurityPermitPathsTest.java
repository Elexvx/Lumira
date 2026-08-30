package com.lumira.saas.infrastructure.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class SecurityPermitPathsTest {

    @Test
    void localizationRuntimeEndpointShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        Assertions.assertTrue(mainConfig.contains("- /api/v2/localization/runtime/**"), mainConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/localization/runtime/**"), testConfig);
        Assertions.assertFalse(mainConfig.contains("- /api/v1/localization/runtime/**"), mainConfig);
        Assertions.assertFalse(testConfig.contains("- /api/v1/localization/runtime/**"), testConfig);
    }

    @Test
    void loginCodeEndpointsShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/login/code/challenge"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/login/code/complete"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v2/auth/login"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v2/auth/login/code/challenge"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v2/auth/login/code/complete"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v2/auth/login-encryption-key"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v2/auth/refresh-token"), mainConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/login/code/challenge"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/login/code/complete"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/auth/login"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/auth/login/code/challenge"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/auth/login/code/complete"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/auth/login-encryption-key"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v2/auth/refresh-token"), testConfig);
    }

    @Test
    void registrationEndpointsShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        for (String path : new String[]{
                "/api/v1/auth/registration/contact/availability",
                "/api/v1/auth/registration/code/challenge",
                "/api/v1/auth/registration/complete"
        }) {
            Assertions.assertTrue(mainConfig.contains("- " + path), mainConfig);
            Assertions.assertTrue(testConfig.contains("- " + path), testConfig);
        }
    }

    @Test
    void updaterMaintenanceAcknowledgementShouldBePublicInMainAndTestConfig() throws IOException {
        String path = "/api/v2/platform/update-maintenance/ack";
        Assertions.assertTrue(readMainConfig().contains("- " + path));
        Assertions.assertTrue(readConfig("src/test/resources/application.yml").contains("- " + path));
    }

    @Test
    void wechatLoginEndpointsShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/wechat/authorize-url"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/wechat/login"), mainConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/wechat/authorize-url"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/wechat/login"), testConfig);
    }

    @Test
    void passkeyLoginEndpointsShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/passkeys/authentication/options"), mainConfig);
        Assertions.assertTrue(mainConfig.contains("- /api/v1/auth/passkeys/authentication/complete"), mainConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/passkeys/authentication/options"), testConfig);
        Assertions.assertTrue(testConfig.contains("- /api/v1/auth/passkeys/authentication/complete"), testConfig);
    }

    @Test
    void uploadStaticResourcesShouldStayPublicForResourceInterceptorInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        Assertions.assertTrue(mainConfig.contains("- /api/uploads/**"), mainConfig);
        Assertions.assertTrue(testConfig.contains("- /api/uploads/**"), testConfig);
    }

    @Test
    void ownerReadinessEndpointsShouldBePublicForOperationalDrillsInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        for (String path : new String[]{"/api/v2/*/readiness", "/api/v2/*/health"}) {
            Assertions.assertTrue(mainConfig.contains("- " + path), mainConfig);
            Assertions.assertTrue(testConfig.contains("- " + path), testConfig);
        }
    }

    @Test
    void metricsEndpointsShouldNotBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        for (String path : new String[]{"/actuator/prometheus", "/api/v2/*/metrics"}) {
            Assertions.assertFalse(mainConfig.contains("- " + path), mainConfig);
            Assertions.assertFalse(testConfig.contains("- " + path), testConfig);
        }
    }

    @Test
    void v2PublicHotPathsShouldBePublicInMainAndTestConfig() throws IOException {
        String mainConfig = readMainConfig();
        String testConfig = readConfig("src/test/resources/application.yml");

        for (String path : new String[]{"/api/v2/platform/public/bootstrap", "/api/v2/localization/runtime/**", "/api/v2/payment/webhook/**", "/api/v2/payment/webhooks/**"}) {
            Assertions.assertTrue(mainConfig.contains("- " + path), mainConfig);
            Assertions.assertTrue(testConfig.contains("- " + path), testConfig);
        }
    }

    private static String readConfig(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private static String readMainConfig() throws IOException {
        return Files.readString(Path.of("../lumira-admin/src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }
}
