package com.lumira.common.runtime;

public record ServiceVersionInfo(
        String serviceName,
        String artifact,
        String version,
        String buildTime,
        String commitId,
        String branch,
        String profiles,
        String javaVersion
) {
}
