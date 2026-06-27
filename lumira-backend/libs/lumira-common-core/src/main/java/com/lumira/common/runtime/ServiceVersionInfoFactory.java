package com.lumira.common.runtime;

public final class ServiceVersionInfoFactory {

    private ServiceVersionInfoFactory() {
    }

    public static ServiceVersionInfo create(
            String serviceName,
            String artifact,
            String version,
            String buildTime,
            String commitId,
            String branch,
            String profiles
    ) {
        return create(serviceName, artifact, version, buildTime, commitId, branch, profiles, null, null, null);
    }

    public static ServiceVersionInfo create(
            String serviceName,
            String artifact,
            String version,
            String buildTime,
            String commitId,
            String branch,
            String profiles,
            String frontendVersion,
            String backendVersion,
            String databaseVersion
    ) {
        return new ServiceVersionInfo(
                firstText(serviceName, artifact, "unknown-service"),
                firstText(artifact, serviceName, "unknown-artifact"),
                firstText(version, "unknown"),
                firstText(buildTime, "unknown"),
                firstText(commitId, "unknown"),
                firstText(branch, "unknown"),
                firstText(profiles, "default"),
                System.getProperty("java.version", "unknown"),
                firstText(frontendVersion, "unknown"),
                firstText(backendVersion, version, "unknown"),
                firstText(databaseVersion, "unknown")
        );
    }

    public static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
