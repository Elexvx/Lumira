package com.lumira.common.web;

import com.lumira.common.runtime.ServiceVersionInfo;
import com.lumira.common.runtime.ServiceVersionInfoFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ServiceVersionProvider {

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    public ServiceVersionProvider(Environment environment, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    public ServiceVersionInfo current() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        String applicationName = environment.getProperty("spring.application.name");
        String artifact = buildProperties == null ? null : buildProperties.getArtifact();
        String version = ServiceVersionInfoFactory.firstText(
                environment.getProperty("APP_VERSION"),
                environment.getProperty("BUILD_VERSION"),
                buildProperties == null ? null : buildProperties.getVersion()
        );
        String buildTime = ServiceVersionInfoFactory.firstText(
                environment.getProperty("BUILD_TIME"),
                formatBuildTime(buildProperties)
        );
        String commitId = ServiceVersionInfoFactory.firstText(
                environment.getProperty("GIT_COMMIT"),
                environment.getProperty("COMMIT_SHA"),
                environment.getProperty("VERCEL_GIT_COMMIT_SHA")
        );
        String branch = ServiceVersionInfoFactory.firstText(
                environment.getProperty("GIT_BRANCH"),
                environment.getProperty("VERCEL_GIT_COMMIT_REF")
        );
        return ServiceVersionInfoFactory.create(
                applicationName,
                artifact,
                version,
                buildTime,
                commitId,
                branch,
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
