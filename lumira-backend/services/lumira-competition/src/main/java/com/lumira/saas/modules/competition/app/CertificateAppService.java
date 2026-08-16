package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PlatformSettingDefaultsPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.dto.CertificateDTO;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import com.lumira.saas.modules.competition.repository.CertificateTemplateRepository;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import com.lumira.team.api.TeamInternalApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
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
    private static final String CERTIFICATE_DEFAULT_GROUP = "CERTIFICATE";
    private static final String DEFAULT_WIDTH_KEY = "certificate.canvas.default-width";
    private static final String DEFAULT_HEIGHT_KEY = "certificate.canvas.default-height";
    private static final String DEFAULT_ORIENTATION_KEY = "certificate.canvas.default-orientation";
    private static final String DEFAULT_UNIT_KEY = "certificate.canvas.default-unit";
    private static final String DEFAULT_DPI_KEY = "certificate.canvas.default-dpi";
    private static final String DEFAULT_CANVAS_KEY = "certificate.canvas.default-json";
    private static final String DEFAULT_VARIABLE_SCHEMA_KEY = "certificate.canvas.default-variable-schema-json";
    private static final String PUBLIC_ORGANIZER_KEY = "certificate.public.organizer";
    private static final String TEMPLATE_STATUSES_KEY = "certificate.rule.template-statuses";
    private static final String SCENE_TYPES_KEY = "certificate.rule.scene-types";
    private static final String SOURCE_TYPES_KEY = "certificate.rule.source-types";
    private static final String RECIPIENT_TYPES_KEY = "certificate.rule.recipient-types";
    private static final String RECORD_STATUSES_KEY = "certificate.rule.record-statuses";
    private static final Set<String> RECORD_STATUSES = Set.of("GENERATING", "ISSUED", "FAILED", "REVOKED");
    private static final Set<String> REQUIRED_PUBLISH_FIELDS = Set.of(
            "recipientName", "awardName", "competitionTitle", "issueDate", "verificationUrl");
    private static final String DEFAULT_SCENE_TYPE_KEY = "certificate.rule.default-scene-type";
    private static final String DEFAULT_SOURCE_TYPE_KEY = "certificate.rule.default-source-type";
    private static final String DEFAULT_RECIPIENT_TYPE_KEY = "certificate.rule.default-recipient-type";
    private static final String TEMPLATE_CODE_PREFIX_KEY = "certificate.number.template-prefix";
    private static final String BATCH_NO_PREFIX_KEY = "certificate.number.batch-prefix";
    private static final String CERTIFICATE_NO_PREFIX_KEY = "certificate.number.certificate-prefix";
    private static final String TIMESTAMP_FORMAT_KEY = "certificate.number.timestamp-format";
    private static final String VERIFICATION_CODE_LENGTH_KEY = "certificate.number.verification-code-length";
    private static final String PREVIEW_BATCH_NO_KEY = "certificate.preview.batch-no";
    private static final String PREVIEW_BATCH_NAME_KEY = "certificate.preview.batch-name";
    private static final String PREVIEW_STATUS_KEY = "certificate.preview.status";
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
    private static final String COMPETITION_AWARD_SETTINGS_TYPE = "AWARD_SETTINGS";
    private static final List<String> COMPETITION_AWARD_NAMES = List.of("一等奖", "二等奖", "三等奖", "优秀奖");
    private static final List<Integer> DEFAULT_COMPETITION_AWARD_QUOTAS = List.of(1, 2, 3, 5);

    private final CertificateTemplateRepository templateRepository;
    private final CertificateRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final FileInternalApi fileInternalApi;
    private final CertificateRenderService renderService;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final CompetitionSettingsRepository competitionSettingsRepository;
    private ObjectProvider<TeamInternalApi> teamInternalApiProvider;
    private ObjectProvider<PlatformSettingDefaultsPort> platformSettingDefaultsPortProvider;
    private final boolean enforceTrustedUserResolution;

    public CertificateAppService(
            CertificateTemplateRepository templateRepository,
            CertificateRecordRepository recordRepository,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(
                templateRepository,
                recordRepository,
                objectMapper,
                fileInternalApi,
                renderService,
                trustedCurrentUserResolver,
                null,
                true
        );
    }

    @Autowired
    public CertificateAppService(
            CertificateTemplateRepository templateRepository,
            CertificateRecordRepository recordRepository,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            CompetitionSettingsRepository competitionSettingsRepository
    ) {
        this(
                templateRepository,
                recordRepository,
                objectMapper,
                fileInternalApi,
                renderService,
                trustedCurrentUserResolver,
                competitionSettingsRepository,
                true
        );
    }

    public CertificateAppService(
            CertificateTemplateRepository templateRepository,
            CertificateRecordRepository recordRepository,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this(
                templateRepository,
                recordRepository,
                objectMapper,
                fileInternalApi,
                renderService,
                trustedCurrentUserResolver,
                null,
                enforceTrustedUserResolution
        );
    }

    public CertificateAppService(
            CertificateTemplateRepository templateRepository,
            CertificateRecordRepository recordRepository,
            ObjectMapper objectMapper,
            FileInternalApi fileInternalApi,
            CertificateRenderService renderService,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            CompetitionSettingsRepository competitionSettingsRepository,
            boolean enforceTrustedUserResolution
    ) {
        this.templateRepository = templateRepository;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        this.fileInternalApi = fileInternalApi;
        this.renderService = renderService;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.competitionSettingsRepository = competitionSettingsRepository;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Autowired
    void setTeamInternalApiProvider(ObjectProvider<TeamInternalApi> teamInternalApiProvider) {
        this.teamInternalApiProvider = teamInternalApiProvider;
    }

    @Autowired
    void setPlatformSettingDefaultsPortProvider(ObjectProvider<PlatformSettingDefaultsPort> platformSettingDefaultsPortProvider) {
        this.platformSettingDefaultsPortProvider = platformSettingDefaultsPortProvider;
    }

    public PageResponse<CertificateVO.Template> listTemplates(CurrentUser currentUser, String keyword, String status, long pageNo, long pageSize) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeSearchText(keyword, "Template keyword is too large");
        CertificateDefaults defaults = certificateDefaults();
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(status, defaults.templateStatuses(), "Invalid template status") : null;
        CertificateTemplateRepository.TemplatePage result = templateRepository.findTemplates(
                normalizedKeyword, normalizedStatus, (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        return page(result.records(), result.total(), normalizedPageNo, normalizedPageSize);
    }

    @Transactional
    public CertificateVO.Template createTemplate(CurrentUser currentUser, CertificateDTO.TemplateUpsertRequest request) {
        Long userId = requirePermission(currentUser, TEMPLATE_CREATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Template request is required");
        validateTemplateRequest(request);
        String templateCode = StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : generateTemplateCode();
        CertificateDefaults defaults = certificateDefaults();
        String sceneType = normalizeEnum(defaultText(request.getSceneType(), defaults.defaultSceneType()),
                defaults.sceneTypes(), "Invalid scene type");
        Long templateId = templateRepository.insertTemplate(templateCode,
                requiredText(request.getTemplateName(), "Template name is required"), sceneType,
                trimToNull(request.getDescription()), userId, userUuid);
        if (templateId == null) throw biz(ErrorCode.BIZ_ERROR, "Certificate template changed, please retry");
        int versionInserted = templateRepository.insertInitialVersion(templateId,
                new CertificateTemplateRepository.TemplateDefaults(defaults.width(), defaults.height(), defaults.orientation(),
                        defaults.unit(), defaults.dpi(), defaults.canvasJson(), defaults.variableSchemaJson()), userId, userUuid);
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
        int updated = templateRepository.updateTemplate(id, existing.getTemplateCode(), existing.getStatus(),
                StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode().trim() : existing.getTemplateCode(),
                requiredText(request.getTemplateName(), "Template name is required"),
                normalizeEnum(defaultText(request.getSceneType(), existing.getSceneType()), certificateDefaults().sceneTypes(), "Invalid scene type"),
                trimToNull(request.getDescription()),
                userId, userUuid, LocalDateTime.now());
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
        int updated = templateRepository.archiveTemplate(id, existing.getTemplateCode(), existing.getStatus(),
                userId, userUuid, LocalDateTime.now());
        requireCertificateWrite(updated, "Certificate template changed, please retry");
        return getTemplate(currentUser, id);
    }

    public List<CertificateVO.TemplateVersion> listVersions(CurrentUser currentUser, Long templateId) {
        requirePermission(currentUser, TEMPLATE_VIEW);
        requirePositiveId(templateId, "Certificate template id is required");
        return templateRepository.findVersions(templateId);
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
        CertificateDefaults defaults = certificateDefaults();
        CertificateTemplateRepository.TemplateCanvas canvas = new CertificateTemplateRepository.TemplateCanvas(
                positive(request.getPageWidth(), defaults.width()), positive(request.getPageHeight(), defaults.height()),
                defaultText(request.getOrientation(), defaults.orientation()).toUpperCase(Locale.ROOT),
                defaultText(request.getUnit(), defaults.unit()).toUpperCase(Locale.ROOT), positive(request.getDpi(), defaults.dpi()),
                requiredText(request.getCanvasJson(), "Canvas JSON is required"),
                defaultText(request.getVariableSchemaJson(), defaults.variableSchemaJson()));
        int updated = templateRepository.updateCanvas(versionId, version.getTemplateId(), version.getVersion(), canvas,
                userId, userUuid, LocalDateTime.now());
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
                trustedUsername(currentUser),
                currentUser.getSimulatedRoleId()
        );
        int updated = templateRepository.updateBackground(versionId, version.getTemplateId(), version.getVersion(),
                uploaded.id(), firstText(uploaded.publicUrl(), uploaded.previewUrl(), uploaded.downloadUrl()),
                userId, userUuid, LocalDateTime.now());
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
        validatePublishableTemplate(draft);
        int versionUpdated = templateRepository.publishVersion(draft, userId, userUuid, LocalDateTime.now());
        requireCertificateWrite(versionUpdated, "Certificate template version changed, please retry");
        CertificateVO.Template template = getTemplate(currentUser, templateId);
        int templateUpdated = templateRepository.publishTemplate(template, draft.getVersion(), userId, userUuid, LocalDateTime.now());
        requireCertificateWrite(templateUpdated, "Certificate template changed, please retry");
        int nextVersion = draft.getVersion() + 1;
        int draftInserted = templateRepository.insertDraftVersion(templateId, nextVersion, draft, userId, userUuid);
        requireCertificateWrite(draftInserted, "Certificate template version changed, please retry");
        return draft;
    }

    void validatePublishableTemplate(CertificateVO.TemplateVersion draft) {
        JsonNode root;
        try {
            root = objectMapper.readTree(defaultText(draft == null ? null : draft.getCanvasJson(), "{}"));
        } catch (JsonProcessingException exception) {
            throw biz(ErrorCode.BAD_REQUEST, "Certificate template canvas is invalid");
        }
        JsonNode elements = root.path("elements");
        if (!elements.isArray() || elements.isEmpty()) {
            throw biz(ErrorCode.BAD_REQUEST, "Certificate template canvas cannot be empty");
        }
        Set<String> fieldKeys = new java.util.HashSet<>();
        boolean hasTitle = false;
        boolean hasVerificationQrCode = false;
        for (JsonNode element : elements) {
            String type = element.path("type").asText("");
            String fieldKey = element.path("fieldKey").asText("");
            if (StringUtils.hasText(fieldKey)) {
                fieldKeys.add(fieldKey);
            }
            if ("qrcode".equalsIgnoreCase(type) && "verificationUrl".equals(fieldKey)) {
                hasVerificationQrCode = true;
            }
            if ("text".equalsIgnoreCase(type)
                    && !StringUtils.hasText(fieldKey)
                    && (StringUtils.hasText(element.path("text").asText(""))
                    || StringUtils.hasText(element.path("placeholder").asText("")))) {
                hasTitle = true;
            }
        }
        Set<String> missing = new java.util.TreeSet<>(REQUIRED_PUBLISH_FIELDS);
        missing.removeAll(fieldKeys);
        if (!hasVerificationQrCode) {
            missing.add("verificationUrl(qrcode)");
        }
        if (!hasTitle) {
            missing.add("certificateTitle");
        }
        if (!missing.isEmpty()) {
            throw biz(ErrorCode.BAD_REQUEST,
                    "Certificate template is incomplete, missing required elements: " + String.join(", ", missing));
        }
    }

    public CertificateVO.GenerateResult previewBatch(CurrentUser currentUser, CertificateDTO.BatchGenerateRequest request) {
        requirePermission(currentUser, BATCH_CREATE);
        requireRequest(request, "Batch request is required");
        validateBatchRequest(request);
        CertificateDefaults defaults = certificateDefaults();
        CertificateVO.GenerateResult result = new CertificateVO.GenerateResult();
        CertificateVO.Batch batch = new CertificateVO.Batch();
        batch.setBatchNo(defaults.previewBatchNo());
        batch.setBatchName(defaultText(request.getBatchName(), defaults.previewBatchName()));
        batch.setTotalCount(request.getRecords() == null ? 0 : request.getRecords().size());
        batch.setSuccessCount(batch.getTotalCount());
        batch.setFailedCount(0);
        batch.setStatus(defaults.previewStatus());
        result.setBatch(batch);
        result.setRecords(List.of());
        return result;
    }

    @Transactional
    public CertificateVO.GenerateResult generateBatch(CurrentUser currentUser, CertificateDTO.BatchGenerateRequest request) {
        return generateBatch(currentUser, request, List.of());
    }

    @Transactional
    public List<CertificateVO.AwardGrant> grantPublishedAwards(
            CurrentUser currentUser,
            CertificateDTO.AwardGrantRequest request
    ) {
        Long userId = requirePermission(currentUser, BATCH_CREATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Award grant request is required");
        requirePositiveId(request.getReviewBatchId(), "Review batch id is required");
        List<AwardRule> rules = normalizeAwardRules(request);
        LocalDateTime grantedAt = LocalDateTime.now();
        recordRepository.revokeUnissuedAwardGrants(
                request.getReviewBatchId(), userId, userUuid, grantedAt);
        for (AwardRule rule : rules) {
            recordRepository.grantPublishedAwards(
                    request.getReviewBatchId(), rule.awardName(), rule.minRank(), rule.maxRank(),
                    userId, userUuid, grantedAt);
        }
        List<CertificateVO.AwardGrant> grants = recordRepository.findAwardGrants(request.getReviewBatchId());
        if (grants.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "No eligible published review results were found for this award");
        }
        return grants;
    }

    @Transactional
    public List<CertificateVO.AwardGrant> grantPublishedAwardsFromCompetitionSettings(
            CurrentUser currentUser,
            String competitionUuid,
            Long reviewBatchId
    ) {
        requirePermission(currentUser, BATCH_CREATE);
        requireRequest(competitionUuid, "Competition uuid is required");
        requirePositiveId(reviewBatchId, "Review batch id is required");
        CertificateDTO.AwardGrantRequest request = new CertificateDTO.AwardGrantRequest();
        request.setReviewBatchId(reviewBatchId);
        request.setRules(competitionAwardRules(competitionUuid).stream().map(rule -> {
            CertificateDTO.AwardRuleRequest item = new CertificateDTO.AwardRuleRequest();
            item.setAwardName(rule.awardName());
            item.setMinRank(rule.minRank());
            item.setMaxRank(rule.maxRank());
            return item;
        }).toList());
        return grantPublishedAwards(currentUser, request);
    }

    public List<CertificateVO.AwardRule> listCompetitionAwardRules(CurrentUser currentUser, String competitionUuid) {
        requirePermission(currentUser, BATCH_CREATE);
        requireRequest(competitionUuid, "Competition uuid is required");
        return competitionAwardRules(competitionUuid).stream().map(rule -> {
            CertificateVO.AwardRule item = new CertificateVO.AwardRule();
            item.setAwardName(rule.awardName());
            item.setMinRank(rule.minRank());
            item.setMaxRank(rule.maxRank());
            return item;
        }).toList();
    }

    public List<CertificateVO.AwardSource> listPublishedAwardSources(CurrentUser currentUser) {
        requirePermission(currentUser, BATCH_CREATE);
        return recordRepository.findPublishedAwardSources();
    }

    public List<CertificateVO.AwardGrant> listAwardGrants(CurrentUser currentUser, Long reviewBatchId) {
        requirePermission(currentUser, BATCH_CREATE);
        requirePositiveId(reviewBatchId, "Review batch id is required");
        return recordRepository.findAwardGrants(reviewBatchId);
    }

    @Transactional
    public CertificateVO.GenerateResult generateAwardCertificates(
            CurrentUser currentUser,
            CertificateDTO.AwardCertificateGenerateRequest awardRequest
    ) {
        requirePermission(currentUser, BATCH_CREATE);
        requireRequest(awardRequest, "Award certificate request is required");
        requirePositiveId(awardRequest.getTemplateId(), "Certificate template id is required");
        requirePositiveId(awardRequest.getTemplateVersionId(), "Certificate template version id is required");
        List<Long> requestedIds = awardRequest.getGrantIds() == null
                ? List.of()
                : awardRequest.getGrantIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (requestedIds.isEmpty() || requestedIds.size() != awardRequest.getGrantIds().size()) {
            throw biz(ErrorCode.BAD_REQUEST, "Award grant ids must be non-empty and unique");
        }
        List<CertificateVO.AwardGrant> loaded = recordRepository.findAwardGrantsByIds(requestedIds);
        Map<Long, CertificateVO.AwardGrant> byId = loaded.stream()
                .collect(java.util.stream.Collectors.toMap(CertificateVO.AwardGrant::getId, grant -> grant));
        List<CertificateVO.AwardGrant> grants = requestedIds.stream().map(byId::get).toList();
        if (grants.stream().anyMatch(java.util.Objects::isNull)) {
            List<CertificateVO.AwardGrant> current = recordRepository.findAwardGrantsByAnyIds(requestedIds);
            Map<Long, CertificateVO.AwardGrant> currentById = current.stream()
                    .collect(java.util.stream.Collectors.toMap(CertificateVO.AwardGrant::getId, grant -> grant));
            long missingCount = requestedIds.stream().filter(id -> !currentById.containsKey(id)).count();
            long issuedCount = current.stream()
                    .filter(grant -> "ISSUED".equals(grant.getStatus()) || grant.getCertificateRecordId() != null)
                    .count();
            if (missingCount > 0) {
                throw biz(ErrorCode.BIZ_ERROR,
                        missingCount + " selected award grant(s) no longer exist; refresh and retry");
            }
            if (issuedCount > 0) {
                throw biz(ErrorCode.BIZ_ERROR,
                        issuedCount + " selected award certificate(s) have already been issued; refresh to view the latest status");
            }
            throw biz(ErrorCode.BIZ_ERROR, "Selected award grant status changed; refresh and retry");
        }
        Long reviewBatchId = grants.getFirst().getReviewBatchId();
        if (grants.stream().anyMatch(grant -> !java.util.Objects.equals(reviewBatchId, grant.getReviewBatchId()))) {
            throw biz(ErrorCode.BAD_REQUEST, "Award grants must belong to the same review batch");
        }
        CertificateVO.AwardGrant first = grants.getFirst();
        CertificateDTO.BatchGenerateRequest request = new CertificateDTO.BatchGenerateRequest();
        request.setBatchName(defaultText(awardRequest.getBatchName(), first.getCompetitionTitle() + " - " + first.getAwardName()));
        request.setTemplateId(awardRequest.getTemplateId());
        request.setTemplateVersionId(awardRequest.getTemplateVersionId());
        request.setCompetitionId(first.getCompetitionId());
        request.setStageId(first.getStageId());
        request.setSourceType("AWARD_RESULT");
        request.setRecords(grants.stream().map(grant -> {
            CertificateDTO.CertificateDataRequest row = new CertificateDTO.CertificateDataRequest();
            row.setRecipientName(grant.getRecipientName());
            row.setRecipientType("USER");
            row.setCompetitionTitle(grant.getCompetitionTitle());
            row.setProjectName(grant.getProjectName());
            row.setTeamName(grant.getTeamName());
            row.setAwardName(grant.getAwardName());
            row.setIssueDate(LocalDate.now());
            row.setData(Map.of(
                    "awardGrantId", grant.getId(),
                    "reviewBatchId", grant.getReviewBatchId(),
                    "publicationId", grant.getPublicationId(),
                    "rankNo", grant.getRankNo()
            ));
            return row;
        }).toList());
        return generateBatch(currentUser, request, grants);
    }

    private List<AwardRule> normalizeAwardRules(CertificateDTO.AwardGrantRequest request) {
        List<CertificateDTO.AwardRuleRequest> requestedRules = request.getRules();
        List<AwardRule> rules;
        if (requestedRules == null || requestedRules.isEmpty()) {
            String awardName = requiredText(request.getAwardName(), "Award name is required").trim();
            requireLength(awardName, MAX_NAME_LENGTH, "Award name is too large");
            int maxRank = request.getMaxRank() == null ? 0 : request.getMaxRank();
            rules = List.of(new AwardRule(awardName, 1, maxRank));
        } else {
            if (requestedRules.size() > 20 || requestedRules.stream().anyMatch(java.util.Objects::isNull)) {
                throw biz(ErrorCode.BAD_REQUEST, "Award rules are invalid");
            }
            rules = requestedRules.stream().map(rule -> new AwardRule(
                    requiredText(rule.getAwardName(), "Award name is required").trim(),
                    rule.getMinRank() == null ? 0 : rule.getMinRank(),
                    rule.getMaxRank() == null ? 0 : rule.getMaxRank()
            )).sorted(java.util.Comparator.comparingInt(AwardRule::minRank)).toList();
        }
        int previousMaxRank = 0;
        for (AwardRule rule : rules) {
            requireLength(rule.awardName(), MAX_NAME_LENGTH, "Award name is too large");
            if (rule.minRank() <= 0 || rule.maxRank() > 10000 || rule.minRank() > rule.maxRank()) {
                throw biz(ErrorCode.BAD_REQUEST, "Award rank range is invalid");
            }
            if (rule.minRank() <= previousMaxRank) {
                throw biz(ErrorCode.BAD_REQUEST, "Award rank ranges cannot overlap");
            }
            previousMaxRank = rule.maxRank();
        }
        return rules;
    }

    private List<AwardRule> competitionAwardRules(String competitionUuid) {
        if (competitionSettingsRepository == null) {
            return defaultCompetitionAwardRules();
        }
        CompetitionVO.ConfigSet configSet = competitionSettingsRepository.findCurrentConfigSet(competitionUuid);
        if (configSet == null || configSet.getId() == null) {
            return defaultCompetitionAwardRules();
        }
        List<CompetitionVO.ConfigItem> items = competitionSettingsRepository.findConfigItems(
                competitionUuid,
                configSet.getId(),
                Set.of(COMPETITION_AWARD_SETTINGS_TYPE)
        );
        CompetitionVO.ConfigItem settingsItem = items.stream()
                .filter(item -> COMPETITION_AWARD_SETTINGS_TYPE.equals(item.getItemType()))
                .findFirst()
                .orElse(null);
        if (settingsItem == null || !StringUtils.hasText(settingsItem.getContentJson())) {
            return defaultCompetitionAwardRules();
        }
        try {
            JsonNode metadata = objectMapper.readTree(settingsItem.getContentJson());
            JsonNode rules = metadata.path("rules");
            if (!rules.isArray() || rules.size() != COMPETITION_AWARD_NAMES.size()) {
                throw biz(ErrorCode.BIZ_ERROR, "赛事获奖设置不完整，请先配置四档奖项");
            }
            List<AwardRule> result = new ArrayList<>();
            int nextRank = 1;
            for (int index = 0; index < COMPETITION_AWARD_NAMES.size(); index++) {
                JsonNode rule = rules.get(index);
                String awardName = rule.path("awardName").asText();
                int quota = rule.path("quota").asInt(0);
                if (!COMPETITION_AWARD_NAMES.get(index).equals(awardName) || quota < 1 || quota > 10000) {
                    throw biz(ErrorCode.BIZ_ERROR, "赛事获奖设置无效，请检查四档奖项名额");
                }
                int minRank = nextRank;
                int maxRank = minRank + quota - 1;
                result.add(new AwardRule(awardName, minRank, maxRank));
                nextRank = maxRank + 1;
            }
            return result;
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw biz(ErrorCode.BIZ_ERROR, "赛事获奖设置格式不正确");
        }
    }

    private List<AwardRule> defaultCompetitionAwardRules() {
        int nextRank = 1;
        List<AwardRule> result = new ArrayList<>();
        for (int index = 0; index < COMPETITION_AWARD_NAMES.size(); index++) {
            int minRank = nextRank;
            int maxRank = minRank + DEFAULT_COMPETITION_AWARD_QUOTAS.get(index) - 1;
            result.add(new AwardRule(COMPETITION_AWARD_NAMES.get(index), minRank, maxRank));
            nextRank = maxRank + 1;
        }
        return result;
    }

    private record AwardRule(String awardName, int minRank, int maxRank) {}

    private CertificateVO.GenerateResult generateBatch(
            CurrentUser currentUser,
            CertificateDTO.BatchGenerateRequest request,
            List<CertificateVO.AwardGrant> awardGrants
    ) {
        Long userId = requirePermission(currentUser, BATCH_CREATE);
        String userUuid = trustedUserUuid(currentUser);
        requireRequest(request, "Batch request is required");
        validateBatchRequest(request);
        CertificateVO.TemplateVersion version = getVersion(currentUser, request.getTemplateVersionId());
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw biz(ErrorCode.BAD_REQUEST, "Only published template version can generate certificates");
        }
        CertificateDefaults defaults = certificateDefaults();
        String batchNo = defaults.batchNoPrefix() + LocalDateTime.now().format(defaults.timestampFormatter());
        String sourceType = normalizeEnum(defaultText(request.getSourceType(), defaults.defaultSourceType()),
                defaults.sourceTypes(), "Invalid source type");
        List<CertificateDTO.CertificateDataRequest> rows = request.getRecords() == null ? List.of() : request.getRecords();
        Long batchId = recordRepository.insertBatch(new CertificateRecordRepository.BatchCreate(
                batchNo, defaultText(request.getBatchName(), batchNo), request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(), sourceType,
                awardGrants.isEmpty() ? null : awardGrants.getFirst().getReviewBatchId(),
                rows.size(), userId, userUuid));
        if (batchId == null) throw biz(ErrorCode.BIZ_ERROR, "Certificate batch changed, please retry");
        List<CertificateVO.Record> created = new ArrayList<>();
        int success = 0;
        int failed = 0;
        String errorMessage = null;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            CertificateDTO.CertificateDataRequest row = rows.get(rowIndex);
            CertificateVO.AwardGrant awardGrant = awardGrants.isEmpty() ? null : awardGrants.get(rowIndex);
            try {
                created.add(createCertificateRecord(
                        userId, userUuid, batchId, request, version, row, awardGrant, batchNo));
                success += 1;
            } catch (RuntimeException exception) {
                failed += 1;
                errorMessage = exception.getMessage();
            }
        }
        String batchStatus = failed == 0 ? "COMPLETED" : success == 0 ? "FAILED" : "PARTIAL_FAILED";
        int batchUpdated = recordRepository.completeBatch(batchId, success, failed,
                batchStatus, errorMessage, userId, userUuid, LocalDateTime.now());
        requireCertificateWrite(batchUpdated, "Certificate batch changed, please retry");
        CertificateVO.GenerateResult result = new CertificateVO.GenerateResult();
        result.setBatch(getBatch(currentUser, batchId));
        result.setRecords(created);
        return result;
    }

    public List<CertificateVO.Record> listMyCertificates(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        return recordRepository.findMyCertificates(userId, userUuid, activeTeamIdsForUser(userId, userUuid));
    }

    public CertificateVO.Record getMyCertificateForDownload(CurrentUser currentUser, Long id) {
        requirePositiveId(id, "Certificate id is required");
        Long userId = requireUserId(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        CertificateVO.Record record = recordRepository.findMyCertificate(
                id, userId, userUuid, activeTeamIdsForUser(userId, userUuid)
        );
        if (record == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate not found");
        }
        return record;
    }

    private List<Long> activeTeamIdsForUser(Long userId, String userUuid) {
        TeamInternalApi teamInternalApi = teamInternalApiProvider == null
                ? null : teamInternalApiProvider.getIfAvailable();
        if (teamInternalApi == null) {
            return List.of();
        }
        List<Long> teamIds = teamInternalApi.listActiveTeamIdsForUser(userId, userUuid);
        return teamIds == null ? List.of() : teamIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(teamId -> teamId > 0)
                .distinct()
                .toList();
    }

    public PageResponse<CertificateVO.Batch> listBatches(CurrentUser currentUser, long pageNo, long pageSize) {
        requirePermission(currentUser, BATCH_VIEW);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        Ownership owner = ownership(currentUser);
        CertificateRecordRepository.BatchPage result = recordRepository.findBatches(owner.userId(), owner.userUuid(),
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        return page(result.records(), result.total(), normalizedPageNo, normalizedPageSize);
    }

    public PageResponse<CertificateVO.Batch> listBatches(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            Long competitionId
    ) {
        requirePermission(currentUser, BATCH_VIEW);
        requirePositiveId(competitionId, "Competition id is required");
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        Ownership owner = ownership(currentUser);
        CertificateRecordRepository.BatchPage result = recordRepository.findBatchesForCompetition(
                owner.userId(),
                owner.userUuid(),
                competitionId,
                (normalizedPageNo - 1) * normalizedPageSize,
                normalizedPageSize
        );
        return page(result.records(), result.total(), normalizedPageNo, normalizedPageSize);
    }

    public CertificateVO.Batch getBatch(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, BATCH_VIEW);
        requirePositiveId(id, "Certificate batch id is required");
        Ownership owner = ownership(currentUser);
        CertificateVO.Batch batch = recordRepository.findBatch(id, owner.userId(), owner.userUuid());
        if (batch == null) {
            throw biz(ErrorCode.NOT_FOUND, "Certificate batch not found");
        }
        return batch;
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
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(status, RECORD_STATUSES, "Invalid certificate status") : null;
        Ownership owner = ownership(currentUser);
        CertificateRecordRepository.RecordPage result = recordRepository.findRecords(
                normalizedCertificateNo, normalizedRecipientName, normalizedStatus, owner.userId(), owner.userUuid(),
                (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize);
        return page(result.records(), result.total(), normalizedPageNo, normalizedPageSize);
    }

    public PageResponse<CertificateVO.Record> listRecords(
            CurrentUser currentUser,
            String certificateNo,
            String recipientName,
            String status,
            long pageNo,
            long pageSize,
            Long competitionId
    ) {
        requirePermission(currentUser, CERTIFICATE_VIEW);
        requirePositiveId(competitionId, "Competition id is required");
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        String normalizedCertificateNo = normalizeSearchText(certificateNo, "Certificate no is too large");
        String normalizedRecipientName = normalizeSearchText(recipientName, "Recipient name is too large");
        String normalizedStatus = StringUtils.hasText(status)
                ? normalizeEnum(status, RECORD_STATUSES, "Invalid certificate status") : null;
        Ownership owner = ownership(currentUser);
        CertificateRecordRepository.RecordPage result = recordRepository.findRecordsForCompetition(
                normalizedCertificateNo,
                normalizedRecipientName,
                normalizedStatus,
                owner.userId(),
                owner.userUuid(),
                competitionId,
                (normalizedPageNo - 1) * normalizedPageSize,
                normalizedPageSize
        );
        return page(result.records(), result.total(), normalizedPageNo, normalizedPageSize);
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
        Ownership owner = ownership(currentUser);
        int updated = recordRepository.revoke(record, trimToNull(reason), userId, userUuid,
                owner.userId(), owner.userUuid(), LocalDateTime.now());
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
        Ownership owner = ownership(currentUser);
        int updated = recordRepository.updateFile(record, fileUrl, userId, userUuid,
                owner.userId(), owner.userUuid(), LocalDateTime.now());
        requireCertificateWrite(updated, "Certificate record changed, please retry");
        return getRecord(currentUser, id);
    }

    public CertificateVO.PublicVerifyResult verifyByToken(String publicToken, String clientIp, String userAgent) {
        String normalizedToken = requiredPublicText(publicToken, MAX_PUBLIC_TOKEN_LENGTH, "Public token is invalid");
        String normalizedClientIp = publicLogText(clientIp);
        String normalizedUserAgent = publicLogText(userAgent);
        CertificateVO.Record record = recordRepository.findByPublicToken(normalizedToken);
        if (record == null) {
            logVerify(null, null, "TOKEN", "NOT_FOUND", normalizedClientIp, normalizedUserAgent);
            return publicResult("NOT_FOUND", null);
        }
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "TOKEN", result, normalizedClientIp, normalizedUserAgent);
        return publicResult(result, record);
    }

    public CertificateVO.PublicVerifyResult verifyByCertificateNo(String certificateNo, String verificationCode, String clientIp, String userAgent) {
        String normalizedCertificateNo = requiredPublicText(certificateNo, MAX_CODE_LENGTH, "Certificate no is invalid");
        String normalizedVerificationCode = requiredPublicText(verificationCode, 16, "Verification code is invalid");
        String normalizedClientIp = publicLogText(clientIp);
        String normalizedUserAgent = publicLogText(userAgent);
        CertificateVO.Record record = recordRepository.findByCertificateNo(normalizedCertificateNo);
        if (record == null) {
            logVerify(null, normalizedCertificateNo, "CERT_NO", "NOT_FOUND", normalizedClientIp, normalizedUserAgent);
            return publicResult("NOT_FOUND", null);
        }
        if (!normalizedVerificationCode.equals(record.getVerificationCode())) {
            logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", "INVALID_CODE", normalizedClientIp, normalizedUserAgent);
            return publicResult("INVALID_CODE", null);
        }
        String result = resolvePublicResult(record);
        logVerify(record.getId(), record.getCertificateNo(), "CERT_NO", result, normalizedClientIp, normalizedUserAgent);
        return publicResult(result, record);
    }

    private CertificateVO.Record createCertificateRecord(
            Long userId,
            String userUuid,
            Long batchId,
            CertificateDTO.BatchGenerateRequest request,
            CertificateVO.TemplateVersion version,
            CertificateDTO.CertificateDataRequest row,
            CertificateVO.AwardGrant awardGrant,
            String batchNo
    ) {
        String certificateNo = generateCertificateNo();
        String publicToken = UUID.randomUUID().toString().replace("-", "");
        CertificateDefaults defaults = certificateDefaults();
        String verificationCode = randomDigits(defaults.verificationCodeLength());
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
        Long recordId = recordRepository.insertRecord(new CertificateRecordRepository.RecordCreate(
                certificateNo, verificationCode, publicToken, batchId, request.getTemplateId(), request.getTemplateVersionId(),
                request.getCompetitionId(), request.getStageId(),
                awardGrant == null ? null : awardGrant.getRegistrationId(),
                awardGrant == null ? null : awardGrant.getProjectId(),
                awardGrant == null ? null : awardGrant.getTeamId(),
                awardGrant == null ? null : awardGrant.getUserId(),
                requiredText(row.getRecipientName(), "Recipient name is required"),
                normalizeEnum(defaultText(row.getRecipientType(), defaults.defaultRecipientType()),
                        defaults.recipientTypes(), "Invalid recipient type"),
                trimToNull(row.getCompetitionTitle()), trimToNull(row.getProjectName()), trimToNull(row.getTeamName()),
                trimToNull(row.getAwardName()), issueDate, row.getExpireDate(), dataJson, userId, userUuid));
        if (recordId == null) throw biz(ErrorCode.BIZ_ERROR, "Certificate record changed, please retry");
        String fileUrl = null;
        try {
            fileUrl = render(certificateNo, batchId, version, data);
            int updated = recordRepository.updateGeneratedFile(recordId, certificateNo, batchId, fileUrl,
                    userId, userUuid, LocalDateTime.now());
            if (updated <= 0) {
                throw biz(ErrorCode.BIZ_ERROR, "Certificate record changed, please retry");
            }
            if (awardGrant != null) {
                int linked = recordRepository.linkAwardGrant(
                        awardGrant.getId(), recordId, userId, userUuid, LocalDateTime.now());
                if (linked <= 0) {
                    throw biz(ErrorCode.BIZ_ERROR, "Award grant changed or already has a certificate");
                }
            }
        } catch (RuntimeException exception) {
            deleteRenderedFile(fileUrl);
            recordRepository.markGenerationFailed(
                    recordId, certificateNo, batchId, userId, userUuid, LocalDateTime.now());
            throw exception;
        }
        CertificateVO.Record record = new CertificateVO.Record();
        record.setId(recordId);
        record.setCertificateNo(certificateNo);
        record.setVerificationCode(verificationCode);
        record.setPublicToken(publicToken);
        record.setBatchId(batchId);
        record.setTemplateId(request.getTemplateId());
        record.setTemplateVersionId(request.getTemplateVersionId());
        record.setCompetitionId(request.getCompetitionId());
        if (awardGrant != null) {
            record.setRegistrationId(awardGrant.getRegistrationId());
            record.setProjectId(awardGrant.getProjectId());
            record.setTeamId(awardGrant.getTeamId());
            record.setUserId(awardGrant.getUserId());
        }
        record.setRecipientName(row.getRecipientName());
        record.setRecipientType(normalizeEnum(defaultText(row.getRecipientType(), defaults.defaultRecipientType()),
                defaults.recipientTypes(), "Invalid recipient type"));
        record.setCompetitionTitle(trimToNull(row.getCompetitionTitle()));
        record.setProjectName(trimToNull(row.getProjectName()));
        record.setTeamName(trimToNull(row.getTeamName()));
        record.setAwardName(trimToNull(row.getAwardName()));
        record.setIssueDate(issueDate);
        record.setExpireDate(row.getExpireDate());
        record.setDataJson(dataJson);
        record.setCertificateFileUrl(fileUrl);
        record.setStatus("ISSUED");
        return record;
    }

    private String render(String certificateNo, Long batchId, CertificateVO.TemplateVersion version, Map<String, Object> data) {
        Path output = Path.of("storage", "certificates", String.valueOf(batchId), certificateNo + ".png");
        try {
            renderService.renderPng(version.getCanvasJson(), version.getBackgroundUrl(), data, output);
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
                // The original rendering failure remains the actionable cause.
            }
            throw exception;
        }
        return "/" + output.toString().replace('\\', '/');
    }

    private void deleteRenderedFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/storage/")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(fileUrl.substring(1)));
        } catch (IOException ignored) {
            // Database state remains FAILED; orphan cleanup can retry separately.
        }
    }

    private CertificateVO.Template findTemplate(Long id) {
        requirePositiveId(id, "Certificate template id is required");
        return templateRepository.findTemplate(id);
    }

    private CertificateVO.TemplateVersion findVersion(Long versionId) {
        requirePositiveId(versionId, "Template version id is required");
        return templateRepository.findVersion(versionId);
    }

    private CertificateVO.TemplateVersion latestVersion(Long templateId) {
        requirePositiveId(templateId, "Certificate template id is required");
        return templateRepository.findLatestVersion(templateId);
    }

    private CertificateVO.Record findRecord(Long id) {
        requirePositiveId(id, "Certificate id is required");
        return recordRepository.findRecord(id);
    }

    private CertificateVO.Record findRecord(CurrentUser currentUser, Long id) {
        requirePositiveId(id, "Certificate id is required");
        Ownership owner = ownership(currentUser);
        return recordRepository.findRecord(id, owner.userId(), owner.userUuid());
    }

    private void logVerify(Long certificateId, String certificateNo, String queryType, String queryResult, String clientIp, String userAgent) {
        recordRepository.insertVerifyLog(certificateId, certificateNo, queryType, queryResult,
                trimToNull(clientIp), trimToNull(userAgent));
    }

    private String resolvePublicResult(CertificateVO.Record record) {
        if ("REVOKED".equals(record.getStatus())) {
            return "REVOKED";
        }
        if (!"ISSUED".equals(record.getStatus())) {
            return "NOT_FOUND";
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
        response.setOrganizer(requiredDefault(certificateDefaultDefinitions(), PUBLIC_ORGANIZER_KEY));
        response.setStatus(record.getStatus());
        response.setCertificateFileUrl(record.getCertificateFileUrl());
        response.setSafeData(Map.of(
                "teamName", firstText(record.getTeamName(), ""),
                "projectName", firstText(record.getProjectName(), "")
        ));
        return response;
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

    private CertificateDefaults certificateDefaults() {
        Map<String, String> values = certificateDefaultDefinitions();
        return new CertificateDefaults(
                requiredPositiveDefault(values, DEFAULT_WIDTH_KEY),
                requiredPositiveDefault(values, DEFAULT_HEIGHT_KEY),
                requiredDefault(values, DEFAULT_ORIENTATION_KEY),
                requiredDefault(values, DEFAULT_UNIT_KEY),
                requiredPositiveDefault(values, DEFAULT_DPI_KEY),
                requiredDefault(values, DEFAULT_CANVAS_KEY),
                requiredDefault(values, DEFAULT_VARIABLE_SCHEMA_KEY),
                requiredSetDefault(values, TEMPLATE_STATUSES_KEY),
                requiredSetDefault(values, SCENE_TYPES_KEY),
                requiredSetDefault(values, SOURCE_TYPES_KEY),
                requiredSetDefault(values, RECIPIENT_TYPES_KEY),
                requiredSetDefault(values, RECORD_STATUSES_KEY),
                requiredDefault(values, DEFAULT_SCENE_TYPE_KEY),
                requiredDefault(values, DEFAULT_SOURCE_TYPE_KEY),
                requiredDefault(values, DEFAULT_RECIPIENT_TYPE_KEY),
                requiredDefault(values, TEMPLATE_CODE_PREFIX_KEY),
                requiredDefault(values, BATCH_NO_PREFIX_KEY),
                requiredDefault(values, CERTIFICATE_NO_PREFIX_KEY),
                requiredFormatter(values, TIMESTAMP_FORMAT_KEY),
                requiredPositiveDefault(values, VERIFICATION_CODE_LENGTH_KEY),
                requiredDefault(values, PREVIEW_BATCH_NO_KEY),
                requiredDefault(values, PREVIEW_BATCH_NAME_KEY),
                requiredDefault(values, PREVIEW_STATUS_KEY)
        );
    }

    private Map<String, String> certificateDefaultDefinitions() {
        PlatformSettingDefaultsPort defaultsPort = platformSettingDefaultsPortProvider == null
                ? null : platformSettingDefaultsPortProvider.getIfAvailable();
        if (defaultsPort == null) {
            throw biz(ErrorCode.SYSTEM_ERROR, "Platform setting defaults boundary is unavailable");
        }
        Map<String, String> values = defaultsPort.findEnabledDefaults(CERTIFICATE_DEFAULT_GROUP);
        return values == null ? Map.of() : values;
    }

    private Set<String> requiredSetDefault(Map<String, String> values, String key) {
        Set<String> result = java.util.Arrays.stream(requiredDefault(values, key).split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (result.isEmpty()) throw biz(ErrorCode.SYSTEM_ERROR, "Invalid certificate setting definition: " + key);
        return result;
    }

    private DateTimeFormatter requiredFormatter(Map<String, String> values, String key) {
        try {
            return DateTimeFormatter.ofPattern(requiredDefault(values, key));
        } catch (IllegalArgumentException exception) {
            throw biz(ErrorCode.SYSTEM_ERROR, "Invalid certificate setting definition: " + key);
        }
    }

    private int requiredPositiveDefault(Map<String, String> values, String key) {
        try {
            int value = Integer.parseInt(requiredDefault(values, key));
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // Converted to a deterministic configuration error below.
        }
        throw biz(ErrorCode.SYSTEM_ERROR, "Invalid certificate setting definition: " + key);
    }

    private String requiredDefault(Map<String, String> values, String key) {
        String value = values.get(key);
        if (!StringUtils.hasText(value)) {
            throw biz(ErrorCode.SYSTEM_ERROR, "Missing certificate setting definition: " + key);
        }
        return value;
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

    private Ownership ownership(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions.contains("*")) {
            return new Ownership(null, null);
        }
        return new Ownership(userId, trustedUserUuid(currentUser));
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        requireUserId(currentUser);
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }

    private void requireCertificateWrite(int updated, String message) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private String generateTemplateCode() {
        CertificateDefaults defaults = certificateDefaults();
        return defaults.templateCodePrefix() + LocalDateTime.now().format(defaults.timestampFormatter());
    }

    private String generateCertificateNo() {
        String prefix = certificateDefaults().certificateNoPrefix() + Year.now().getValue() + "-";
        long count = recordRepository.countCertificateNumbers(prefix + "%");
        return prefix + String.format("%06d", count + 1);
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
        CertificateDefaults defaults = certificateDefaults();
        int width = positive(request.getPageWidth(), defaults.width());
        int height = positive(request.getPageHeight(), defaults.height());
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

    private record CertificateDefaults(
            int width,
            int height,
            String orientation,
            String unit,
            int dpi,
            String canvasJson,
            String variableSchemaJson,
            Set<String> templateStatuses,
            Set<String> sceneTypes,
            Set<String> sourceTypes,
            Set<String> recipientTypes,
            Set<String> recordStatuses,
            String defaultSceneType,
            String defaultSourceType,
            String defaultRecipientType,
            String templateCodePrefix,
            String batchNoPrefix,
            String certificateNoPrefix,
            DateTimeFormatter timestampFormatter,
            int verificationCodeLength,
            String previewBatchNo,
            String previewBatchName,
            String previewStatus
    ) {
    }

    private record Ownership(Long userId, String userUuid) {
    }
}
