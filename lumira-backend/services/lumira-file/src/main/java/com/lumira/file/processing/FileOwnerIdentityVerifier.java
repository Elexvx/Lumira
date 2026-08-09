package com.lumira.file.processing;

import com.lumira.api.client.SystemInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Verifies that a file owner is still an enabled System user without reading
 * System-owned user persistence from the File bounded context.
 */
@Service
public class FileOwnerIdentityVerifier {

    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public FileOwnerIdentityVerifier(ObjectProvider<SystemInternalApi> systemInternalApiProvider) {
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    public void requireEnabledOwner(Long userId, String userUuid) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new IllegalStateException("File owner identity is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider == null
                ? null
                : systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new IllegalStateException("File owner identity resolver is unavailable");
        }
        String resolvedUserUuid = systemInternalApi.findTargetUserUuidById(userId);
        if (!StringUtils.hasText(resolvedUserUuid)
                || !resolvedUserUuid.trim().equals(userUuid.trim())) {
            throw new IllegalStateException("File owner is disabled or no longer active");
        }
    }
}
