package com.yourcompany.saas.modules.iam.service;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import org.springframework.stereotype.Component;

@Component
public class PermissionGuard {

    public void requirePermission(CurrentUser currentUser, String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return;
        }
        if (currentUser == null || currentUser.getPermissions() == null || !currentUser.getPermissions().contains(permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + permissionKey);
        }
    }
}
