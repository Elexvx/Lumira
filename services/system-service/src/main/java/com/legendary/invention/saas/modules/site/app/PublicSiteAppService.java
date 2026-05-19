package com.legendary.invention.saas.modules.site.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.modules.site.domain.SiteEnums;
import com.legendary.invention.saas.modules.site.dto.SiteDTO;
import com.legendary.invention.saas.modules.site.vo.SiteVO;
import com.legendary.invention.saas.modules.system.app.SystemPlatformSettingsAppService;
import com.legendary.invention.saas.modules.system.vo.SystemVO;
import org.springframework.dao.EmptyResultDataAccessException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.legendary.invention.saas.infrastructure.persistence.mybatis.SqlRow;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicSiteAppService {

    private static final long DEFAULT_TENANT_ID = 1001L;
    private static final int MAX_SUBMISSION_JSON_LENGTH = 64 * 1024;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SiteManagementAppService siteManagementAppService;
    private final SystemPlatformSettingsAppService systemPlatformSettingsAppService;

    public PublicSiteAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            SiteManagementAppService siteManagementAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.siteManagementAppService = siteManagementAppService;
        this.systemPlatformSettingsAppService = systemPlatformSettingsAppService;
    }

    public SiteVO.PublicRuntimeVO runtime() {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        SiteVO.PublicRuntimeVO vo = new SiteVO.PublicRuntimeVO();
        vo.site = publicSite(siteId);
        vo.navigation = navigation(siteId);
        vo.carousels = carousels(siteId);
        return vo;
    }

    public List<SiteVO.NavigationVO> navigation() {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        return navigation(siteId);
    }

    public List<SiteVO.CarouselVO> carousels() {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        return carousels(siteId);
    }

    public SiteVO.PublicPageVO page(String slug) {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        SiteVO.PublicPageVO vo = new SiteVO.PublicPageVO();
        vo.site = publicSite(siteId);
        vo.page = queryOptional(() -> jdbcTemplate.queryForObject(
                        """
                                select p.*, pv.blocks_json
                                from site_page p
                                join site_page_version pv
                                  on pv.id = p.current_published_version
                                 and pv.tenant_id = p.tenant_id
                                 and pv.status = 'PUBLISHED'
                                where p.tenant_id = ? and p.site_id = ? and p.slug in (?, ?) and p.status = 'PUBLISHED' and p.deleted = 0
                                order by case when p.slug = ? then 0 else 1 end
                                limit 1
                                """,
                        (rs, rowNum) -> mapPage(rs),
                        DEFAULT_TENANT_ID,
                        siteId,
                        normalizeSlug(slug),
                        legacySlug(slug),
                        normalizeSlug(slug)
                )
        );
        if (vo.page == null) {
            return null;
        }
        vo.blocksJson = vo.page.blocksJson;
        return vo;
    }

    public PageResponse<SiteVO.ContentVO> contents(Long categoryId, long pageNo, long pageSize) {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(Math.max(1, pageSize), 50);
        String filter = categoryId == null ? "" : " and c.category_id = ?";
        Object[] countArgs = categoryId == null
                ? new Object[]{DEFAULT_TENANT_ID, siteId}
                : new Object[]{DEFAULT_TENANT_ID, siteId, categoryId};
        Long total = jdbcTemplate.queryForObject("select count(1) from site_content c where c.tenant_id = ? and c.site_id = ? and c.status = 'PUBLISHED' and c.deleted = 0" + filter, Long.class, countArgs);
        Object[] listArgs = categoryId == null
                ? new Object[]{DEFAULT_TENANT_ID, siteId, (safePageNo - 1) * safePageSize, safePageSize}
                : new Object[]{DEFAULT_TENANT_ID, siteId, categoryId, (safePageNo - 1) * safePageSize, safePageSize};
        List<SiteVO.ContentVO> records = jdbcTemplate.query(
                "select c.*, f.public_url as cover_url from site_content c left join file_object f on f.tenant_id = c.tenant_id and f.id = c.cover_file_id and f.deleted = 0 where c.tenant_id = ? and c.site_id = ? and c.status = 'PUBLISHED' and c.deleted = 0" + filter + " order by c.published_at desc, c.id desc limit ?, ?",
                (rs, rowNum) -> mapContent(rs),
                listArgs
        );
        PageResponse<SiteVO.ContentVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public SiteVO.ContentVO content(String slug) {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        return queryOptional(() -> jdbcTemplate.queryForObject(
                        """
                                select c.*, f.public_url as cover_url
                                from site_content c
                                left join file_object f on f.tenant_id = c.tenant_id and f.id = c.cover_file_id and f.deleted = 0
                                where c.tenant_id = ? and c.site_id = ? and c.slug in (?, ?) and c.status = 'PUBLISHED' and c.deleted = 0
                                order by case when c.slug = ? then 0 else 1 end
                                limit 1
                                """,
                        (rs, rowNum) -> mapContent(rs),
                        DEFAULT_TENANT_ID,
                        siteId,
                        normalizeSlug(slug),
                        legacySlug(slug),
                        normalizeSlug(slug)
                )
        );
    }

    public SiteVO.FormVO form(String code) {
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        return queryOptional(() -> jdbcTemplate.queryForObject(
                        "select * from site_form where tenant_id = ? and site_id = ? and code = ? and status = 'ENABLED' and deleted = 0 limit 1",
                        (rs, rowNum) -> mapForm(rs),
                        DEFAULT_TENANT_ID,
                        siteId,
                        code
                )
        );
    }

    @Transactional
    public SiteVO.SubmissionVO submit(String code, SiteDTO.SubmissionRequest request, String ip, Long userId) {
        if (StringUtils.hasText(request.website)) {
            throw new BizException(ErrorCode.TRAFFIC_LIMITED, "提交过于频繁，请稍后再试");
        }
        guardSubmissionPayloadSize(request);
        validateJson(request.dataJson, "提交内容");
        validateJson(request.attachmentFileIdsJson, "附件");
        Long siteId = siteManagementAppService.defaultSiteId(DEFAULT_TENANT_ID, 0L);
        SiteVO.FormVO form = form(code);
        if (form == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "表单不存在或未启用");
        }
        if ("LOGIN_REQUIRED".equals(form.submitPolicy) && userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "该表单需要登录后提交");
        }
        guardSubmissionFrequency(siteId, form.id, ip, userId);
        jdbcTemplate.update(
                """
                        insert into site_form_submission (
                            tenant_id, site_id, form_id, submitter_user_id, submitter_ip, data_json,
                            attachment_file_ids_json, status, deleted
                        ) values (?, ?, ?, ?, ?, cast(? as json), cast(? as json), ?, 0)
                        """,
                DEFAULT_TENANT_ID, siteId, form.id, userId, ip, request.dataJson, jsonOrNull(request.attachmentFileIdsJson), SiteEnums.SUBMISSION_PENDING
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return jdbcTemplate.queryForObject(
                "select s.*, f.name as form_name from site_form_submission s left join site_form f on f.id = s.form_id and f.tenant_id = s.tenant_id where s.id = ?",
                (rs, rowNum) -> mapSubmission(rs),
                id
        );
    }

    private void guardSubmissionPayloadSize(SiteDTO.SubmissionRequest request) {
        if (request.dataJson != null && request.dataJson.length() > MAX_SUBMISSION_JSON_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提交内容过大");
        }
        if (request.attachmentFileIdsJson != null && request.attachmentFileIdsJson.length() > MAX_SUBMISSION_JSON_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "附件内容过大");
        }
    }

    private void guardSubmissionFrequency(Long siteId, Long formId, String ip, Long userId) {
        Long recentByIp = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from site_form_submission
                        where tenant_id = ? and site_id = ? and form_id = ? and submitter_ip = ?
                          and deleted = 0 and created_at >= date_sub(now(), interval 1 minute)
                        """,
                Long.class,
                DEFAULT_TENANT_ID,
                siteId,
                formId,
                ip
        );
        if (recentByIp != null && recentByIp >= 3) {
            throw new BizException(ErrorCode.TRAFFIC_LIMITED, "提交过于频繁，请稍后再试");
        }
        if (userId == null) {
            return;
        }
        Long recentByUser = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from site_form_submission
                        where tenant_id = ? and site_id = ? and form_id = ? and submitter_user_id = ?
                          and deleted = 0 and created_at >= date_sub(now(), interval 1 minute)
                        """,
                Long.class,
                DEFAULT_TENANT_ID,
                siteId,
                formId,
                userId
        );
        if (recentByUser != null && recentByUser >= 3) {
            throw new BizException(ErrorCode.TRAFFIC_LIMITED, "提交过于频繁，请稍后再试");
        }
    }

    private SiteVO.SiteSettingsVO publicSite(Long siteId) {
        SiteVO.SiteSettingsVO site = jdbcTemplate.queryForObject(
                """
                        select s.*, lf.public_url as logo_url, ff.public_url as favicon_url
                        from site_site s
                        left join file_object lf on lf.tenant_id = s.tenant_id and lf.id = s.logo_file_id and lf.deleted = 0
                        left join file_object ff on ff.tenant_id = s.tenant_id and ff.id = s.favicon_file_id and ff.deleted = 0
                        where s.tenant_id = ? and s.id = ? and s.status = 'ENABLED' and s.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> mapSite(rs),
                DEFAULT_TENANT_ID,
                siteId
        );
        applyBrandingFallback(site);
        return site;
    }

    private void applyBrandingFallback(SiteVO.SiteSettingsVO site) {
        if (site == null) {
            return;
        }
        SystemVO.BrandingSettingsVO branding = systemPlatformSettingsAppService.getPublicBrandingSettings(DEFAULT_TENANT_ID);
        if (!StringUtils.hasText(site.name)) {
            site.name = branding.getWebsiteName();
        }
        if (!StringUtils.hasText(site.logoUrl)) {
            site.logoUrl = branding.getWebsiteLogoUrl();
        }
        if (!StringUtils.hasText(site.faviconUrl)) {
            site.faviconUrl = branding.getWebsiteFaviconUrl();
        }
        if (!StringUtils.hasText(site.loginRoute)) {
            site.loginRoute = "/user/login";
        }
    }

    private List<SiteVO.NavigationVO> navigation(Long siteId) {
        return jdbcTemplate.query(
                """
                        select *
                        from site_navigation
                        where tenant_id = ? and site_id = ? and status = 'VISIBLE' and deleted = 0
                        order by sort_order asc, id asc
                        """,
                (rs, rowNum) -> mapNavigation(rs),
                DEFAULT_TENANT_ID,
                siteId
        );
    }

    private List<SiteVO.CarouselVO> carousels(Long siteId) {
        return jdbcTemplate.query(
                """
                        select c.*, f.public_url as file_public_url
                        from site_carousel_item c
                        left join file_object f on f.tenant_id = c.tenant_id and f.id = c.image_file_id and f.deleted = 0
                        where c.tenant_id = ? and c.site_id = ? and c.status = 'VISIBLE' and c.deleted = 0
                        order by c.sort_order asc, c.id asc
                        """,
                (rs, rowNum) -> mapCarousel(rs),
                DEFAULT_TENANT_ID,
                siteId
        );
    }

    private SiteVO.SiteSettingsVO mapSite(SqlRow rs) {
        SiteVO.SiteSettingsVO vo = new SiteVO.SiteSettingsVO();
        vo.id = rs.getLong("id");
        vo.code = rs.getString("code");
        vo.name = rs.getString("name");
        vo.primaryDomain = rs.getString("primary_domain");
        vo.loginRoute = rs.getString("login_route");
        vo.logoFileId = longObject(rs, "logo_file_id");
        vo.logoUrl = safeString(rs, "logo_url");
        vo.faviconFileId = longObject(rs, "favicon_file_id");
        vo.faviconUrl = safeString(rs, "favicon_url");
        vo.themeJson = rs.getString("theme_json");
        vo.seoJson = rs.getString("seo_json");
        vo.status = rs.getString("status");
        return vo;
    }

    private SiteVO.NavigationVO mapNavigation(SqlRow rs) {
        SiteVO.NavigationVO vo = new SiteVO.NavigationVO();
        vo.id = rs.getLong("id");
        vo.parentId = longObject(rs, "parent_id");
        vo.title = rs.getString("title");
        vo.linkType = rs.getString("link_type");
        vo.linkTarget = rs.getString("link_target");
        vo.openType = rs.getString("open_type");
        vo.sortOrder = rs.getInt("sort_order");
        vo.status = rs.getString("status");
        return vo;
    }

    private SiteVO.CarouselVO mapCarousel(SqlRow rs) {
        SiteVO.CarouselVO vo = new SiteVO.CarouselVO();
        vo.id = rs.getLong("id");
        vo.title = rs.getString("title");
        vo.subtitle = rs.getString("subtitle");
        vo.imageFileId = longObject(rs, "image_file_id");
        vo.imageUrl = clean(rs.getString("file_public_url"), rs.getString("image_url"));
        vo.linkType = rs.getString("link_type");
        vo.linkTarget = rs.getString("link_target");
        vo.openType = rs.getString("open_type");
        vo.sortOrder = rs.getInt("sort_order");
        vo.status = rs.getString("status");
        vo.updatedAt = localDateTime(rs, "updated_at");
        return vo;
    }

    private SiteVO.PageVO mapPage(SqlRow rs) {
        SiteVO.PageVO vo = new SiteVO.PageVO();
        vo.id = rs.getLong("id");
        vo.title = rs.getString("title");
        vo.slug = rs.getString("slug");
        vo.pageType = rs.getString("page_type");
        vo.seoJson = rs.getString("seo_json");
        vo.blocksJson = rs.getString("blocks_json");
        vo.status = rs.getString("status");
        vo.publishedAt = localDateTime(rs, "published_at");
        vo.updatedAt = localDateTime(rs, "updated_at");
        return vo;
    }

    private SiteVO.ContentVO mapContent(SqlRow rs) {
        SiteVO.ContentVO vo = new SiteVO.ContentVO();
        vo.id = rs.getLong("id");
        vo.categoryId = longObject(rs, "category_id");
        vo.title = rs.getString("title");
        vo.slug = rs.getString("slug");
        vo.summary = rs.getString("summary");
        vo.coverFileId = longObject(rs, "cover_file_id");
        vo.coverUrl = safeString(rs, "cover_url");
        vo.bodyType = rs.getString("body_type");
        vo.bodyText = rs.getString("body_text");
        vo.bodyJson = rs.getString("body_json");
        vo.seoJson = rs.getString("seo_json");
        vo.tagsJson = rs.getString("tags_json");
        vo.status = rs.getString("status");
        vo.publishedAt = localDateTime(rs, "published_at");
        vo.updatedAt = localDateTime(rs, "updated_at");
        return vo;
    }

    private SiteVO.FormVO mapForm(SqlRow rs) {
        SiteVO.FormVO vo = new SiteVO.FormVO();
        vo.id = rs.getLong("id");
        vo.code = rs.getString("code");
        vo.name = rs.getString("name");
        vo.submitPolicy = rs.getString("submit_policy");
        vo.schemaJson = rs.getString("schema_json");
        vo.status = rs.getString("status");
        return vo;
    }

    private SiteVO.SubmissionVO mapSubmission(SqlRow rs) {
        SiteVO.SubmissionVO vo = new SiteVO.SubmissionVO();
        vo.id = rs.getLong("id");
        vo.formId = rs.getLong("form_id");
        vo.formName = safeString(rs, "form_name");
        vo.submitterUserId = longObject(rs, "submitter_user_id");
        vo.submitterIp = rs.getString("submitter_ip");
        vo.dataJson = rs.getString("data_json");
        vo.attachmentFileIdsJson = rs.getString("attachment_file_ids_json");
        vo.status = rs.getString("status");
        vo.createdAt = localDateTime(rs, "created_at");
        return vo;
    }

    private void validateJson(String value, String label) {
        if (value == null || value.isBlank()) return;
        try {
            objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, label + "不是合法 JSON");
        }
    }

    private String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank() || "/".equals(slug)) return "/";
        return slug.startsWith("/") ? slug : "/" + slug;
    }

    private String legacySlug(String slug) {
        String normalized = normalizeSlug(slug);
        return "/".equals(normalized) ? normalized : normalized.substring(1);
    }

    private String jsonOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Long longObject(SqlRow rs, String column) {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime localDateTime(SqlRow rs, String column) {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private String safeString(SqlRow rs, String column) {
        return rs.getString(column);
    }

    private <T> T queryOptional(QuerySupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    @FunctionalInterface
    private interface QuerySupplier<T> {
        T get();
    }
}
