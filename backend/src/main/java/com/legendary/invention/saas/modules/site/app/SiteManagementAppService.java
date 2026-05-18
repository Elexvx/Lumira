package com.legendary.invention.saas.modules.site.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.site.domain.SiteEnums;
import com.legendary.invention.saas.modules.site.dto.SiteDTO;
import com.legendary.invention.saas.modules.site.vo.SiteVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SiteManagementAppService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OperationAuditService operationAuditService;

    public SiteManagementAppService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OperationAuditService operationAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.operationAuditService = operationAuditService;
    }

    public SiteVO.SiteSettingsVO settings(CurrentUser currentUser) {
        Long tenantId = tenantId(currentUser);
        ensureDefaultSite(tenantId, currentUser.getUserId());
        return jdbcTemplate.queryForObject(
                """
                        select s.*, lf.public_url as logo_url, ff.public_url as favicon_url
                        from site_site s
                        left join file_object lf on lf.tenant_id = s.tenant_id and lf.id = s.logo_file_id and lf.deleted = 0
                        left join file_object ff on ff.tenant_id = s.tenant_id and ff.id = s.favicon_file_id and ff.deleted = 0
                        where s.tenant_id = ? and s.code = 'main' and s.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> mapSite(rs),
                tenantId
        );
    }

    @Transactional
    public SiteVO.SiteSettingsVO updateSettings(CurrentUser currentUser, SiteDTO.SiteSettingsRequest request) {
        Long tenantId = tenantId(currentUser);
        validateJson(request.themeJson, "主题配置");
        validateJson(request.seoJson, "SEO 配置");
        ensureDefaultSite(tenantId, currentUser.getUserId());
        jdbcTemplate.update(
                """
                        update site_site
                        set code = ?, name = ?, primary_domain = ?, login_route = ?, logo_file_id = ?, favicon_file_id = ?,
                            theme_json = cast(? as json), seo_json = cast(? as json), status = ?,
                            updated_by = ?, updated_at = now(), version = version + 1
                        where tenant_id = ? and code = 'main' and deleted = 0
                        """,
                clean(request.code, "main"),
                clean(request.name, "官网"),
                cleanNullable(request.primaryDomain),
                clean(request.loginRoute, "/user/login"),
                request.logoFileId,
                request.faviconFileId,
                jsonOrNull(request.themeJson),
                jsonOrNull(request.seoJson),
                clean(request.status, SiteEnums.SITE_ENABLED),
                currentUser.getUserId(),
                tenantId
        );
        audit(currentUser, "site-settings-update", "UPDATE", "更新官网设置");
        return settings(currentUser);
    }

    public List<SiteVO.NavigationVO> navigations(CurrentUser currentUser) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        return jdbcTemplate.query(
                """
                        select *
                        from site_navigation
                        where tenant_id = ? and site_id = ? and deleted = 0
                        order by sort_order asc, id asc
                        """,
                (rs, rowNum) -> mapNavigation(rs),
                tenantId,
                siteId
        );
    }

    @Transactional
    public SiteVO.NavigationVO createNavigation(CurrentUser currentUser, SiteDTO.NavigationRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update(
                """
                        insert into site_navigation (
                            tenant_id, site_id, parent_id, title, link_type, link_target, open_type, sort_order,
                            status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId, siteId, request.parentId, request.title, request.linkType, request.linkTarget,
                clean(request.openType, "SELF"), defaultInt(request.sortOrder), clean(request.status, SiteEnums.NAV_VISIBLE),
                currentUser.getUserId(), currentUser.getUserId()
        );
        Long id = lastId();
        audit(currentUser, "site-navigation-create", "CREATE", "新增官网导航: " + request.title);
        return navigation(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.NavigationVO updateNavigation(CurrentUser currentUser, Long id, SiteDTO.NavigationRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update(
                """
                        update site_navigation
                        set parent_id = ?, title = ?, link_type = ?, link_target = ?, open_type = ?, sort_order = ?,
                            status = ?, updated_by = ?, updated_at = now(), version = version + 1
                        where id = ? and tenant_id = ? and site_id = ? and deleted = 0
                        """,
                request.parentId, request.title, request.linkType, request.linkTarget, clean(request.openType, "SELF"),
                defaultInt(request.sortOrder), clean(request.status, SiteEnums.NAV_VISIBLE), currentUser.getUserId(), id, tenantId, siteId
        );
        audit(currentUser, "site-navigation-update", "UPDATE", "更新官网导航: " + id);
        return navigation(tenantId, siteId, id);
    }

    @Transactional
    public boolean deleteNavigation(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_navigation set deleted = 1, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ?", currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-navigation-delete", "DELETE", "删除官网导航: " + id);
        return true;
    }

    public List<SiteVO.CarouselVO> carousels(CurrentUser currentUser) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        return jdbcTemplate.query(
                """
                        select c.*, f.public_url as file_public_url
                        from site_carousel_item c
                        left join file_object f on f.tenant_id = c.tenant_id and f.id = c.image_file_id and f.deleted = 0
                        where c.tenant_id = ? and c.site_id = ? and c.deleted = 0
                        order by c.sort_order asc, c.id asc
                        """,
                (rs, rowNum) -> mapCarousel(rs),
                tenantId,
                siteId
        );
    }

    @Transactional
    public SiteVO.CarouselVO createCarousel(CurrentUser currentUser, SiteDTO.CarouselRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update(
                """
                        insert into site_carousel_item (
                            tenant_id, site_id, title, subtitle, image_file_id, image_url, link_type, link_target,
                            open_type, sort_order, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                siteId,
                request.title,
                cleanNullable(request.subtitle),
                request.imageFileId,
                cleanNullable(request.imageUrl),
                clean(request.linkType, "NONE"),
                cleanNullable(request.linkTarget),
                clean(request.openType, "SELF"),
                defaultInt(request.sortOrder),
                clean(request.status, SiteEnums.CAROUSEL_VISIBLE),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        Long id = lastId();
        audit(currentUser, "site-carousel-create", "CREATE", "新增官网轮播: " + request.title);
        return carousel(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.CarouselVO updateCarousel(CurrentUser currentUser, Long id, SiteDTO.CarouselRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update(
                """
                        update site_carousel_item
                        set title = ?, subtitle = ?, image_file_id = ?, image_url = ?, link_type = ?, link_target = ?,
                            open_type = ?, sort_order = ?, status = ?, updated_by = ?, updated_at = now(), version = version + 1
                        where id = ? and tenant_id = ? and site_id = ? and deleted = 0
                        """,
                request.title,
                cleanNullable(request.subtitle),
                request.imageFileId,
                cleanNullable(request.imageUrl),
                clean(request.linkType, "NONE"),
                cleanNullable(request.linkTarget),
                clean(request.openType, "SELF"),
                defaultInt(request.sortOrder),
                clean(request.status, SiteEnums.CAROUSEL_VISIBLE),
                currentUser.getUserId(),
                id,
                tenantId,
                siteId
        );
        audit(currentUser, "site-carousel-update", "UPDATE", "更新官网轮播: " + id);
        return carousel(tenantId, siteId, id);
    }

    @Transactional
    public boolean deleteCarousel(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_carousel_item set deleted = 1, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ?", currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-carousel-delete", "DELETE", "删除官网轮播: " + id);
        return true;
    }

    public PageResponse<SiteVO.PageVO> pages(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        String filter = status == null || status.isBlank() ? "" : " and status = ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, siteId));
        if (!filter.isEmpty()) args.add(status);
        return page("site_page", filter, args, pageNo, pageSize, (rs, rowNum) -> mapPage(rs));
    }

    @Transactional
    public SiteVO.PageVO createPage(CurrentUser currentUser, SiteDTO.PageRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        validateJson(request.blocksJson, "页面区块");
        validateJson(request.seoJson, "SEO 配置");
        jdbcTemplate.update(
                """
                        insert into site_page (
                            tenant_id, site_id, title, slug, page_type, seo_json, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, cast(? as json), 'DRAFT', ?, ?, 0)
                        """,
                tenantId, siteId, request.title, normalizeSlug(request.slug), clean(request.pageType, "CUSTOM"),
                jsonOrNull(request.seoJson), currentUser.getUserId(), currentUser.getUserId()
        );
        Long pageId = lastId();
        Long versionId = createPageVersion(tenantId, siteId, pageId, 1L, request.blocksJson, "DRAFT", currentUser.getUserId());
        jdbcTemplate.update("update site_page set current_draft_version = ? where id = ? and tenant_id = ?", versionId, pageId, tenantId);
        audit(currentUser, "site-page-create", "CREATE", "新增官网页面: " + request.title);
        return page(tenantId, siteId, pageId);
    }

    @Transactional
    public SiteVO.PageVO updatePage(CurrentUser currentUser, Long id, SiteDTO.PageRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        validateJson(request.blocksJson, "页面区块");
        validateJson(request.seoJson, "SEO 配置");
        long nextVersion = jdbcTemplate.queryForObject("select coalesce(max(version_no), 0) + 1 from site_page_version where tenant_id = ? and page_id = ?", Long.class, tenantId, id);
        Long versionId = createPageVersion(tenantId, siteId, id, nextVersion, request.blocksJson, "DRAFT", currentUser.getUserId());
        jdbcTemplate.update(
                """
                        update site_page
                        set title = ?, slug = ?, page_type = ?, seo_json = cast(? as json), current_draft_version = ?,
                            status = if(status = 'PUBLISHED', status, 'DRAFT'), updated_by = ?, updated_at = now(), version = version + 1
                        where id = ? and tenant_id = ? and site_id = ? and deleted = 0
                        """,
                request.title, normalizeSlug(request.slug), clean(request.pageType, "CUSTOM"), jsonOrNull(request.seoJson),
                versionId, currentUser.getUserId(), id, tenantId, siteId
        );
        audit(currentUser, "site-page-update", "UPDATE", "更新官网页面: " + id);
        return page(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.PageVO publishPage(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        SiteVO.PageVO page = page(tenantId, siteId, id);
        if (page.currentDraftVersion == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "页面没有可发布草稿");
        }
        jdbcTemplate.update("update site_page_version set status = ? where tenant_id = ? and page_id = ? and status = ?", SiteEnums.ARCHIVED, tenantId, id, SiteEnums.PUBLISHED);
        jdbcTemplate.update("update site_page_version set status = ? where id = ? and tenant_id = ?", SiteEnums.PUBLISHED, page.currentDraftVersion, tenantId);
        jdbcTemplate.update("update site_page set current_published_version = current_draft_version, status = ?, published_at = now(), updated_by = ?, updated_at = now() where id = ? and tenant_id = ?", SiteEnums.PUBLISHED, currentUser.getUserId(), id, tenantId);
        audit(currentUser, "site-page-publish", "PUBLISH", "发布官网页面: " + id);
        return page(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.PageVO offlinePage(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_page set status = ?, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ? and deleted = 0", SiteEnums.OFFLINE, currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-page-offline", "UPDATE", "下线官网页面: " + id);
        return page(tenantId, siteId, id);
    }

    @Transactional
    public boolean deletePage(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_page set deleted = 1, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ?", currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-page-delete", "DELETE", "删除官网页面: " + id);
        return true;
    }

    public PageResponse<SiteVO.ContentVO> contents(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        String filter = status == null || status.isBlank() ? "" : " and c.status = ?";
        List<Object> args = new ArrayList<>(List.of(tenantId, siteId));
        if (!filter.isEmpty()) args.add(status);
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(Math.max(1, pageSize), 100);
        String where = " where c.tenant_id = ? and c.site_id = ? and c.deleted = 0" + filter;
        Long total = jdbcTemplate.queryForObject("select count(1) from site_content c" + where, Long.class, args.toArray());
        args.add((safePageNo - 1) * safePageSize);
        args.add(safePageSize);
        List<SiteVO.ContentVO> records = jdbcTemplate.query(
                "select c.*, f.public_url as cover_url from site_content c left join file_object f on f.tenant_id = c.tenant_id and f.id = c.cover_file_id and f.deleted = 0" + where + " order by c.updated_at desc limit ?, ?",
                (rs, rowNum) -> mapContent(rs),
                args.toArray()
        );
        return pageResponse(records, total == null ? 0 : total, safePageNo, safePageSize);
    }

    @Transactional
    public SiteVO.ContentVO createContent(CurrentUser currentUser, SiteDTO.ContentRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        validateJson(request.bodyJson, "内容结构");
        validateJson(request.seoJson, "SEO 配置");
        validateJson(request.tagsJson, "标签");
        jdbcTemplate.update(
                """
                        insert into site_content (
                            tenant_id, site_id, category_id, title, slug, summary, cover_file_id, body_type, body_text,
                            body_json, seo_json, tags_json, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as json), cast(? as json), cast(? as json), 'DRAFT', ?, ?, 0)
                        """,
                tenantId, siteId, request.categoryId, request.title, normalizeSlug(request.slug), cleanNullable(request.summary),
                request.coverFileId, clean(request.bodyType, "RICH_TEXT"), cleanNullable(request.bodyText), jsonOrNull(request.bodyJson),
                jsonOrNull(request.seoJson), jsonOrNull(request.tagsJson), currentUser.getUserId(), currentUser.getUserId()
        );
        Long id = lastId();
        audit(currentUser, "site-content-create", "CREATE", "新增官网内容: " + request.title);
        return content(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.ContentVO updateContent(CurrentUser currentUser, Long id, SiteDTO.ContentRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        validateJson(request.bodyJson, "内容结构");
        validateJson(request.seoJson, "SEO 配置");
        validateJson(request.tagsJson, "标签");
        jdbcTemplate.update(
                """
                        update site_content
                        set category_id = ?, title = ?, slug = ?, summary = ?, cover_file_id = ?, body_type = ?,
                            body_text = ?, body_json = cast(? as json), seo_json = cast(? as json), tags_json = cast(? as json),
                            status = if(status = 'PUBLISHED', status, 'DRAFT'), updated_by = ?, updated_at = now(), version = version + 1
                        where id = ? and tenant_id = ? and site_id = ? and deleted = 0
                        """,
                request.categoryId, request.title, normalizeSlug(request.slug), cleanNullable(request.summary), request.coverFileId,
                clean(request.bodyType, "RICH_TEXT"), cleanNullable(request.bodyText), jsonOrNull(request.bodyJson), jsonOrNull(request.seoJson),
                jsonOrNull(request.tagsJson), currentUser.getUserId(), id, tenantId, siteId
        );
        audit(currentUser, "site-content-update", "UPDATE", "更新官网内容: " + id);
        return content(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.ContentVO publishContent(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_content set status = ?, published_at = coalesce(published_at, now()), updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ? and deleted = 0", SiteEnums.PUBLISHED, currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-content-publish", "PUBLISH", "发布官网内容: " + id);
        return content(tenantId, siteId, id);
    }

    @Transactional
    public SiteVO.ContentVO offlineContent(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_content set status = ?, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ? and deleted = 0", SiteEnums.OFFLINE, currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-content-offline", "UPDATE", "下线官网内容: " + id);
        return content(tenantId, siteId, id);
    }

    @Transactional
    public boolean deleteContent(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("update site_content set deleted = 1, updated_by = ?, updated_at = now() where id = ? and tenant_id = ? and site_id = ?", currentUser.getUserId(), id, tenantId, siteId);
        audit(currentUser, "site-content-delete", "DELETE", "删除官网内容: " + id);
        return true;
    }

    public List<SiteVO.ContentCategoryVO> categories(CurrentUser currentUser) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        return jdbcTemplate.query("select * from site_content_category where tenant_id = ? and site_id = ? and deleted = 0 order by sort_order asc, id asc", (rs, rowNum) -> mapCategory(rs), tenantId, siteId);
    }

    @Transactional
    public SiteVO.ContentCategoryVO createCategory(CurrentUser currentUser, SiteDTO.CategoryRequest request) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        jdbcTemplate.update("insert into site_content_category (tenant_id, site_id, parent_id, code, name, sort_order, status, created_by, updated_by, deleted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", tenantId, siteId, request.parentId, request.code, request.name, defaultInt(request.sortOrder), clean(request.status, "ENABLED"), currentUser.getUserId(), currentUser.getUserId());
        return category(tenantId, siteId, lastId());
    }

    public PageResponse<SiteVO.SubmissionVO> submissions(CurrentUser currentUser, Long formId, String status, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        Long siteId = defaultSiteId(tenantId, currentUser.getUserId());
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(Math.max(1, pageSize), 100);
        StringBuilder where = new StringBuilder(" where s.tenant_id = ? and s.site_id = ? and s.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(tenantId, siteId));
        if (formId != null) {
            where.append(" and s.form_id = ?");
            args.add(formId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and s.status = ?");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("select count(1) from site_form_submission s" + where, Long.class, args.toArray());
        args.add((safePageNo - 1) * safePageSize);
        args.add(safePageSize);
        List<SiteVO.SubmissionVO> records = jdbcTemplate.query("select s.*, f.name as form_name from site_form_submission s left join site_form f on f.id = s.form_id and f.tenant_id = s.tenant_id" + where + " order by s.created_at desc limit ?, ?", (rs, rowNum) -> mapSubmission(rs), args.toArray());
        return pageResponse(records, total == null ? 0 : total, safePageNo, safePageSize);
    }

    @Transactional
    public SiteVO.SubmissionVO reviewSubmission(CurrentUser currentUser, Long id, SiteDTO.ReviewRequest request) {
        Long tenantId = tenantId(currentUser);
        jdbcTemplate.update("update site_form_submission set status = ?, reviewed_by = ?, reviewed_at = now(), review_remark = ?, updated_at = now(), version = version + 1 where id = ? and tenant_id = ? and deleted = 0", request.status, currentUser.getUserId(), cleanNullable(request.reviewRemark), id, tenantId);
        audit(currentUser, "site-submission-review", "UPDATE", "审核官网提交记录: " + id);
        return jdbcTemplate.queryForObject("select s.*, f.name as form_name from site_form_submission s left join site_form f on f.id = s.form_id and f.tenant_id = s.tenant_id where s.id = ? and s.tenant_id = ? and s.deleted = 0", (rs, rowNum) -> mapSubmission(rs), id, tenantId);
    }

    private void ensureDefaultSite(Long tenantId, Long operatorId) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from site_site where tenant_id = ? and code = 'main' and deleted = 0", Integer.class, tenantId);
        if (count != null && count > 0) return;
        jdbcTemplate.update("insert into site_site (tenant_id, code, name, status, created_by, updated_by, deleted) values (?, 'main', '官网', 'ENABLED', ?, ?, 0)", tenantId, operatorId, operatorId);
    }

    public Long defaultSiteId(Long tenantId, Long operatorId) {
        ensureDefaultSite(tenantId, operatorId);
        return jdbcTemplate.queryForObject("select id from site_site where tenant_id = ? and code = 'main' and deleted = 0 limit 1", Long.class, tenantId);
    }

    private Long tenantId(CurrentUser currentUser) {
        return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private Long createPageVersion(Long tenantId, Long siteId, Long pageId, Long versionNo, String blocksJson, String status, Long operatorId) {
        jdbcTemplate.update("insert into site_page_version (tenant_id, site_id, page_id, version_no, blocks_json, snapshot_json, status, created_by) values (?, ?, ?, ?, cast(? as json), cast(? as json), ?, ?)", tenantId, siteId, pageId, versionNo, blocksJson, blocksJson, status, operatorId);
        return lastId();
    }

    private Long lastId() {
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    private SiteVO.NavigationVO navigation(Long tenantId, Long siteId, Long id) {
        return jdbcTemplate.queryForObject("select * from site_navigation where id = ? and tenant_id = ? and site_id = ? and deleted = 0", (rs, rowNum) -> mapNavigation(rs), id, tenantId, siteId);
    }

    private SiteVO.CarouselVO carousel(Long tenantId, Long siteId, Long id) {
        return jdbcTemplate.queryForObject(
                """
                        select c.*, f.public_url as file_public_url
                        from site_carousel_item c
                        left join file_object f on f.tenant_id = c.tenant_id and f.id = c.image_file_id and f.deleted = 0
                        where c.id = ? and c.tenant_id = ? and c.site_id = ? and c.deleted = 0
                        """,
                (rs, rowNum) -> mapCarousel(rs),
                id,
                tenantId,
                siteId
        );
    }

    private SiteVO.PageVO page(Long tenantId, Long siteId, Long id) {
        return jdbcTemplate.queryForObject("select p.*, pv.blocks_json from site_page p left join site_page_version pv on pv.id = p.current_draft_version where p.id = ? and p.tenant_id = ? and p.site_id = ? and p.deleted = 0", (rs, rowNum) -> mapPage(rs), id, tenantId, siteId);
    }

    private SiteVO.ContentVO content(Long tenantId, Long siteId, Long id) {
        return jdbcTemplate.queryForObject("select c.*, f.public_url as cover_url from site_content c left join file_object f on f.tenant_id = c.tenant_id and f.id = c.cover_file_id and f.deleted = 0 where c.id = ? and c.tenant_id = ? and c.site_id = ? and c.deleted = 0", (rs, rowNum) -> mapContent(rs), id, tenantId, siteId);
    }

    private SiteVO.ContentCategoryVO category(Long tenantId, Long siteId, Long id) {
        return jdbcTemplate.queryForObject("select * from site_content_category where id = ? and tenant_id = ? and site_id = ? and deleted = 0", (rs, rowNum) -> mapCategory(rs), id, tenantId, siteId);
    }

    private <T> PageResponse<T> page(String table, String extraFilter, List<Object> args, long pageNo, long pageSize, org.springframework.jdbc.core.RowMapper<T> mapper) {
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(Math.max(1, pageSize), 100);
        String alias = table.equals("site_page") ? "p" : "f";
        String sqlTable = table + " " + alias;
        String where = " where " + alias + ".tenant_id = ? and " + alias + ".site_id = ? and " + alias + ".deleted = 0" + extraFilter;
        Long total = jdbcTemplate.queryForObject("select count(1) from " + sqlTable + where, Long.class, args.toArray());
        args.add((safePageNo - 1) * safePageSize);
        args.add(safePageSize);
        List<T> records = jdbcTemplate.query("select " + alias + ".* from " + sqlTable + where + " order by " + alias + ".updated_at desc limit ?, ?", mapper, args.toArray());
        return pageResponse(records, total == null ? 0 : total, safePageNo, safePageSize);
    }

    private <T> PageResponse<T> pageResponse(List<T> records, long total, long pageNo, long pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    private SiteVO.SiteSettingsVO mapSite(ResultSet rs) throws SQLException {
        SiteVO.SiteSettingsVO vo = new SiteVO.SiteSettingsVO();
        vo.id = rs.getLong("id");
        vo.tenantId = rs.getLong("tenant_id");
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
        vo.updatedAt = localDateTime(rs, "updated_at");
        return vo;
    }

    private SiteVO.NavigationVO mapNavigation(ResultSet rs) throws SQLException {
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

    private SiteVO.CarouselVO mapCarousel(ResultSet rs) throws SQLException {
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

    private SiteVO.PageVO mapPage(ResultSet rs) throws SQLException {
        SiteVO.PageVO vo = new SiteVO.PageVO();
        vo.id = rs.getLong("id");
        vo.title = rs.getString("title");
        vo.slug = rs.getString("slug");
        vo.pageType = rs.getString("page_type");
        vo.seoJson = rs.getString("seo_json");
        vo.currentDraftVersion = longObject(rs, "current_draft_version");
        vo.currentPublishedVersion = longObject(rs, "current_published_version");
        vo.blocksJson = safeString(rs, "blocks_json");
        vo.status = rs.getString("status");
        vo.publishedAt = localDateTime(rs, "published_at");
        vo.updatedAt = localDateTime(rs, "updated_at");
        return vo;
    }

    private SiteVO.ContentCategoryVO mapCategory(ResultSet rs) throws SQLException {
        SiteVO.ContentCategoryVO vo = new SiteVO.ContentCategoryVO();
        vo.id = rs.getLong("id");
        vo.parentId = longObject(rs, "parent_id");
        vo.code = rs.getString("code");
        vo.name = rs.getString("name");
        vo.sortOrder = rs.getInt("sort_order");
        vo.status = rs.getString("status");
        return vo;
    }

    private SiteVO.ContentVO mapContent(ResultSet rs) throws SQLException {
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

    private SiteVO.SubmissionVO mapSubmission(ResultSet rs) throws SQLException {
        SiteVO.SubmissionVO vo = new SiteVO.SubmissionVO();
        vo.id = rs.getLong("id");
        vo.formId = rs.getLong("form_id");
        vo.formName = safeString(rs, "form_name");
        vo.submitterUserId = longObject(rs, "submitter_user_id");
        vo.submitterIp = rs.getString("submitter_ip");
        vo.dataJson = rs.getString("data_json");
        vo.attachmentFileIdsJson = rs.getString("attachment_file_ids_json");
        vo.status = rs.getString("status");
        vo.reviewedBy = longObject(rs, "reviewed_by");
        vo.reviewedAt = localDateTime(rs, "reviewed_at");
        vo.reviewRemark = rs.getString("review_remark");
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

    private String jsonOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSlug(String slug) {
        String value = clean(slug, "/");
        return value.startsWith("/") ? value : "/" + value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Long longObject(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    private void audit(CurrentUser currentUser, String action, String type, String message) {
        operationAuditService.log(tenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "site", action, type, "SUCCESS", message);
    }
}
