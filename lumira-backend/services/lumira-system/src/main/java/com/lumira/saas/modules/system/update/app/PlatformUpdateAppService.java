package com.lumira.saas.modules.system.update.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ServiceVersionInfo;
import com.lumira.common.runtime.ServiceVersionInfoFactory;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.update.entity.PlatformUpdateTaskEntity;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import com.lumira.saas.modules.system.update.vo.PlatformUpdateVO;
import io.micrometer.core.instrument.Metrics;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlatformUpdateAppService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUpdateAppService.class);
    private static final String DEFAULT_BRANCH = "main";
    private static final String DEFAULT_SOURCE_URL = "https://api.github.com/repos/Elexvx/lumira/commits/main";
    private static final String STATUS_UP_TO_DATE = "UP_TO_DATE";
    private static final String STATUS_UPDATE_AVAILABLE = "UPDATE_AVAILABLE";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_CHECK_FAILED = "CHECK_FAILED";
    private static final String TASK_RUNNING = "RUNNING";
    private static final String TASK_INSTALL = "INSTALL";
    private static final String TASK_ROLLBACK = "ROLLBACK";
    private static final String PERMISSION_VIEW = "system:update:view";
    private static final String PERMISSION_CHECK = "system:update:check";
    private static final String PERMISSION_INSTALL = "system:update:install";
    private static final String PERMISSION_ROLLBACK = "system:update:rollback";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String IMAGE_DIGEST_MARKER = "@sha256:";
    private static final int MAX_VERSION_LENGTH = 80;
    private static final int MAX_COMMIT_LENGTH = 64;
    private static final int MAX_BRANCH_LENGTH = 80;
    private static final int MAX_RELEASED_AT_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final int MAX_URL_LENGTH = 1024;
    private static final int MAX_IMAGE_LENGTH = 512;
    private static final int MAX_UPDATER_TASK_ID_LENGTH = 128;
    private static final int MAX_UPDATER_TEXT_LENGTH = 2000;
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,64}$");
    private static final Pattern DIGEST_PINNED_IMAGE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,446}@sha256:[0-9a-fA-F]{64}$");
    private static final Set<String> UPDATER_STATUSES = Set.of("PENDING", TASK_RUNNING, "SUCCEEDED", "FAILED", "ROLLED_BACK", "CANCELLED");
    private static final String ACTIVE_TASK_KEY = "GLOBAL";

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ObjectMapper objectMapper;
    private final PlatformUpdateTaskMapper taskMapper;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;
    private final HttpClient httpClient;
    private volatile PlatformUpdateVO.StatusVO cachedStatus;
    private volatile Path cachedGitDirectory;

    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                environment,
                buildPropertiesProvider,
                objectMapper,
                taskMapper,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    @Autowired
    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                environment,
                buildPropertiesProvider,
                objectMapper,
                taskMapper,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.objectMapper = objectMapper;
        this.taskMapper = taskMapper;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                environment,
                buildPropertiesProvider,
                objectMapper,
                taskMapper,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper
    ) {
        this(environment, buildPropertiesProvider, objectMapper, taskMapper, null, null, null, false);
    }

    public PlatformUpdateVO.StatusVO getStatus(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_VIEW);
        return getStatusInternal();
    }

    private PlatformUpdateVO.StatusVO getStatusInternal() {
        PlatformUpdateVO.StatusVO status = cachedStatus;
        if (status == null) {
            status = checkLatestInternal();
        } else {
            PlatformUpdateVO.UpdaterCapabilitiesVO capabilities = updaterCapabilities();
            status.setUpdaterCapabilities(capabilities);
            status.setUpdaterAvailable(capabilities != null);
        }
        status.setActiveTask(findActiveTask());
        return status;
    }

    public PlatformUpdateVO.StatusVO checkLatest(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_CHECK);
        return checkLatestInternal();
    }

    private PlatformUpdateVO.StatusVO checkLatestInternal() {
        PlatformUpdateVO.StatusVO status = new PlatformUpdateVO.StatusVO();
        ServiceVersionInfo currentVersion = currentVersion();
        PlatformUpdateVO.CurrentVersionVO current = new PlatformUpdateVO.CurrentVersionVO();
        current.setVersion(currentVersion.version());
        current.setCommitId(currentVersion.commitId());
        current.setBranch(currentVersion.branch());
        current.setBuildTime(currentVersion.buildTime());
        status.setCurrent(current);
        status.setSourceUrl(resolveSourceUrl());
        status.setSourceType(resolveSourceType(status.getSourceUrl()));
        status.setCheckedAt(LocalDateTime.now());
        status.setComparisonBasis("commit");
        status.setCurrentKnown(hasKnownCommit(current.getCommitId()));
        status.setLatestKnown(false);
        status.setSourceReachable(false);
        PlatformUpdateVO.UpdaterCapabilitiesVO capabilities = updaterCapabilities();
        status.setUpdaterCapabilities(capabilities);
        status.setUpdaterAvailable(capabilities != null);
        status.setUpdateAvailable(false);

        try {
            PlatformUpdateVO.LatestVersionVO latest = fetchLatest(status.getSourceUrl(), "manifest".equals(status.getSourceType()));
            status.setLatest(latest);
            status.setSourceReachable(true);
            status.setLatestKnown(hasKnownCommit(latest.getCommitId()));
            boolean comparable = Boolean.TRUE.equals(status.getCurrentKnown()) && Boolean.TRUE.equals(status.getLatestKnown());
            boolean updateAvailable = comparable && isDifferentCommit(current.getCommitId(), latest.getCommitId());
            status.setUpdateAvailable(updateAvailable);
            if ("manifest".equals(status.getSourceType())) {
                PlatformUpdateVO.ManifestVO manifest = new PlatformUpdateVO.ManifestVO();
                manifest.setServerImage(latest.getServerImage());
                manifest.setFrontendImage(latest.getFrontendImage());
                manifest.setAsyncImage(latest.getAsyncImage());
                manifest.setJobExecutorImage(latest.getJobExecutorImage());
                manifest.setMigratorImage(latest.getMigratorImage());
                manifest.setSchemaVersion(latest.getSchemaVersion());
                manifest.setStrategy(latest.getStrategy());
                manifest.setMinUpdaterProtocol(latest.getMinUpdaterProtocol());
                manifest.setMigrationMode(latest.getMigrationMode());
                manifest.setDatabaseVersion(latest.getDatabaseVersion());
                manifest.setMigrationRequired(latest.getMigrationRequired());
                manifest.setRollbackSupported(latest.getRollbackSupported());
                manifest.setReleaseNotes(latest.getTitle());
                status.setManifest(manifest);
            }
            if (!comparable) {
                status.setStatus(STATUS_UNKNOWN);
                status.setActionRequired("Current deployment or update source is missing comparable commit information. Ensure GIT_COMMIT is injected and the manifest includes commit.");
                status.setNotes(List.of("The system only displays status and will not install updates automatically.", "Configure PLATFORM_UPDATE_MANIFEST_URL to show images and release notes."));
            } else if (updateAvailable) {
                status.setStatus(STATUS_UPDATE_AVAILABLE);
                status.setActionRequired("A new version is available. Run preflight and confirm the blue-green update.");
                status.setNotes(List.of("HTTP/API traffic is switched with an Nginx hot reload.", "Database migrations must remain expand-only so the previous slot can be restored safely."));
            } else {
                status.setStatus(STATUS_UP_TO_DATE);
                status.setActionRequired("No action required.");
                status.setNotes(List.of("The current deployment commit matches the update source.", "You can run a manual check again at any time."));
            }
        } catch (Exception ex) {
            if (isDefaultSource(status.getSourceUrl())) {
                PlatformUpdateVO.LatestVersionVO localLatest = localLatest(currentVersion);
                if (localLatest != null) {
                    status.setLatest(localLatest);
                    status.setLatestKnown(true);
                    status.setUpdateAvailable(false);
                    status.setStatus(STATUS_UP_TO_DATE);
                    status.setActionRequired("Local runtime mode is using the local Git commit as the version baseline. Configure PLATFORM_UPDATE_MANIFEST_URL to enable remote update checks.");
                    status.setErrorMessage(ex.getMessage());
                    status.setNotes(List.of(
                            "The default GitHub update source is currently unreachable; local Git information is used instead.",
                            "For production updates, configure PLATFORM_UPDATE_MANIFEST_URL, PLATFORM_UPDATE_AGENT_URL, and PLATFORM_UPDATE_AGENT_TOKEN."
                    ));
                    status.setActiveTask(findActiveTask());
                    cachedStatus = status;
                    return status;
                }
            }
            status.setUpdateAvailable(false);
            status.setStatus(STATUS_CHECK_FAILED);
            status.setLatestKnown(false);
            status.setSourceReachable(false);
            status.setActionRequired("Update source check failed. Verify manifest URL, network access, or token configuration.");
            status.setErrorMessage(ex.getMessage());
            status.setNotes(List.of("Check failures do not affect the running system.", "The system will not perform update actions because a check failed."));
        }
        status.setActiveTask(findActiveTask());
        cachedStatus = status;
        return status;
    }

    public PlatformUpdateVO.PreflightVO preflight(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_INSTALL);
        PlatformUpdateVO.StatusVO status = checkLatestInternal();
        requireUpdateAvailable(status);
        requireUpdater(status);
        PlatformUpdateVO.LatestVersionVO latest = requireInstallableLatest(status);
        try {
            JsonNode response = postUpdater("/v1/update/preflight", Map.of("manifest", manifestPayload(latest)));
            PlatformUpdateVO.PreflightVO result = toPreflightVO(response);
            Metrics.counter("lumira.platform.update.preflight", "ready", String.valueOf(Boolean.TRUE.equals(result.getReady()))).increment();
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Platform update preflight failed: " + ex.getMessage(), ex);
        }
    }

    public PlatformUpdateVO.TaskVO install(CurrentUser currentUser) {
        return install(currentUser, null);
    }

    public PlatformUpdateVO.TaskVO install(CurrentUser currentUser, PlatformUpdateVO.InstallRequest request) {
        requirePermission(currentUser, PERMISSION_INSTALL);
        PlatformUpdateVO.StatusVO status = checkLatestInternal();
        requireUpdateAvailable(status);
        requireUpdater(status);
        PlatformUpdateVO.LatestVersionVO latest = requireInstallableLatest(status);
        if (request != null && StringUtils.hasText(request.getTargetCommit())
                && !latest.getCommitId().equalsIgnoreCase(normalizeCommit(request.getTargetCommit(), false))) {
            throw new IllegalStateException("The release changed after preflight. Run preflight again.");
        }
        PlatformUpdateTaskEntity task = createTask(TASK_INSTALL, latest, currentUser);
        task.setPreflightId(request == null ? null : boundedText(request.getPreflightId(), 128, "preflightId"));
        return startUpdaterTask(task, "/v1/update/install", new UpdaterRequest(
                task.getTargetVersion(), task.getTargetCommit(), task.getServerImage(), task.getFrontendImage(), task.getId(),
                task.getPreflightId(), manifestPayload(latest)
        ));
    }

    public PlatformUpdateVO.TaskVO rollback(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_ROLLBACK);
        if (!isUpdaterAvailable()) {
            throw new IllegalStateException("Platform update agent is unavailable. Configure and start lumira-updater first.");
        }
        PlatformUpdateVO.LatestVersionVO latest = getStatusInternal().getLatest();
        PlatformUpdateTaskEntity task = createTask(TASK_ROLLBACK, latest, currentUser);
        return startUpdaterTask(task, "/v1/update/rollback", new UpdaterRequest(
                task.getTargetVersion(), task.getTargetCommit(), task.getServerImage(), task.getFrontendImage(), task.getId(), null, null
        ));
    }

    public PlatformUpdateVO.TaskVO cancel(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, PERMISSION_INSTALL);
        requirePositiveId(id, "Update task id is required");
        PlatformUpdateTaskEntity task = taskMapper.selectById(id);
        if (task == null || !StringUtils.hasText(task.getUpdaterTaskId())) {
            throw new IllegalArgumentException("Update task does not exist or has not started: " + id);
        }
        if (isTerminal(task.getStatus())) return toTaskVO(task);
        PlatformUpdateTaskEntity expected = snapshotTask(task);
        try {
            JsonNode response = postUpdater("/v1/update/tasks/" + task.getUpdaterTaskId() + "/cancel", Map.of());
            applyUpdaterState(task, response);
            task.setUpdatedAt(LocalDateTime.now());
            updateTaskIfUnchanged(task, expected);
            Metrics.counter("lumira.platform.update.cancel.requested", "phase", firstText(task.getPhase(), "unknown")).increment();
            log.info("platform_update_cancel_requested taskId={} updaterTaskId={} phase={} actor={}", task.getId(), task.getUpdaterTaskId(), task.getPhase(), currentUser.getUsername());
            return toTaskVO(task);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to cancel or roll back update task: " + ex.getMessage(), ex);
        }
    }

    public List<PlatformUpdateVO.TaskVO> listTasks(CurrentUser currentUser) {
        requirePermission(currentUser, PERMISSION_VIEW);
        return taskMapper.selectList(new LambdaQueryWrapper<PlatformUpdateTaskEntity>()
                        .orderByDesc(PlatformUpdateTaskEntity::getCreatedAt)
                        .last("LIMIT 20"))
                .stream()
                .map(this::toTaskVO)
                .toList();
    }

    public PlatformUpdateVO.TaskVO getTask(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, PERMISSION_VIEW);
        requirePositiveId(id, "Update task id is required");
        PlatformUpdateTaskEntity task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("更新任务不存在: " + id);
        }
        syncUpdaterTask(task);
        return toTaskVO(taskMapper.selectById(id));
    }

    @Scheduled(initialDelayString = "${platform.update.check-initial-delay-ms:60000}", fixedDelayString = "${platform.update.check-interval-ms:1800000}")
    public void scheduledCheckLatest() {
        try {
            checkLatestInternal();
        } catch (Exception ex) {
            log.warn("Platform update scheduled check failed", ex);
        }
    }

    private PlatformUpdateVO.TaskVO startUpdaterTask(PlatformUpdateTaskEntity task, String path, Object request) {
        PlatformUpdateTaskEntity expected = snapshotTask(task);
        try {
            JsonNode response = postUpdater(path, request);
            task.setUpdaterTaskId(normalizeUpdaterTaskId(firstText(response.path("taskId").asText(null), response.path("id").asText(null))));
            task.setStatus(normalizeUpdaterStatus(firstText(response.path("status").asText(null), TASK_RUNNING), TASK_RUNNING));
            task.setLogSummary(boundedText(response.path("message").asText(null), MAX_UPDATER_TEXT_LENGTH, "updater message"));
            task.setBackupPath(boundedText(response.path("backupPath").asText(null), MAX_URL_LENGTH, "updater backupPath"));
            applyUpdaterState(task, response);
            task.setUpdatedAt(LocalDateTime.now());
            updateTaskIfUnchanged(task, expected);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            task.setActiveKey(null);
            task.setUpdatedAt(LocalDateTime.now());
            updateTaskIfUnchanged(task, expected);
        }
        return toTaskVO(task);
    }

    private void syncUpdaterTask(PlatformUpdateTaskEntity task) {
        if (!StringUtils.hasText(task.getUpdaterTaskId()) || isTerminal(task.getStatus())) {
            return;
        }
        PlatformUpdateTaskEntity expected = snapshotTask(task);
        try {
            JsonNode response = getUpdater("/v1/update/tasks/" + task.getUpdaterTaskId());
            applyUpdaterState(task, response);
            task.setBackupPath(boundedText(firstText(response.path("backupPath").asText(null), task.getBackupPath()), MAX_URL_LENGTH, "updater backupPath"));
            task.setLogSummary(boundedText(firstText(response.path("message").asText(null), response.path("logSummary").asText(null), task.getLogSummary()), MAX_UPDATER_TEXT_LENGTH, "updater logSummary"));
            task.setErrorMessage(boundedText(firstText(response.path("errorMessage").asText(null), task.getErrorMessage()), MAX_UPDATER_TEXT_LENGTH, "updater errorMessage"));
            if (isTerminal(task.getStatus()) && task.getFinishedAt() == null) {
                task.setFinishedAt(LocalDateTime.now());
                task.setActiveKey(null);
                Metrics.counter("lumira.platform.update.completed", "type", task.getTaskType(), "status", task.getStatus()).increment();
                if (task.getStartedAt() != null) {
                    Metrics.timer("lumira.platform.update.duration", "type", task.getTaskType(), "status", task.getStatus())
                            .record(Duration.between(task.getStartedAt(), task.getFinishedAt()));
                }
                log.info("platform_update_completed taskId={} updaterTaskId={} status={} phase={} activeSlot={} targetSlot={}", task.getId(), task.getUpdaterTaskId(), task.getStatus(), task.getPhase(), task.getActiveSlot(), task.getTargetSlot());
            }
            task.setUpdatedAt(LocalDateTime.now());
            updateTaskIfUnchanged(task, expected);
        } catch (Exception ex) {
            log.warn("Failed to sync updater task {}", task.getUpdaterTaskId(), ex);
        }
    }

    private PlatformUpdateVO.TaskVO findActiveTask() {
        PlatformUpdateTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<PlatformUpdateTaskEntity>()
                .in(PlatformUpdateTaskEntity::getStatus, "PENDING", TASK_RUNNING)
                .orderByDesc(PlatformUpdateTaskEntity::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            return null;
        }
        syncUpdaterTask(task);
        return toTaskVO(taskMapper.selectById(task.getId()));
    }

    private PlatformUpdateTaskEntity createTask(String taskType, PlatformUpdateVO.LatestVersionVO latest, CurrentUser currentUser) {
        Long actorUserId = requireTrustedUserId(currentUser);
        String actorUserUuid = currentUser.getUserUuid().trim();
        String actorUsername = currentUser.getUsername().trim();
        releaseStaleTerminalTaskLock();

        PlatformUpdateTaskEntity task = new PlatformUpdateTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setStrategy(firstText(latest == null ? null : latest.getStrategy(), "single-host-blue-green"));
        task.setPhase("PREFLIGHT");
        task.setProgressPercent(0);
        task.setActiveKey(ACTIVE_TASK_KEY);
        task.setTargetVersion(latest == null ? null : boundedText(latest.getVersion(), MAX_VERSION_LENGTH, "version"));
        task.setTargetCommit(latest == null ? null : normalizeCommit(latest.getCommitId(), false));
        task.setServerImage(latest == null ? null : requireDigestPinnedImage(latest.getServerImage(), "serverImage"));
        task.setFrontendImage(latest == null ? null : requireDigestPinnedImage(latest.getFrontendImage(), "frontendImage"));
        task.setCreatedBy(actorUserId);
        task.setCreatedByUuid(actorUserUuid);
        task.setCreatedByName(actorUsername);
        task.setStartedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        int inserted;
        try {
            inserted = taskMapper.insert(task);
        } catch (DuplicateKeyException exception) {
            throw biz(ErrorCode.BIZ_ERROR, "已有平台更新任务正在运行，请刷新任务列表后重试");
        }
        if (inserted != 1) {
            throw biz(ErrorCode.BIZ_ERROR, "Platform update task changed, please retry");
        }
        Metrics.counter("lumira.platform.update.started", "type", taskType, "strategy", task.getStrategy()).increment();
        log.info("platform_update_started taskId={} type={} targetCommit={} strategy={} actor={}", task.getId(), taskType, task.getTargetCommit(), task.getStrategy(), task.getCreatedByName());
        return task;
    }

    private void releaseStaleTerminalTaskLock() {
        taskMapper.update(null, new UpdateWrapper<PlatformUpdateTaskEntity>()
                .set("active_key", null)
                .eq("active_key", ACTIVE_TASK_KEY)
                .notIn("status", "PENDING", TASK_RUNNING));
    }

    private PlatformUpdateTaskEntity snapshotTask(PlatformUpdateTaskEntity task) {
        PlatformUpdateTaskEntity snapshot = new PlatformUpdateTaskEntity();
        snapshot.setId(task.getId());
        snapshot.setTaskType(task.getTaskType());
        snapshot.setStatus(task.getStatus());
        snapshot.setUpdaterTaskId(task.getUpdaterTaskId());
        snapshot.setActiveKey(task.getActiveKey());
        snapshot.setCreatedBy(task.getCreatedBy());
        snapshot.setCreatedByUuid(task.getCreatedByUuid());
        return snapshot;
    }

    private void updateTaskIfUnchanged(PlatformUpdateTaskEntity task, PlatformUpdateTaskEntity expected) {
        if (task == null || expected == null || expected.getId() == null) {
            return;
        }
        LambdaUpdateWrapper<PlatformUpdateTaskEntity> wrapper = new LambdaUpdateWrapper<PlatformUpdateTaskEntity>()
                .eq(PlatformUpdateTaskEntity::getId, expected.getId())
                .eq(PlatformUpdateTaskEntity::getTaskType, expected.getTaskType())
                .eq(PlatformUpdateTaskEntity::getStatus, expected.getStatus())
                .eq(PlatformUpdateTaskEntity::getCreatedBy, expected.getCreatedBy())
                .eq(PlatformUpdateTaskEntity::getCreatedByUuid, expected.getCreatedByUuid());
        if (StringUtils.hasText(expected.getUpdaterTaskId())) {
            wrapper.eq(PlatformUpdateTaskEntity::getUpdaterTaskId, expected.getUpdaterTaskId());
        } else {
            wrapper.isNull(PlatformUpdateTaskEntity::getUpdaterTaskId);
        }
        int updated = taskMapper.update(task, wrapper);
        if (updated <= 0) {
            log.warn("Platform update task changed before state write taskId={} taskType={}", expected.getId(), expected.getTaskType());
        }
    }

    private Long requireTrustedUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        Long actorUserId = requireTrustedUserId(currentUser);
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (!permissions.contains("*") && !permissions.contains(permissionKey)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return actorUserId;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
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
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private JsonNode postUpdater(String path, Object request) throws Exception {
        String body = objectMapper.writeValueAsString(request == null ? Map.of() : request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(updaterUri(path))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "lumira-update-center")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addUpdaterToken(builder);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("updater 请求失败: HTTP " + response.statusCode() + " " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode getUpdater(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(updaterUri(path))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "lumira-update-center")
                .GET();
        addUpdaterToken(builder);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("updater 查询失败: HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private void addUpdaterToken(HttpRequest.Builder builder) {
        String token = firstText(
                environment.getProperty("PLATFORM_UPDATE_AGENT_TOKEN"),
                environment.getProperty("platform.update.agent-token"),
                System.getenv("PLATFORM_UPDATE_AGENT_TOKEN")
        );
        if (StringUtils.hasText(token)) {
            builder.header("X-Lumira-Updater-Token", token);
        }
    }

    private PlatformUpdateVO.LatestVersionVO fetchLatest(String sourceUrl, boolean manifestSource) throws Exception {
        validateUpdateSourceUrl(sourceUrl, manifestSource);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json, application/json")
                .header("User-Agent", "lumira-update-checker")
                .GET();
        addSourceToken(builder, sourceUrl);
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("更新源请求失败 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return manifestSource ? fromManifest(root) : fromGithubCommit(root);
    }

    private void validateManifestSourceUrl(String sourceUrl) {
        validateUpdateSourceUrl(sourceUrl, true);
    }

    private void validateUpdateSourceUrl(String sourceUrl, boolean manifestSource) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Update manifest URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("Update manifest URL must use HTTPS");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Update manifest URL host is required");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalStateException("Update manifest URL must not contain user info");
        }
        if (isDefaultGithubHost(host)) {
            return;
        }
        String allowedHosts = firstText(
                environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS"),
                environment.getProperty("platform.update.allowed-hosts"),
                System.getenv("PLATFORM_UPDATE_ALLOWED_HOSTS")
        );
        if (!StringUtils.hasText(allowedHosts) && manifestSource) {
            return;
        }
        if (isAllowedUpdateHost(host, allowedHosts)) {
            return;
        }
        throw new IllegalStateException("Update manifest host is not allowed");
    }

    private PlatformUpdateVO.LatestVersionVO fromManifest(JsonNode root) {
        JsonNode manifest = unwrapGithubReleaseManifest(root);
        JsonNode images = manifest.path("images");
        JsonNode update = manifest.path("update");
        JsonNode database = update.path("database");
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(normalizeCommit(firstText(manifest.path("commit").asText(null), manifest.path("commitId").asText(null)), true));
        latest.setVersion(boundedText(firstText(manifest.path("version").asText(null), latest.getCommitId()), MAX_VERSION_LENGTH, "version"));
        latest.setBranch(boundedText(firstText(manifest.path("branch").asText(null), manifest.path("channel").asText(null), DEFAULT_BRANCH), MAX_BRANCH_LENGTH, "branch"));
        latest.setReleasedAt(boundedText(manifest.path("releasedAt").asText(null), MAX_RELEASED_AT_LENGTH, "releasedAt"));
        latest.setTitle(boundedText(firstText(manifest.path("releaseNotes").asText(null), manifest.path("title").asText(null), "Lumira release"), MAX_TITLE_LENGTH, "releaseNotes"));
        latest.setUrl(boundedText(firstText(manifest.path("url").asText(null), manifest.path("releaseUrl").asText(null), root.path("html_url").asText(null)), MAX_URL_LENGTH, "releaseUrl"));
        latest.setServerImage(requireDigestPinnedImage(firstText(images.path("server").asText(null), manifest.path("serverImage").asText(null)), "serverImage"));
        latest.setFrontendImage(requireDigestPinnedImage(firstText(images.path("frontend").asText(null), manifest.path("frontendImage").asText(null)), "frontendImage"));
        latest.setAsyncImage(requireDigestPinnedImage(images.path("async").asText(null), "images.async"));
        latest.setJobExecutorImage(requireDigestPinnedImage(images.path("jobExecutor").asText(null), "images.jobExecutor"));
        latest.setMigratorImage(requireDigestPinnedImage(images.path("migrator").asText(null), "images.migrator"));
        latest.setSchemaVersion(manifest.path("schemaVersion").asInt(1));
        latest.setStrategy(firstText(update.path("strategy").asText(null), latest.getSchemaVersion() >= 2 ? "single-host-blue-green" : "legacy-restart"));
        latest.setMinUpdaterProtocol(update.path("minUpdaterProtocol").asInt(latest.getSchemaVersion() >= 2 ? 2 : 1));
        latest.setMigrationMode(firstText(database.path("mode").asText(null), "expand-only"));
        latest.setDatabaseVersion(database.path("targetVersion").asText(null));
        latest.setMigrationRequired(database.has("required")
                ? database.path("required").asBoolean(false)
                : manifest.path("migrationRequired").asBoolean(false));
        latest.setRollbackSupported(update.path("rollbackCompatible").asBoolean(manifest.path("rollbackSupported").asBoolean(true)));
        if (latest.getSchemaVersion() >= 2) {
            if (!StringUtils.hasText(latest.getAsyncImage()) || !StringUtils.hasText(latest.getJobExecutorImage()) || !StringUtils.hasText(latest.getMigratorImage())) {
                throw new IllegalStateException("Update manifest v2 must pin server, async, jobExecutor, and migrator images");
            }
            if (!"expand-only".equalsIgnoreCase(latest.getMigrationMode())) {
                throw new IllegalStateException("Online update manifest must declare expand-only database migration mode");
            }
        }
        return latest;
    }

    private JsonNode unwrapGithubReleaseManifest(JsonNode root) {
        JsonNode body = root.path("body");
        if (!body.isTextual() || !StringUtils.hasText(body.asText())) {
            return root;
        }
        try {
            JsonNode manifest = objectMapper.readTree(body.asText());
            if (manifest == null || !manifest.isObject()) {
                throw new IllegalStateException("GitHub release body must contain a JSON update manifest");
            }
            return manifest;
        } catch (Exception ex) {
            throw new IllegalStateException("GitHub release body contains an invalid update manifest", ex);
        }
    }

    private String requireDigestPinnedImage(String image, String fieldName) {
        if (!StringUtils.hasText(image)) {
            return image;
        }
        String trimmed = image.trim();
        if (!trimmed.contains(IMAGE_DIGEST_MARKER) || trimmed.length() > MAX_IMAGE_LENGTH || !DIGEST_PINNED_IMAGE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalStateException("Update manifest " + fieldName + " must use sha256 digest pinning");
        }
        return trimmed;
    }

    private PlatformUpdateVO.LatestVersionVO fromGithubCommit(JsonNode root) {
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(normalizeCommit(firstText(root.path("commit").path("sha").asText(null), root.path("sha").asText(null)), true));
        latest.setVersion(boundedText(firstText(root.path("version").asText(null), latest.getCommitId()), MAX_VERSION_LENGTH, "version"));
        latest.setBranch(boundedText(firstText(root.path("branch").asText(null), DEFAULT_BRANCH), MAX_BRANCH_LENGTH, "branch"));
        latest.setReleasedAt(boundedText(firstText(root.path("releasedAt").asText(null), root.path("commit").path("committer").path("date").asText(null)), MAX_RELEASED_AT_LENGTH, "releasedAt"));
        latest.setTitle(boundedText(firstText(root.path("title").asText(null), firstLine(root.path("commit").path("message").asText(null)), "GitHub latest commit"), MAX_TITLE_LENGTH, "title"));
        latest.setUrl(boundedText(firstText(root.path("updateUrl").asText(null), root.path("html_url").asText(null)), MAX_URL_LENGTH, "updateUrl"));
        return latest;
    }

    private ServiceVersionInfo currentVersion() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        return ServiceVersionInfoFactory.create(
                environment.getProperty("spring.application.name"),
                buildProperties == null ? null : buildProperties.getArtifact(),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("APP_VERSION"),
                        environment.getProperty("BUILD_VERSION"),
                        buildProperties == null ? null : buildProperties.getVersion()
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("BUILD_TIME"),
                        buildProperties == null || buildProperties.getTime() == null ? null : buildProperties.getTime().toString()
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("GIT_COMMIT"),
                        environment.getProperty("git.commit.id"),
                        environment.getProperty("git.commit.id.full"),
                        environment.getProperty("git.commit.id.abbrev"),
                        environment.getProperty("COMMIT_SHA"),
                        environment.getProperty("VERCEL_GIT_COMMIT_SHA"),
                        System.getProperty("GIT_COMMIT"),
                        System.getenv("GIT_COMMIT"),
                        System.getenv("COMMIT_SHA"),
                        System.getenv("VERCEL_GIT_COMMIT_SHA"),
                        resolveLocalGitCommit()
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("GIT_BRANCH"),
                        environment.getProperty("git.branch"),
                        environment.getProperty("VERCEL_GIT_COMMIT_REF"),
                        System.getProperty("GIT_BRANCH"),
                        System.getenv("GIT_BRANCH"),
                        System.getenv("VERCEL_GIT_COMMIT_REF"),
                        resolveLocalGitBranch(),
                        DEFAULT_BRANCH
                ),
                String.join(",", environment.getActiveProfiles()),
                environment.getProperty("FRONTEND_VERSION"),
                ServiceVersionInfoFactory.firstText(environment.getProperty("BACKEND_VERSION"), environment.getProperty("BUILD_VERSION")),
                environment.getProperty("DATABASE_VERSION")
        );
    }

    private String resolveLocalGitCommit() {
        Path gitDirectory = findGitDirectory();
        if (gitDirectory == null) {
            return null;
        }
        try {
            String head = Files.readString(gitDirectory.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(head)) {
                return null;
            }
            if (!head.startsWith("ref:")) {
                return head;
            }
            String ref = head.substring(4).trim();
            Path refPath = gitDirectory.resolve(ref).normalize();
            if (Files.isRegularFile(refPath)) {
                return Files.readString(refPath, StandardCharsets.UTF_8).trim();
            }
            Path packedRefs = gitDirectory.resolve("packed-refs");
            if (!Files.isRegularFile(packedRefs)) {
                return null;
            }
            return Files.readAllLines(packedRefs, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#") && line.endsWith(" " + ref))
                    .map(line -> line.split("\\s+", 2)[0])
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.debug("Failed to resolve local git commit", ex);
            return null;
        }
    }

    private String resolveLocalGitBranch() {
        Path gitDirectory = findGitDirectory();
        if (gitDirectory == null) {
            return null;
        }
        try {
            String head = Files.readString(gitDirectory.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            if (head.startsWith("ref: refs/heads/")) {
                return head.substring("ref: refs/heads/".length()).trim();
            }
        } catch (Exception ex) {
            log.debug("Failed to resolve local git branch", ex);
        }
        return null;
    }

    private Path findGitDirectory() {
        Path cached = cachedGitDirectory;
        if (cached != null) {
            return cached;
        }
        for (Path root : List.of(Path.of("").toAbsolutePath().normalize(), applicationCodePath())) {
            Path current = root;
            for (int depth = 0; current != null && depth < 8; depth++) {
                Path candidate = current.resolve(".git");
                if (Files.isDirectory(candidate)) {
                    cachedGitDirectory = candidate;
                    return candidate;
                }
                current = current.getParent();
            }
        }
        return null;
    }

    private Path applicationCodePath() {
        try {
            Path path = Path.of(PlatformUpdateAppService.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            return Files.isRegularFile(path) ? path.getParent() : path;
        } catch (Exception ex) {
            return Path.of("").toAbsolutePath().normalize();
        }
    }

    private String resolveSourceUrl() {
        return firstText(
                environment.getProperty("PLATFORM_UPDATE_MANIFEST_URL"),
                System.getenv("PLATFORM_UPDATE_MANIFEST_URL"),
                environment.getProperty("PLATFORM_UPDATE_SOURCE_URL"),
                System.getenv("PLATFORM_UPDATE_SOURCE_URL"),
                DEFAULT_SOURCE_URL
        );
    }

    private String resolveUpdaterBaseUrl() {
        return firstText(
                environment.getProperty("PLATFORM_UPDATE_AGENT_URL"),
                environment.getProperty("platform.update.agent-url"),
                System.getenv("PLATFORM_UPDATE_AGENT_URL"),
                "http://127.0.0.1:9788"
        );
    }

    private URI updaterUri(String path) {
        String baseUrl = resolveUpdaterBaseUrl();
        URI baseUri = validateUpdaterBaseUrl(baseUrl);
        String normalizedBase = baseUri.toString().endsWith("/") ? baseUri.toString().substring(0, baseUri.toString().length() - 1) : baseUri.toString();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private URI validateUpdaterBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Updater agent URL is invalid", exception);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("Updater agent URL must use HTTP(S)");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Updater agent URL host is required");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalStateException("Updater agent URL must not contain user info");
        }
        if (isLoopbackHost(host) || isAllowedUpdaterAgentHost(host)) {
            return uri;
        }
        throw new IllegalStateException("Updater agent host is not allowed");
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private boolean isAllowedUpdaterAgentHost(String host) {
        String allowedHosts = firstText(
                environment.getProperty("PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS"),
                environment.getProperty("platform.update.agent-allowed-hosts"),
                System.getenv("PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS")
        );
        return isAllowedUpdateHost(host, allowedHosts);
    }

    private void addSourceToken(HttpRequest.Builder builder, String sourceUrl) {
        String token = resolveSourceToken(sourceUrl);
        if (StringUtils.hasText(token) && isTrustedSourceTokenHost(sourceUrl)) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private String resolveSourceToken(String sourceUrl) {
        if (isTrustedGithubSourceHost(sourceUrl)) {
            return firstText(
                    environment.getProperty("PLATFORM_UPDATE_SOURCE_TOKEN"),
                    environment.getProperty("platform.update.source-token"),
                    environment.getProperty("GITHUB_TOKEN"),
                    System.getenv("PLATFORM_UPDATE_SOURCE_TOKEN"),
                    System.getenv("GITHUB_TOKEN")
            );
        }
        return firstText(
                environment.getProperty("PLATFORM_UPDATE_SOURCE_TOKEN"),
                environment.getProperty("platform.update.source-token"),
                System.getenv("PLATFORM_UPDATE_SOURCE_TOKEN")
        );
    }

    private boolean isTrustedSourceTokenHost(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if (isDefaultGithubHost(host)) {
            return true;
        }
        String allowedHosts = firstText(
                environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS"),
                environment.getProperty("platform.update.allowed-hosts"),
                System.getenv("PLATFORM_UPDATE_ALLOWED_HOSTS")
        );
        return isAllowedUpdateHost(host, allowedHosts);
    }

    private boolean isTrustedGithubSourceHost(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String host = uri.getHost();
        return StringUtils.hasText(host) && isDefaultGithubHost(host);
    }

    private boolean isDefaultGithubHost(String host) {
        return "api.github.com".equalsIgnoreCase(host) || "github.com".equalsIgnoreCase(host);
    }

    private boolean isAllowedUpdateHost(String host, String allowedHosts) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(allowedHosts)) {
            return false;
        }
        for (String allowedHost : allowedHosts.split(",")) {
            if (host.equalsIgnoreCase(allowedHost.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUpdaterAvailable() {
        return updaterCapabilities() != null;
    }

    private PlatformUpdateVO.UpdaterCapabilitiesVO updaterCapabilities() {
        try {
            JsonNode response = getUpdater("/v1/capabilities");
            String status = firstText(response.path("status").asText(null), response.path("state").asText(null));
            if ("DOWN".equalsIgnoreCase(status)) return null;
            PlatformUpdateVO.UpdaterCapabilitiesVO capabilities = new PlatformUpdateVO.UpdaterCapabilitiesVO();
            capabilities.setProtocolVersion(response.path("protocolVersion").asInt(1));
            capabilities.setStrategy(response.path("strategy").asText("legacy-restart"));
            capabilities.setActiveSlot(response.path("activeSlot").asText(null));
            capabilities.setSupportsPreflight(response.path("supportsPreflight").asBoolean(false));
            capabilities.setSupportsCancel(response.path("supportsCancel").asBoolean(false));
            capabilities.setSupportsExpandOnlyMigration(response.path("supportsExpandOnlyMigration").asBoolean(false));
            return capabilities;
        } catch (Exception ex) {
            log.debug("Platform updater agent is unavailable", ex);
            return null;
        }
    }

    private boolean isDefaultSource(String sourceUrl) {
        return DEFAULT_SOURCE_URL.equals(sourceUrl);
    }

    private PlatformUpdateVO.LatestVersionVO localLatest(ServiceVersionInfo currentVersion) {
        if (currentVersion == null || !hasKnownCommit(currentVersion.commitId())) {
            return null;
        }
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(currentVersion.commitId());
        latest.setVersion(currentVersion.version());
        latest.setBranch(currentVersion.branch());
        latest.setReleasedAt(currentVersion.buildTime());
        latest.setTitle("Local Git baseline");
        latest.setMigrationRequired(false);
        latest.setRollbackSupported(false);
        return latest;
    }

    private boolean isDifferentCommit(String currentCommit, String latestCommit) {
        String normalizedCurrent = currentCommit.trim();
        String normalizedLatest = latestCommit.trim();
        return !normalizedLatest.startsWith(normalizedCurrent) && !normalizedCurrent.startsWith(normalizedLatest);
    }

    private boolean hasKnownCommit(String commit) {
        return StringUtils.hasText(commit) && !"unknown".equalsIgnoreCase(commit.trim());
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "ROLLED_BACK".equals(status) || "CANCELLED".equals(status);
    }

    private void requireUpdater(PlatformUpdateVO.StatusVO status) {
        if (!Boolean.TRUE.equals(status.getUpdaterAvailable())) {
            throw new IllegalStateException("Platform update agent is unavailable. Install and start lumira-updater first.");
        }
        PlatformUpdateVO.LatestVersionVO latest = status.getLatest();
        PlatformUpdateVO.UpdaterCapabilitiesVO capabilities = status.getUpdaterCapabilities();
        if (latest != null && latest.getMinUpdaterProtocol() != null && capabilities != null
                && capabilities.getProtocolVersion() < latest.getMinUpdaterProtocol()) {
            throw new IllegalStateException("lumira-updater protocol is too old for this release. Upgrade the host agent first.");
        }
    }

    private void requireUpdateAvailable(PlatformUpdateVO.StatusVO status) {
        if (Boolean.TRUE.equals(status.getUpdateAvailable())
                && STATUS_UPDATE_AVAILABLE.equals(status.getStatus())) {
            return;
        }
        if (STATUS_UP_TO_DATE.equals(status.getStatus())) {
            throw new IllegalStateException("The current deployment is already on the latest release.");
        }
        throw new IllegalStateException("No verified newer platform release is available.");
    }

    private PlatformUpdateVO.LatestVersionVO requireInstallableLatest(PlatformUpdateVO.StatusVO status) {
        PlatformUpdateVO.LatestVersionVO latest = status.getLatest();
        if (latest == null || !StringUtils.hasText(latest.getServerImage())) {
            throw new IllegalStateException("Current update source does not provide an installable digest-pinned server image.");
        }
        return latest;
    }

    private Map<String, Object> manifestPayload(PlatformUpdateVO.LatestVersionVO latest) {
        Map<String, Object> images = new LinkedHashMap<>();
        images.put("server", latest.getServerImage());
        images.put("frontend", latest.getFrontendImage());
        images.put("async", latest.getAsyncImage());
        images.put("jobExecutor", latest.getJobExecutorImage());
        images.put("migrator", latest.getMigratorImage());
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("mode", latest.getMigrationMode());
        database.put("targetVersion", latest.getDatabaseVersion());
        database.put("required", latest.getMigrationRequired());
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("strategy", latest.getStrategy());
        update.put("minUpdaterProtocol", latest.getMinUpdaterProtocol());
        update.put("rollbackCompatible", latest.getRollbackSupported());
        update.put("database", database);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", latest.getSchemaVersion());
        manifest.put("version", latest.getVersion());
        manifest.put("commit", latest.getCommitId());
        manifest.put("branch", latest.getBranch());
        manifest.put("serverImage", latest.getServerImage());
        manifest.put("frontendImage", latest.getFrontendImage());
        manifest.put("images", images);
        manifest.put("update", update);
        manifest.put("migrationRequired", latest.getMigrationRequired());
        manifest.put("rollbackSupported", latest.getRollbackSupported());
        return manifest;
    }

    private PlatformUpdateVO.PreflightVO toPreflightVO(JsonNode response) {
        PlatformUpdateVO.PreflightVO vo = new PlatformUpdateVO.PreflightVO();
        vo.setPreflightId(response.path("preflightId").asText(null));
        vo.setReady(response.path("ready").asBoolean(false));
        vo.setStrategy(response.path("strategy").asText(null));
        vo.setActiveSlot(response.path("activeSlot").asText(null));
        vo.setTargetSlot(response.path("targetSlot").asText(null));
        vo.setTargetCommit(response.path("targetCommit").asText(null));
        vo.setTargetVersion(response.path("targetVersion").asText(null));
        vo.setMigrationMode(response.path("migrationMode").asText(null));
        vo.setDatabaseTargetVersion(response.path("databaseTargetVersion").asText(null));
        vo.setBlockers(jsonStringList(response.path("blockers")));
        vo.setWarnings(jsonStringList(response.path("warnings")));
        vo.setCheckedAt(response.path("checkedAt").asText(null));
        vo.setExpiresAt(response.path("expiresAt").asText(null));
        return vo;
    }

    private List<String> jsonStringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText).filter(StringUtils::hasText).toList();
    }

    private void applyUpdaterState(PlatformUpdateTaskEntity task, JsonNode response) {
        task.setStatus(normalizeUpdaterStatus(firstText(response.path("status").asText(null), task.getStatus()), task.getStatus()));
        task.setStrategy(boundedText(firstText(response.path("strategy").asText(null), task.getStrategy()), 64, "strategy"));
        task.setPhase(boundedText(firstText(response.path("phase").asText(null), task.getPhase()), 64, "phase"));
        if (response.has("progressPercent")) task.setProgressPercent(Math.max(0, Math.min(100, response.path("progressPercent").asInt())));
        task.setActiveSlot(boundedText(firstText(response.path("activeSlot").asText(null), task.getActiveSlot()), 16, "activeSlot"));
        task.setTargetSlot(boundedText(firstText(response.path("targetSlot").asText(null), task.getTargetSlot()), 16, "targetSlot"));
        task.setTargetVersion(boundedText(firstText(response.path("targetVersion").asText(null), task.getTargetVersion()), MAX_VERSION_LENGTH, "targetVersion"));
        task.setTargetCommit(normalizeCommit(firstText(response.path("targetCommit").asText(null), task.getTargetCommit()), true));
        task.setServerImage(requireDigestPinnedImage(firstText(response.path("serverImage").asText(null), task.getServerImage()), "serverImage"));
        task.setPreflightId(boundedText(firstText(response.path("preflightId").asText(null), task.getPreflightId()), 128, "preflightId"));
        task.setManifestHash(boundedText(firstText(response.path("manifestHash").asText(null), task.getManifestHash()), 128, "manifestHash"));
        if (response.path("rollbackOfTaskId").canConvertToLong()) task.setRollbackOfTaskId(response.path("rollbackOfTaskId").asLong());
        if (isTerminal(task.getStatus())) task.setActiveKey(null);
    }

    private String resolveSourceType(String sourceUrl) {
        if (StringUtils.hasText(environment.getProperty("PLATFORM_UPDATE_MANIFEST_URL")) || StringUtils.hasText(System.getenv("PLATFORM_UPDATE_MANIFEST_URL"))) {
            return "manifest";
        }
        if (!StringUtils.hasText(sourceUrl)) {
            return "unknown";
        }
        return sourceUrl.contains("api.github.com") || sourceUrl.contains("github.com") ? "github" : "custom";
    }

    private String normalizeCommit(String commit, boolean optional) {
        if (!StringUtils.hasText(commit)) {
            if (optional) {
                return null;
            }
            throw new IllegalStateException("Update manifest commit is required");
        }
        String trimmed = commit.trim();
        if (trimmed.length() > MAX_COMMIT_LENGTH || !COMMIT_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalStateException("Update manifest commit is invalid");
        }
        return trimmed;
    }

    private String boundedText(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalStateException("Update field " + fieldName + " is too long");
        }
        return trimmed;
    }

    private String normalizeUpdaterTaskId(String taskId) {
        return boundedText(taskId, MAX_UPDATER_TASK_ID_LENGTH, "updater taskId");
    }

    private String normalizeUpdaterStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : fallback;
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        if (!UPDATER_STATUSES.contains(normalized)) {
            throw new IllegalStateException("Updater returned invalid task status");
        }
        return normalized;
    }

    private String firstLine(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.lines().findFirst().orElse(value).trim();
    }

    private String firstText(String... values) {
        return ServiceVersionInfoFactory.firstText(values);
    }

    private PlatformUpdateVO.TaskVO toTaskVO(PlatformUpdateTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        PlatformUpdateVO.TaskVO vo = new PlatformUpdateVO.TaskVO();
        vo.setId(entity.getId());
        vo.setTaskType(entity.getTaskType());
        vo.setStatus(entity.getStatus());
        vo.setStrategy(entity.getStrategy());
        vo.setPhase(entity.getPhase());
        vo.setProgressPercent(entity.getProgressPercent());
        vo.setActiveSlot(entity.getActiveSlot());
        vo.setTargetSlot(entity.getTargetSlot());
        vo.setPreflightId(entity.getPreflightId());
        vo.setManifestHash(entity.getManifestHash());
        vo.setRollbackOfTaskId(entity.getRollbackOfTaskId());
        vo.setTargetVersion(entity.getTargetVersion());
        vo.setTargetCommit(entity.getTargetCommit());
        vo.setServerImage(entity.getServerImage());
        vo.setFrontendImage(entity.getFrontendImage());
        vo.setUpdaterTaskId(entity.getUpdaterTaskId());
        vo.setBackupPath(entity.getBackupPath());
        vo.setLogSummary(entity.getLogSummary());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedByUuid(entity.getCreatedByUuid());
        vo.setCreatedByName(entity.getCreatedByName());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private record UpdaterRequest(String targetVersion, String targetCommit, String serverImage, String frontendImage,
                                  Long platformTaskId, String preflightId, Map<String, Object> manifest) {
    }
}
