package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordPluginStateRepository;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SensitiveWordPluginStateService {

    private static final String PLUGIN_CODE = "sensitive-words";
    private static final int REQUIRED_SENSITIVE_WORD_COLUMNS = 14;
    private static final String STATUS_ENABLED = "ENABLED";

    private final SensitiveWordPluginStateRepository repository;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final boolean enforceTrustedUserResolution;
    private volatile Boolean sensitiveWordSchemaReady;

    public SensitiveWordPluginStateService(SensitiveWordPluginStateRepository repository) {
        this(repository, null, null, false);
    }

    @Autowired
    public SensitiveWordPluginStateService(
            SensitiveWordPluginStateRepository repository,
            PermissionSnapshotService permissionSnapshotService,
            @Lazy
            SystemInternalApi systemInternalApi
    ) {
        this(repository, permissionSnapshotService, systemInternalApi, true);
    }

    private SensitiveWordPluginStateService(
            SensitiveWordPluginStateRepository repository,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            boolean enforceTrustedUserResolution
    ) {
        this.repository = repository;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public SensitiveWordPluginStateService(
            SensitiveWordPluginStateRepository repository,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(repository, permissionSnapshotService, null, false);
    }

    public boolean isEnabled(CurrentUser currentUser) {
        if (!isTrustedActiveUser(currentUser)) {
            return false;
        }
        boolean enabled = repository.isPluginEnabled(PLUGIN_CODE);
        return enabled && hasSensitiveWordSchema();
    }

    private boolean isTrustedActiveUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return false;
        }
        Long userId = currentUser.getUserId();
        String userUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return false;
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(userSnapshot.userUuid())
                    || !userUuid.equals(userSnapshot.userUuid().trim())
                    || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                return false;
            }
            String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
            // "Trusted user username is unavailable" must fail closed for plugin-state probes.
            if (!StringUtils.hasText(currentUsername)) {
                return false;
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(currentUsername);
            userId = userSnapshot.userId();
            userUuid = userSnapshot.userUuid().trim();
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return true;
        }
        return permissionSnapshotService.isTrustedActiveUser(userId, userUuid);
    }

    public void ensureEnabled(CurrentUser currentUser) {
        if (!isEnabled(currentUser)) {
            throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "敏感词拦截插件未启用");
        }
    }

    public boolean hasSensitiveWordSchema() {
        Boolean cached = sensitiveWordSchemaReady;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            Boolean refreshed = sensitiveWordSchemaReady;
            if (refreshed == null) {
                refreshed = repository.hasRequiredSchema(REQUIRED_SENSITIVE_WORD_COLUMNS);
                sensitiveWordSchemaReady = refreshed;
            }
            return refreshed;
        }
    }

}
