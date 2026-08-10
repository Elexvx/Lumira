package com.lumira.server;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class LumiraServerApplicationYamlTest {

    @Test
    void verificationSettingsUseSaasVerificationPrefixAndDoNotExposeDeadAiOwnerConfig() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("saas.verification.issuer")).isEqualTo("${VERIFICATION_ISSUER:lumira}");
        assertThat(properties.getProperty("saas.verification.totp-digits")).isEqualTo("${VERIFICATION_TOTP_DIGITS:6}");
        assertThat(properties.getProperty("saas.verification.totp-step-seconds")).isEqualTo("${VERIFICATION_TOTP_STEP_SECONDS:30}");
        assertThat(properties.getProperty("saas.verification.bind-challenge-expire-minutes"))
                .isEqualTo("${VERIFICATION_BIND_CHALLENGE_EXPIRE_MINUTES:10}");
        assertThat(properties.getProperty("saas.verification.login-challenge-expire-minutes"))
                .isEqualTo("${VERIFICATION_LOGIN_CHALLENGE_EXPIRE_MINUTES:5}");
        assertThat(properties.getProperty("saas.verification.recovery-code-count"))
                .isEqualTo("${VERIFICATION_RECOVERY_CODE_COUNT:8}");
        assertThat(properties.getProperty("saas.verification.recovery-code-length"))
                .isEqualTo("${VERIFICATION_RECOVERY_CODE_LENGTH:8}");
        assertThat(properties.getProperty("saas.verification.expose-debug-code"))
                .isEqualTo("${VERIFICATION_EXPOSE_DEBUG_CODE:false}");
        assertThat(properties.getProperty("saas.verification.email-login-enabled"))
                .isEqualTo("${VERIFICATION_EMAIL_LOGIN_ENABLED:false}");
        assertThat(properties).doesNotContainKeys(
                "AUTH_SERVICE_BASE_URL",
                "SYSTEM_SERVICE_BASE_URL",
                "FILE_SERVICE_BASE_URL",
                "MESSAGE_SERVICE_BASE_URL",
                "PLUGIN_SERVICE_BASE_URL",
                "LOCALIZATION_SERVICE_BASE_URL",
                "saas.payment.public-base-url",
                "saas.payment.auth-service-base-url",
                "lumira.ai.owner-integrations.system-token",
                "lumira.ai.totp-digits",
                "lumira.ai.login-challenge-expire-minutes");
    }

    @Test
    void runtimeProfilesGenerateApiDocsWithoutExposingSwaggerUi() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("${SPRINGDOC_API_DOCS_ENABLED:true}");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled"))
                .isEqualTo("${SPRINGDOC_SWAGGER_UI_ENABLED:false}");
    }

    @Test
    void reviewInvitationLinksArePublicWhileTheReviewWorkbenchRemainsProtected() {
        Properties properties = loadApplicationProperties();

        assertThat(properties.getProperty("lumira.review.invitation-url"))
                .isEqualTo("${LUMIRA_REVIEW_INVITATION_URL:http://localhost:8000/review/invitation}");
        assertThat(properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("saas.security.permit-paths"))
                .map(properties::getProperty)
                .toList())
                .contains("/api/v2/reviews/invitations/**");
    }

    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
