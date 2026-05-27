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
        status.setCheckedAt(LocalDateTime.now());
        status.setNotes(List.of("GitHub 推送只用于发现新版本，不会自动执行更新。"));

        try {
            PlatformUpdateVO.LatestVersionVO latest = fetchLatest(status.getSourceUrl());
            status.setLatest(latest);
            status.setUpdateAvailable(isDifferentCommit(current.getCommitId(), latest.getCommitId()));
        } catch (Exception ex) {
            status.setUpdateAvailable(false);
            status.setErrorMessage(ex.getMessage());
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
                root.path("commit").path("message").asText(null),
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
        if (!StringUtils.hasText(currentCommit) || !StringUtils.hasText(latestCommit) || "unknown".equalsIgnoreCase(currentCommit)) {
            return false;
        }
        String normalizedCurrent = currentCommit.trim();
        String normalizedLatest = latestCommit.trim();
        return !normalizedLatest.startsWith(normalizedCurrent) && !normalizedCurrent.startsWith(normalizedLatest);
    }

    private String firstText(String... values) {
        return ServiceVersionInfoFactory.firstText(values);
    }
}
