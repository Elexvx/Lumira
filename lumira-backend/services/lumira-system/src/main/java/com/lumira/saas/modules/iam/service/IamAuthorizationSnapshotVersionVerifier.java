package com.lumira.saas.modules.iam.service;

import com.lumira.common.security.AuthorizationSnapshotVersionVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * IAM-side implementation of the authorization version boundary consumed by
 * authentication. Keeping the contract in common-security prevents the auth
 * module from depending on an IAM implementation type.
 */
@Component
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "true")
public class IamAuthorizationSnapshotVersionVerifier implements AuthorizationSnapshotVersionVerifier {

    private final PermissionSnapshotService permissionSnapshotService;

    public IamAuthorizationSnapshotVersionVerifier(PermissionSnapshotService permissionSnapshotService) {
        this.permissionSnapshotService = permissionSnapshotService;
    }

    @Override
    public boolean isCurrent(String authorizationSnapshotVersion) {
        return permissionSnapshotService.isAuthoritativeSessionPermissionSnapshotCurrent(authorizationSnapshotVersion);
    }
}
