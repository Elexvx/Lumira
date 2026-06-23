package com.lumira.saas.modules.expert.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PlatformContext;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ExpertManagementAppService {
    private static final Set<String> STATUSES = Set.of("active", "inactive");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final DateTimeFormatter EXPERT_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MyBatisQueryOperations jdbcTemplate;

    public ExpertManagementAppService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ExpertVO.Expert> listExperts(CurrentUser currentUser, String keyword, String status, long pageNo, long pageSize) {
        Long tenantId = requireTenantId(currentUser);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_expert where tenant_id = ? and deleted = 0");
        params.add(tenantId);
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
        ExpertVO.Expert expert = findExpert(requireTenantId(currentUser), id);
        if (expert == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return expert;
    }

    @Transactional
    public ExpertVO.Expert createExpert(CurrentUser currentUser, ExpertDTO.ExpertUpsertRequest request) {
        Long tenantId = requireTenantId(currentUser);
        Long userId = requireUserId(currentUser);
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, generateExpertCode());
        jdbcTemplate.update(
                """
                        insert into aiadc_expert (
                            tenant_id, code, name, title, organization, position, expertise,
                            phone, email, avatar_url, bio, tags, status, sort, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                normalized.getCode(),
                normalized.getName(),
                normalized.getTitle(),
                normalized.getOrganization(),
                normalized.getPosition(),
                normalized.getExpertise(),
                normalized.getPhone(),
                normalized.getEmail(),
                normalized.getAvatarUrl(),
                normalized.getBio(),
                normalized.getTags(),
                normalized.getStatus(),
                normalized.getSort(),
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getExpert(currentUser, id);
    }

    @Transactional
    public ExpertVO.Expert updateExpert(CurrentUser currentUser, Long id, ExpertDTO.ExpertUpsertRequest request) {
        Long tenantId = requireTenantId(currentUser);
        ExpertVO.Expert existing = findExpert(tenantId, id);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        ExpertDTO.ExpertUpsertRequest normalized = normalizeRequest(request, existing.getCode());
        int updated = jdbcTemplate.update(
                """
                        update aiadc_expert
                        set code = ?, name = ?, title = ?, organization = ?, position = ?, expertise = ?,
                            phone = ?, email = ?, avatar_url = ?, bio = ?, tags = ?, status = ?, sort = ?,
                            updated_by = ?, updated_at = ?
                        where tenant_id = ? and id = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getName(),
                normalized.getTitle(),
                normalized.getOrganization(),
                normalized.getPosition(),
                normalized.getExpertise(),
                normalized.getPhone(),
                normalized.getEmail(),
                normalized.getAvatarUrl(),
                normalized.getBio(),
                normalized.getTags(),
                normalized.getStatus(),
                normalized.getSort(),
                requireUserId(currentUser),
                LocalDateTime.now(),
                tenantId,
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
                "update aiadc_expert set deleted = 1, updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                requireTenantId(currentUser),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Expert not found");
        }
        return true;
    }

    private ExpertVO.Expert findExpert(Long tenantId, Long id) {
        List<ExpertVO.Expert> records = jdbcTemplate.query(
                expertSelect() + " from aiadc_expert where tenant_id = ? and id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                tenantId,
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private ExpertDTO.ExpertUpsertRequest normalizeRequest(ExpertDTO.ExpertUpsertRequest request, String fallbackCode) {
        ExpertDTO.ExpertUpsertRequest normalized = new ExpertDTO.ExpertUpsertRequest();
        normalized.setCode(StringUtils.hasText(request.getCode()) ? request.getCode().trim() : trimRequired(fallbackCode, "Expert code is required"));
        normalized.setName(trimRequired(request.getName(), "Expert name is required"));
        normalized.setTitle(trimToNull(request.getTitle()));
        normalized.setOrganization(trimToNull(request.getOrganization()));
        normalized.setPosition(trimToNull(request.getPosition()));
        normalized.setExpertise(trimRequired(request.getExpertise(), "Expertise is required"));
        normalized.setPhone(trimToNull(request.getPhone()));
        normalized.setEmail(trimToNull(request.getEmail()));
        normalized.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        normalized.setBio(trimToNull(request.getBio()));
        normalized.setTags(trimToNull(request.getTags()));
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

    private Long requireTenantId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return PlatformContext.compatibilityTenantId();
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

    private String expertSelect() {
        return """
                select id, tenant_id as tenantId, code, name, title, organization, position, expertise,
                       phone, email, avatar_url as avatarUrl, bio, tags, status, sort,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
