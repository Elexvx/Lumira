package com.lumira.auth.service;

import com.lumira.api.auth.CurrentUserDTO;
import java.util.List;
import java.util.Map;

public interface AuthPostLoginBootstrapProvider {

    AuthPostLoginBootstrapPayload load(CurrentUserDTO currentUser);

    default AuthPostLoginBootstrapPayload load(
            CurrentUserDTO currentUser,
            AuthReadModelVersionProvider.AuthBootstrapReadModelVersions readModelVersions
    ) {
        return load(currentUser);
    }

    record AuthPostLoginBootstrapPayload(
            List<Map<String, Object>> menuTree,
            List<Object> availablePlugins,
            Map<String, Object> runtimeAppearanceSettings
    ) {
        public AuthPostLoginBootstrapPayload(List<Map<String, Object>> menuTree, List<Object> availablePlugins) {
            this(menuTree, availablePlugins, Map.of());
        }
    }
}
