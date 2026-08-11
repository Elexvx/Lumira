package com.lumira.payment.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Payment-side compatibility adapter for resolving a trusted actor.
 *
 * <p>Business services depend on this single boundary while request/session
 * authentication ownership is migrated to the auth control plane.</p>
 */
@Component
public class PaymentActorResolver {

    public PaymentActorResolver() {
    }

    public Actor require(CurrentUser currentUser, String requiredPermission) {
        Actor actor = requireAuthenticated(currentUser);
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (!permissions.contains("*") && !permissions.contains(requiredPermission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + requiredPermission);
        }
        return actor;
    }

    public Actor requireAuthenticated(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid user is required");
        }
        Long userId = currentUser.getUserId();
        String userUuid = normalize(currentUser.getUserUuid());
        if (userId == null || userId <= 0 || userUuid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid user is required");
        }

        if (!StringUtils.hasText(currentUser.getPermissionsVersion())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted permission snapshot is required");
        }
        return new Actor(userId, userUuid);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Actor(Long userId, String userUuid) {
    }
}
