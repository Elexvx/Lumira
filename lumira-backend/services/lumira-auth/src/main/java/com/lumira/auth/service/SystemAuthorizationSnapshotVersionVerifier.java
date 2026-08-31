package com.lumira.auth.service;

import com.lumira.api.system.port.AuthorizationVersionPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthorizationSnapshotVersionVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Split-runtime adapter for the authorization-version boundary. The system
 * control plane owns the authoritative IAM version; an unavailable internal
 * call must reject the request rather than leave an old session trusted.
 */
@Component
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class SystemAuthorizationSnapshotVersionVerifier implements AuthorizationSnapshotVersionVerifier {

    private static final Logger log = LoggerFactory.getLogger(SystemAuthorizationSnapshotVersionVerifier.class);

    private final AuthorizationVersionPort authorizationVersionPort;

    public SystemAuthorizationSnapshotVersionVerifier(AuthorizationVersionPort authorizationVersionPort) {
        this.authorizationVersionPort = authorizationVersionPort;
    }

    @Override
    public boolean isCurrent(String authorizationSnapshotVersion) {
        if (!StringUtils.hasText(authorizationSnapshotVersion)) {
            return false;
        }
        try {
            Boolean current = authorizationVersionPort.isPermissionSnapshotVersionCurrent(authorizationSnapshotVersion.trim());
            if (current == null) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
            }
            return current;
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to verify IAM authorization snapshot version through SystemInternalApi reason={}",
                    exception.getClass().getSimpleName()
            );
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
        }
    }
}
