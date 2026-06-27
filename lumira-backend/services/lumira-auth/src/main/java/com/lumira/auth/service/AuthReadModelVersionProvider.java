package com.lumira.auth.service;

public interface AuthReadModelVersionProvider {

    AuthBootstrapReadModelVersions loadBootstrapVersions();

    record AuthBootstrapReadModelVersions(
            Long publicBootstrapVersion,
            Long runtimeAppearanceVersion,
            Long pluginBootstrapVersion,
            Long platformMenuTreeVersion
    ) {
    }
}
