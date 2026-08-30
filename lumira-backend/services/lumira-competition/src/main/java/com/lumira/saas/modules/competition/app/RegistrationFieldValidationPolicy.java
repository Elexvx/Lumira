package com.lumira.saas.modules.competition.app;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared policy for competition field settings and registration submission.
 * The three protected persistence fields retain a safe minimum rule when an
 * older configuration still contains {@code NONE}.
 */
final class RegistrationFieldValidationPolicy {

    static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "IMAGE", "ROLE", "NUMBER", "DATE", "SELECT", "MULTI_SELECT", "CASCADER", "MOBILE", "EMAIL"
    );
    static final Set<String> VALIDATION_RULES = Set.of(
            "NONE", "PERSON_NAME", "DISPLAY_NAME", "CHINA_MOBILE", "EMAIL", "ID_CARD"
    );

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^(?:\\d{15}|\\d{17}[\\dXx])$");
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("^[\\p{IsHan}A-Za-z·]{2,64}$");
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile(
            "^(?=.{1,128}$)(?=.*[\\p{IsHan}A-Za-z0-9])[\\p{IsHan}A-Za-z0-9 ·•&＋+（）()《》【】\\[\\]—_:/：，,.、'’#\\-]+$"
    );
    private static final Pattern UNSAFE_DISPLAY_WHITESPACE = Pattern.compile("\\s{2,}|[\\t\\r\\n]");
    private static final Pattern SUSPICIOUS_DISPLAY_INITIALISM = Pattern.compile(
            "(?:^|[^A-Za-z])(?:[A-Za-z]['’]){2,}[A-Za-z](?:$|[^A-Za-z])"
    );

    private RegistrationFieldValidationPolicy() {
    }

    static String normalizeFieldType(String fieldType) {
        return normalize(fieldType, "TEXT");
    }

    static String normalizeValidationRule(String validationRule) {
        return normalize(validationRule, "NONE");
    }

    static String resolveValidationRule(String scope, String itemKey, String fieldType, String validationRule) {
        String normalizedType = normalizeFieldType(fieldType);
        if ("MOBILE".equals(normalizedType)) {
            return "CHINA_MOBILE";
        }
        if ("EMAIL".equals(normalizedType)) {
            return "EMAIL";
        }
        String configuredRule = normalizeValidationRule(validationRule);
        if (!"NONE".equals(configuredRule)) {
            return configuredRule;
        }
        String normalizedScope = normalize(scope, "");
        String normalizedKey = itemKey == null ? "" : itemKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if ("TEAM_FIELD".equals(normalizedScope) && Set.of("teamname", "name").contains(normalizedKey)) {
            return "DISPLAY_NAME";
        }
        if (Set.of("MEMBER_FIELD", "TEACHER_FIELD").contains(normalizedScope)
                && Set.of("membername", "name").contains(normalizedKey)) {
            return "PERSON_NAME";
        }
        if ("PROJECT_FIELD".equals(normalizedScope)
                && Set.of("projecttitle", "projectname", "title", "name").contains(normalizedKey)) {
            return "DISPLAY_NAME";
        }
        if ("EXPERT_FIELD".equals(normalizedScope) && Set.of("expertname", "fullname", "name").contains(normalizedKey)) {
            return "PERSON_NAME";
        }
        return configuredRule;
    }

    static boolean isValid(String validationRule, String value) {
        String rule = normalizeValidationRule(validationRule);
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return switch (rule) {
            case "NONE" -> true;
            case "CHINA_MOBILE" -> MOBILE_PATTERN.matcher(trimmed).matches();
            case "EMAIL" -> EMAIL_PATTERN.matcher(trimmed).matches();
            case "ID_CARD" -> ID_CARD_PATTERN.matcher(trimmed).matches();
            case "PERSON_NAME" -> value.equals(trimmed) && PERSON_NAME_PATTERN.matcher(value).matches();
            case "DISPLAY_NAME" -> value.equals(trimmed)
                    && !UNSAFE_DISPLAY_WHITESPACE.matcher(value).find()
                    && !SUSPICIOUS_DISPLAY_INITIALISM.matcher(value).find()
                    && DISPLAY_NAME_PATTERN.matcher(value).matches();
            default -> false;
        };
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
