package com.lumira.saas.modules.expert.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class ExpertManagementAppService {
    private static final String EXPERT_VIEW = "expert:view";
    private static final String EXPERT_CREATE = "expert:create";
    private static final String EXPERT_UPDATE = "expert:update";
    private static final String EXPERT_DELETE = "expert:delete";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final Set<String> STATUSES = Set.of("active", "inactive");
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
    private static final java.util.regex.Pattern EXPERT_NAME_PATTERN = java.util.regex.Pattern.compile("^[\\p{IsHan}A-Za-z·\\s]{2,64}$");
    private static final java.util.regex.Pattern PHONE_PATTERN = java.util.regex.Pattern.compile("^(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}(?:-\\d{1,6})?)$");
    private static final java.util.regex.Pattern MOBILE_PATTERN = java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MyBatisQueryOperations jdbcTemplate;
    private final WorkflowAppService workflowAppService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Autowired
    public ExpertManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            WorkflowAppService workflowAppService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, workflowAppService, permissionSnapshotService, null, sessionAuthenticationService);
    }

    public ExpertManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            WorkflowAppService workflowAppService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowAppService = workflowAppService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public ExpertManagementAppService(
            MyBatisQueryOperations jdbcTemplate,
            WorkflowAppService workflowAppService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, workflowAppService, permissionSnapshotService, null);
    }

    public ExpertManagementAppService(MyBatisQueryOperations jdbcTemplate, WorkflowAppService workflowAppService) {
        this(jdbcTemplate, workflowAppService, null);
    }

    public PageResponse<ExpertVO.Expert> listExperts(CurrentUser currentUser, String keyword, String status, String approvalStatus, long pageNo, long pageSize) {
        requirePermission(currentUser, EXPERT_VIEW);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_expert where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (name like ? or code like ? or title like ? or organization like ? or expertise like ? or tags like ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            params.add(normalizeStatus(status));
        }
        if (StringUtils.hasText(approvalStatus)) {
            where.append(" and approval_status = ?");
            params.add(normalizeApprovalStatus(approvalStatus));
        }

        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((normalizedPageNo - 1) * normalizedPageSize);
        selectParams.add(normalizedPageSize);
        List<ExpertVO.Expert> records = jdbcTemplate.query(
                expertSelect() + where + " order by sort asc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                selectParams.toArray()
        );

        PageResponse<ExpertVO.Expert> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
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
        Long userId = requirePermission(currentUser, EXPERT_CREATE);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request);
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, generateExpertCode());
        int inserted = jdbcTemplate.update(
                """
                        insert into aiadc_expert (
                            code, name, title, organization, position, expertise,
                            phone, mobile, id_card_number, email, avatar_url, bio, tags, status, approval_status, sort,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, 0)
                        """,
                normalized.getCode(),
                normalized.getName(),
                normalized.getTitle(),
                normalized.getOrganization(),
                normalized.getPosition(),
                normalized.getExpertise(),
                normalized.getPhone(),
                normalized.getMobile(),
                normalized.getIdCardNumber(),
                normalized.getEmail(),
                normalized.getAvatarUrl(),
                normalized.getBio(),
                normalized.getTags(),
                "inactive",
                normalized.getSort(),
                userId,
                userUuid,
                userId,
                userUuid
        );
        if (inserted != 1) {
            throw biz(ErrorCode.BIZ_ERROR, "Expert application changed, please retry");
        }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        Long workflowInstanceId = workflowAppService.startWorkflow(
                currentUser,
                WorkflowAppService.BUSINESS_EXPERT_APPLICATION,
                id,
                normalized.getCode(),
                normalized.getName(),
                Map.of(
                        "name", normalized.getName(),
                        "email", normalized.getEmail() == null ? "" : normalized.getEmail(),
                        "expertise", normalized.getExpertise()
                )
        );
        int updated = jdbcTemplate.update(
                """
                        update aiadc_expert
                        set approval_instance_id = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and code = ?
                          and status = ?
                          and approval_status = 'PENDING'
                          and deleted = 0
                        """,
                workflowInstanceId,
                userId,
                userUuid,
                LocalDateTime.now(),
                id,
                normalized.getCode(),
                "inactive"
        );
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
        int updated = jdbcTemplate.update(
                """
                        update aiadc_expert
                        set code = ?, name = ?, title = ?, organization = ?, position = ?, expertise = ?,
                            phone = ?, mobile = ?, id_card_number = ?, email = ?, avatar_url = ?, bio = ?, tags = ?, status = ?, sort = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getName(),
                normalized.getTitle(),
                normalized.getOrganization(),
                normalized.getPosition(),
                normalized.getExpertise(),
                normalized.getPhone(),
                normalized.getMobile(),
                normalized.getIdCardNumber(),
                normalized.getEmail(),
                normalized.getAvatarUrl(),
                normalized.getBio(),
                normalized.getTags(),
                normalized.getStatus(),
                normalized.getSort(),
                userId,
                userUuid,
                LocalDateTime.now(),
                id,
                existing.getCode(),
                existing.getStatus(),
                existing.getApprovalStatus()
        );
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
        int updated = jdbcTemplate.update(
                """
                        update aiadc_expert
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                        """,
                userId,
                userUuid,
                LocalDateTime.now(),
                id,
                existing.getCode(),
                existing.getStatus(),
                existing.getApprovalStatus()
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return true;
    }

    private ExpertVO.Expert findExpert(Long id) {
        requirePositiveId(id, "Expert id is required");
        List<ExpertVO.Expert> records = jdbcTemplate.query(
                expertSelect() + " from aiadc_expert where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private ExpertDTO.ExpertUpsertRequest normalizeRequest(ExpertDTO.ExpertUpsertRequest request, String fallbackCode) {
        ExpertDTO.ExpertUpsertRequest normalized = new ExpertDTO.ExpertUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode())
                ? trimRequired(request.getCode(), "Expert code is required", MAX_CODE_LENGTH, "Expert code is too long")
                : trimRequired(fallbackCode, "Expert code is required", MAX_CODE_LENGTH, "Expert code is too long"));
        normalized.setName(normalizeName(request.getName()));
        normalized.setTitle(validateOptionalDictValue("aiadc_expert_title", trimOptional(request.getTitle(), MAX_SHORT_TEXT_LENGTH, "Expert title is too long"), "专家头衔"));
        normalized.setOrganization(trimOptional(request.getOrganization(), MAX_SHORT_TEXT_LENGTH, "Expert organization is too long"));
        normalized.setPosition(validateOptionalDictValue("aiadc_expert_position", trimOptional(request.getPosition(), MAX_SHORT_TEXT_LENGTH, "Expert position is too long"), "职务"));
        normalized.setExpertise(validateDictValues("aiadc_expert_expertise", trimRequired(request.getExpertise(), "Expertise is required", MAX_EXPERTISE_LENGTH, "Expertise is too long"), "专业领域"));
        normalized.setPhone(normalizePhone(request.getPhone()));
        normalized.setMobile(normalizeMobile(request.getMobile()));
        normalized.setIdCardNumber(normalizeIdCardNumber(request.getIdCardNumber()));
        normalized.setEmail(normalizeEmail(request.getEmail()));
        normalized.setAvatarUrl(normalizeUrl(request.getAvatarUrl(), "Expert avatar URL"));
        normalized.setBio(trimOptional(request.getBio(), MAX_LONG_TEXT_LENGTH, "Expert bio is too long"));
        normalized.setTags(validateDictValues("aiadc_expert_tag", trimOptional(request.getTags(), MAX_LONG_TEXT_LENGTH, "Expert tags are too long"), "标签"));
        normalized.setStatus(StringUtils.hasText(request.getStatus()) ? normalizeStatus(request.getStatus()) : "active");
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        return normalized;
    }

    private String generateExpertCode() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L), 36);
        return "exp-" + LocalDateTime.now().format(EXPERT_CODE_TIME_FORMATTER) + "-" + random;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid expert status");
        }
        return normalized;
    }

    private String normalizeApprovalStatus(String approvalStatus) {
        String normalized = approvalStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "RUNNING", "APPROVED", "REJECTED").contains(normalized)) {
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
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshed = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    ),
                    "Login required"
            );
            copyTrustedCurrentUser(currentUser, refreshed);
            return;
        }
        if (permissionSnapshotService == null) {
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess,
            String message
    ) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return authenticatedAccess.currentUser();
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
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
        if (value == null || !dictTypeExists(dictCode)) {
            return value;
        }
        if (!dictItemExists(dictCode, value)) {
            throw biz(ErrorCode.VALIDATION_ERROR, label + "必须来自字典管理");
        }
        return value;
    }

    private String validateDictValues(String dictCode, String value, String label) {
        if (value == null || !dictTypeExists(dictCode)) {
            return value;
        }
        List<String> values = List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        for (String itemValue : values) {
            if (!dictItemExists(dictCode, itemValue)) {
                throw biz(ErrorCode.VALIDATION_ERROR, label + "必须来自字典管理");
            }
        }
        return String.join(",", values);
    }

    private boolean dictTypeExists(String dictCode) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from sys_dict_type where dict_code = ? and status = 'ENABLED' and deleted = 0",
                Long.class,
                dictCode
        );
        return count != null && count > 0;
    }

    private boolean dictItemExists(String dictCode, String itemValue) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_dict_type dt
                        join sys_dict_item di on di.dict_type_id = dt.id
                        where dt.dict_code = ? and dt.status = 'ENABLED' and dt.deleted = 0
                          and di.item_value = ? and di.status = 'ENABLED' and di.deleted = 0
                """,
                Long.class,
                dictCode,
                itemValue
        );
        return count != null && count > 0;
    }

    private String normalizeIdCardNumber(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.length() == 18 && !hasValidIdCardChecksum(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "请输入有效身份证号码");
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

    private String expertSelect() {
        return """
                select id, code, name, title, organization, position, expertise,
                       phone, mobile, id_card_number as idCardNumber, user_id as userId, user_uuid as userUuid, account_status as accountStatus,
                       initial_password_reset_required as initialPasswordResetRequired,
                       email, avatar_url as avatarUrl, bio, tags, status, approval_status as approvalStatus,
                       approval_instance_id as approvalInstanceId, sort,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
