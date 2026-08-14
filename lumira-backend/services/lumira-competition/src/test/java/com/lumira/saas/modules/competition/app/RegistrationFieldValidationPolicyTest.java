package com.lumira.saas.modules.competition.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFieldValidationPolicyTest {

    @Test
    void legacyNoneRulesKeepSafeMinimumsForProtectedFields() {
        assertThat(RegistrationFieldValidationPolicy.resolveValidationRule(
                "TEAM_FIELD", "teamName", "TEXT", "NONE"
        )).isEqualTo("DISPLAY_NAME");
        assertThat(RegistrationFieldValidationPolicy.resolveValidationRule(
                "MEMBER_FIELD", "memberName", "TEXT", "NONE"
        )).isEqualTo("PERSON_NAME");
        assertThat(RegistrationFieldValidationPolicy.resolveValidationRule(
                "PROJECT_FIELD", "projectName", "TEXT", null
        )).isEqualTo("DISPLAY_NAME");
        assertThat(RegistrationFieldValidationPolicy.resolveValidationRule(
                "TEAM_FIELD", "campus", "TEXT", "NONE"
        )).isEqualTo("NONE");
    }

    @Test
    void displayNamesAllowNormalBilingualValuesAndRejectUnexpectedSymbols() {
        assertThat(RegistrationFieldValidationPolicy.isValid("DISPLAY_NAME", "AIADC 创新团队 2026")).isTrue();
        assertThat(RegistrationFieldValidationPolicy.isValid("DISPLAY_NAME", "Lumira-AI（杭州）")).isTrue();
        assertThat(RegistrationFieldValidationPolicy.isValid("DISPLAY_NAME", "大噶地方刮大风官方阿哥十多个a'f'd'g")).isFalse();
        assertThat(RegistrationFieldValidationPolicy.isValid("DISPLAY_NAME", " AIADC")).isFalse();
    }

    @Test
    void configuredContactAndIdentityRulesAreEnforced() {
        assertThat(RegistrationFieldValidationPolicy.isValid("CHINA_MOBILE", "13800138000")).isTrue();
        assertThat(RegistrationFieldValidationPolicy.isValid("EMAIL", "student@example.com")).isTrue();
        assertThat(RegistrationFieldValidationPolicy.isValid("EMAIL", "not-an-email")).isFalse();
        assertThat(RegistrationFieldValidationPolicy.isValid("ID_CARD", "110101200001011234")).isTrue();
        assertThat(RegistrationFieldValidationPolicy.isValid("ID_CARD", "123")).isFalse();
    }
}
