package com.lumira.saas.modules.project.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.vo.ProjectVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProjectManagementAppService {
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final Set<String> STATUSES = Set.of("draft", "published");
    private static final Set<String> RATINGS = Set.of("all", "excellent", "popular", "new");
    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations jdbcTemplate;

    public ProjectManagementAppService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ProjectVO.Project> listProjects(
            CurrentUser currentUser,
            String keyword,
            String category,
            String ownerName,
            String rating,
            String status,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    ) {
        requireAuthenticated(currentUser);
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_project where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (title like ? or code like ? or description like ? or tags like ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(category) && !"all".equalsIgnoreCase(category.trim())) {
            where.append(" and category = ?");
            params.add(category.trim());
        }
        if (StringUtils.hasText(ownerName)) {
            where.append(" and owner_name like ?");
            params.add("%" + ownerName.trim() + "%");
        }
        if (StringUtils.hasText(rating) && !"all".equalsIgnoreCase(rating.trim())) {
            where.append(" and rating = ?");
            params.add(normalizeEnum(rating, null, RATINGS, "Invalid project rating"));
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            params.add(normalizeEnum(status, null, STATUSES, "Invalid project status"));
        }
        if (StringUtils.hasText(locale)) {
            where.append(" and locale = ?");
            params.add(normalizeEnum(locale, null, LOCALES, "Invalid project locale"));
        }
        if (featured != null) {
            where.append(" and featured = ?");
            params.add(Boolean.TRUE.equals(featured) ? 1 : 0);
        }

        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((normalizedPageNo - 1) * normalizedPageSize);
        selectParams.add(normalizedPageSize);
        List<ProjectVO.Project> records = jdbcTemplate.query(
                projectSelect() + where + " order by sort asc, featured desc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ProjectVO.Project.class),
                selectParams.toArray()
        );

        PageResponse<ProjectVO.Project> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setHasMore(normalizedPageNo * normalizedPageSize < response.getTotal());
        return response;
    }

    public ProjectVO.Project getProject(CurrentUser currentUser, Long id) {
        requireAuthenticated(currentUser);
        ProjectVO.Project project = findProject(id);
        if (project == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return project;
    }

    @Transactional
    public ProjectVO.Project createProject(CurrentUser currentUser, ProjectDTO.ProjectUpsertRequest request) {
        Long userId = requireUserId(currentUser);
        ProjectDTO.ProjectUpsertRequest normalized = normalizeRequest(request);
        jdbcTemplate.update(
                """
                        insert into aiadc_project (
                            code, locale, title, category, description, image_url,
                            owner_name, rating, sort, status, tags, cta_label, cta_href,
                            featured, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getCategory(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getOwnerName(),
                normalized.getRating(),
                normalized.getSort(),
                normalized.getStatus(),
                normalized.getTags(),
                normalized.getCtaLabel(),
                normalized.getCtaHref(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                userId,
                userId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return getProject(currentUser, id);
    }

    @Transactional
    public ProjectVO.Project updateProject(CurrentUser currentUser, Long id, ProjectDTO.ProjectUpsertRequest request) {
        ProjectDTO.ProjectUpsertRequest normalized = normalizeRequest(request);
        int updated = jdbcTemplate.update(
                """
                        update aiadc_project
                        set code = ?, locale = ?, title = ?, category = ?, description = ?, image_url = ?,
                            owner_name = ?, rating = ?, sort = ?, status = ?, tags = ?, cta_label = ?,
                            cta_href = ?, featured = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                normalized.getCode(),
                normalized.getLocale(),
                normalized.getTitle(),
                normalized.getCategory(),
                normalized.getDescription(),
                normalized.getImageUrl(),
                normalized.getOwnerName(),
                normalized.getRating(),
                normalized.getSort(),
                normalized.getStatus(),
                normalized.getTags(),
                normalized.getCtaLabel(),
                normalized.getCtaHref(),
                Boolean.TRUE.equals(normalized.getFeatured()) ? 1 : 0,
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return getProject(currentUser, id);
    }

    @Transactional
    public boolean deleteProject(CurrentUser currentUser, Long id) {
        int updated = jdbcTemplate.update(
                "update aiadc_project set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                requireUserId(currentUser),
                LocalDateTime.now(),
                id
        );
        if (updated == 0) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        return true;
    }

    private ProjectVO.Project findProject(Long id) {
        List<ProjectVO.Project> records = jdbcTemplate.query(
                projectSelect() + " from aiadc_project where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ProjectVO.Project.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private ProjectDTO.ProjectUpsertRequest normalizeRequest(ProjectDTO.ProjectUpsertRequest request) {
        ProjectDTO.ProjectUpsertRequest normalized = new ProjectDTO.ProjectUpsertRequest();
        normalized.setCode(trimRequired(request.getCode(), "Project code is required"));
        normalized.setLocale(normalizeEnum(request.getLocale(), "zh", LOCALES, "Invalid project locale"));
        normalized.setTitle(trimRequired(request.getTitle(), "Project title is required"));
        normalized.setCategory(trimRequired(request.getCategory(), "Project category is required"));
        normalized.setDescription(trimToNull(request.getDescription()));
        normalized.setImageUrl(trimToNull(request.getImageUrl()));
        normalized.setOwnerName(trimToNull(request.getOwnerName()));
        normalized.setRating(normalizeEnum(request.getRating(), "popular", RATINGS, "Invalid project rating"));
        normalized.setSort(request.getSort() == null ? 100 : request.getSort());
        normalized.setStatus(normalizeEnum(request.getStatus(), "draft", STATUSES, "Invalid project status"));
        normalized.setTags(trimToNull(request.getTags()));
        normalized.setCtaLabel(trimToNull(request.getCtaLabel()));
        normalized.setCtaHref(trimToNull(request.getCtaHref()));
        normalized.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
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

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
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

    private String projectSelect() {
        return """
                select id, code, locale, title, category, description,
                       image_url as imageUrl, owner_name as ownerName, rating, sort, status,
                       tags, cta_label as ctaLabel, cta_href as ctaHref, featured,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }
}
