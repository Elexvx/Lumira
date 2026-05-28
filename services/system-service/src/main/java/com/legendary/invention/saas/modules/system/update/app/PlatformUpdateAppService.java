package com.legendary.invention.saas.modules.system.update.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.runtime.ServiceVersionInfo;
import com.legendary.invention.common.runtime.ServiceVersionInfoFactory;
import com.legendary.invention.saas.modules.system.update.vo.PlatformUpdateVO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final String DEFAULT_SOURCE_URL = "https://api.github.com/repos/Elexvx/legendary-invention/commits/main";
    private static final String STATUS_UP_TO_DATE = "UP_TO_DATE";
    private static final String STATUS_UPDATE_AVAILABLE = "UPDATE_AVAILABLE";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String STATUS_CHECK_FAILED = "CHECK_FAILED";

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile PlatformUpdateVO.StatusVO cachedStatus;

    public PlatformUpdateAppService(
            Environment environment,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectMapper objectMapper
    ) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public PlatformUpdateVO.StatusVO getStatus() {
        PlatformUpdateVO.StatusVO status = cachedStatus;
        if (status != null) {
            return status;
        }
        return checkLatest();
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
        status.setUpdateAvailable(false);

        try {
            PlatformUpdateVO.LatestVersionVO latest = fetchLatest(status.getSourceUrl());
            status.setLatest(latest);
            status.setLatestKnown(hasKnownCommit(latest.getCommitId()));
            boolean comparable = Boolean.TRUE.equals(status.getCurrentKnown()) && Boolean.TRUE.equals(status.getLatestKnown());
            boolean updateAvailable = comparable && isDifferentCommit(current.getCommitId(), latest.getCommitId());
            status.setUpdateAvailable(updateAvailable);
            if (!comparable) {
                status.setStatus(STATUS_UNKNOWN);
                status.setActionRequired("当前部署缺少可比对的提交信息，请在部署脚本中注入 GIT_COMMIT 后重新检查。");
                status.setNotes(List.of(
                        "更新源可访问，但当前版本或最新版本缺少提交号，不能可靠判断是否需要更新。",
                        "系统只做只读检查，不会自动拉代码、执行命令或重启服务。"
                ));
            } else if (updateAvailable) {
                status.setStatus(STATUS_UPDATE_AVAILABLE);
                status.setActionRequired("有新提交可用，请按发布流程完成备份、部署和健康检查。");
                status.setNotes(List.of(
                        "已基于提交号确认当前部署落后于更新源。",
                        "这个页面只提醒更新状态，不执行自动部署。"
                ));
            } else {
                status.setStatus(STATUS_UP_TO_DATE);
                status.setActionRequired("无需处理。");
                status.setNotes(List.of(
                        "当前部署提交与更新源一致。",
                        "系统会定时检查，也可以手动刷新。"
                ));
            }
        } catch (Exception ex) {
            status.setUpdateAvailable(false);
            status.setStatus(STATUS_CHECK_FAILED);
            status.setLatestKnown(false);
            status.setActionRequired("更新源检查失败，请确认网络、接口地址或令牌配置。");
            status.setErrorMessage(ex.getMessage());
            status.setNotes(List.of(
                    "检查失败不会影响当前系统运行。",
                    "系统不会因为检查失败而执行任何更新动作。"
            ));
        }
        cachedStatus = status;
        return status;
    }

    @Scheduled(initialDelayString = "${platform.update.check-initial-delay-ms:60000}", fixedDelayString = "${platform.update.check-interval-ms:1800000}")
    public void scheduledCheckLatest() {
        try {
            checkLatest();
        } catch (Exception ex) {
            log.warn("Platform update scheduled check failed", ex);
        }
    }

    private PlatformUpdateVO.LatestVersionVO fetchLatest(String sourceUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json, application/json")
                .header("User-Agent", "legendary-invention-update-checker")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("更新源请求失败: HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        PlatformUpdateVO.LatestVersionVO latest = new PlatformUpdateVO.LatestVersionVO();
        latest.setCommitId(firstText(root.path("commit").path("sha").asText(null), root.path("sha").asText(null)));
        latest.setVersion(firstText(root.path("version").asText(null), latest.getCommitId()));
        latest.setBranch(firstText(root.path("branch").asText(null), DEFAULT_BRANCH));
        latest.setReleasedAt(firstText(
                root.path("releasedAt").asText(null),
                root.path("commit").path("committer").path("date").asText(null)
        ));
        latest.setTitle(firstText(
                root.path("title").asText(null),
                firstLine(root.path("commit").path("message").asText(null)),
                "GitHub 最新提交"
        ));
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
                        environment.getProperty("COMMIT_SHA"),
                        environment.getProperty("VERCEL_GIT_COMMIT_SHA")
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("GIT_BRANCH"),
                        environment.getProperty("VERCEL_GIT_COMMIT_REF"),
                        DEFAULT_BRANCH
                ),
                String.join(",", environment.getActiveProfiles())
        );
    }

    private String resolveSourceUrl() {
        return firstText(
                environment.getProperty("PLATFORM_UPDATE_SOURCE_URL"),
                System.getenv("PLATFORM_UPDATE_SOURCE_URL"),
                DEFAULT_SOURCE_URL
        );
    }

    private boolean isDifferentCommit(String currentCommit, String latestCommit) {
        String normalizedCurrent = currentCommit.trim();
        String normalizedLatest = latestCommit.trim();
        return !normalizedLatest.startsWith(normalizedCurrent) && !normalizedCurrent.startsWith(normalizedLatest);
    }

    private boolean hasKnownCommit(String commit) {
        return StringUtils.hasText(commit) && !"unknown".equalsIgnoreCase(commit.trim());
    }

    private String resolveSourceType(String sourceUrl) {
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
}
