package com.lumira.saas.modules.expert.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private static final Set<String> STATUSES = Set.of("active", "inactive");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter EXPERT_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int[] CHINA_ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHINA_ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private final MyBatisQueryOperations jdbcTemplate;
    private final WorkflowAppService workflowAppService;

    public ExpertManagementAppService(MyBatisQueryOperations jdbcTemplate, WorkflowAppService workflowAppService) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowAppService = workflowAppService;
    }

    public PageResponse<ExpertVO.Expert> listExperts(CurrentUser currentUser, String keyword, String status, String approvalStatus, long pageNo, long pageSize) {
        requireAuthenticated(currentUser);
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
        requireAuthenticated(currentUser);
        ExpertVO.Expert expert = findExpert(id);
        if (expert == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return expert;
    }

    @Transactional
    public ExpertVO.Expert createExpert(CurrentUser currentUser, ExpertDTO.ExpertUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, generateExpertCode());
        jdbcTemplate.update(
                """
                        insert into aiadc_expert (
                            code, name, title, organization, position, expertise,
                            phone, mobile, id_card_number, email, avatar_url, bio, tags, status, approval_status, sort, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 0)
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
                userId
        );
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
        jdbcTemplate.update(
                "update aiadc_expert set approval_instance_id = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                workflowInstanceId,
                userId,
                LocalDateTime.now(),
                id
        );
        ExpertVO.Expert expert = getExpert(currentUser, id);
        return expert;
    }

    @Transactional
    public ExpertVO.Expert updateExpert(CurrentUser currentUser, Long id, ExpertDTO.ExpertUpsertRequest request) {
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
                            updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
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
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return getExpert(currentUser, id);
    }

    @Transactional
    public boolean deleteExpert(CurrentUser currentUser, Long id) {
        int updated = jdbcTemplate.update(
                "update aiadc_expert set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return true;
    }

    private ExpertVO.Expert findExpert(Long id) {
        List<ExpertVO.Expert> records = jdbcTemplate.query(
                expertSelect() + " from aiadc_expert where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private ExpertDTO.ExpertUpsertRequest normalizeRequest(ExpertDTO.ExpertUpsertRequest request, String fallbackCode) {
        ExpertDTO.ExpertUpsertRequest normalized = new ExpertDTO.ExpertUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode()) ? request.getCode().trim() : trimRequired(fallbackCode, "Expert code is required"));
        normalized.setName(trimRequired(request.getName(), "Expert name is required"));
        normalized.setTitle(validateOptionalDictValue("aiadc_expert_title", trimToNull(request.getTitle()), "专家头衔"));
        normalized.setOrganization(trimToNull(request.getOrganization()));
        normalized.setPosition(validateOptionalDictValue("aiadc_expert_position", trimToNull(request.getPosition()), "职务"));
        normalized.setExpertise(validateDictValues("aiadc_expert_expertise", trimRequired(request.getExpertise(), "Expertise is required"), "专业领域"));
        normalized.setPhone(trimToNull(request.getPhone()));
        normalized.setMobile(trimToNull(request.getMobile()));
        normalized.setIdCardNumber(normalizeIdCardNumber(request.getIdCardNumber()));
        normalized.setEmail(trimToNull(request.getEmail()));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setBio(trimToNull(request.getBio()));
        normalized.setTags(validateDictValues("aiadc_expert_tag", trimToNull(request.getTags()), "标签"));
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

    private void requireAuthenticated(CurrentUser currentUser) {
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null || currentUser.getUserId() <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
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
                       phone, mobile, id_card_number as idCardNumber, user_id as userId, account_status as accountStatus,
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
