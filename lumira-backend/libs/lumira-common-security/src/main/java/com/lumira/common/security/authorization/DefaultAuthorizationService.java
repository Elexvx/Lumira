package com.lumira.common.security.authorization;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultAuthorizationService implements AuthorizationService {

    @Override
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        if (request == null) {
            return AuthorizationDecision.deny("AUTHZ_REQUEST_MISSING", "Authorization request is missing");
        }
        if (request.tenantId() == null) {
            return AuthorizationDecision.deny("TENANT_MISSING", "Tenant context is required");
        }
        if (!StringUtils.hasText(request.permissionKey())) {
            return AuthorizationDecision.deny("PERMISSION_KEY_MISSING", "Permission key is required");
        }
        CurrentUser currentUser = request.currentUser();
        if (currentUser == null || currentUser.getPermissions() == null) {
            return AuthorizationDecision.deny("SUBJECT_PERMISSION_MISSING", "Subject permissions are missing");
        }
        if (currentUser.getCurrentTenantId() == null || !request.tenantId().equals(currentUser.getCurrentTenantId())) {
            return AuthorizationDecision.deny("TENANT_MISMATCH", "Tenant context does not match current user");
        }
        if (currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(request.permissionKey())) {
            return AuthorizationDecision.allow("LEGACY_PERMISSION_MATCH", "Permission granted");
        }
        return AuthorizationDecision.deny("LEGACY_PERMISSION_MISSING", "Permission denied");
    }

    @Override
    public void require(AuthorizationRequest request) {
        AuthorizationDecision decision = evaluate(request);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, decision.message());
        }
    }
}
