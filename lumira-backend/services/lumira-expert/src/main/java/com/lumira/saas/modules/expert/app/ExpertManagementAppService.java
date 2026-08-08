package com.lumira.saas.modules.expert.app;

import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.api.workflow.WorkflowStartPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class ExpertManagementAppService {
    private static final String EXPERT_VIEW = "expert:view";
    private static final String EXPERT_UPDATE = "expert:update";
    private static final String EXPERT_DELETE = "expert:delete";
    private static final String STATUS_DICT_CODE = "aiadc_expert_status";
    private static final String INITIAL_STATUS_DICT_CODE = "aiadc_expert_initial_status";
    private static final String APPROVAL_STATUS_DICT_CODE = "aiadc_expert_approval_status";
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter EXPERT_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int[] CHINA_ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHINA_ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_SHORT_TEXT_LENGTH = 128;
    private static final int MAX_EXPERTISE_LENGTH = 255;
    private static final int MAX_CONTACT_LENGTH = 64;
    private static final int MAX_MOBILE_LENGTH = 32;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_LONG_TEXT_LENGTH = 1000;
    private static final java.util.regex.Pattern EXPERT_NAME_PATTERN = java.util.regex.Pattern.compile("^[\\p{IsHan}A-Za-z鐠虹棆\s]{2,64}$");
    private static final java.util.regex.Pattern PHONE_PATTERN = java.util.regex.Pattern.compile("^(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}(?:-\\d{1,6})?)$");
    private static final java.util.regex.Pattern MOBILE_PATTERN = java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ExpertRepository expertRepository;
    private final WorkflowStartPort workflowStartPort;
    private final DictionaryValueNormalizer dictionaryValueNormalizer;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public ExpertManagementAppService(
            ExpertRepository expertRepository,
            WorkflowStartPort workflowStartPort,
            DictionaryValueNormalizer dictionaryValueNormalizer,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(expertRepository, workflowStartPort, dictionaryValueNormalizer, trustedCurrentUserResolver, true);
    }

    public ExpertManagementAppService(
            ExpertRepository expertRepository,
            WorkflowStartPort workflowStartPort,
            DictionaryValueNormalizer dictionaryValueNormalizer
    ) {
        this(expertRepository, workflowStartPort, dictionaryValueNormalizer, null, false);
    }

    private ExpertManagementAppService(
            ExpertRepository expertRepository,
            WorkflowStartPort workflowStartPort,
            DictionaryValueNormalizer dictionaryValueNormalizer,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.expertRepository = expertRepository;
        this.workflowStartPort = workflowStartPort;
        this.dictionaryValueNormalizer = dictionaryValueNormalizer;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public PageResponse<ExpertVO.Expert> listExperts(CurrentUser currentUser, String keyword, String status, String approvalStatus, long pageNo, long pageSize) {
        requirePermission(currentUser, EXPERT_VIEW);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        String normalizedStatus = StringUtils.hasText(status) ? normalizeStatus(status) : null;
        String normalizedApprovalStatus = StringUtils.hasText(approvalStatus) ? normalizeApprovalStatus(approvalStatus) : null;
        ExpertRepository.PageData page = expertRepository.search(keyword, normalizedStatus, normalizedApprovalStatus,
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);

        PageResponse<ExpertVO.Expert> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    public ExpertVO.Expert getExpert(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, EXPERT_VIEW);
        requirePositiveId(id, "Expert id is required");
        return getExpertInternal(id);
    }

    private ExpertVO.Expert getExpertInternal(Long id) {
        requirePositiveId(id, "Expert id is required");
        ExpertVO.Expert expert = findExpert(id);
        if (expert == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return expert;
    }

    @Transactional
    public ExpertVO.Expert createExpert(CurrentUser currentUser, ExpertDTO.ExpertUpsertRequest request) {
        // This operation is the self-service expert application entry point.
        // Approval, update, and deletion remain privileged operations, but an
        // authenticated applicant must not already need expert:create.
        Long userId = requireUserId(currentUser);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request);
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, generateExpertCode());
        String initialStatus = requiredDictValues(INITIAL_STATUS_DICT_CODE).getFirst();
        String initialApprovalStatus = requiredDictValues(APPROVAL_STATUS_DICT_CODE).getFirst();
        Long id = expertRepository.create(normalized, initialStatus, initialApprovalStatus, userId, userUuid);
        if (id == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert application changed, please retry");
        }
        Long workflowInstanceId = workflowStartPort.startWorkflow(
                currentUser,
                WorkflowStartPort.BUSINESS_EXPERT_APPLICATION,
                id,
                normalized.getCode(),
                normalized.getName(),
                Map.of(
                        "name", normalized.getName(),
                        "email", normalized.getEmail() == null ? "" : normalized.getEmail(),
                        "expertise", normalized.getExpertise()
                )
        );
        int updated = expertRepository.attachWorkflow(id, normalized.getCode(), initialStatus, initialApprovalStatus,
                workflowInstanceId, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert application changed, please retry");
        }
        return getExpertInternal(id);
    }

    @Transactional
    public ExpertVO.Expert updateExpert(CurrentUser currentUser, Long id, ExpertDTO.ExpertUpsertRequest request) {
        Long userId = requirePermission(currentUser, EXPERT_UPDATE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Expert id is required");
        requireRequest(request);
        ExpertVO.Expert existing = findExpert(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, existing.getCode());
        int updated = expertRepository.update(id, existing, normalized, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return getExpertInternal(id);
    }

    @Transactional
    public boolean deleteExpert(CurrentUser currentUser, Long id) {
        Long userId = requirePermission(currentUser, EXPERT_DELETE);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(id, "Expert id is required");
        ExpertVO.Expert existing = findExpert(id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        int updated = expertRepository.delete(id, existing, userId, userUuid);
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return true;
    }

    private ExpertVO.Expert findExpert(Long id) {
        requirePositiveId(id, "Expert id is required");
        return expertRepository.findById(id).orElse(null);
    }

    private ExpertDTO.ExpertUpsertRequest normalizeRequest(ExpertDTO.ExpertUpsertRequest request, String fallbackCode) {
        ExpertDTO.ExpertUpsertRequest normalized = new ExpertDTO.ExpertUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? trimRequired(request.getCode(), "Expert code is required", MAX_CODE_LENGTH, "Expert code is too long")
                : trimRequired(fallbackCode, "Expert code is required", MAX_CODE_LENGTH, "Expert code is too long"));
        normalized.setName(normalizeName(request.getName()));
        normalized.setTitle(trimOptional(request.getTitle(), MAX_SHORT_TEXT_LENGTH, "Expert title is too long"));
        normalized.setOrganization(trimOptional(request.getOrganization(), MAX_SHORT_TEXT_LENGTH, "Expert organization is too long"));
        normalized.setPosition(trimOptional(request.getPosition(), MAX_SHORT_TEXT_LENGTH, "Expert position is too long"));
        normalized.setExpertise(trimRequired(request.getExpertise(), "Expertise is required", MAX_EXPERTISE_LENGTH, "Expertise is too long"));
        normalized.setPhone(normalizePhone(request.getPhone()));
        normalized.setMobile(normalizeMobile(request.getMobile()));
        normalized.setIdCardNumber(normalizeIdCardNumber(request.getIdCardNumber()));
        normalized.setEmail(normalizeEmail(request.getEmail()));
        normalized.setAvatarUrl(normalizeUrl(request.getAvatarUrl(), "Expert avatar URL"));
        normalized.setBio(trimOptional(request.getBio(), MAX_LONG_TEXT_LENGTH, "Expert bio is too long"));
        normalized.setTags(trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Expert tags are too long"));
        List<String> statuses = requiredDictValues(STATUS_DICT_CODE);
        normalized.setTitle(validateOptionalDictValue("aiadc_expert_title", normalized.getTitle(), "Expert title"));
        normalized.setPosition(validateOptionalDictValue("aiadc_expert_position", normalized.getPosition(), "Expert position"));
        normalized.setExpertise(validateDictValues("aiadc_expert_expertise", normalized.getExpertise(), "Expert expertise"));
        normalized.setTags(validateDictValues("aiadc_expert_tag", normalized.getTags(), "Expert tags"));
        normalized.setStatus(StringUtils.hasText(request.getStatus())
                ? normalizeStatus(request.getStatus()) : statuses.getFirst());
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String generateExpertCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L), 36);
        return "exp-" + LocalDateTime.now().format(EXPERT_CODE_TIME_FORMATTER) + "-" + random;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!requiredDictValues(STATUS_DICT_CODE).contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert status");
        }
        return normalized;
    }

    private String normalizeApprovalStatus(String approvalStatus) {
        String normalized = approvalStatus.trim().toUpperCase(Locale.ROOT);
        if (!requiredDictValues(APPROVAL_STATUS_DICT_CODE).contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert approval status");
        }
        return normalized;
    }

    private Long requireUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String requireUserUuid(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getUserUuid().trim();
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        Long userId = requireUserId(currentUser);
        if (!hasPermission(currentUser, permissionKey)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return userId;
    }

    private void requireRequest(ExpertDTO.ExpertUpsertRequest request) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert request is required");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return false;
        }
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        ExpertAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private String trimRequired(String value, String requiredMessage, int maxLength, String tooLongMessage) {
        String trimmed = trimRequired(value, requiredMessage);
        if (trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, tooLongMessage);
        }
        return trimmed;
    }

    private String trimOptional(String value, int maxLength, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, tooLongMessage);
        }
        return trimmed;
    }

    private String normalizeName(String value) {
        String normalized = trimRequired(value, "Expert name is required", MAX_NAME_LENGTH, "Expert name is too long");
        if (!EXPERT_NAME_PATTERN.matcher(normalized).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert name");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        String normalized = trimOptional(value, MAX_CONTACT_LENGTH, "Expert phone is too long");
        if (normalized != null && !PHONE_PATTERN.matcher(normalized).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert phone");
        }
        return normalized;
    }

    private String normalizeMobile(String value) {
        String normalized = trimOptional(value, MAX_MOBILE_LENGTH, "Expert mobile is too long");
        if (normalized != null && !MOBILE_PATTERN.matcher(normalized).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert mobile");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = trimOptional(value, MAX_EMAIL_LENGTH, "Expert email is too long");
        if (normalized != null && !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert email");
        }
        return normalized;
    }

    private String normalizeUrl(String value, String fieldName) {
        String trimmed = trimOptional(value, MAX_URL_LENGTH, fieldName + " is too long");
        if (trimmed == null) {
            return null;
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("\\")) {
            return trimmed;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (URISyntaxException exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
        }
        throw biz(ErrorCode.VALIDATION_ERROR, fieldName + " is invalid");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String validateOptionalDictValue(String dictCode, String value, String label) {
        if (value == null) {
            return value;
        }
        if (!requiredDictValues(dictCode).contains(value)) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + " contains a dictionary value that does not exist or is disabled");
        }
        return value;
    }

    private String validateDictValues(String dictCode, String value, String label) {
        if (value == null) {
            return value;
        }
        List<String> values = List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        Set<String> allowed = Set.copyOf(requiredDictValues(dictCode));
        for (String itemValue : values) {
            if (!allowed.contains(itemValue)) {
                throw biz(ErrorCode.VALIDATION_ERROR, label + " contains a dictionary value that does not exist or is disabled");
            }
        }
        return String.join(",", values);
    }

    private List<String> requiredDictValues(String dictCode) {
        List<String> values = dictionaryValueNormalizer == null ? List.of() : dictionaryValueNormalizer.enabledValues(dictCode);
        if (values == null || values.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert dictionary is not configured: " + dictCode);
        }
        return values;
    }

    private String normalizeIdCardNumber(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.length() == 18 && !hasValidIdCardChecksum(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid ID card checksum");
        }
        return normalized;
    }

    private boolean hasValidIdCardChecksum(String value) {
        int sum = 0;
        for (int index = 0; index < CHINA_ID_CARD_WEIGHTS.length; index += 1) {
            sum += Character.digit(value.charAt(index), 10) * CHINA_ID_CARD_WEIGHTS[index];
        }
        return CHINA_ID_CARD_CHECK_CODES[sum % 11] == value.charAt(17);
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
