package com.lumira.common.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@Component
public class PermissionGuard {

    private final AuthorizationService authorizationService;

    public PermissionGuard() {
        this.authorizationService = null;
    }

    @Autowired
    public PermissionGuard(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public void requirePermission(CurrentUser currentUser, String permissionKey) {
        if (authorizationService != null) {
            authorizationService.require(AuthorizationRequest.permission(currentUser, permissionKey));
            return;
        }
        if (!StringUtils.hasText(permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission configuration");
        }
        if (!isTrustedCurrentUser(currentUser)
                || (!currentUser.getPermissions().contains("*") && !currentUser.getPermissions().contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
    }

    public boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        if (!StringUtils.hasText(permissionKey) || !isTrustedCurrentUser(currentUser)) {
            return false;
        }
        if (authorizationService != null) {
            return authorizationService.evaluate(AuthorizationRequest.permission(currentUser, permissionKey)).allowed();
        }
        return currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(permissionKey);
    }
}
