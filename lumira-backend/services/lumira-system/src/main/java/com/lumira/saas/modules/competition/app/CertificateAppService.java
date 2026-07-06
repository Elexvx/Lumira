package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
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
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class CertificateAppService {
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String TEMPLATE_VIEW = "aiadc:certificate-template:view";
    private static final String TEMPLATE_CREATE = "aiadc:certificate-template:create";
    private static final String TEMPLATE_UPDATE = "aiadc:certificate-template:update";
    private static final String TEMPLATE_PUBLISH = "aiadc:certificate-template:publish";
    private static final String TEMPLATE_DELETE = "aiadc:certificate-template:delete";
    private static final String BATCH_VIEW = "aiadc:certificate-batch:view";
    private static final String BATCH_CREATE = "aiadc:certificate-batch:create";
    private static final String CERTIFICATE_VIEW = "aiadc:certificate:view";
    private static final String CERTIFICATE_DOWNLOAD = "aiadc:certificate:download";
    private static final String CERTIFICATE_REGENERATE = "aiadc:certificate:regenerate";
    private static final String CERTIFICATE_REVOKE = "aiadc:certificate:revoke";
    private static final Set<String> TEMPLATE_STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> VERSION_STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> SCENE_TYPES = Set.of("COMPETITION_AWARD", "PARTICIPATION", "CUSTOM");
    private static final Set<String> SOURCE_TYPES = Set.of("MANUAL", "IMPORT", "REGISTRATION", "AWARD_RESULT");
    private static final Set<String> RECIPIENT_TYPES = Set.of("USER", "TEAM", "PROJECT", "CUSTOM");
    private static final Set<String> RECORD_STATUS = Set.of("ISSUED", "REVOKED");
    private static final int DEFAULT_WIDTH = 3508;
    private static final int DEFAULT_HEIGHT = 2480;
    private static final String DEFAULT_VARIABLE_SCHEMA = """
            {"variables":[
              {"key":"recipientName","label":"Recipient","type":"text","required":true},
              {"key":"competitionTitle","label":"Competition","type":"text","required":true},
              {"key":"projectName","label":"Project","type":"text","required":false},
              {"key":"teamName","label":"Team","type":"text","required":false},
              {"key":"awardName","label":"Award","type":"text","required":true},
              {"key":"certificateNo","label":"Certificate No","type":"text","required":true},
              {"key":"issueDate","label":"Issue Date","type":"date","required":true},
              {"key":"verificationUrl","label":"Verification URL","type":"qrcode","required":true}
            ]}
            """;
    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_CANVAS_JSON_LENGTH = 20000;
    private static final int MAX_DATA_JSON_LENGTH = 10000;
    private static final int MAX_BATCH_RECORDS = 200;
    private static final int MAX_PUBLIC_TOKEN_LENGTH = 128;
    private static final int MAX_CLIENT_TEXT_LENGTH = 512;
    private static final int MAX_SEARCH_TEXT_LENGTH = 128;
    private static final long MAX_PAGE_NO = 100000L;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FileInternalApi fileInternalApi;
    private final CertificateRenderService renderService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Autowired
    public CertificateAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                fileInternalApi,
                renderService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService
        );
    }

    public CertificateAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.fileInternalApi = fileInternalApi;
        this.renderService = renderService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public CertificateAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, objectMapper, fileInternalApi, renderService, permissionSnapshotService, null, null);
    }

    public CertificateAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService
    ) {
        this(jdbcTemplate, objectMapper, fileInternalApi, renderService, null);
    }

    public PageResponse<CertificateVO.Template> listTemplates(CurrentUser currentUser, String keyword, String status, long pageNo, long pageSize) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeSearchText(keyword, "Template keyword is too large");
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from certificate_template where deleted = 0");
        if (normalizedKeyword != null) {
            where.append(" and (template_code like ? or template_name like ?)");
            String pattern = "%" + normalizedKeyword + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            params.add(normalizeEnum(status, TEMPLATE_STATUS, "Invalid template status"));
        }
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add((normalizedPageNo - 1) * normalizedPageSize);
        queryParams.add(normalizedPageSize);
        List<CertificateVO.Template> records = jdbcTemplate.query(
                templateSelect() + where + " order by updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CertificateVO.Template.class),
                queryParams.toArray()
        );
        return page(records, total, normalizedPageNo, normalizedPageSize);
    }

    @Transactional
    public CertificateVO.Template createTemplate(CurrentUser currentUser, CertificateDTO.TemplateUpsertRequest request) {
        Long userId = requirePermission(currentUser, TEMPLATE_CREATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Template request is required");
        validateTemplateRequest(request);
        String templateCode = StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : generateTemplateCode();
        String sceneType = normalizeEnum(defaultText(request.getSceneType(), "COMPETITION_AWARD"), SCENE_TYPES, "Invalid scene type");
        int templateInserted = jdbcTemplate.update(
                """
                        insert into certificate_template (
                            template_code, template_name, template_type, scene_type, description,
                            latest_version, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, 'CERTIFICATE', ?, ?, 1, 'DRAFT', ?, ?, ?, ?, 0)
                        """,
                templateCode, requiredText(request.getTemplateName(), "Template name is required"), sceneType,
                trimToNull(request.getDescription()), userId, userUuid, userId, userUuid
        );
        requireCertificateWrite(templateInserted, "Certificate template changed, please retry");
        Long templateId = lastInsertId();
        int versionInserted = jdbcTemplate.update(
                """
                        insert into certificate_template_version (
                            template_id, version, page_width, page_height, orientation, unit, dpi,
                            canvas_json, variable_schema_json, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, 1, ?, ?, 'LANDSCAPE', 'PX', 300, ?, ?, 'DRAFT', ?, ?, ?, ?, 0)
                        """,
                templateId, DEFAULT_WIDTH, DEFAULT_HEIGHT, defaultCanvas(), DEFAULT_VARIABLE_SCHEMA, userId, userUuid, userId, userUuid
        );
        requireCertificateWrite(versionInserted, "Certificate template version changed, please retry");
        return getTemplate(currentUser, templateId);
    }

    public CertificateVO.Template getTemplate(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        CertificateVO.Template template = findTemplate(id);
        if (template == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate template not found");
        }
        return template;
    }

    @Transactional
    public CertificateVO.Template updateTemplate(CurrentUser currentUser, Long id, CertificateDTO.TemplateUpsertRequest request) {
        Long userId = requirePermission(currentUser, TEMPLATE_UPDATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Template request is required");
        validateTemplateRequest(request);
        CertificateVO.Template existing = getTemplate(currentUser, id);
        if ("ARCHIVED".equals(existing.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Archived template cannot be edited");
        }
        int updated = jdbcTemplate.update(
                """
                        update certificate_template
                        set template_code = ?, template_name = ?, scene_type = ?, description = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and template_code = ? and status = ? and deleted = 0
                        """,
                StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : existing.getTemplateCode(),
                requiredText(request.getTemplateName(), "Template name is required"),
                normalizeEnum(defaultText(request.getSceneType(), existing.getSceneType()), SCENE_TYPES, "Invalid scene type"),
                trimToNull(request.getDescription()),
                userId, userUuid, LocalDateTime.now(), id, existing.getTemplateCode(), existing.getStatus()
        );
        requireCertificateWrite(updated, "Certificate template changed, please retry");
        return getTemplate(currentUser, id);
    }

    @Transactional
    public CertificateVO.Template duplicateTemplate(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, TEMPLATE_CREATE);
        CertificateVO.Template source = getTemplate(currentUser, id);
        CertificateDTO.TemplateUpsertRequest request = new CertificateDTO.TemplateUpsertRequest();
        request.setTemplateName(source.getTemplateName() + " Copy");
        request.setSceneType(source.getSceneType());
        request.setDescription(source.getDescription());
        CertificateVO.Template duplicated = createTemplate(currentUser, request);
        CertificateVO.TemplateVersion sourceVersion = latestVersion(id);
        saveCanvas(currentUser, latestVersion(duplicated.getId()).getId(), toCanvasRequest(sourceVersion));
        return getTemplate(currentUser, duplicated.getId());
    }

    @Transactional
    public CertificateVO.Template archiveTemplate(CurrentUser currentUser, Long id) {
        Long userId = requirePermission(currentUser, TEMPLATE_DELETE);
        String userUuid = trustedUserUuid(currentUser);
        requirePositiveId(id, "Certificate template id is required");
        CertificateVO.Template existing = getTemplate(currentUser, id);
        int updated = jdbcTemplate.update(
                "update certificate_template set status = 'ARCHIVED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_code = ? and status = ? and deleted = 0",
                userId, userUuid, LocalDateTime.now(), id, existing.getTemplateCode(), existing.getStatus()
        );
        requireCertificateWrite(updated, "Certificate template changed, please retry");
        return getTemplate(currentUser, id);
    }

    public List<CertificateVO.TemplateVersion> listVersions(CurrentUser currentUser, Long templateId) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        requirePositiveId(templateId, "Certificate template id is required");
        return jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                templateId
        );
    }

    public CertificateVO.TemplateVersion getVersion(CurrentUser currentUser, Long versionId) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        CertificateVO.TemplateVersion version = findVersion(versionId);
        if (version == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate template version not found");
        }
        return version;
    }

    @Transactional
    public CertificateVO.TemplateVersion saveCanvas(CurrentUser currentUser, Long versionId, CertificateDTO.CanvasSaveRequest request) {
        Long userId = requirePermission(currentUser, TEMPLATE_UPDATE);
        String userUuid = trustedUserUuid(currentUser);
        requirePositiveId(versionId, "Template version id is required");
        requireRequest(request, "Canvas request is required");
        validateCanvasRequest(request);
        CertificateVO.TemplateVersion version = getVersion(currentUser, versionId);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Published template version cannot be overwritten");
        }
        int updated = jdbcTemplate.update(
                """
                        update certificate_template_version
                        set page_width = ?, page_height = ?, orientation = ?, unit = ?, dpi = ?, canvas_json = ?,
                            variable_schema_json = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0
                        """,
                positive(request.getPageWidth(), DEFAULT_WIDTH), positive(request.getPageHeight(), DEFAULT_HEIGHT),
                defaultText(request.getOrientation(), "LANDSCAPE").toUpperCase(Locale.ROOT),
                defaultText(request.getUnit(), "PX").toUpperCase(Locale.ROOT),
                positive(request.getDpi(), 300),
                requiredText(request.getCanvasJson(), "Canvas JSON is required"),
                defaultText(request.getVariableSchemaJson(), DEFAULT_VARIABLE_SCHEMA),
                userId, userUuid, LocalDateTime.now(), versionId, version.getTemplateId(), version.getVersion()
        );
        requireCertificateWrite(updated, "Certificate template version changed, please retry");
        return getVersion(currentUser, versionId);
    }

    @Transactional
    public CertificateVO.TemplateVersion uploadBackground(CurrentUser currentUser, Long versionId, MultipartFile file) {
        Long userId = requirePermission(currentUser, TEMPLATE_UPDATE);
        String userUuid = trustedUserUuid(currentUser);
        requirePositiveId(versionId, "Template version id is required");
        validateBackgroundFile(file);
        CertificateVO.TemplateVersion version = getVersion(currentUser, versionId);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Published template version cannot be overwritten");
        }
        FileObjectDTO uploaded = fileInternalApi.uploadImageForUser(
                file,
                "certificate-template",
                "certificate background",
                "certificate-template",
                userId,
                trustedUserUuid(currentUser),
                trustedUsername(currentUser)
        );
        int updated = jdbcTemplate.update(
                """
                        update certificate_template_version
                        set background_file_id = ?, background_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0
                """,
                uploaded.id(), firstText(uploaded.publicUrl(), uploaded.previewUrl(), uploaded.downloadUrl()),
                userId, userUuid, LocalDateTime.now(), versionId, version.getTemplateId(), version.getVersion()
        );
        requireCertificateWrite(updated, "Certificate template version changed, please retry");
        return getVersion(currentUser, versionId);
    }

    @Transactional
    public CertificateVO.TemplateVersion publishTemplate(CurrentUser currentUser, Long templateId) {
        Long userId = requirePermission(currentUser, TEMPLATE_PUBLISH);
        String userUuid = trustedUserUuid(currentUser);
        requirePositiveId(templateId, "Certificate template id is required");
        CertificateVO.TemplateVersion draft = latestVersion(templateId);
        if (draft == null) {
            throw biz(ErrorCode.NOT_FOUND, "Template version not found");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Only draft template version can be published");
        }
        int versionUpdated = jdbcTemplate.update(
                "update certificate_template_version set status = 'PUBLISHED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_id = ? and version = ? and status = 'DRAFT' and deleted = 0",
                userId, userUuid, LocalDateTime.now(), draft.getId(), draft.getTemplateId(), draft.getVersion()
        );
        requireCertificateWrite(versionUpdated, "Certificate template version changed, please retry");
        CertificateVO.Template template = getTemplate(currentUser, templateId);
        int templateUpdated = jdbcTemplate.update(
                "update certificate_template set status = 'PUBLISHED', latest_version = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and template_code = ? and status = ? and deleted = 0",
                draft.getVersion(), userId, userUuid, LocalDateTime.now(), templateId, template.getTemplateCode(), template.getStatus()
        );
        requireCertificateWrite(templateUpdated, "Certificate template changed, please retry");
        int nextVersion = draft.getVersion() + 1;
        int draftInserted = jdbcTemplate.update(
                """
                        insert into certificate_template_version (
                            template_id, version, background_file_id, background_url, page_width, page_height,
                            orientation, unit, dpi, canvas_json, variable_schema_json, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, 0)
                        """,
                templateId, nextVersion, draft.getBackgroundFileId(), draft.getBackgroundUrl(),
                draft.getPageWidth(), draft.getPageHeight(), draft.getOrientation(), draft.getUnit(), draft.getDpi(),
                draft.getCanvasJson(), draft.getVariableSchemaJson(), userId, userUuid, userId, userUuid
        );
        requireCertificateWrite(draftInserted, "Certificate template version changed, please retry");
        return draft;
    }

    public CertificateVO.GenerateResult previewBatch(CurrentUser currentUser, CertificateDTO.BatchGenerateRequest request) {
        requirePermission(currentUser, BATCH_CREATE);
        requireRequest(request, "Batch request is required");
        validateBatchRequest(request);
        CertificateVO.GenerateResult result = new CertificateVO.GenerateResult();
        CertificateVO.Batch batch = new CertificateVO.Batch();
        batch.setBatchNo("PREVIEW");
        batch.setBatchName(defaultText(request.getBatchName(), "Preview"));
        batch.setTotalCount(request.getRecords() == null ? 0 : request.getRecords().size());
        batch.setSuccessCount(batch.getTotalCount());
        batch.setFailedCount(0);
        batch.setStatus("PREVIEW");
        result.setBatch(batch);
        result.setRecords(List.of());
        return result;
    }

    @Transactional
    public CertificateVO.GenerateResult generateBatch(CurrentUser currentUser, CertificateDTO.BatchGenerateRequest request) {
        Long userId = requirePermission(currentUser, BATCH_CREATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Batch request is required");
        validateBatchRequest(request);
        CertificateVO.TemplateVersion version = getVersion(currentUser, request.getTemplateVersionId());
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Only published template version can generate certificates");
        }
        String batchNo = "CB-" + LocalDateTime.now().format(BATCH_FORMAT);
        String sourceType = normalizeEnum(defaultText(request.getSourceType(), "MANUAL"), SOURCE_TYPES, "Invalid source type");
        List<CertificateDTO.CertificateDataRequest> rows = request.getRecords() == null ? List.of() : request.getRecords();
        int batchInserted = jdbcTemplate.update(
                """
                        insert into certificate_batch (
                            batch_no, batch_name, template_id, template_version_id, competition_id, stage_id,
                            source_type, total_count, success_count, failed_count, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'GENERATING', ?, ?, ?, ?, 0)
                        """,
                batchNo, defaultText(request.getBatchName(), batchNo), request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(), sourceType, rows.size(), userId, userUuid, userId, userUuid
        );
        requireCertificateWrite(batchInserted, "Certificate batch changed, please retry");
        Long batchId = lastInsertId();
        List<CertificateVO.Record> created = new ArrayList<>();
        int success = 0;
        int failed = 0;
        String errorMessage = null;
        for (CertificateDTO.CertificateDataRequest row : rows) {
            try {
                Long recordId = createCertificateRecord(userId, userUuid, batchId, request, version, row, batchNo);
                created.add(findRecord(recordId));
                success += 1;
            } catch (RuntimeException exception) {
                failed += 1;
                errorMessage = exception.getMessage();
            }
        }
        int batchUpdated = jdbcTemplate.update(
                """
                        update certificate_batch
                        set success_count = ?, failed_count = ?, status = ?, error_message = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and created_by = ? and created_by_uuid = ? and deleted = 0
                        """,
                success,
                failed,
                failed == 0 ? "COMPLETED" : "FAILED",
                errorMessage,
                userId,
                userUuid,
                LocalDateTime.now(),
                batchId,
                userId,
                userUuid
        );
        requireCertificateWrite(batchUpdated, "Certificate batch changed, please retry");
        CertificateVO.GenerateResult result = new CertificateVO.GenerateResult();
        result.setBatch(getBatch(currentUser, batchId));
        result.setRecords(created);
        return result;
    }

    public PageResponse<CertificateVO.Batch> listBatches(CurrentUser currentUser, long pageNo, long pageSize) {
        requirePermission(currentUser, BATCH_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        List<Object> params = new ArrayList<>();
        String where = " from certificate_batch where deleted = 0" + ownerFilter(currentUser, "certificate_batch", params);
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add((normalizedPageNo - 1) * normalizedPageSize);
        queryParams.add(normalizedPageSize);
        List<CertificateVO.Batch> records = jdbcTemplate.query(
                batchSelect() + where + " order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CertificateVO.Batch.class),
                queryParams.toArray()
        );
        return page(records, total, normalizedPageNo, normalizedPageSize);
    }

    public CertificateVO.Batch getBatch(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, BATCH_VIEW);
        requirePositiveId(id, "Certificate batch id is required");
        List<Object> params = new ArrayList<>();
        params.add(id);
        String where = " from certificate_batch where id = ? and deleted = 0" + ownerFilter(currentUser, "certificate_batch", params);
        List<CertificateVO.Batch> records = jdbcTemplate.query(
                batchSelect() + where,
                new BeanPropertyRowMapper<>(CertificateVO.Batch.class),
                params.toArray()
        );
        if (records.isEmpty()) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate batch not found");
        }
        return records.get(0);
    }

    public PageResponse<CertificateVO.Record> listRecords(
            CurrentUser currentUser,
            String certificateNo,
            String recipientName,
            String status,
            long pageNo,
            long pageSize
    ) {
        requirePermission(currentUser, CERTIFICATE_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        String normalizedCertificateNo = normalizeSearchText(certificateNo, "Certificate no is too large");
        String normalizedRecipientName = normalizeSearchText(recipientName, "Recipient name is too large");
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from certificate_record r left join certificate_template t on r.template_id = t.id where r.deleted = 0");
        where.append(ownerFilter(currentUser, "r", params));
        if (normalizedCertificateNo != null) {
            where.append(" and r.certificate_no like ?");
            params.add("%" + normalizedCertificateNo + "%");
        }
        if (normalizedRecipientName != null) {
            where.append(" and r.recipient_name like ?");
            params.add("%" + normalizedRecipientName + "%");
        }
        if (StringUtils.hasText(status)) {
            where.append(" and r.status = ?");
            params.add(normalizeEnum(status, RECORD_STATUS, "Invalid certificate status"));
        }
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add((normalizedPageNo - 1) * normalizedPageSize);
        queryParams.add(normalizedPageSize);
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + where + " order by r.created_at desc, r.id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                queryParams.toArray()
        );
        return page(records, total, normalizedPageNo, normalizedPageSize);
    }

    public CertificateVO.Record getRecord(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, CERTIFICATE_VIEW);
        CertificateVO.Record record = findRecord(currentUser, id);
        if (record == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate not found");
        }
        return record;
    }

    public CertificateVO.Record getRecordForDownload(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, CERTIFICATE_DOWNLOAD);
        CertificateVO.Record record = findRecord(currentUser, id);
        if (record == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate not found");
        }
        return record;
    }

    @Transactional
    public CertificateVO.Record revokeRecord(CurrentUser currentUser, Long id, String reason) {
        Long userId = requirePermission(currentUser, CERTIFICATE_REVOKE);
        String userUuid = trustedUserUuid(currentUser);
        CertificateVO.Record record = getRecord(currentUser, id);
        List<Object> params = new ArrayList<>();
        params.add(trimToNull(reason));
        params.add(LocalDateTime.now());
        params.add(userId);
        params.add(userUuid);
        params.add(LocalDateTime.now());
        params.add(id);
        params.add(record.getCertificateNo());
        params.add(record.getBatchId());
        params.add(record.getStatus());
        String ownerClause = ownerFilter(currentUser, "certificate_record", params);
        int updated = jdbcTemplate.update(
                """
                        update certificate_record
                        set status = 'REVOKED', revoked_reason = ?, revoked_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and certificate_no = ? and batch_id = ? and status = ? and deleted = 0
                """ + ownerClause,
                params.toArray()
        );
        requireCertificateWrite(updated, "Certificate record changed, please retry");
        return getRecord(currentUser, id);
    }

    @Transactional
    public CertificateVO.Record regenerateRecord(CurrentUser currentUser, Long id) {
        Long userId = requirePermission(currentUser, CERTIFICATE_REGENERATE);
        String userUuid = trustedUserUuid(currentUser);
        CertificateVO.Record record = getRecord(currentUser, id);
        CertificateVO.TemplateVersion version = getVersion(currentUser, record.getTemplateVersionId());
        Map<String, Object> data = parseData(record.getDataJson());
        String fileUrl = render(record.getCertificateNo(), record.getBatchId(), version, data);
        List<Object> params = new ArrayList<>();
        params.add(fileUrl);
        params.add(userId);
        params.add(userUuid);
        params.add(LocalDateTime.now());
        params.add(id);
        params.add(record.getCertificateNo());
        params.add(record.getBatchId());
        params.add(record.getStatus());
        String ownerClause = ownerFilter(currentUser, "certificate_record", params);
        int updated = jdbcTemplate.update(
                "update certificate_record set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = ?" + ownerClause,
                params.toArray()
        );
        requireCertificateWrite(updated, "Certificate record changed, please retry");
        return getRecord(currentUser, id);
    }

    public CertificateVO.PublicVerifyResult verifyByToken(String publicToken, String clientIp, String userAgent) {
        String normalizedToken = requiredPublicText(publicToken, MAX_PUBLIC_TOKEN_LENGTH, "Public token is invalid");
        String normalizedClientIp = publicLogText(clientIp);
        String normalizedUserAgent = publicLogText(userAgent);
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.public_token = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                normalizedToken
        );
        if (records.isEmpty()) {
            logVerify(null, null, "TOKEN", "NOT_FOUND", normalizedClientIp, normalizedUserAgent);
            return publicResult("NOT_FOUND", null);
        }
        CertificateVO.Record record = records.get(0);
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "TOKEN", result, normalizedClientIp, normalizedUserAgent);
        return publicResult(result, record);
    }

    public CertificateVO.PublicVerifyResult verifyByCertificateNo(String certificateNo, String verificationCode, String clientIp, String userAgent) {
        String normalizedCertificateNo = requiredPublicText(certificateNo, MAX_CODE_LENGTH, "Certificate no is invalid");
        String normalizedVerificationCode = requiredPublicText(verificationCode, 16, "Verification code is invalid");
        String normalizedClientIp = publicLogText(clientIp);
        String normalizedUserAgent = publicLogText(userAgent);
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.certificate_no = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                normalizedCertificateNo
        );
        if (records.isEmpty()) {
            logVerify(null, normalizedCertificateNo, "CERT_NO", "NOT_FOUND", normalizedClientIp, normalizedUserAgent);
            return publicResult("NOT_FOUND", null);
        }
        CertificateVO.Record record = records.get(0);
        if (!normalizedVerificationCode.equals(record.getVerificationCode())) {
            logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", "INVALID_CODE", normalizedClientIp, normalizedUserAgent);
            return publicResult("INVALID_CODE", null);
        }
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", result, normalizedClientIp, normalizedUserAgent);
        return publicResult(result, record);
    }

    private Long createCertificateRecord(
            Long userId,
            String userUuid,
            Long batchId,
            CertificateDTO.BatchGenerateRequest request,
            CertificateVO.TemplateVersion version,
            CertificateDTO.CertificateDataRequest row,
            String batchNo
    ) {
        String certificateNo = generateCertificateNo();
        String publicToken = UUID.randomUUID().toString().replace("-", "");
        String verificationCode = randomDigits(6);
        LocalDate issueDate = row.getIssueDate() == null ? LocalDate.now() : row.getIssueDate();
        Map<String, Object> data = new LinkedHashMap<>();
        if (row.getData() != null) {
            requireJsonSize(row.getData(), "Certificate data is too large");
            data.putAll(row.getData());
        }
        data.put("recipientName", row.getRecipientName());
        data.put("competitionTitle", firstText(row.getCompetitionTitle(), ""));
        data.put("projectName", firstText(row.getProjectName(), ""));
        data.put("teamName", firstText(row.getTeamName(), ""));
        data.put("awardName", firstText(row.getAwardName(), ""));
        data.put("certificateNo", certificateNo);
        data.put("issueDate", issueDate.toString());
        data.put("verificationCode", verificationCode);
        data.put("verificationUrl", "/certificate/verify/" + publicToken);
        String dataJson = toJson(data);
        int inserted = jdbcTemplate.update(
                """
                        insert into certificate_record (
                            certificate_no, verification_code, public_token, batch_id, template_id, template_version_id,
                            competition_id, stage_id, recipient_name, recipient_type, competition_title, project_name, team_name, award_name,
                            issue_date, expire_date, data_json, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?, ?, ?, 0)
                        """,
                certificateNo, verificationCode, publicToken, batchId, request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(), requiredText(row.getRecipientName(), "Recipient name is required"),
                normalizeEnum(defaultText(row.getRecipientType(), "CUSTOM"), RECIPIENT_TYPES, "Invalid recipient type"),
                trimToNull(row.getCompetitionTitle()), trimToNull(row.getProjectName()), trimToNull(row.getTeamName()), trimToNull(row.getAwardName()),
                issueDate, row.getExpireDate(), dataJson, userId, userUuid, userId, userUuid
        );
        requireCertificateWrite(inserted, "Certificate record changed, please retry");
        Long recordId = lastInsertId();
        String fileUrl = render(certificateNo, batchId, version, data);
        int updated = jdbcTemplate.update(
                """
                        update certificate_record
                        set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and certificate_no = ? and batch_id = ? and status = 'ISSUED'
                          and created_by = ? and created_by_uuid = ? and deleted = 0
                        """,
                fileUrl, userId, userUuid, LocalDateTime.now(), recordId, certificateNo, batchId, userId, userUuid
        );
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Certificate record changed, please retry");
        }
        return recordId;
    }

    private String render(String certificateNo, Long batchId, CertificateVO.TemplateVersion version, Map<String, Object> data) {
        Path output = Path.of("storage", "certificates", String.valueOf(batchId), certificateNo + ".png");
        renderService.renderPng(version.getCanvasJson(), version.getBackgroundUrl(), data, output);
        return "/" + output.toString().replace('\\', '/');
    }

    private CertificateVO.Template findTemplate(Long id) {
        requirePositiveId(id, "Certificate template id is required");
        List<CertificateVO.Template> records = jdbcTemplate.query(
                templateSelect() + " from certificate_template where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Template.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.TemplateVersion findVersion(Long versionId) {
        requirePositiveId(versionId, "Template version id is required");
        List<CertificateVO.TemplateVersion> records = jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                versionId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.TemplateVersion latestVersion(Long templateId) {
        requirePositiveId(templateId, "Certificate template id is required");
        List<CertificateVO.TemplateVersion> records = jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                templateId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.Record findRecord(Long id) {
        requirePositiveId(id, "Certificate id is required");
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.Record findRecord(CurrentUser currentUser, Long id) {
        requirePositiveId(id, "Certificate id is required");
        List<Object> params = new ArrayList<>();
        params.add(id);
        String where = " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0"
                + ownerFilter(currentUser, "r", params)
                + " limit 1";
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + where,
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                params.toArray()
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private void logVerify(Long certificateId, String certificateNo, String queryType, String queryResult, String clientIp, String userAgent) {
        jdbcTemplate.update(
                """
                        insert into certificate_verify_log (
                            certificate_id, certificate_no, query_type, query_result, client_ip, user_agent
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                certificateId, certificateNo, queryType, queryResult, trimToNull(clientIp), trimToNull(userAgent)
        );
    }

    private String resolvePublicResult(CertificateVO.Record record) {
        if ("REVOKED".equals(record.getStatus())) {
            return "REVOKED";
        }
        if (record.getExpireDate() != null && record.getExpireDate().isBefore(LocalDate.now())) {
            return "EXPIRED";
        }
        return "VALID";
    }

    private CertificateVO.PublicVerifyResult publicResult(String result, CertificateVO.Record record) {
        CertificateVO.PublicVerifyResult response = new CertificateVO.PublicVerifyResult();
        response.setResult(result);
        if (record == null || "NOT_FOUND".equals(result) || "INVALID_CODE".equals(result)) {
            return response;
        }
        response.setCertificateNo(record.getCertificateNo());
        response.setRecipientName(maskName(record.getRecipientName()));
        response.setCompetitionTitle(record.getCompetitionTitle());
        response.setProjectName(record.getProjectName());
        response.setAwardName(record.getAwardName());
        response.setIssueDate(record.getIssueDate());
        response.setOrganizer("Lumira");
        response.setStatus(record.getStatus());
        response.setCertificateFileUrl(record.getCertificateFileUrl());
        response.setSafeData(Map.of(
                "teamName", firstText(record.getTeamName(), ""),
                "projectName", firstText(record.getProjectName(), "")
        ));
        return response;
    }

    private String templateSelect() {
        return "select id, template_code, template_name, template_type, scene_type, description, latest_version, status, created_at, updated_at";
    }

    private String versionSelect() {
        return "select id, template_id, version, background_file_id, background_url, page_width, page_height, orientation, unit, dpi, canvas_json, variable_schema_json, preview_file_id, status, created_at, updated_at";
    }

    private String batchSelect() {
        return "select id, batch_no, batch_name, template_id, template_version_id, competition_id, stage_id, source_type, total_count, success_count, failed_count, status, error_message, created_at, updated_at";
    }

    private String recordSelect() {
        return """
                select r.id, r.certificate_no, r.verification_code, r.public_token, r.batch_id, r.template_id, r.template_version_id,
                       t.template_name, r.competition_id, r.recipient_name, r.recipient_type, r.competition_title, r.project_name,
                       r.team_name, r.award_name, r.issue_date, r.expire_date, r.data_json, r.certificate_file_url,
                       r.status, r.revoked_reason, r.revoked_at, r.created_at, r.updated_at
                """;
    }

    private CertificateDTO.CanvasSaveRequest toCanvasRequest(CertificateVO.TemplateVersion version) {
        CertificateDTO.CanvasSaveRequest request = new CertificateDTO.CanvasSaveRequest();
        request.setPageWidth(version.getPageWidth());
        request.setPageHeight(version.getPageHeight());
        request.setOrientation(version.getOrientation());
        request.setUnit(version.getUnit());
        request.setDpi(version.getDpi());
        request.setCanvasJson(version.getCanvasJson());
        request.setVariableSchemaJson(version.getVariableSchemaJson());
        return request;
    }

    private String defaultCanvas() {
        return """
                {"page":{"width":3508,"height":2480,"dpi":300,"orientation":"LANDSCAPE"},"elements":[
                  {"id":"el_name","type":"text","fieldKey":"recipientName","x":1200,"y":920,"width":1100,"height":120,"fontFamily":"Microsoft YaHei","fontSize":72,"fontWeight":"bold","color":"#222222","textAlign":"center","placeholder":"${recipientName}"},
                  {"id":"el_award","type":"text","fieldKey":"awardName","x":1200,"y":1200,"width":1100,"height":100,"fontFamily":"Microsoft YaHei","fontSize":56,"fontWeight":"normal","color":"#222222","textAlign":"center","placeholder":"${awardName}"},
                  {"id":"el_qr","type":"qrcode","fieldKey":"verificationUrl","x":2920,"y":1900,"width":220,"height":220}
                ]}
                """;
    }


    private Long requireUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        Long userId = requireUserId(currentUser);
        if (!hasPermission(currentUser, permissionKey)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return userId;
    }

    private String trustedUsername(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getUserUuid().trim();
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    private String ownerFilter(CurrentUser currentUser, String alias, List<Object> params) {
        Long userId = requireUserId(currentUser);
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions.contains("*")) {
            return "";
        }
        params.add(userId);
        params.add(trustedUserUuid(currentUser));
        return " and " + alias + ".created_by = ? and " + alias + ".created_by_uuid = ?";
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
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
                    "User context is required"
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
            throw biz(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw biz(ErrorCode.UNAUTHORIZED, "User context is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw biz(ErrorCode.UNAUTHORIZED, "User context is required");
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

    private Long lastInsertId() {
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    private void requireCertificateWrite(int updated, String message) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private String generateTemplateCode() {
        return "CTPL-" + LocalDateTime.now().format(BATCH_FORMAT);
    }

    private String generateCertificateNo() {
        String prefix = "CERT-" + Year.now().getValue() + "-";
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from certificate_record where certificate_no like ?",
                Long.class,
                prefix + "%"
        );
        return prefix + String.format("%06d", (count == null ? 0 : count) + 1);
    }

    private String randomDigits(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i += 1) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private String normalizeEnum(String value, Set<String> allowed, String message) {
        String normalized = requiredText(value, message).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private void validateTemplateRequest(CertificateDTO.TemplateUpsertRequest request) {
        requireLength(request.getTemplateCode(), MAX_CODE_LENGTH, "Template code is too large");
        requireLength(requiredText(request.getTemplateName(), "Template name is required"), MAX_NAME_LENGTH, "Template name is too large");
        requireLength(request.getSceneType(), 32, "Scene type is too large");
        requireLength(request.getDescription(), MAX_DESCRIPTION_LENGTH, "Template description is too large");
    }

    private void validateCanvasRequest(CertificateDTO.CanvasSaveRequest request) {
        int width = positive(request.getPageWidth(), DEFAULT_WIDTH);
        int height = positive(request.getPageHeight(), DEFAULT_HEIGHT);
        if (width > 10000 || height > 10000) {
            throw biz(ErrorCode.BAD_REQUEST, "Canvas size is too large");
        }
        requireLength(request.getOrientation(), 16, "Canvas orientation is too large");
        requireLength(request.getUnit(), 16, "Canvas unit is too large");
        requireLength(requiredText(request.getCanvasJson(), "Canvas JSON is required"), MAX_CANVAS_JSON_LENGTH, "Canvas JSON is too large");
        requireLength(request.getVariableSchemaJson(), MAX_DATA_JSON_LENGTH, "Variable schema JSON is too large");
    }

    private void validateBatchRequest(CertificateDTO.BatchGenerateRequest request) {
        requirePositiveId(request.getTemplateId(), "Template id is required");
        requirePositiveId(request.getTemplateVersionId(), "Template version id is required");
        if (request.getCompetitionId() != null && request.getCompetitionId() <= 0) {
            throw biz(ErrorCode.BAD_REQUEST, "Competition id is invalid");
        }
        if (request.getStageId() != null && request.getStageId() <= 0) {
            throw biz(ErrorCode.BAD_REQUEST, "Stage id is invalid");
        }
        requireLength(request.getBatchName(), MAX_NAME_LENGTH, "Batch name is too large");
        requireLength(request.getSourceType(), 32, "Source type is too large");
        List<CertificateDTO.CertificateDataRequest> records = request.getRecords();
        if (records == null || records.isEmpty()) {
            throw biz(ErrorCode.BAD_REQUEST, "Certificate records are required");
        }
        if (records.size() > MAX_BATCH_RECORDS) {
            throw biz(ErrorCode.BAD_REQUEST, "Too many certificate records");
        }
        for (CertificateDTO.CertificateDataRequest row : records) {
            requireLength(requiredText(row.getRecipientName(), "Recipient name is required"), MAX_NAME_LENGTH, "Recipient name is too large");
            requireLength(row.getRecipientType(), 32, "Recipient type is too large");
            requireLength(row.getCompetitionTitle(), MAX_NAME_LENGTH, "Competition title is too large");
            requireLength(row.getProjectName(), MAX_NAME_LENGTH, "Project name is too large");
            requireLength(row.getTeamName(), MAX_NAME_LENGTH, "Team name is too large");
            requireLength(row.getAwardName(), MAX_NAME_LENGTH, "Award name is too large");
            requireJsonSize(row.getData(), "Certificate data is too large");
        }
    }

    private void validateBackgroundFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw biz(ErrorCode.BAD_REQUEST, "Background image is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw biz(ErrorCode.BAD_REQUEST, "Background image is too large");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw biz(ErrorCode.BAD_REQUEST, "Background image type is invalid");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
    }

    private void requireLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
    }

    private void requireJsonSize(Object value, String message) {
        if (value == null) {
            return;
        }
        try {
            if (objectMapper.writeValueAsString(value).length() > MAX_DATA_JSON_LENGTH) {
                throw biz(ErrorCode.BAD_REQUEST, message);
            }
        } catch (BizException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requiredPublicText(String value, int maxLength, String message) {
        String normalized = requiredText(value, message);
        if (normalized.length() > maxLength) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String publicLogText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > MAX_CLIENT_TEXT_LENGTH ? normalized.substring(0, MAX_CLIENT_TEXT_LENGTH) : normalized;
    }

    private String normalizeSearchText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > MAX_SEARCH_TEXT_LENGTH) {
            throw biz(ErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private long normalizePageNo(long pageNo) {
        if (pageNo < 1L || pageNo > MAX_PAGE_NO) {
            throw biz(ErrorCode.BAD_REQUEST, "Page number is invalid");
        }
        return pageNo;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L || pageSize > 100L) {
            throw biz(ErrorCode.BAD_REQUEST, "Page size is invalid");
        }
        return pageSize;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize certificate data", exception);
        }
    }

    private Map<String, Object> parseData(String json) {
        try {
            return objectMapper.readValue(defaultText(json, "{}"), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String maskName(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 2) {
            return value;
        }
        return value.charAt(0) + "*" + value.substring(value.length() - 1);
    }

    private <T> PageResponse<T> page(List<T> records, Long total, long pageNo, long pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setHasMore(pageNo * pageSize < response.getTotal());
        return response;
    }

    private BizException biz(ErrorCode code, String message) {
        return new BizException(code, message);
    }
}
