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
    private static final String ASYNC_SESSION_PREFIX = "internal-registration-export-task-";
    private static final long EXPORT_PAGE_SIZE = 100L;
    private static final int MAX_SELECTED_REGISTRATIONS = 500;
    private static final long MAX_MATERIAL_FILE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_MATERIAL_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final String MATERIAL_REFERENCE_TYPE = "competition.registration.material";
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
                columns().stream().map(ExportColumn::key).toList(),
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
                ASYNC_SESSION_PREFIX + taskId,
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
                    registrationAppService.listMaterials(refreshedUser, registration.getId()),
                    hasPermission(refreshedUser, SENSITIVE_EXPORT_PERMISSION)
            ));
        }
        return excelExportService.export("报名团队资料", columns(), rows);
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
        List<Map<String, Object>> manifest = new ArrayList<>();
        long totalBytes = 0L;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            Set<String> usedPaths = new LinkedHashSet<>();
            for (CompetitionRegistrationVO.Registration registration : registrations) {
                CurrentUser refreshedUser = buildQueuedAsyncUser(
                        currentUser.getUserId(),
                        currentUser.getUserUuid(),
                        currentUser.getSimulatedRoleId(),
                        taskId
                );
                requireMaterialDownloadPermission(refreshedUser);
                List<CompetitionRegistrationVO.MaterialSubmission> submissions =
                        registrationAppService.listMaterials(refreshedUser, registration.getId());
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
                                materialPath(registration, submission, value, file)
                        );
                        zip.putNextEntry(new ZipEntry(path));
                        zip.write(content);
                        zip.closeEntry();

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("competitionId", registration.getCompetitionId());
                        item.put("registrationId", registration.getId());
                        item.put("registrationNo", registration.getRegistrationNo());
                        item.put("teamName", registration.getTeamName());
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
            List<CompetitionRegistrationVO.MaterialSubmission> materials,
            boolean includeSensitiveData
    ) {
        JsonNode memberArray = readJson(registration.getMemberSnapshotJson());
        List<JsonNode> members = new ArrayList<>();
        if (memberArray.isArray()) {
            memberArray.forEach(members::add);
        }
        if (members.isEmpty()) {
            members.add(objectMapper.createObjectNode());
        }
        String restricted = "[需要“导出报名敏感数据”权限]";
        String materialsJson = includeSensitiveData
                ? writeJson(materials == null ? List.of() : materials)
                : restricted;
        List<RegistrationExportRow> rows = new ArrayList<>();
        for (int index = 0; index < members.size(); index++) {
            JsonNode member = members.get(index);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("competitionId", registration.getCompetitionId());
            values.put("registrationNo", registration.getRegistrationNo());
            values.put("participantNo", registration.getParticipantNo());
            values.put("status", registration.getStatus());
            values.put("teamName", registration.getTeamName());
            values.put("projectTitle", registration.getProjectTitle());
            values.put("memberCount", registration.getMemberCount());
            values.put("memberIndex", index + 1);
            String memberName = firstText(member, "memberName", "name", "realName");
            values.put("memberName", includeSensitiveData ? memberName : maskName(memberName));
            values.put("memberRole", firstText(member, "role", "roleName"));
            String studentEmployeeNo = firstText(member, "studentNo", "employeeNo");
            values.put(
                    "studentEmployeeNo",
                    includeSensitiveData ? studentEmployeeNo : maskIdentifier(studentEmployeeNo)
            );
            values.put("department", firstText(member, "departmentName", "department"));
            values.put("remark", includeSensitiveData ? firstText(member, "remark") : "");
            values.put("materialSubmissionCount", registration.getMaterialSubmissionCount());
            values.put("materialFileCount", registration.getMaterialFileCount());
            values.put("createdAt", registration.getCreatedAt());
            values.put(
                    "registrationSnapshotJson",
                    includeSensitiveData ? registration.getRegistrationSnapshotJson() : restricted
            );
            values.put(
                    "teamSnapshotJson",
                    includeSensitiveData ? registration.getTeamSnapshotJson() : restricted
            );
            values.put("projectSnapshotJson", registration.getProjectSnapshotJson());
            values.put("memberSnapshotJson", includeSensitiveData ? member.toString() : restricted);
            values.put("collectionSchemaSnapshotJson", registration.getCollectionSchemaSnapshotJson());
            values.put("materialsJson", materialsJson);
            rows.add(new RegistrationExportRow(values));
        }
        return rows;
    }

    private List<ExportColumn<RegistrationExportRow>> columns() {
        return List.of(
                column("competitionId", "比赛ID"),
                column("registrationNo", "报名编号"),
                column("participantNo", "参赛编号"),
                column("status", "报名状态"),
                column("teamName", "团队名称"),
                column("projectTitle", "项目名称"),
                column("memberCount", "团队人数"),
                column("memberIndex", "成员序号"),
                column("memberName", "成员姓名"),
                column("memberRole", "成员角色"),
                column("studentEmployeeNo", "学号/工号"),
                column("department", "院系/部门"),
                column("remark", "成员备注"),
                column("materialSubmissionCount", "材料提交次数"),
                column("materialFileCount", "材料文件数"),
                column("createdAt", "报名时间"),
                column("registrationSnapshotJson", "报名完整快照(JSON)"),
                column("teamSnapshotJson", "团队完整快照(JSON)"),
                column("projectSnapshotJson", "项目完整快照(JSON)"),
                column("memberSnapshotJson", "成员完整快照(JSON)"),
                column("collectionSchemaSnapshotJson", "采集结构快照(JSON)"),
                column("materialsJson", "阶段材料清单(JSON)")
        );
    }

    private ExportColumn<RegistrationExportRow> column(String key, String label) {
        return new ExportColumn<>(key, label, true, row -> row.values().get(key));
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize registration export data", exception);
        }
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
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

    private String materialPath(
            CompetitionRegistrationVO.Registration registration,
            CompetitionRegistrationVO.MaterialSubmission submission,
            CompetitionRegistrationVO.MaterialValue value,
            FileContentDTO file
    ) {
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
        String stageSegment = "stage-" + submission.getStageId();
        String fieldSegment = sanitizeSegment(
                StringUtils.hasText(value.getFieldKey()) ? value.getFieldKey() : "material"
        );
        String originalName = sanitizeSegment(
                StringUtils.hasText(file.originalFileName())
                        ? file.originalFileName()
                        : "file-" + value.getFileId() + extension(file.fileExtension())
        );
        return registrationSegment + "-" + teamSegment + "/" + stageSegment + "/"
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
