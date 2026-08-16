package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.model.ActivityRegistrationAnswer;
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivityRegistrationAppService {
    private static final String DATA_SCOPE_RESOURCE = "activity:registration";
    private static final int MAX_SINGLE_LINE_LENGTH = 1000;
    private static final int MAX_TEXTAREA_LENGTH = 5000;
    private static final Pattern CHINA_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final ActivityRegistrationRepository repository;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    @org.springframework.beans.factory.annotation.Autowired
    public ActivityRegistrationAppService(
            ActivityRegistrationRepository repository,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(repository, trustedCurrentUserResolver, true);
    }

    public ActivityRegistrationAppService(
            ActivityRegistrationRepository repository,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.repository = repository;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public ActivityRegistrationAppService(ActivityRegistrationRepository repository) {
        this(repository, null, false);
    }

    public List<ActivityRegistrationVO> list(CurrentUser user) {
        CurrentUser trustedUser = requireUser(user);
        DataPermissionDecision decision = resolveDataPermission(trustedUser);
        boolean viewAll = decision.scopeType() == DataScopeType.ALL;
        return repository.listVisible(trustedUser.getUserId(), trustedUser.getUserUuid().trim(), viewAll);
    }

    @Transactional
    public ActivityRegistrationVO create(CurrentUser user, ActivityRegistrationDTO.CreateRequest request) {
        CurrentUser trustedUser = requireUser(user);
        if (request == null || request.getActivityId() == null || request.getActivityId() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Activity id is required");
        }
        ActivityRegistrationRepository.RegistrationForm registrationForm = repository
                .findPublishedRegistrationForm(request.getActivityId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Activity not found"));
        Map<String, Object> submittedValues = collectSubmittedValues(request);
        List<ActivityRegistrationAnswer> answers = normalizeAnswers(registrationForm.fields(), submittedValues);
        ActivityRegistrationRepository.RegistrationSubmission submission = new ActivityRegistrationRepository.RegistrationSubmission(
                request.getActivityId(),
                compatibilityValue(answers, "name", null, request.getName(), trustedUser.getUsername(), 128),
                compatibilityValue(answers, "mobile", "MOBILE", request.getMobile(), null, 32),
                compatibilityValue(answers, "email", "EMAIL", request.getEmail(), null, 255),
                compatibilityValue(answers, "organization", null, request.getOrganization(), null, 255),
                compatibilityValue(answers, "position", null, request.getPosition(), null, 128),
                compatibilityValue(answers, "remark", null, request.getRemark(), null, 1000),
                answers
        );
        return repository.create(
                trustedUser.getUserId(),
                trustedUser.getUserUuid(),
                trustedUser.getUsername(),
                submission
        );
    }

    private Map<String, Object> collectSubmittedValues(ActivityRegistrationDTO.CreateRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (request.getAnswers() != null) {
            values.putAll(request.getAnswers());
        }
        putLegacyValueIfMissing(values, "name", request.getName());
        putLegacyValueIfMissing(values, "mobile", request.getMobile());
        putLegacyValueIfMissing(values, "email", request.getEmail());
        putLegacyValueIfMissing(values, "organization", request.getOrganization());
        putLegacyValueIfMissing(values, "position", request.getPosition());
        putLegacyValueIfMissing(values, "remark", request.getRemark());
        return values;
    }

    private void putLegacyValueIfMissing(Map<String, Object> values, String fieldKey, String value) {
        if (!values.containsKey(fieldKey) && StringUtils.hasText(value)) {
            values.put(fieldKey, value);
        }
    }

    private List<ActivityRegistrationAnswer> normalizeAnswers(
            List<ActivityRegistrationField> configuredFields,
            Map<String, Object> submittedValues
    ) {
        List<ActivityRegistrationField> fields = configuredFields == null ? List.of() : configuredFields;
        Set<String> configuredKeys = fields.stream().map(ActivityRegistrationField::getFieldKey).collect(java.util.stream.Collectors.toSet());
        for (String submittedKey : submittedValues.keySet()) {
            if (!configuredKeys.contains(submittedKey)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Unknown activity registration field: " + submittedKey);
            }
        }

        List<ActivityRegistrationAnswer> answers = new ArrayList<>(fields.size());
        for (ActivityRegistrationField field : fields) {
            Object value = normalizeAnswerValue(field, submittedValues.get(field.getFieldKey()));
            if (Boolean.TRUE.equals(field.getRequired()) && isEmptyValue(value)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Activity registration field is required: " + field.getLabel());
            }
            answers.add(new ActivityRegistrationAnswer(field.getFieldKey(), field.getLabel(), field.getFieldType(), value));
        }
        return List.copyOf(answers);
    }

    private Object normalizeAnswerValue(ActivityRegistrationField field, Object rawValue) {
        String fieldType = field.getFieldType() == null ? "TEXT" : field.getFieldType().trim().toUpperCase(java.util.Locale.ROOT);
        if ("MULTI_SELECT".equals(fieldType)) {
            return normalizeMultipleChoice(field, rawValue);
        }
        String value = normalizeScalar(rawValue, "TEXTAREA".equals(fieldType) ? MAX_TEXTAREA_LENGTH : MAX_SINGLE_LINE_LENGTH, field.getLabel());
        if (value == null) {
            return null;
        }
        return switch (fieldType) {
            case "MOBILE" -> {
                if (!CHINA_MOBILE.matcher(value).matches()) {
                    throw invalidField(field, "must be a valid China mobile number");
                }
                yield value;
            }
            case "EMAIL" -> {
                if (!EMAIL.matcher(value).matches()) {
                    throw invalidField(field, "must be a valid email address");
                }
                yield value;
            }
            case "NUMBER" -> normalizeNumber(field, value);
            case "DATE" -> normalizeDate(field, value);
            case "SELECT" -> normalizeSingleChoice(field, value);
            case "TEXT", "TEXTAREA" -> value;
            default -> throw invalidField(field, "has an unsupported field type");
        };
    }

    private String normalizeScalar(Object rawValue, int maxLength, String label) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Collection<?> || rawValue instanceof Map<?, ?> || rawValue.getClass().isArray()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Activity registration field must be a single value: " + label);
        }
        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Activity registration field is too long: " + label);
        }
        return value;
    }

    private String normalizeNumber(ActivityRegistrationField field, String value) {
        try {
            BigDecimal decimal = new BigDecimal(value).stripTrailingZeros();
            if (decimal.precision() > MAX_SINGLE_LINE_LENGTH
                    || Math.abs((long) decimal.scale()) > MAX_SINGLE_LINE_LENGTH) {
                throw invalidField(field, "is too large");
            }
            String normalized = decimal.toPlainString();
            if (normalized.length() > MAX_SINGLE_LINE_LENGTH) {
                throw invalidField(field, "is too large");
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw invalidField(field, "must be a number");
        }
    }

    private String normalizeDate(ActivityRegistrationField field, String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw invalidField(field, "must be a valid date");
        }
    }

    private String normalizeSingleChoice(ActivityRegistrationField field, String value) {
        if (field.getOptions() == null || !field.getOptions().contains(value)) {
            throw invalidField(field, "contains an invalid option");
        }
        return value;
    }

    private List<String> normalizeMultipleChoice(ActivityRegistrationField field, Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (!(rawValue instanceof Collection<?> collection)) {
            throw invalidField(field, "must be a list of options");
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Object item : collection) {
            String value = normalizeScalar(item, MAX_SINGLE_LINE_LENGTH, field.getLabel());
            if (value != null) {
                selected.add(value);
            }
        }
        List<String> options = field.getOptions() == null ? List.of() : field.getOptions();
        if (!options.containsAll(selected)) {
            throw invalidField(field, "contains an invalid option");
        }
        return List.copyOf(selected);
    }

    private boolean isEmptyValue(Object value) {
        return value == null || value instanceof String text && !StringUtils.hasText(text)
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private BizException invalidField(ActivityRegistrationField field, String reason) {
        return new BizException(ErrorCode.VALIDATION_ERROR, "Activity registration field " + field.getLabel() + " " + reason);
    }

    private String compatibilityValue(
            List<ActivityRegistrationAnswer> answers,
            String fieldKey,
            String fallbackFieldType,
            String legacyValue,
            String finalFallback,
            int maxLength
    ) {
        Object answerValue = answers.stream()
                .filter(answer -> fieldKey.equals(answer.getFieldKey()))
                .map(ActivityRegistrationAnswer::getValue)
                .filter(value -> !isEmptyValue(value))
                .findFirst()
                .orElse(null);
        if (answerValue == null && fallbackFieldType != null) {
            answerValue = answers.stream()
                    .filter(answer -> fallbackFieldType.equalsIgnoreCase(answer.getFieldType()))
                    .map(ActivityRegistrationAnswer::getValue)
                    .filter(value -> !isEmptyValue(value))
                    .findFirst()
                    .orElse(null);
        }
        String value = answerValue == null ? legacyValue : answerValueToString(answerValue);
        if (!StringUtils.hasText(value)) {
            value = finalFallback;
        }
        if (!StringUtils.hasText(value)) {
            return "name".equals(fieldKey) ? "" : null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String answerValueToString(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private CurrentUser requireUser(CurrentUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0 || !StringUtils.hasText(user.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return user;
        }
        CurrentUser trustedUser = trustedCurrentUserResolver.resolve(user);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(trustedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return trustedUser;
    }

    private DataPermissionDecision resolveDataPermission(CurrentUser user) {
        return DataPermissionResolver.resolve(
                DATA_SCOPE_RESOURCE,
                user.getUserId(),
                user.getDeptIds() == null ? Set.of() : user.getDeptIds(),
                user.getDescendantDeptIds() == null ? Set.of() : user.getDescendantDeptIds(),
                user.getDataScopes() == null ? List.<DataPermissionRule>of() : user.getDataScopes(),
                user.getPermissions() == null ? Set.of() : user.getPermissions()
        );
    }
}
