package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.vo.CertificateVO;
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
    private static final Set<String> TEMPLATE_STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> VERSION_STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> SCENE_TYPES = Set.of("COMPETITION_AWARD", "PARTICIPATION", "CUSTOM");
    private static final Set<String> SOURCE_TYPES = Set.of("MANUAL", "IMPORT", "REGISTRATION", "AWARD_RESULT");
    private static final Set<String> RECIPIENT_TYPES = Set.of("USER", "TEAM", "PROJECT", "CUSTOM");
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FileInternalApi fileInternalApi;
    private final CertificateRenderService renderService;

    public CertificateAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.fileInternalApi = fileInternalApi;
        this.renderService = renderService;
    }

    public PageResponse<CertificateVO.Template> listTemplates(CurrentUser currentUser, String keyword, String status, long pageNo, long pageSize) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from certificate_template where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (template_code like ? or template_name like ?)");
            String pattern = "%" + keyword.trim() + "%";
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
        Long userId = requireUserId(currentUser);
        String templateCode = StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : generateTemplateCode();
        String sceneType = normalizeEnum(defaultText(request.getSceneType(), "COMPETITION_AWARD"), SCENE_TYPES, "Invalid scene type");
        jdbcTemplate.update(
                """
                        insert into certificate_template (
                            template_code, template_name, template_type, scene_type, description,
                            latest_version, status, created_by, updated_by, deleted
                        ) values (?, ?, 'CERTIFICATE', ?, ?, 1, 'DRAFT', ?, ?, 0)
                        """,
                templateCode, requiredText(request.getTemplateName(), "Template name is required"), sceneType,
                trimToNull(request.getDescription()), userId, userId
        );
        Long templateId = lastInsertId();
        jdbcTemplate.update(
                """
                        insert into certificate_template_version (
                            template_id, version, page_width, page_height, orientation, unit, dpi,
                            canvas_json, variable_schema_json, status, created_by, updated_by, deleted
                        ) values (?, 1, ?, ?, 'LANDSCAPE', 'PX', 300, ?, ?, 'DRAFT', ?, ?, 0)
                        """,
                templateId, DEFAULT_WIDTH, DEFAULT_HEIGHT, defaultCanvas(), DEFAULT_VARIABLE_SCHEMA, userId, userId
        );
        return getTemplate(currentUser, templateId);
    }

    public CertificateVO.Template getTemplate(CurrentUser currentUser, Long id) {
        CertificateVO.Template template = findTemplate(id);
        if (template == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate template not found");
        }
        return template;
    }

    @Transactional
    public CertificateVO.Template updateTemplate(CurrentUser currentUser, Long id, CertificateDTO.TemplateUpsertRequest request) {
        CertificateVO.Template existing = getTemplate(currentUser, id);
        if ("ARCHIVED".equals(existing.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Archived template cannot be edited");
        }
        jdbcTemplate.update(
                """
                        update certificate_template
                        set template_code = ?, template_name = ?, scene_type = ?, description = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : existing.getTemplateCode(),
                requiredText(request.getTemplateName(), "Template name is required"),
                normalizeEnum(defaultText(request.getSceneType(), existing.getSceneType()), SCENE_TYPES, "Invalid scene type"),
                trimToNull(request.getDescription()),
                requireUserId(currentUser), LocalDateTime.now(), id
        );
        return getTemplate(currentUser, id);
    }

    @Transactional
    public CertificateVO.Template duplicateTemplate(CurrentUser currentUser, Long id) {
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
        jdbcTemplate.update(
                "update certificate_template set status = 'ARCHIVED', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                requireUserId(currentUser), LocalDateTime.now(), id
        );
        return getTemplate(currentUser, id);
    }

    public List<CertificateVO.TemplateVersion> listVersions(CurrentUser currentUser, Long templateId) {
        return jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                templateId
        );
    }

    public CertificateVO.TemplateVersion getVersion(CurrentUser currentUser, Long versionId) {
        CertificateVO.TemplateVersion version = findVersion(versionId);
        if (version == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate template version not found");
        }
        return version;
    }

    @Transactional
    public CertificateVO.TemplateVersion saveCanvas(CurrentUser currentUser, Long versionId, CertificateDTO.CanvasSaveRequest request) {
        CertificateVO.TemplateVersion version = getVersion(currentUser, versionId);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Published template version cannot be overwritten");
        }
        jdbcTemplate.update(
                """
                        update certificate_template_version
                        set page_width = ?, page_height = ?, orientation = ?, unit = ?, dpi = ?, canvas_json = ?,
                            variable_schema_json = ?, updated_by = ?, updated_at = ?
                        where id = ? and status = 'DRAFT' and deleted = 0
                        """,
                positive(request.getPageWidth(), DEFAULT_WIDTH), positive(request.getPageHeight(), DEFAULT_HEIGHT),
                defaultText(request.getOrientation(), "LANDSCAPE").toUpperCase(Locale.ROOT),
                defaultText(request.getUnit(), "PX").toUpperCase(Locale.ROOT),
                positive(request.getDpi(), 300),
                requiredText(request.getCanvasJson(), "Canvas JSON is required"),
                defaultText(request.getVariableSchemaJson(), DEFAULT_VARIABLE_SCHEMA),
                requireUserId(currentUser), LocalDateTime.now(), versionId
        );
        return getVersion(currentUser, versionId);
    }

    @Transactional
    public CertificateVO.TemplateVersion uploadBackground(CurrentUser currentUser, Long versionId, MultipartFile file) {
        CertificateVO.TemplateVersion version = getVersion(currentUser, versionId);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Published template version cannot be overwritten");
        }
        FileObjectDTO uploaded = fileInternalApi.uploadImage(file, "certificate-template", "certificate background", "certificate-template");
        jdbcTemplate.update(
                """
                        update certificate_template_version
                        set background_file_id = ?, background_url = ?, updated_by = ?, updated_at = ?
                        where id = ? and status = 'DRAFT' and deleted = 0
                """,
                uploaded.id(), firstText(uploaded.publicUrl(), uploaded.previewUrl(), uploaded.downloadUrl()),
                requireUserId(currentUser), LocalDateTime.now(), versionId
        );
        return getVersion(currentUser, versionId);
    }

    @Transactional
    public CertificateVO.TemplateVersion publishTemplate(CurrentUser currentUser, Long templateId) {
        Long userId = requireUserId(currentUser);
        CertificateVO.TemplateVersion draft = latestVersion(templateId);
        if (draft == null) {
            throw biz(ErrorCode.NOT_FOUND, "Template version not found");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Only draft template version can be published");
        }
        jdbcTemplate.update(
                "update certificate_template_version set status = 'PUBLISHED', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                userId, LocalDateTime.now(), draft.getId()
        );
        jdbcTemplate.update(
                "update certificate_template set status = 'PUBLISHED', latest_version = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                draft.getVersion(), userId, LocalDateTime.now(), templateId
        );
        int nextVersion = draft.getVersion() + 1;
        jdbcTemplate.update(
                """
                        insert into certificate_template_version (
                            template_id, version, background_file_id, background_url, page_width, page_height,
                            orientation, unit, dpi, canvas_json, variable_schema_json, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, 0)
                        """,
                templateId, nextVersion, draft.getBackgroundFileId(), draft.getBackgroundUrl(),
                draft.getPageWidth(), draft.getPageHeight(), draft.getOrientation(), draft.getUnit(), draft.getDpi(),
                draft.getCanvasJson(), draft.getVariableSchemaJson(), userId, userId
        );
        return draft;
    }

    public CertificateVO.GenerateResult previewBatch(CurrentUser currentUser, CertificateDTO.BatchGenerateRequest request) {
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
        Long userId = requireUserId(currentUser);
        CertificateVO.TemplateVersion version = getVersion(currentUser, request.getTemplateVersionId());
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Only published template version can generate certificates");
        }
        String batchNo = "CB-" + LocalDateTime.now().format(BATCH_FORMAT);
        String sourceType = normalizeEnum(defaultText(request.getSourceType(), "MANUAL"), SOURCE_TYPES, "Invalid source type");
        List<CertificateDTO.CertificateDataRequest> rows = request.getRecords() == null ? List.of() : request.getRecords();
        jdbcTemplate.update(
                """
                        insert into certificate_batch (
                            batch_no, batch_name, template_id, template_version_id, competition_id, stage_id,
                            source_type, total_count, success_count, failed_count, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'GENERATING', ?, ?, 0)
                        """,
                batchNo, defaultText(request.getBatchName(), batchNo), request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(), sourceType, rows.size(), userId, userId
        );
        Long batchId = lastInsertId();
        List<CertificateVO.Record> created = new ArrayList<>();
        int success = 0;
        int failed = 0;
        String errorMessage = null;
        for (CertificateDTO.CertificateDataRequest row : rows) {
            try {
                Long recordId = createCertificateRecord(userId, batchId, request, version, row, batchNo);
                created.add(findRecord(recordId));
                success += 1;
            } catch (RuntimeException exception) {
                failed += 1;
                errorMessage = exception.getMessage();
            }
        }
        jdbcTemplate.update(
                "update certificate_batch set success_count = ?, failed_count = ?, status = ?, error_message = ?, updated_by = ?, updated_at = ? where id = ?",
                success, failed, failed == 0 ? "COMPLETED" : "FAILED", errorMessage, userId, LocalDateTime.now(), batchId
        );
        CertificateVO.GenerateResult result = new CertificateVO.GenerateResult();
        result.setBatch(getBatch(currentUser, batchId));
        result.setRecords(created);
        return result;
    }

    public PageResponse<CertificateVO.Batch> listBatches(CurrentUser currentUser, long pageNo, long pageSize) {
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        Long total = jdbcTemplate.queryForObject("select count(1) from certificate_batch where deleted = 0", Long.class);
        List<CertificateVO.Batch> records = jdbcTemplate.query(
                batchSelect() + " from certificate_batch where deleted = 0 order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CertificateVO.Batch.class),
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize
        );
        return page(records, total, normalizedPageNo, normalizedPageSize);
    }

    public CertificateVO.Batch getBatch(CurrentUser currentUser, Long id) {
        List<CertificateVO.Batch> records = jdbcTemplate.query(
                batchSelect() + " from certificate_batch where id = ? and deleted = 0",
                new BeanPropertyRowMapper<>(CertificateVO.Batch.class),
                id
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
        long normalizedPageNo = Math.max(1L, pageNo);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from certificate_record r left join certificate_template t on r.template_id = t.id where r.deleted = 0");
        if (StringUtils.hasText(certificateNo)) {
            where.append(" and r.certificate_no like ?");
            params.add("%" + certificateNo.trim() + "%");
        }
        if (StringUtils.hasText(recipientName)) {
            where.append(" and r.recipient_name like ?");
            params.add("%" + recipientName.trim() + "%");
        }
        if (StringUtils.hasText(status)) {
            where.append(" and r.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
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
        CertificateVO.Record record = findRecord(id);
        if (record == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate not found");
        }
        return record;
    }

    @Transactional
    public CertificateVO.Record revokeRecord(CurrentUser currentUser, Long id, String reason) {
        jdbcTemplate.update(
                """
                        update certificate_record
                        set status = 'REVOKED', revoked_reason = ?, revoked_at = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                trimToNull(reason), LocalDateTime.now(), requireUserId(currentUser), LocalDateTime.now(), id
        );
        return getRecord(currentUser, id);
    }

    @Transactional
    public CertificateVO.Record regenerateRecord(CurrentUser currentUser, Long id) {
        CertificateVO.Record record = getRecord(currentUser, id);
        CertificateVO.TemplateVersion version = getVersion(currentUser, record.getTemplateVersionId());
        Map<String, Object> data = parseData(record.getDataJson());
        String fileUrl = render(record.getCertificateNo(), record.getBatchId(), version, data);
        jdbcTemplate.update(
                "update certificate_record set certificate_file_url = ?, updated_by = ?, updated_at = ? where id = ?",
                fileUrl, requireUserId(currentUser), LocalDateTime.now(), id
        );
        return getRecord(currentUser, id);
    }

    public CertificateVO.PublicVerifyResult verifyByToken(String publicToken, String clientIp, String userAgent) {
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.public_token = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                publicToken
        );
        if (records.isEmpty()) {
            logVerify(null, null, "TOKEN", "NOT_FOUND", clientIp, userAgent);
            return publicResult("NOT_FOUND", null);
        }
        CertificateVO.Record record = records.get(0);
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "TOKEN", result, clientIp, userAgent);
        return publicResult(result, record);
    }

    public CertificateVO.PublicVerifyResult verifyByCertificateNo(String certificateNo, String verificationCode, String clientIp, String userAgent) {
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.certificate_no = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                certificateNo
        );
        if (records.isEmpty()) {
            logVerify(null, certificateNo, "CERT_NO", "NOT_FOUND", clientIp, userAgent);
            return publicResult("NOT_FOUND", null);
        }
        CertificateVO.Record record = records.get(0);
        if (!StringUtils.hasText(verificationCode) || !verificationCode.equals(record.getVerificationCode())) {
            logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", "INVALID_CODE", clientIp, userAgent);
            return publicResult("INVALID_CODE", null);
        }
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", result, clientIp, userAgent);
        return publicResult(result, record);
    }

    private Long createCertificateRecord(
            Long userId,
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
        jdbcTemplate.update(
                """
                        insert into certificate_record (
                            certificate_no, verification_code, public_token, batch_id, template_id, template_version_id,
                            competition_id, stage_id, recipient_name, recipient_type, competition_title, project_name, team_name, award_name,
                            issue_date, expire_date, data_json, status, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?, 0)
                        """,
                certificateNo, verificationCode, publicToken, batchId, request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(), requiredText(row.getRecipientName(), "Recipient name is required"),
                normalizeEnum(defaultText(row.getRecipientType(), "CUSTOM"), RECIPIENT_TYPES, "Invalid recipient type"),
                trimToNull(row.getCompetitionTitle()), trimToNull(row.getProjectName()), trimToNull(row.getTeamName()), trimToNull(row.getAwardName()),
                issueDate, row.getExpireDate(), dataJson, userId, userId
        );
        Long recordId = lastInsertId();
        String fileUrl = render(certificateNo, batchId, version, data);
        jdbcTemplate.update(
                "update certificate_record set certificate_file_url = ?, updated_by = ?, updated_at = ? where id = ?",
                fileUrl, userId, LocalDateTime.now(), recordId
        );
        return recordId;
    }

    private String render(String certificateNo, Long batchId, CertificateVO.TemplateVersion version, Map<String, Object> data) {
        Path output = Path.of("storage", "certificates", String.valueOf(batchId), certificateNo + ".png");
        renderService.renderPng(version.getCanvasJson(), version.getBackgroundUrl(), data, output);
        return "/" + output.toString().replace('\\', '/');
    }

    private CertificateVO.Template findTemplate(Long id) {
        List<CertificateVO.Template> records = jdbcTemplate.query(
                templateSelect() + " from certificate_template where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Template.class),
                id
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.TemplateVersion findVersion(Long versionId) {
        List<CertificateVO.TemplateVersion> records = jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                versionId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.TemplateVersion latestVersion(Long templateId) {
        List<CertificateVO.TemplateVersion> records = jdbcTemplate.query(
                versionSelect() + " from certificate_template_version where template_id = ? and deleted = 0 order by version desc limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.TemplateVersion.class),
                templateId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    private CertificateVO.Record findRecord(Long id) {
        List<CertificateVO.Record> records = jdbcTemplate.query(
                recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                id
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
        if (currentUser == null || currentUser.getUserId() == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private Long lastInsertId() {
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
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
