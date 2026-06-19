package com.lumira.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ServiceVersionInfo;
import com.lumira.common.runtime.ServiceVersionInfoFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController("jobVersionController")
public class VersionController {

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    public VersionController(Environment environment, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    @GetMapping("/api/v1/job/version")
    public ApiResponse<ServiceVersionInfo> version(HttpServletRequest request) {
        return ApiResponse.success(current(), null, request.getRequestURI());
    }

    private ServiceVersionInfo current() {
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
                        formatBuildTime(buildProperties)
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("GIT_COMMIT"),
                        environment.getProperty("COMMIT_SHA"),
                        environment.getProperty("VERCEL_GIT_COMMIT_SHA")
                ),
                ServiceVersionInfoFactory.firstText(
                        environment.getProperty("GIT_BRANCH"),
                        environment.getProperty("VERCEL_GIT_COMMIT_REF")
                ),
                String.join(",", environment.getActiveProfiles())
        );
    }

    private String formatBuildTime(BuildProperties buildProperties) {
        if (buildProperties == null) {
            return null;
        }
        Instant time = buildProperties.getTime();
        return time == null ? null : time.toString();
    }
}
