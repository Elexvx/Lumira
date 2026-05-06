package com.legendary.invention.file.app;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import org.springframework.stereotype.Component;

@Component
public class PermissionGuard {

    public void requirePermission(CurrentUser currentUser, String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return;
        }
        if (currentUser == null
                || currentUser.getPermissions() == null
                || (!currentUser.getPermissions().contains("*") && !currentUser.getPermissions().contains(permissionKey))) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + permissionKey);
        }
    }
}
