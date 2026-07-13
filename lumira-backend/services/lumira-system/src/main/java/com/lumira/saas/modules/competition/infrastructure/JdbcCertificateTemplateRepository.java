package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.repository.CertificateTemplateRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcCertificateTemplateRepository implements CertificateTemplateRepository {
    private final MyBatisQueryOperations database;

    public JdbcCertificateTemplateRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public TemplatePage findTemplates(String keyword, String status, long offset, long limit) {
        StringBuilder where = new StringBuilder(" from certificate_template where deleted = 0");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" and (template_code like ? or template_name like ?)");
            String pattern = "%" + keyword + "%";
            params.add(pattern); params.add(pattern);
        }
        if (StringUtils.hasText(status)) { where.append(" and status = ?"); params.add(status); }
        Long total = database.queryForObject("select count(1)" + where, Long.class, params.toArray());
        params.add(offset); params.add(limit);
        List<CertificateVO.Template> records = database.query(templateSelect() + where + " order by updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CertificateVO.Template.class), params.toArray());
        return new TemplatePage(records, total == null ? 0L : total);
    }

    @Override
    public Long insertTemplate(String code, String name, String sceneType, String description, Long userId, String uuid) {
        int inserted = database.update("""
                insert into certificate_template (template_code, template_name, template_type, scene_type, description,
                    latest_version, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, 'CERTIFICATE', ?, ?, 1, 'DRAFT', ?, ?, ?, ?, 0)
                """, code, name, sceneType, description, userId, uuid, userId, uuid);
        return inserted > 0 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public int insertInitialVersion(Long templateId, TemplateDefaults d, Long userId, String uuid) {
        return database.update("""
                insert into certificate_template_version (template_id, version, page_width, page_height, orientation, unit, dpi,
                    canvas_json, variable_schema_json, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, 1, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, 0)
                """, templateId, d.width(), d.height(), d.orientation(), d.unit(), d.dpi(), d.canvasJson(),
                d.variableSchemaJson(), userId, uuid, userId, uuid);
    }

    @Override public CertificateVO.Template findTemplate(Long id) { return first(database.query(templateSelect() + " from certificate_template where id = ? and deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Template.class), id)); }
    @Override public List<CertificateVO.TemplateVersion> findVersions(Long id) { return database.query(versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc", new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class), id); }
    @Override public CertificateVO.TemplateVersion findVersion(Long id) { return first(database.query(versionSelect() + " from certificate_template_version where id = ? and deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class), id)); }
    @Override public CertificateVO.TemplateVersion findLatestVersion(Long id) { return first(database.query(versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc limit 1", new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class), id)); }

    @Override
    public int updateTemplate(Long id, String expectedCode, String expectedStatus, String code, String name, String sceneType,
                              String description, Long userId, String uuid, LocalDateTime at) {
        return database.update("update certificate_template set template_code = ?, template_name = ?, scene_type = ?, description = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_code = ? and status = ? and deleted = 0",
                code, name, sceneType, description, userId, uuid, at, id, expectedCode, expectedStatus);
    }
    @Override public int archiveTemplate(Long id, String code, String status, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_template set status = 'ARCHIVED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_code = ? and status = ? and deleted = 0", userId, uuid, at, id, code, status); }
    @Override public int updateCanvas(Long id, Long templateId, Integer version, TemplateCanvas c, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_template_version set page_width = ?, page_height = ?, orientation = ?, unit = ?, dpi = ?, canvas_json = ?, variable_schema_json = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0", c.width(), c.height(), c.orientation(), c.unit(), c.dpi(), c.canvasJson(), c.variableSchemaJson(), userId, uuid, at, id, templateId, version); }
    @Override public int updateBackground(Long id, Long templateId, Integer version, Long fileId, String url, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_template_version set background_file_id = ?, background_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0", fileId, url, userId, uuid, at, id, templateId, version); }
    @Override public int publishVersion(CertificateVO.TemplateVersion d, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_template_version set status = 'PUBLISHED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0", userId, uuid, at, d.getId(), d.getTemplateId(), d.getVersion()); }
    @Override public int publishTemplate(CertificateVO.Template t, Integer version, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_template set status = 'PUBLISHED', latest_version = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_code = ? and status = ? and deleted = 0", version, userId, uuid, at, t.getId(), t.getTemplateCode(), t.getStatus()); }
    @Override public int insertDraftVersion(Long templateId, Integer version, CertificateVO.TemplateVersion d, Long userId, String uuid) { return database.update("""
            insert into certificate_template_version (template_id, version, background_file_id, background_url, page_width, page_height,
                orientation, unit, dpi, canvas_json, variable_schema_json, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, 0)
            """, templateId, version, d.getBackgroundFileId(), d.getBackgroundUrl(), d.getPageWidth(), d.getPageHeight(), d.getOrientation(), d.getUnit(), d.getDpi(), d.getCanvasJson(), d.getVariableSchemaJson(), userId, uuid, userId, uuid); }

    @Override
    public Map<String, String> findDefaultDefinitions(String groupCode) {
        List<Map<String, Object>> rows = database.queryForList("select config_key as configKey, default_value as configValue from sys_platform_setting_definition where group_code = ? and status = 'ENABLED' and deleted = 0 order by sort_no, id", groupCode);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) values.put(String.valueOf(row.get("configKey")), row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        return values;
    }

    private static <T> T first(List<T> values) { return values.isEmpty() ? null : values.getFirst(); }
    private static String templateSelect() { return "select id, template_code as templateCode, template_name as templateName, template_type as templateType, scene_type as sceneType, description, latest_version as latestVersion, status, created_by as createdBy, created_at as createdAt, updated_at as updatedAt"; }
    private static String versionSelect() { return "select id, template_id as templateId, version, background_file_id as backgroundFileId, background_url as backgroundUrl, page_width as pageWidth, page_height as pageHeight, orientation, unit, dpi, canvas_json as canvasJson, variable_schema_json as variableSchemaJson, status, created_by as createdBy, created_at as createdAt, updated_at as updatedAt"; }
}
