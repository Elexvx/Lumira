package com.lumira.saas.modules.system.update.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ServiceVersionInfo;
import com.lumira.common.runtime.ServiceVersionInfoFactory;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.update.entity.PlatformUpdateTaskEntity;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import com.lumira.saas.modules.system.update.vo.PlatformUpdateVO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
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
    private static final String IMAGE_DIGEST_MARKER = "@sha256:";

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ObjectMapper objectMapper;
    private final PlatformUpdateTaskMapper taskMapper;
    private final HttpClient httpClient;
    private volatile PlatformUpdateVO.StatusVO cachedStatus;
    private volatile Path cachedGitDirectory;

    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper,
            PlatformUpdateTaskMapper taskMapper
    ) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.objectMapper = objectMapper;
        this.taskMapper = taskMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public PlatformUpdateVO.StatusVO getStatus() {
        PlatformUpdateVO.StatusVO status = cachedStatus;
        if (status == null) {
            status = checkLatest();
        }
        status.setActiveTask(findActiveTask());
        return status;
    }

    public PlatformUpdateVO.StatusVO checkLatest() {
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
        status.setUpdaterAvailable(isUpdaterAvailable());
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
                manifest.setMigrationRequired(latest.getMigrationRequired());
                manifest.setRollbackSupported(latest.getRollbackSupported());
                manifest.setReleaseNotes(latest.getTitle());
                status.setManifest(manifest);
            }
            if (!comparable) {
                status.setStatus(STATUS_UNKNOWN);
                status.setActionRequired("当前部署或更新源缺少可比较的提交信息，请确认构建注入 GIT_COMMIT 且 manifest 包含 commit。");
                status.setNotes(List.of("系统只展示状态，不会自动安装更新。", "配置 PLATFORM_UPDATE_MANIFEST_URL 后可展示镜像和发布说明。"));
            } else if (updateAvailable) {
                status.setStatus(STATUS_UPDATE_AVAILABLE);
                status.setActionRequired("发现新版本。管理员可在确认备份和维护窗口后手动安装。");
                status.setNotes(List.of("安装由宿主机 lumira-updater 代理执行。", "业务服务不会直接执行 shell 或控制 Docker。"));
            } else {
                status.setStatus(STATUS_UP_TO_DATE);
                status.setActionRequired("无需处理。");
                status.setNotes(List.of("当前部署提交与更新源一致。", "可随时手动重新检查。"));
            }
        } catch (Exception ex) {
            if (isDefaultSource(status.getSourceUrl())) {
                PlatformUpdateVO.LatestVersionVO localLatest = localLatest(currentVersion);
                if (localLatest != null) {
                    status.setLatest(localLatest);
                    status.setLatestKnown(true);
                    status.setUpdateAvailable(false);
                    status.setStatus(STATUS_UP_TO_DATE);
                    status.setActionRequired("当前为本地运行模式，已使用本地 Git 提交作为版本基准。配置 PLATFORM_UPDATE_MANIFEST_URL 后可启用远程更新检查。");
                    status.setErrorMessage(ex.getMessage());
                    status.setNotes(List.of(
                            "默认 GitHub 更新源当前不可达，已回退到本地 Git 信息。",
                            "生产更新请配置 PLATFORM_UPDATE_MANIFEST_URL、PLATFORM_UPDATE_AGENT_URL 和 PLATFORM_UPDATE_AGENT_TOKEN。"
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
            status.setActionRequired("更新源检查失败，请确认 manifest 地址、网络或令牌配置。");
            status.setErrorMessage(ex.getMessage());
            status.setNotes(List.of("检查失败不会影响当前系统运行。", "系统不会因为检查失败而执行任何更新动作。"));
        }
        status.setActiveTask(findActiveTask());
        cachedStatus = status;
        return status;
    }

    public PlatformUpdateVO.TaskVO install(CurrentUser currentUser) {
        PlatformUpdateVO.StatusVO status = checkLatest();
        if (!Boolean.TRUE.equals(status.getUpdaterAvailable())) {
            throw new IllegalStateException("平台更新代理不可用，请先配置并启动 lumira-updater。");
        }
        PlatformUpdateVO.LatestVersionVO latest = status.getLatest();
        if (latest == null || !StringUtils.hasText(latest.getServerImage())) {
            throw new IllegalStateException("当前更新源没有可安装的 serverImage，请配置 release manifest。");
        }
        PlatformUpdateTaskEntity task = createTask(TASK_INSTALL, latest, currentUser);
        return startUpdaterTask(task, "/v1/update/install");
    }

    public PlatformUpdateVO.TaskVO rollback(CurrentUser currentUser) {
        if (!isUpdaterAvailable()) {
            throw new IllegalStateException("平台更新代理不可用，请先配置并启动 lumira-updater。");
        }
        PlatformUpdateVO.LatestVersionVO latest = getStatus().getLatest();
        PlatformUpdateTaskEntity task = createTask(TASK_ROLLBACK, latest, currentUser);
        return startUpdaterTask(task, "/v1/update/rollback");
    }

    public List<PlatformUpdateVO.TaskVO> listTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<PlatformUpdateTaskEntity>()
                        .orderByDesc(PlatformUpdateTaskEntity::getCreatedAt)
                        .last("LIMIT 20"))
                .stream()
                .map(this::toTaskVO)
                .toList();
    }

    public PlatformUpdateVO.TaskVO getTask(Long id) {
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
            checkLatest();
        } catch (Exception ex) {
            log.warn("Platform update scheduled check failed", ex);
        }
    }

    private PlatformUpdateVO.TaskVO startUpdaterTask(PlatformUpdateTaskEntity task, String path) {
        try {
            JsonNode response = postUpdater(path, task);
            task.setUpdaterTaskId(firstText(response.path("taskId").asText(null), response.path("id").asText(null)));
            task.setStatus(firstText(response.path("status").asText(null), TASK_RUNNING));
            task.setLogSummary(response.path("message").asText(null));
            task.setBackupPath(response.path("backupPath").asText(null));
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        return toTaskVO(task);
    }

    private void syncUpdaterTask(PlatformUpdateTaskEntity task) {
        if (!StringUtils.hasText(task.getUpdaterTaskId()) || isTerminal(task.getStatus())) {
            return;
        }
        try {
            JsonNode response = getUpdater("/v1/update/tasks/" + task.getUpdaterTaskId());
            task.setStatus(firstText(response.path("status").asText(null), task.getStatus()));
            task.setBackupPath(firstText(response.path("backupPath").asText(null), task.getBackupPath()));
            task.setLogSummary(firstText(response.path("message").asText(null), response.path("logSummary").asText(null), task.getLogSummary()));
            task.setErrorMessage(firstText(response.path("errorMessage").asText(null), task.getErrorMessage()));
            if (isTerminal(task.getStatus()) && task.getFinishedAt() == null) {
                task.setFinishedAt(LocalDateTime.now());
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
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
        PlatformUpdateTaskEntity task = new PlatformUpdateTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setTargetVersion(latest == null ? null : latest.getVersion());
        task.setTargetCommit(latest == null ? null : latest.getCommitId());
        task.setServerImage(latest == null ? null : latest.getServerImage());
        task.setFrontendImage(latest == null ? null : latest.getFrontendImage());
        task.setCreatedBy(currentUser == null ? null : currentUser.getUserId());
        task.setCreatedByName(currentUser == null ? null : currentUser.getUsername());
        task.setStartedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    private JsonNode postUpdater(String path, PlatformUpdateTaskEntity task) throws Exception {
        String body = objectMapper.writeValueAsString(new UpdaterRequest(
                task.getTargetVersion(),
                task.getTargetCommit(),
                task.getServerImage(),
                task.getFrontendImage(),
                task.getId()
        ));
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
        if (manifestSource) {
            validateManifestSourceUrl(sourceUrl);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json, application/json")
                .header("User-Agent", "lumira-update-checker")
                .GET();
        addSourceToken(builder);
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("更新源请求失败: HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return manifestSource ? fromManifest(root) : fromGithubCommit(root);
    }

    private void validateManifestSourceUrl(String sourceUrl) {
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
        String allowedHosts = firstText(
                environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS"),
                environment.getProperty("platform.update.allowed-hosts"),
                System.getenv("PLATFORM_UPDATE_ALLOWED_HOSTS")
        );
        if (!StringUtils.hasText(allowedHosts)) {
            return;
        }
        for (String allowedHost : allowedHosts.split(",")) {
            if (host.equalsIgnoreCase(allowedHost.trim())) {
                return;
            }
        }
        throw new IllegalStateException("Update manifest host is not allowed");
    }

    private PlatformUpdateVO.LatestVersionVO fromManifest(JsonNode root) {
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(firstText(root.path("commit").asText(null), root.path("commitId").asText(null)));
        latest.setVersion(firstText(root.path("version").asText(null), latest.getCommitId()));
        latest.setBranch(firstText(root.path("branch").asText(null), root.path("channel").asText(null), DEFAULT_BRANCH));
        latest.setReleasedAt(root.path("releasedAt").asText(null));
        latest.setTitle(firstText(root.path("releaseNotes").asText(null), root.path("title").asText(null), "Lumira release"));
        latest.setUrl(firstText(root.path("url").asText(null), root.path("releaseUrl").asText(null)));
        latest.setServerImage(requireDigestPinnedImage(root.path("serverImage").asText(null), "serverImage"));
        latest.setFrontendImage(requireDigestPinnedImage(root.path("frontendImage").asText(null), "frontendImage"));
        latest.setMigrationRequired(root.path("migrationRequired").asBoolean(false));
        latest.setRollbackSupported(root.path("rollbackSupported").asBoolean(true));
        return latest;
    }

    private String requireDigestPinnedImage(String image, String fieldName) {
        if (!StringUtils.hasText(image)) {
            return image;
        }
        String trimmed = image.trim();
        if (!trimmed.contains(IMAGE_DIGEST_MARKER)) {
            throw new IllegalStateException("Update manifest " + fieldName + " must use sha256 digest pinning");
        }
        return trimmed;
    }

    private PlatformUpdateVO.LatestVersionVO fromGithubCommit(JsonNode root) {
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(firstText(root.path("commit").path("sha").asText(null), root.path("sha").asText(null)));
        latest.setVersion(firstText(root.path("version").asText(null), latest.getCommitId()));
        latest.setBranch(firstText(root.path("branch").asText(null), DEFAULT_BRANCH));
        latest.setReleasedAt(firstText(root.path("releasedAt").asText(null), root.path("commit").path("committer").path("date").asText(null)));
        latest.setTitle(firstText(root.path("title").asText(null), firstLine(root.path("commit").path("message").asText(null)), "GitHub latest commit"));
        latest.setUrl(firstText(root.path("updateUrl").asText(null), root.path("html_url").asText(null)));
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
                String.join(",", environment.getActiveProfiles())
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
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private void addSourceToken(HttpRequest.Builder builder) {
        String token = firstText(
                environment.getProperty("PLATFORM_UPDATE_SOURCE_TOKEN"),
                environment.getProperty("platform.update.source-token"),
                environment.getProperty("GITHUB_TOKEN"),
                System.getenv("PLATFORM_UPDATE_SOURCE_TOKEN"),
                System.getenv("GITHUB_TOKEN")
        );
        if (StringUtils.hasText(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private boolean isUpdaterAvailable() {
        try {
            JsonNode response = getUpdater("/v1/health");
            String status = firstText(response.path("status").asText(null), response.path("state").asText(null));
            return status == null || !"DOWN".equalsIgnoreCase(status);
        } catch (Exception ex) {
            log.debug("Platform updater agent is unavailable", ex);
            return false;
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
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "ROLLED_BACK".equals(status);
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
        vo.setTargetVersion(entity.getTargetVersion());
        vo.setTargetCommit(entity.getTargetCommit());
        vo.setServerImage(entity.getServerImage());
        vo.setFrontendImage(entity.getFrontendImage());
        vo.setUpdaterTaskId(entity.getUpdaterTaskId());
        vo.setBackupPath(entity.getBackupPath());
        vo.setLogSummary(entity.getLogSummary());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedByName(entity.getCreatedByName());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private record UpdaterRequest(String targetVersion, String targetCommit, String serverImage, String frontendImage, Long platformTaskId) {
    }
}
