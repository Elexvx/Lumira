package com.lumira.saas.modules.system.user.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.export.ExcelExportService;
import com.lumira.saas.modules.system.export.ExportColumn;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportFieldVO;
import com.lumira.saas.modules.system.export.ExportTaskEntity;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class UserExportAppService {
    private static final Logger log = LoggerFactory.getLogger(UserExportAppService.class);
    private static final String MODULE_KEY = "system:user";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final long SYNC_THRESHOLD = 5000L;
    private static final long EXPORT_PAGE_SIZE = 100L;
    private static final String PERMISSION_USER_EXPORT = "system:user:export";
    private static final String PERMISSION_USER_SENSITIVE_VIEW = "system:user:sensitive:view";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int MAX_EXPORT_FIELDS = 30;
    private static final int MAX_FILTER_TEXT_LENGTH = 128;
    private static final int MAX_DATE_TEXT_LENGTH = 32;
    private static final Set<String> SENSITIVE_EXPORT_FIELDS = Set.of("idCardNumber");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ASYNC_EXPORT_SESSION_PREFIX = "internal-export-task-";

    private final SystemUserManagementAppService systemUserManagementAppService;
    private final ExcelExportService excelExportService;
    private final ExportTaskService exportTaskService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final ExecutorService executorService;
    private final ObjectProvider<UserExportTaskWorkerService> userExportTaskWorkerServiceProvider;
    private final boolean enforceTrustedUserResolution;

    public UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService,
            PermissionSnapshotService permissionSnapshotService,
            ObjectProvider<ExecutorService> executorServiceProvider
    ) {
        this(
                systemUserManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                null,
                null,
                executorServiceProvider,
                null,
                false
        );
    }

    @Autowired
    public UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            ObjectProvider<ExecutorService> executorServiceProvider
    ) {
        this(
                systemUserManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                executorServiceProvider,
                null,
                true
        );
    }

    public UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            ObjectProvider<ExecutorService> executorServiceProvider
    ) {
        this(
                systemUserManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                executorServiceProvider,
                null,
                true
        );
    }

    public UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            ObjectProvider<ExecutorService> executorServiceProvider,
            ObjectProvider<UserExportTaskWorkerService> userExportTaskWorkerServiceProvider
    ) {
        this(
                systemUserManagementAppService,
                excelExportService,
                exportTaskService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                executorServiceProvider,
                userExportTaskWorkerServiceProvider,
                true
        );
    }

    private UserExportAppService(
            SystemUserManagementAppService systemUserManagementAppService,
            ExcelExportService excelExportService,
            ExportTaskService exportTaskService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            ObjectProvider<ExecutorService> executorServiceProvider,
            ObjectProvider<UserExportTaskWorkerService> userExportTaskWorkerServiceProvider,
            boolean enforceTrustedUserResolution
    ) {
        this.systemUserManagementAppService = systemUserManagementAppService;
        this.excelExportService = excelExportService;
        this.exportTaskService = exportTaskService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.executorService = executorServiceProvider.getIfAvailable(Executors::newVirtualThreadPerTaskExecutor);
        this.userExportTaskWorkerServiceProvider = userExportTaskWorkerServiceProvider;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public List<ExportFieldVO> listUserExportFields(CurrentUser currentUser) {
        CurrentUser trustedUser = refreshTrustedCurrentUserSnapshot(currentUser);
        List<ExportColumn<SystemVO.UserVO>> columns = userColumns();
        List<ExportFieldVO> fields = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index += 1) {
            ExportColumn<SystemVO.UserVO> column = columns.get(index);
            fields.add(new ExportFieldVO(column.key(), column.label(), column.defaultSelected(), index + 1));
        }
        return fields;
    }

    public ExportVO.ExportStartVO exportUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        CurrentUser trustedUser = refreshTrustedCurrentUserSnapshot(currentUser);
        requireRequest(request, "User export request");
        ExportDTO.UserExportRequest normalizedRequest = normalizeRequest(request);
        List<ExportColumn<SystemVO.UserVO>> selectedColumns = List.copyOf(selectedColumns(trustedUser, normalizedRequest.getFields()));
        long total = countUsers(trustedUser, normalizedRequest);
        String fileName = buildFileName();
        if (total <= SYNC_THRESHOLD) {
            byte[] content = excelExportService.export("user management", selectedColumns, loadAllUsers(trustedUser, normalizedRequest));
            ExportVO.ExportStartVO response = new ExportVO.ExportStartVO();
            response.setMode("SYNC");
            response.setFileName(fileName);
            response.setContentType(XLSX_CONTENT_TYPE);
            response.setContentBase64(Base64.getEncoder().encodeToString(content));
            response.setTotalCount(total);
            return response;
        }

        ExportTaskEntity task = exportTaskService.createTask(
                trustedUser,
                MODULE_KEY,
                asyncTaskPayload(normalizedRequest, fileName, trustedUser.getSimulatedRoleId()),
                selectedColumns.stream().map(ExportColumn::key).toList(),
                total
        );
        CurrentUser asyncUser = trustedCurrentUserSnapshot(trustedUser);
        asyncUser.setSessionId(asyncExportSessionId(task.getId()));
        submitAsyncExport(task.getId(), asyncUser, normalizedRequest, selectedColumns, fileName);
        ExportVO.ExportStartVO response = new ExportVO.ExportStartVO();
        response.setMode("ASYNC");
        response.setTaskId(task.getId());
        response.setFileName(fileName);
        response.setTotalCount(total);
        return response;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    private void submitAsyncExport(
            Long taskId,
            CurrentUser asyncUser,
            ExportDTO.UserExportRequest normalizedRequest,
            List<ExportColumn<SystemVO.UserVO>> selectedColumns,
            String fileName
    ) {
        UserExportTaskWorkerService workerService = userExportTaskWorkerServiceProvider == null
                ? null
                : userExportTaskWorkerServiceProvider.getIfAvailable();
        try {
            if (workerService != null) {
                executorService.submit(() -> workerService.processPendingTasks(1));
                return;
            }
            executorService.submit(() -> runAsyncExport(asyncUser, normalizedRequest, selectedColumns, taskId, fileName));
        } catch (RejectedExecutionException exception) {
            if (workerService != null) {
                log.warn("user export worker submission rejected for taskId={}: {}", taskId, exception.getMessage());
                return;
            }
            try {
                exportTaskService.markFailedFromTrustedSnapshot(asyncUser, taskId, exception);
            } catch (RuntimeException ignored) {
                log.warn("failed to mark rejected export task as failed taskId={}", taskId);
            }
            throw exception;
        }
    }

    private void runAsyncExport(
            CurrentUser currentUser,
            ExportDTO.UserExportRequest request,
            List<ExportColumn<SystemVO.UserVO>> selectedColumns,
            Long taskId,
            String fileName
    ) {
        try {
            CurrentUser refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser, true);
            exportTaskService.markRunningFromTrustedSnapshot(refreshedUser, taskId);
            byte[] content = excelExportService.export("user management", selectedColumns, loadAllUsers(refreshedUser, request));
            refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser, true);
            FileObjectDTO uploaded = exportTaskService.uploadExportFile(refreshedUser, content, fileName, "user export", "export,user", "user async export");
            refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser, true);
            exportTaskService.markSuccessFromTrustedSnapshot(refreshedUser, taskId, uploaded, fileName);
        } catch (Exception exception) {
            try {
                exportTaskService.markFailedFromTrustedSnapshot(refreshTrustedCurrentUserSnapshot(currentUser, true), taskId, exception);
            } catch (RuntimeException ignored) {
                // A revoked or expired session must not keep mutating async export state.
            }
        }
    }

    CurrentUser buildQueuedAsyncUser(Long userId, String userUuid, Long simulatedRoleId, Long taskId) {
        simulatedRoleId = normalizeSimulatedRoleId(simulatedRoleId);
        if (permissionSnapshotService == null || systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        if (userId == null || userId <= 0 || !org.springframework.util.StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        String normalizedUserUuid = userUuid.trim();
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null
                || userSnapshot.userId() == null
                || !userId.equals(userSnapshot.userId())
                || !org.springframework.util.StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
        }
        String currentUsername = org.springframework.util.StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
        if (!org.springframework.util.StringUtils.hasText(currentUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
        }
        PermissionSnapshotService.PermissionSnapshot permissionSnapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(userId, normalizedUserUuid, simulatedRoleId)
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (permissionSnapshot == null || permissionSnapshot.getVersion() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user permission snapshot is unavailable");
        }
        CurrentUser currentUser = buildCurrentUserSnapshot(
                userId,
                currentUsername,
                asyncExportSessionId(taskId),
                1,
                permissionSnapshot.getPermissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.getPermissions()),
                permissionSnapshot.getRoleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.getRoleIds()),
                permissionSnapshot.getPrimaryDeptId(),
                permissionSnapshot.getDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.getDeptIds()),
                permissionSnapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.getDescendantDeptIds()),
                permissionSnapshot.getDataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.getDataScopes())
        );
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissionsVersion(permissionSnapshot.getVersion());
        currentUser.setDefaultHomePath(permissionSnapshot.getDefaultHomePath());
        currentUser.setSimulatedRoleId(simulatedRoleId);
        return currentUser;
    }

    byte[] exportUsersFromTrustedSnapshot(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        CurrentUser refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser, true);
        ExportDTO.UserExportRequest normalizedRequest = normalizeRequest(request);
        List<ExportColumn<SystemVO.UserVO>> selectedColumns = List.copyOf(selectedColumns(refreshedUser, normalizedRequest.getFields()));
        return excelExportService.export("user management", selectedColumns, loadAllUsers(refreshedUser, normalizedRequest));
    }

    private long countUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        CurrentUser refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser);
        PageResponse<SystemVO.UserVO> page = systemUserManagementAppService.listUsersFromTrustedSnapshot(
                refreshedUser,
                request.getUserId(),
                null,
                request.getUsername(),
                request.getMobile(),
                request.getEmail(),
                request.getDeptId(),
                request.getStatus(),
                request.getSource(),
                request.getRegisteredStart(),
                request.getRegisteredEnd(),
                request.getLastLoginStart(),
                request.getLastLoginEnd(),
                null,
                null,
                1,
                1
        );
        return Math.max(page.getTotal(), 0L);
    }

    private List<SystemVO.UserVO> loadAllUsers(CurrentUser currentUser, ExportDTO.UserExportRequest request) {
        List<SystemVO.UserVO> users = new ArrayList<>();
        Long cursorId = null;
        String cursorCreatedAt = null;
        while (true) {
            CurrentUser refreshedUser = refreshTrustedCurrentUserSnapshot(currentUser);
            // Re-check field-level permission on every page so revoked sensitive access
            // cannot continue exporting through a long-running job.
            selectedColumns(refreshedUser, request.getFields());
            PageResponse<SystemVO.UserVO> page = systemUserManagementAppService.listUsersFromTrustedSnapshot(
                    refreshedUser,
                    request.getUserId(),
                    null,
                    request.getUsername(),
                    request.getMobile(),
                    request.getEmail(),
                    request.getDeptId(),
                    request.getStatus(),
                    request.getSource(),
                    request.getRegisteredStart(),
                    request.getRegisteredEnd(),
                    request.getLastLoginStart(),
                    request.getLastLoginEnd(),
                    cursorId,
                    cursorCreatedAt,
                    1,
                    EXPORT_PAGE_SIZE
            );
            users.addAll(page.getRecords());
            if (!Boolean.TRUE.equals(page.getHasMore()) || CollectionUtils.isEmpty(page.getRecords())) {
                return users;
            }
            cursorId = page.getNextCursorId();
            cursorCreatedAt = page.getNextCursorCreatedAt();
            if (cursorId == null && cursorCreatedAt == null) {
                return users;
            }
        }
    }

    private List<ExportColumn<SystemVO.UserVO>> selectedColumns(CurrentUser currentUser, List<String> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "At least one export field is required");
        }
        if (fields.size() > MAX_EXPORT_FIELDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Too many export fields");
        }
        Map<String, ExportColumn<SystemVO.UserVO>> columns = new LinkedHashMap<>();
        for (ExportColumn<SystemVO.UserVO> column : userColumns()) {
            columns.put(column.key(), column);
        }
        Set<String> dedupedFields = new LinkedHashSet<>();
        for (String field : fields) {
            if (!org.springframework.util.StringUtils.hasText(field)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Export field cannot be blank");
            }
            dedupedFields.add(field.trim());
        }
        List<ExportColumn<SystemVO.UserVO>> selected = new ArrayList<>();
        for (String field : dedupedFields) {
            if (SENSITIVE_EXPORT_FIELDS.contains(field) && !canExportSensitiveFields(currentUser)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_USER_SENSITIVE_VIEW);
            }
            ExportColumn<SystemVO.UserVO> column = columns.get(field);
            if (column == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported export field: " + field);
            }
            selected.add(column);
        }
        return selected;
    }

    private ExportDTO.UserExportRequest normalizeRequest(ExportDTO.UserExportRequest request) {
        ExportDTO.UserExportRequest normalized = new ExportDTO.UserExportRequest();
        normalized.setFields(normalizeFields(request.getFields()));
        normalized.setUserId(normalizePositiveId(request.getUserId(), "User id is invalid"));
        normalized.setUsername(trimOptional(request.getUsername(), MAX_FILTER_TEXT_LENGTH, "Username filter is too long"));
        normalized.setMobile(trimOptional(request.getMobile(), MAX_FILTER_TEXT_LENGTH, "Mobile filter is too long"));
        normalized.setEmail(trimOptional(request.getEmail(), MAX_FILTER_TEXT_LENGTH, "Email filter is too long"));
        normalized.setDeptId(normalizePositiveId(request.getDeptId(), "Department id is invalid"));
        normalized.setStatus(trimOptional(request.getStatus(), MAX_FILTER_TEXT_LENGTH, "Status filter is too long"));
        normalized.setSource(trimOptional(request.getSource(), MAX_FILTER_TEXT_LENGTH, "Source filter is too long"));
        normalized.setRegisteredStart(trimOptional(request.getRegisteredStart(), MAX_DATE_TEXT_LENGTH, "Registered start is too long"));
        normalized.setRegisteredEnd(trimOptional(request.getRegisteredEnd(), MAX_DATE_TEXT_LENGTH, "Registered end is too long"));
        normalized.setLastLoginStart(trimOptional(request.getLastLoginStart(), MAX_DATE_TEXT_LENGTH, "Last login start is too long"));
        normalized.setLastLoginEnd(trimOptional(request.getLastLoginEnd(), MAX_DATE_TEXT_LENGTH, "Last login end is too long"));
        return normalized;
    }

    private List<String> normalizeFields(List<String> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "At least one export field is required");
        }
        if (fields.size() > MAX_EXPORT_FIELDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Too many export fields");
        }
        Set<String> dedupedFields = new LinkedHashSet<>();
        for (String field : fields) {
            String normalized = trimOptional(field, MAX_FILTER_TEXT_LENGTH, "Export field is too long");
            if (normalized == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Export field cannot be blank");
            }
            dedupedFields.add(normalized);
        }
        return List.copyOf(dedupedFields);
    }

    private boolean canExportSensitiveFields(CurrentUser currentUser) {
        Set<String> permissions = trustedPermissionsOrEmpty(currentUser);
        return permissions.contains("*") || permissions.contains(PERMISSION_USER_SENSITIVE_VIEW);
    }

    private CurrentUser trustedCurrentUserSnapshot(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        CurrentUser snapshot = buildCurrentUserSnapshot(
                currentUser.getUserId(),
                currentUser.getUsername().trim(),
                currentUser.getSessionId().trim(),
                currentUser.getSessionVersion(),
                Set.copyOf(currentUser.getPermissions()),
                Set.copyOf(currentUser.getRoleIds()),
                currentUser.getPrimaryDeptId(),
                Set.copyOf(currentUser.getDeptIds()),
                Set.copyOf(currentUser.getDescendantDeptIds()),
                List.copyOf(currentUser.getDataScopes())
        );
        snapshot.setUserUuid(currentUser.getUserUuid().trim());
        snapshot.setPermissionsVersion(currentUser.getPermissionsVersion().trim());
        snapshot.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        snapshot.setDefaultHomePath(currentUser.getDefaultHomePath());
        snapshot.setSimulatedRoleId(normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()));
        return snapshot;
    }

    private CurrentUser refreshTrustedCurrentUserSnapshot(CurrentUser currentUser) {
        return refreshTrustedCurrentUserSnapshot(currentUser, false);
    }

    private CurrentUser refreshTrustedCurrentUserSnapshot(CurrentUser currentUser, boolean bypassSessionAuthentication) {
        CurrentUser trustedSnapshot = trustedCurrentUserSnapshot(currentUser);
        Long simulatedRoleId = normalizeSimulatedRoleId(trustedSnapshot.getSimulatedRoleId());
        trustedSnapshot.setSimulatedRoleId(simulatedRoleId);
        if (!shouldBypassSessionAuthentication(trustedSnapshot, bypassSessionAuthentication) && sessionAuthenticationService != null) {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                            trustedSnapshot.getSessionId(),
                            trustedSnapshot.getUserId(),
                            trustedSnapshot.getUserUuid(),
                            simulatedRoleId,
                            trustedSnapshot.getSessionVersion(),
                            trustedSnapshot.getPermissionsVersion()
                    );
            CurrentUser refreshedSnapshot = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
            requireExportPermission(refreshedSnapshot);
            return refreshedSnapshot;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            requireExportPermission(trustedSnapshot);
            return trustedSnapshot;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(trustedSnapshot.getUserId(), trustedSnapshot.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(trustedSnapshot.getUserId());
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !trustedSnapshot.getUserId().equals(userSnapshot.userId())
                    || !org.springframework.util.StringUtils.hasText(userSnapshot.userUuid())
                    || !trustedSnapshot.getUserUuid().equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
            }
            String currentUsername = org.springframework.util.StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            if (!org.springframework.util.StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            trustedSnapshot.setUserId(userSnapshot.userId());
            trustedSnapshot.setUserUuid(userSnapshot.userUuid().trim());
            trustedSnapshot.setUsername(currentUsername);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(trustedSnapshot.getUserId(), trustedSnapshot.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Export user is disabled or no longer trusted");
        }
        PermissionSnapshotService.PermissionSnapshot permissionSnapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                trustedSnapshot.getUserId(),
                trustedSnapshot.getUserUuid(),
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(
                trustedSnapshot.getUserId(),
                trustedSnapshot.getUserUuid()
        );
        if (permissionSnapshot == null || permissionSnapshot.getVersion() == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Export user permission snapshot is unavailable");
            }
            requireExportPermission(trustedSnapshot);
            return trustedSnapshot;
        }
        CurrentUser refreshedSnapshot = buildCurrentUserSnapshot(
                trustedSnapshot.getUserId(),
                trustedSnapshot.getUsername(),
                trustedSnapshot.getSessionId(),
                trustedSnapshot.getSessionVersion(),
                Set.copyOf(permissionSnapshot.getPermissions()),
                Set.copyOf(permissionSnapshot.getRoleIds()),
                permissionSnapshot.getPrimaryDeptId(),
                Set.copyOf(permissionSnapshot.getDeptIds()),
                Set.copyOf(permissionSnapshot.getDescendantDeptIds()),
                List.copyOf(permissionSnapshot.getDataScopes())
        );
        refreshedSnapshot.setUserUuid(trustedSnapshot.getUserUuid());
        refreshedSnapshot.setPermissionsVersion(permissionSnapshot.getVersion());
        refreshedSnapshot.setRequiresPasswordChange(trustedSnapshot.getRequiresPasswordChange());
        refreshedSnapshot.setDefaultHomePath(permissionSnapshot.getDefaultHomePath());
        refreshedSnapshot.setSimulatedRoleId(simulatedRoleId);
        requireExportPermission(refreshedSnapshot);
        return refreshedSnapshot;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private boolean shouldBypassSessionAuthentication(CurrentUser currentUser, boolean bypassSessionAuthentication) {
        return bypassSessionAuthentication || isAsyncExportSession(currentUser);
    }

    private boolean isAsyncExportSession(CurrentUser currentUser) {
        return currentUser != null
                && currentUser.getSessionId() != null
                && currentUser.getSessionId().startsWith(ASYNC_EXPORT_SESSION_PREFIX);
    }

    private String asyncExportSessionId(Long taskId) {
        return ASYNC_EXPORT_SESSION_PREFIX + taskId;
    }

    private CurrentUser buildCurrentUserSnapshot(
            Long userId,
            String username,
            String sessionId,
            Integer sessionVersion,
            Set<String> permissions,
            Set<Long> roleIds,
            Long primaryDeptId,
            Set<Long> deptIds,
            Set<Long> descendantDeptIds,
            List<com.lumira.common.security.data.DataPermissionRule> dataScopes
    ) {
        return new CurrentUser(
                userId,
                username,
                sessionId,
                sessionVersion,
                true,
                permissions,
                roleIds,
                primaryDeptId,
                deptIds,
                descendantDeptIds,
                dataScopes
        );
    }

    private List<ExportColumn<SystemVO.UserVO>> userColumns() {
        return List.of(
                column("id", "User ID", true, SystemVO.UserVO::getId),
                column("userNo", "User No", true, SystemVO.UserVO::getUserNo),
                column("username", "Username", true, SystemVO.UserVO::getUsername),
                column("mobile", "Mobile", true, SystemVO.UserVO::getMobile),
                column("email", "Email", true, SystemVO.UserVO::getEmail),
                column("nickname", "Nickname", true, SystemVO.UserVO::getNickname),
                column("realName", "Real Name", true, SystemVO.UserVO::getRealName),
                column("status", "Status", true, SystemVO.UserVO::getStatus),
                column("source", "Source", true, SystemVO.UserVO::getSource),
                column("registeredAt", "Registered At", true, SystemVO.UserVO::getRegisteredAt),
                column("lastLoginAt", "Last Login At", true, SystemVO.UserVO::getLastLoginAt),
                column("roleNames", "Roles", true, SystemVO.UserVO::getRoleNames),
                column("deptNames", "Departments", true, SystemVO.UserVO::getDeptNames),
                column("idCardNumber", "ID Card Number", false, SystemVO.UserVO::getIdCardNumber),
                column("avatarUrl", "Avatar URL", false, SystemVO.UserVO::getAvatarUrl),
                column("birthMonth", "Birth Month", false, SystemVO.UserVO::getBirthMonth),
                column("gender", "Gender", false, SystemVO.UserVO::getGender),
                column("region", "Region", false, SystemVO.UserVO::getRegion),
                column("availableTime", "Available Time", false, SystemVO.UserVO::getAvailableTime),
                column("createdAt", "Created At", false, SystemVO.UserVO::getCreatedAt),
                column("updatedAt", "Updated At", false, SystemVO.UserVO::getUpdatedAt)
        );
    }

    private ExportColumn<SystemVO.UserVO> column(String key, String label, boolean defaultSelected, java.util.function.Function<SystemVO.UserVO, Object> valueExtractor) {
        return new ExportColumn<>(key, label, defaultSelected, valueExtractor);
    }

    private void requireExportPermission(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        Set<String> permissions = trustedPermissionsOrEmpty(currentUser);
        if (!permissions.contains("*") && !permissions.contains(PERMISSION_USER_EXPORT)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_USER_EXPORT);
        }
    }

    private Set<String> trustedPermissionsOrEmpty(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser) || currentUser.getPermissions() == null) {
            return Set.of();
        }
        return currentUser.getPermissions();
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void requireRequest(Object request, String name) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
    }

    private Long normalizePositiveId(Long id, String message) {
        if (id == null) {
            return null;
        }
        if (id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
        return id;
    }

    private String trimOptional(String value, int maxLength, String message) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private AsyncTaskPayload asyncTaskPayload(
            ExportDTO.UserExportRequest request,
            String fileName,
            Long simulatedRoleId
    ) {
        AsyncTaskPayload payload = new AsyncTaskPayload();
        payload.setRequest(request);
        payload.setFileName(fileName);
        payload.setSimulatedRoleId(simulatedRoleId);
        return payload;
    }

    private String buildFileName() {
        return "user-export-" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
    }

    static class AsyncTaskPayload {
        private ExportDTO.UserExportRequest request;
        private String fileName;
        private Long simulatedRoleId;

        public ExportDTO.UserExportRequest getRequest() {
            return request;
        }

        public void setRequest(ExportDTO.UserExportRequest request) {
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
    }
}
