package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.file.FileContentDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.export.CompetitionExcelExportService;
import com.lumira.saas.modules.competition.export.ExportColumn;
import com.lumira.saas.modules.competition.export.ExportVO;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.repository.RegistrationDatasetRepository;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class CompetitionRegistrationExportAppService {
    public static final String MODULE_KEY = "competition:registration";
    public static final String EXPORT_PERMISSION = "registration:dataset:export";
    public static final String SENSITIVE_EXPORT_PERMISSION = "registration:dataset:export-sensitive";
    public static final String MATERIAL_DOWNLOAD_PERMISSION = "registration:material:download";
    public static final String EXPORT_TYPE_DATA_XLSX = "DATA_XLSX";
    public static final String EXPORT_TYPE_MATERIAL_ZIP = "MATERIAL_ZIP";

    private static final Logger log = LoggerFactory.getLogger(CompetitionRegistrationExportAppService.class);
    private static final long EXPORT_PAGE_SIZE = 100L;
    private static final int MAX_SELECTED_REGISTRATIONS = 500;
    private static final long MAX_MATERIAL_FILE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_MATERIAL_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final String MATERIAL_REFERENCE_TYPE = "competition.registration.material";
    private static final String MATERIAL_PACKAGE_WORKBOOK_NAME = "报名记录.xlsx";
    private static final String REGISTRATION_FIELD_SCOPE = "REGISTRATION_FIELD";
    private static final String TEAM_FIELD_SCOPE = "TEAM_FIELD";
    private static final String MEMBER_FIELD_SCOPE = "MEMBER_FIELD";
    private static final String PROJECT_FIELD_SCOPE = "PROJECT_FIELD";
    private static final String INTELLECTUAL_PROPERTY_GROUP = "知识产权信息";
    private static final String INTELLECTUAL_PROPERTY_ENTRIES_KEY = "intellectualProperties";
    private static final Set<String> COLLECTION_FIELD_SCOPES = Set.of(
            REGISTRATION_FIELD_SCOPE,
            TEAM_FIELD_SCOPE,
            MEMBER_FIELD_SCOPE,
            PROJECT_FIELD_SCOPE
    );
    private static final Map<String, String> REGISTRATION_STATUS_LABELS = Map.of(
            "DRAFT", "草稿",
            "CREATED", "待提交材料",
            "MATERIAL_SUBMITTED", "材料已提交",
            "PENDING_PAYMENT", "待付款",
            "PAID", "已支付",
            "CONFIRMED", "已确认",
            "CANCELLED", "已取消"
    );
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CompetitionRegistrationAppService registrationAppService;
    private final RegistrationDatasetRepository datasetRepository;
    private final CompetitionExcelExportService excelExportService;
    private final ExportTaskPort exportTaskPort;
    private final TrustedUserSnapshotResolver trustedUserSnapshotResolver;
    private final FileInternalApi fileInternalApi;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CompetitionRegistrationExportTaskWorkerService> workerProvider;
    private final ExecutorService executorService;

    public CompetitionRegistrationExportAppService(
            CompetitionRegistrationAppService registrationAppService,
            RegistrationDatasetRepository datasetRepository,
            CompetitionExcelExportService excelExportService,
            ExportTaskPort exportTaskPort,
            TrustedUserSnapshotResolver trustedUserSnapshotResolver,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            ObjectProvider<CompetitionRegistrationExportTaskWorkerService> workerProvider,
            ObjectProvider<ExecutorService> executorServiceProvider
    ) {
        this.registrationAppService = registrationAppService;
        this.datasetRepository = datasetRepository;
        this.excelExportService = excelExportService;
        this.exportTaskPort = exportTaskPort;
        this.trustedUserSnapshotResolver = trustedUserSnapshotResolver;
        this.fileInternalApi = fileInternalApi;
        this.objectMapper = objectMapper;
        this.workerProvider = workerProvider;
        this.executorService = executorServiceProvider.getIfAvailable(Executors::newVirtualThreadPerTaskExecutor);
    }

    public ExportVO.ExportStartVO startExport(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        return startTask(currentUser, request, EXPORT_TYPE_DATA_XLSX);
    }

    public ExportVO.ExportStartVO startMaterialPackage(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        requireMaterialDownloadPermission(currentUser);
        return startTask(currentUser, request, EXPORT_TYPE_MATERIAL_ZIP);
    }

    private ExportVO.ExportStartVO startTask(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request,
            String exportType
    ) {
        CompetitionRegistrationDTO.RegistrationExportRequest normalizedRequest = normalizeRequest(request);
        long totalCount = countRegistrations(currentUser, normalizedRequest);
        String fileName = buildFileName(normalizedRequest.getCompetitionId(), exportType);
        AsyncTaskPayload payload = new AsyncTaskPayload();
        payload.setRequest(normalizedRequest);
        payload.setFileName(fileName);
        payload.setSimulatedRoleId(normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()));
        payload.setExportType(exportType);
        ExportTaskPort.ExportTask task = exportTaskPort.createTask(
                currentUser,
                MODULE_KEY,
                payload,
                taskFieldKeys(),
                totalCount,
                EXPORT_PERMISSION
        );
        submitWorker(task.id());

        ExportVO.ExportStartVO response = new ExportVO.ExportStartVO();
        response.setMode("ASYNC");
        response.setTaskId(task.id());
        response.setFileName(fileName);
        response.setTotalCount(totalCount);
        return response;
    }

    CurrentUser buildQueuedAsyncUser(
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Long taskId
    ) {
        simulatedRoleId = normalizeSimulatedRoleId(simulatedRoleId);
        if (trustedUserSnapshotResolver == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Trusted export user resolver is unavailable");
        }
        return trustedUserSnapshotResolver.resolve(
                userId,
                userUuid,
                simulatedRoleId,
                CompetitionAuthenticationTrust.asyncExportSessionId(taskId),
                EXPORT_PERMISSION
        );
    }

    byte[] exportFromTrustedSnapshot(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request,
            Long taskId
    ) {
        CompetitionRegistrationDTO.RegistrationExportRequest normalizedRequest = normalizeRequest(request);
        List<CompetitionRegistrationVO.Registration> registrations =
                loadRegistrations(currentUser, normalizedRequest, taskId);
        List<CollectedExportField> collectedFields = collectExportFields(registrations);
        List<RegistrationExportRow> rows = new ArrayList<>();
        for (CompetitionRegistrationVO.Registration registration : registrations) {
            CurrentUser refreshedUser = buildQueuedAsyncUser(
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getSimulatedRoleId(),
                    taskId
            );
            rows.addAll(toRows(
                    registration,
                    hasPermission(refreshedUser, SENSITIVE_EXPORT_PERMISSION),
                    collectedFields
            ));
        }
        return excelExportService.export("报名与材料", columns(collectedFields), rows);
    }

    byte[] exportMaterialPackageFromTrustedSnapshot(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request,
            Long taskId
    ) {
        CompetitionRegistrationDTO.RegistrationExportRequest normalizedRequest = normalizeRequest(request);
        requireMaterialDownloadPermission(currentUser);
        List<CompetitionRegistrationVO.Registration> registrations =
                loadRegistrations(currentUser, normalizedRequest, taskId);
        List<CollectedExportField> collectedFields = collectExportFields(registrations);
        List<Map<String, Object>> manifest = new ArrayList<>();
        List<RegistrationExportRow> rows = new ArrayList<>();
        long totalBytes = 0L;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            Set<String> usedPaths = new LinkedHashSet<>();
            for (int registrationIndex = 0; registrationIndex < registrations.size(); registrationIndex += 1) {
                CompetitionRegistrationVO.Registration registration = registrations.get(registrationIndex);
                CurrentUser refreshedUser = buildQueuedAsyncUser(
                        currentUser.getUserId(),
                        currentUser.getUserUuid(),
                        currentUser.getSimulatedRoleId(),
                        taskId
                );
                requireMaterialDownloadPermission(refreshedUser);
                List<CompetitionRegistrationVO.MaterialSubmission> submissions =
                        registrationAppService.listMaterials(refreshedUser, registration.getId());
                rows.addAll(toRows(
                        registration,
                        hasPermission(refreshedUser, SENSITIVE_EXPORT_PERMISSION),
                        collectedFields
                ));
                String registrationFolder = registrationFolder(registration, registrationIndex + 1);
                String directoryPath = registrationFolder + "/";
                usedPaths.add(directoryPath);
                zip.putNextEntry(new ZipEntry(directoryPath));
                zip.closeEntry();
                for (CompetitionRegistrationVO.MaterialSubmission submission : safeList(submissions)) {
                    for (CompetitionRegistrationVO.MaterialValue value : safeList(submission.getValues())) {
                        if (value.getFileId() == null || value.getFileId() <= 0) {
                            continue;
                        }
                        FileContentDTO file = fileInternalApi.readFileContentForAuthorizedBusinessReference(
                                value.getFileId(),
                                refreshedUser.getUserId(),
                                refreshedUser.getUserUuid(),
                                refreshedUser.getUsername(),
                                MATERIAL_REFERENCE_TYPE,
                                registration.getId(),
                                refreshedUser.getSimulatedRoleId()
                        );
                        byte[] content = requireMaterialContent(file, value.getFileId());
                        if (content.length > MAX_MATERIAL_FILE_BYTES) {
                            throw new BizException(
                                    ErrorCode.BAD_REQUEST,
                                    "A material file exceeds the 100 MB package limit"
                            );
                        }
                        totalBytes += content.length;
                        if (totalBytes > MAX_MATERIAL_PACKAGE_BYTES) {
                            throw new BizException(
                                    ErrorCode.BAD_REQUEST,
                                    "Material package exceeds the 512 MB limit"
                            );
                        }
                        String path = uniqueZipPath(
                                usedPaths,
                                materialPath(registrationFolder, submission, value, file)
                        );
                        zip.putNextEntry(new ZipEntry(path));
                        zip.write(content);
                        zip.closeEntry();

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("competitionId", registration.getCompetitionId());
                        item.put("registrationId", registration.getId());
                        item.put("registrationNo", registration.getRegistrationNo());
                        item.put("teamName", registration.getTeamName());
                        item.put("registrationFolder", registrationFolder);
                        item.put("stageId", submission.getStageId());
                        item.put("submissionId", submission.getId());
                        item.put("fieldKey", value.getFieldKey());
                        item.put("fileId", value.getFileId());
                        item.put("originalFileName", file.originalFileName());
                        item.put("zipPath", path);
                        item.put("size", content.length);
                        manifest.add(item);
                    }
                }
            }
            byte[] workbook = excelExportService.export("报名记录", columns(collectedFields), rows);
            totalBytes += workbook.length;
            if (totalBytes > MAX_MATERIAL_PACKAGE_BYTES) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Material package exceeds the 512 MB limit");
            }
            zip.putNextEntry(new ZipEntry(MATERIAL_PACKAGE_WORKBOOK_NAME));
            zip.write(workbook);
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (BizException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build registration material package", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    private void submitWorker(Long taskId) {
        CompetitionRegistrationExportTaskWorkerService worker = workerProvider.getIfAvailable();
        if (worker == null) {
            log.info("registration export task queued for external worker taskId={}", taskId);
            return;
        }
        try {
            executorService.submit(() -> worker.processPendingTasks(1));
        } catch (RejectedExecutionException exception) {
            log.warn("registration export worker submission rejected taskId={}", taskId);
        }
    }

    private long countRegistrations(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        if (!request.getRegistrationIds().isEmpty()) {
            return loadSelectedRegistrations(currentUser, request).size();
        }
        PageResponse<CompetitionRegistrationVO.Registration> page = registrationAppService.listRegistrations(
                currentUser,
                1,
                1,
                request.getCompetitionId(),
                request.getStatus(),
                request.getKeyword(),
                true
        );
        return Math.max(0L, page.getTotal());
    }

    private List<CompetitionRegistrationVO.Registration> loadRegistrations(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request,
            Long taskId
    ) {
        if (!request.getRegistrationIds().isEmpty()) {
            CurrentUser refreshedUser = buildQueuedAsyncUser(
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getSimulatedRoleId(),
                    taskId
            );
            return loadSelectedRegistrations(refreshedUser, request);
        }
        List<CompetitionRegistrationVO.Registration> records = new ArrayList<>();
        long pageNo = 1L;
        while (true) {
            CurrentUser refreshedUser = buildQueuedAsyncUser(
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    currentUser.getSimulatedRoleId(),
                    taskId
            );
            PageResponse<CompetitionRegistrationVO.Registration> page = registrationAppService.listRegistrations(
                    refreshedUser,
                    pageNo,
                    EXPORT_PAGE_SIZE,
                    request.getCompetitionId(),
                    request.getStatus(),
                    request.getKeyword(),
                    true
            );
            records.addAll(page.getRecords() == null ? List.of() : page.getRecords());
            if (!Boolean.TRUE.equals(page.getHasMore())) {
                break;
            }
            pageNo++;
        }
        return records;
    }

    private List<CompetitionRegistrationVO.Registration> loadSelectedRegistrations(
            CurrentUser currentUser,
            CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        List<CompetitionRegistrationVO.Registration> registrations = new ArrayList<>();
        for (Long registrationId : request.getRegistrationIds()) {
            CompetitionRegistrationVO.Registration registration =
                    registrationAppService.getRegistration(currentUser, registrationId);
            if (!request.getCompetitionId().equals(registration.getCompetitionId())
                    || !datasetRepository.isLinked(request.getCompetitionId(), registrationId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "Registration is not part of the requested competition dataset");
            }
            registrations.add(registration);
        }
        return registrations;
    }

    private CompetitionRegistrationDTO.RegistrationExportRequest normalizeRequest(
            CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        if (request == null || request.getCompetitionId() == null || request.getCompetitionId() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Competition id is required");
        }
        List<Long> registrationIds = request.getRegistrationIds() == null
                ? List.of()
                : request.getRegistrationIds();
        if (registrationIds.size() > MAX_SELECTED_REGISTRATIONS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Too many selected registrations");
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        for (Long registrationId : registrationIds) {
            if (registrationId == null || registrationId <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Registration id must be positive");
            }
            normalizedIds.add(registrationId);
        }
        CompetitionRegistrationDTO.RegistrationExportRequest normalized =
                new CompetitionRegistrationDTO.RegistrationExportRequest();
        normalized.setCompetitionId(request.getCompetitionId());
        String status = trimToNull(request.getStatus());
        normalized.setStatus(status == null ? null : status.toUpperCase(Locale.ROOT));
        normalized.setKeyword(trimToNull(request.getKeyword()));
        normalized.setRegistrationIds(List.copyOf(normalizedIds));
        return normalized;
    }

    private List<RegistrationExportRow> toRows(
            CompetitionRegistrationVO.Registration registration,
            boolean includeSensitiveData,
            List<CollectedExportField> collectedFields
    ) {
        JsonNode registrationSnapshot = readJson(registration.getRegistrationSnapshotJson());
        JsonNode teamSnapshot = readJson(registration.getTeamSnapshotJson());
        JsonNode projectSnapshot = readJson(registration.getProjectSnapshotJson());
        JsonNode memberArray = readJson(registration.getMemberSnapshotJson());
        List<JsonNode> members = new ArrayList<>();
        if (memberArray.isArray()) {
            memberArray.forEach(members::add);
        }
        if (members.isEmpty()) {
            members.add(objectMapper.createObjectNode());
        }
        List<RegistrationExportRow> rows = new ArrayList<>();
        for (int index = 0; index < members.size(); index++) {
            JsonNode member = members.get(index);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("registrationNo", registration.getRegistrationNo());
            values.put("participantNo", registration.getParticipantNo());
            values.put("status", registrationStatusLabel(registration.getStatus()));
            values.put("createdAt", registration.getCreatedAt());
            values.put("memberCount", registration.getMemberCount());
            values.put("memberIndex", index + 1);
            for (CollectedExportField field : collectedFields) {
                values.put(
                        collectedFieldColumnKey(field),
                        collectedFieldValue(
                                field,
                                registration,
                                registrationSnapshot,
                                teamSnapshot,
                                projectSnapshot,
                                member,
                                includeSensitiveData
                        )
                );
            }
            values.put("materialSubmissionCount", registration.getMaterialSubmissionCount());
            values.put("materialFileCount", registration.getMaterialFileCount());
            rows.add(new RegistrationExportRow(values));
        }
        return rows;
    }

    private List<CollectedExportField> collectExportFields(
            List<CompetitionRegistrationVO.Registration> registrations
    ) {
        Map<String, CollectedExportField> fields = new LinkedHashMap<>();
        for (CompetitionRegistrationVO.Registration registration : safeList(registrations)) {
            JsonNode schema = readJson(registration.getCollectionSchemaSnapshotJson());
            if (!schema.isArray()) {
                continue;
            }
            for (JsonNode item : schema) {
                String scope = item.path("scope").asText("").trim().toUpperCase(Locale.ROOT);
                String itemKey = item.path("itemKey").asText("").trim();
                if (!COLLECTION_FIELD_SCOPES.contains(scope) || !StringUtils.hasText(itemKey)) {
                    continue;
                }
                String title = item.path("title").asText("").trim();
                String fieldType = item.path("fieldType").asText("TEXT").trim().toUpperCase(Locale.ROOT);
                String groupLabel = item.path("groupLabel").asText("").trim();
                CollectedExportField field = new CollectedExportField(
                        scope,
                        itemKey,
                        StringUtils.hasText(title) ? title : itemKey,
                        fieldType,
                        groupLabel
                );
                fields.putIfAbsent(collectedFieldIdentity(field), field);
            }
        }
        if (fields.isEmpty()) {
            // Legacy registrations created before schema snapshots still need a useful minimal export.
            ensureFallbackCollectedField(fields, TEAM_FIELD_SCOPE, "teamName", "团队名称");
            ensureFallbackCollectedField(fields, MEMBER_FIELD_SCOPE, "memberName", "成员姓名");
            ensureFallbackCollectedField(fields, PROJECT_FIELD_SCOPE, "title", "项目名称");
        }
        return List.copyOf(fields.values());
    }

    private void ensureFallbackCollectedField(
            Map<String, CollectedExportField> fields,
            String scope,
            String itemKey,
            String title
    ) {
        CollectedExportField fallback = new CollectedExportField(scope, itemKey, title, "TEXT", "");
        fields.putIfAbsent(collectedFieldIdentity(fallback), fallback);
    }

    private List<ExportColumn<RegistrationExportRow>> columns(List<CollectedExportField> collectedFields) {
        List<ExportColumn<RegistrationExportRow>> columns = new ArrayList<>();
        columns.add(column("registrationNo", "报名编号"));
        columns.add(column("participantNo", "参赛编号"));
        columns.add(column("status", "报名状态"));
        columns.add(column("createdAt", "报名时间"));
        columns.add(column("memberCount", "团队人数"));
        columns.add(column("memberIndex", "成员序号"));

        Map<String, Integer> titleCounts = new LinkedHashMap<>();
        for (CollectedExportField field : collectedFields) {
            titleCounts.merge(field.title(), 1, Integer::sum);
        }
        for (CollectedExportField field : collectedFields) {
            columns.add(column(
                    collectedFieldColumnKey(field),
                    collectedFieldLabel(field, titleCounts.getOrDefault(field.title(), 0) > 1)
            ));
        }
        columns.add(column("materialSubmissionCount", "材料提交次数"));
        columns.add(column("materialFileCount", "材料文件数"));
        return List.copyOf(columns);
    }

    private List<String> taskFieldKeys() {
        return List.of(
                "registrationNo",
                "participantNo",
                "status",
                "createdAt",
                "memberCount",
                "memberIndex",
                "configuredCollectionFields",
                "materialSubmissionCount",
                "materialFileCount"
        );
    }

    private ExportColumn<RegistrationExportRow> column(String key, String label) {
        return new ExportColumn<>(key, label, true, row -> row.values().get(key));
    }

    private String collectedFieldLabel(CollectedExportField field, boolean duplicateTitle) {
        if (!duplicateTitle) {
            return field.title();
        }
        String scopeLabel = switch (field.scope()) {
            case REGISTRATION_FIELD_SCOPE -> "报名";
            case TEAM_FIELD_SCOPE -> "团队";
            case MEMBER_FIELD_SCOPE -> "成员";
            case PROJECT_FIELD_SCOPE -> "项目";
            default -> "字段";
        };
        return StringUtils.hasText(field.groupLabel())
                ? scopeLabel + "-" + field.groupLabel() + "-" + field.title()
                : scopeLabel + "-" + field.title();
    }

    private String collectedFieldValue(
            CollectedExportField field,
            CompetitionRegistrationVO.Registration registration,
            JsonNode registrationSnapshot,
            JsonNode teamSnapshot,
            JsonNode projectSnapshot,
            JsonNode memberSnapshot,
            boolean includeSensitiveData
    ) {
        String value;
        if (isIntellectualPropertyField(field)) {
            value = repeatedProjectFieldValue(projectSnapshot, field);
        } else {
            JsonNode source = switch (field.scope()) {
                case REGISTRATION_FIELD_SCOPE -> registrationSnapshot;
                case TEAM_FIELD_SCOPE -> teamSnapshot;
                case MEMBER_FIELD_SCOPE -> memberSnapshot;
                case PROJECT_FIELD_SCOPE -> projectSnapshot;
                default -> null;
            };
            JsonNode node = resolveCollectedFieldNode(source, field);
            if (isMissing(node)) {
                node = fallbackStandardFieldNode(registration, field);
            }
            value = humanValue(node);
        }
        return applySensitiveFieldPolicy(field, value, includeSensitiveData);
    }

    private JsonNode resolveCollectedFieldNode(JsonNode source, CollectedExportField field) {
        if (source == null || !source.isObject()) {
            return null;
        }
        String standardKey = resolveStandardCollectedFieldKey(field.scope(), field.itemKey());
        if (standardKey != null) {
            JsonNode standardValue = source.get(standardKey);
            if (!isMissing(standardValue)) {
                return standardValue;
            }
            if (MEMBER_FIELD_SCOPE.equals(field.scope()) && "employeeNo".equals(standardKey)) {
                JsonNode legacyStudentNo = source.get("studentNo");
                if (!isMissing(legacyStudentNo)) {
                    return legacyStudentNo;
                }
            }
        }
        JsonNode extraValues = extraValuesNode(source);
        if (extraValues != null) {
            JsonNode configuredValue = extraValues.get(field.itemKey());
            if (!isMissing(configuredValue)) {
                return configuredValue;
            }
        }
        JsonNode legacyValue = source.get(field.itemKey());
        return isMissing(legacyValue) ? null : legacyValue;
    }

    private JsonNode fallbackStandardFieldNode(
            CompetitionRegistrationVO.Registration registration,
            CollectedExportField field
    ) {
        String standardKey = resolveStandardCollectedFieldKey(field.scope(), field.itemKey());
        if (TEAM_FIELD_SCOPE.equals(field.scope()) && "teamName".equals(standardKey)) {
            return objectMapper.valueToTree(registration.getTeamName());
        }
        if (PROJECT_FIELD_SCOPE.equals(field.scope()) && "title".equals(standardKey)) {
            return objectMapper.valueToTree(registration.getProjectTitle());
        }
        return null;
    }

    private JsonNode extraValuesNode(JsonNode source) {
        if (source == null || !source.isObject()) {
            return null;
        }
        JsonNode extraValues = source.get("extraValues");
        if (extraValues != null && extraValues.isObject()) {
            return extraValues;
        }
        JsonNode extraValuesJson = source.get("extraValuesJson");
        if (extraValuesJson != null && extraValuesJson.isTextual()) {
            JsonNode parsed = readJson(extraValuesJson.asText());
            return parsed.isObject() ? parsed : null;
        }
        return null;
    }

    private String repeatedProjectFieldValue(JsonNode projectSnapshot, CollectedExportField field) {
        JsonNode extraValues = extraValuesNode(projectSnapshot);
        if (extraValues == null) {
            return "";
        }
        JsonNode entries = extraValues.get(INTELLECTUAL_PROPERTY_ENTRIES_KEY);
        if (entries == null || !entries.isArray()) {
            return humanValue(extraValues.get(field.itemKey()));
        }
        List<String> values = new ArrayList<>();
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                continue;
            }
            String value = humanValue(entry.get(field.itemKey()));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        if (values.size() <= 1) {
            return values.isEmpty() ? "" : values.getFirst();
        }
        List<String> numbered = new ArrayList<>();
        for (int index = 0; index < values.size(); index += 1) {
            numbered.add((index + 1) + ". " + values.get(index));
        }
        return String.join("；", numbered);
    }

    private String humanValue(JsonNode value) {
        if (isMissing(value)) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText().trim();
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "是" : "否";
        }
        if (value.isNumber()) {
            return value.asText();
        }
        if (value.isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode item : value) {
                String text = humanValue(item);
                if (StringUtils.hasText(text)) {
                    items.add(text);
                }
            }
            return String.join("、", items);
        }
        if (value.isObject()) {
            List<String> entries = new ArrayList<>();
            value.fields().forEachRemaining(entry -> {
                String text = humanValue(entry.getValue());
                if (StringUtils.hasText(text)) {
                    entries.add(entry.getKey() + "：" + text);
                }
            });
            return String.join("；", entries);
        }
        return value.asText("").trim();
    }

    private String applySensitiveFieldPolicy(
            CollectedExportField field,
            String value,
            boolean includeSensitiveData
    ) {
        if (includeSensitiveData || PROJECT_FIELD_SCOPE.equals(field.scope()) || !StringUtils.hasText(value)) {
            return value;
        }
        String standardKey = resolveStandardCollectedFieldKey(field.scope(), field.itemKey());
        if (TEAM_FIELD_SCOPE.equals(field.scope()) && "teamName".equals(standardKey)) {
            return value;
        }
        if (MEMBER_FIELD_SCOPE.equals(field.scope())) {
            if ("memberName".equals(standardKey)) {
                return maskName(value);
            }
            if ("employeeNo".equals(standardKey)) {
                return maskIdentifier(value);
            }
            if ("departmentName".equals(standardKey) || "role".equals(field.itemKey())) {
                return value;
            }
        }
        return "";
    }

    private String resolveStandardCollectedFieldKey(String scope, String itemKey) {
        if (MEMBER_FIELD_SCOPE.equals(scope) && "role".equals(itemKey)) {
            return null;
        }
        String normalized = itemKey == null
                ? ""
                : itemKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return switch (scope) {
            case TEAM_FIELD_SCOPE -> switch (normalized) {
                case "teamname", "name" -> "teamName";
                case "teamtype", "type" -> "teamType";
                case "avatarurl", "avatar" -> "avatarUrl";
                case "description", "teamdescription", "intro" -> "description";
                default -> null;
            };
            case MEMBER_FIELD_SCOPE -> switch (normalized) {
                case "membername", "name" -> "memberName";
                case "employeeno", "studentno", "memberno" -> "employeeNo";
                case "departmentname", "department" -> "departmentName";
                case "remark", "note" -> "remark";
                default -> null;
            };
            case PROJECT_FIELD_SCOPE -> switch (normalized) {
                case "projecttitle", "projectname", "title", "name" -> "title";
                case "imageurl", "projectimage", "projectavatar", "logourl", "logo" -> "imageUrl";
                case "projectdescription", "description", "intro" -> "description";
                default -> null;
            };
            default -> null;
        };
    }

    private String registrationStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return REGISTRATION_STATUS_LABELS.getOrDefault(normalized, normalized);
    }

    private boolean isIntellectualPropertyField(CollectedExportField field) {
        return PROJECT_FIELD_SCOPE.equals(field.scope())
                && INTELLECTUAL_PROPERTY_GROUP.equals(field.groupLabel());
    }

    private boolean isMissing(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode();
    }

    private String collectedFieldIdentity(CollectedExportField field) {
        return field.scope() + ":" + field.itemKey();
    }

    private String collectedFieldColumnKey(CollectedExportField field) {
        return "collected:" + collectedFieldIdentity(field);
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.createArrayNode();
        }
    }

    private String maskName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= 1) {
            return "*";
        }
        int firstEnd = normalized.offsetByCodePoints(0, 1);
        return normalized.substring(0, firstEnd) + "*".repeat(Math.min(6, codePoints - 1));
    }

    private String maskIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return "*".repeat(normalized.length());
        }
        return normalized.substring(0, 2)
                + "*".repeat(Math.min(12, normalized.length() - 4))
                + normalized.substring(normalized.length() - 2);
    }

    private boolean hasPermission(CurrentUser currentUser, String permission) {
        Set<String> permissions = currentUser == null || currentUser.getPermissions() == null
                ? Set.of()
                : currentUser.getPermissions();
        return permissions.contains("*") || permissions.contains(permission);
    }

    private String buildFileName(Long competitionId, String exportType) {
        String suffix = EXPORT_TYPE_MATERIAL_ZIP.equals(exportType) ? "materials" : "registration-export";
        String extension = EXPORT_TYPE_MATERIAL_ZIP.equals(exportType) ? ".zip" : ".xlsx";
        return "competition-" + competitionId + "-" + suffix + "-"
                + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + extension;
    }

    private void requireMaterialDownloadPermission(CurrentUser currentUser) {
        if (!hasPermission(currentUser, MATERIAL_DOWNLOAD_PERMISSION)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + MATERIAL_DOWNLOAD_PERMISSION);
        }
    }

    private byte[] requireMaterialContent(FileContentDTO file, Long expectedFileId) {
        if (file == null
                || file.id() == null
                || !expectedFileId.equals(file.id())
                || file.content() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Registration material file is unavailable");
        }
        return file.content();
    }

    private String registrationFolder(
            CompetitionRegistrationVO.Registration registration,
            int registrationIndex
    ) {
        String indexSegment = String.format(Locale.ROOT, "%03d", registrationIndex);
        String registrationSegment = sanitizeSegment(
                StringUtils.hasText(registration.getRegistrationNo())
                        ? registration.getRegistrationNo()
                        : "registration-" + registration.getId()
        );
        String teamSegment = sanitizeSegment(
                StringUtils.hasText(registration.getTeamName())
                        ? registration.getTeamName()
                        : "team"
        );
        return indexSegment + "-" + registrationSegment + "-" + teamSegment;
    }

    private String materialPath(
            String registrationFolder,
            CompetitionRegistrationVO.MaterialSubmission submission,
            CompetitionRegistrationVO.MaterialValue value,
            FileContentDTO file
    ) {
        String stageSegment = "stage-" + submission.getStageId();
        String fieldSegment = sanitizeSegment(
                StringUtils.hasText(value.getFieldKey()) ? value.getFieldKey() : "material"
        );
        String originalName = sanitizeSegment(
                StringUtils.hasText(file.originalFileName())
                        ? file.originalFileName()
                        : "file-" + value.getFileId() + extension(file.fileExtension())
        );
        return registrationFolder + "/" + stageSegment + "/"
                + fieldSegment + "-" + value.getFileId() + "-" + originalName;
    }

    private String uniqueZipPath(Set<String> usedPaths, String requestedPath) {
        String candidate = requestedPath;
        int duplicate = 2;
        while (!usedPaths.add(candidate)) {
            int slash = requestedPath.lastIndexOf('/');
            int dot = requestedPath.lastIndexOf('.');
            if (dot <= slash) {
                dot = requestedPath.length();
            }
            candidate = requestedPath.substring(0, dot)
                    + "-" + duplicate
                    + requestedPath.substring(dot);
            duplicate++;
        }
        return candidate;
    }

    private String sanitizeSegment(String value) {
        String sanitized = value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\.{2,}", "_")
                .replaceAll("\\s+", " ");
        if (sanitized.isBlank() || ".".equals(sanitized)) {
            sanitized = "unnamed";
        }
        return sanitized.length() <= 100 ? sanitized : sanitized.substring(0, 100);
    }

    private String extension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        String normalized = extension.trim().replaceAll("[^A-Za-z0-9]", "");
        return normalized.isEmpty() ? "" : "." + normalized;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private <T> Set<T> safeSet(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    record RegistrationExportRow(Map<String, Object> values) {
    }

    record CollectedExportField(
            String scope,
            String itemKey,
            String title,
            String fieldType,
            String groupLabel
    ) {
    }

    public static class AsyncTaskPayload {
        private CompetitionRegistrationDTO.RegistrationExportRequest request;
        private String fileName;
        private Long simulatedRoleId;
        private String exportType;

        public CompetitionRegistrationDTO.RegistrationExportRequest getRequest() {
            return request;
        }

        public void setRequest(CompetitionRegistrationDTO.RegistrationExportRequest request) {
            this.request = request;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Long getSimulatedRoleId() {
            return simulatedRoleId;
        }

        public void setSimulatedRoleId(Long simulatedRoleId) {
            this.simulatedRoleId = simulatedRoleId;
        }

        public String getExportType() {
            return StringUtils.hasText(exportType) ? exportType : EXPORT_TYPE_DATA_XLSX;
        }

        public void setExportType(String exportType) {
            this.exportType = exportType;
        }
    }
}
