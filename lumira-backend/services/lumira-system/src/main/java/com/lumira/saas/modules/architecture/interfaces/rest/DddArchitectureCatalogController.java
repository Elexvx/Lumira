package com.lumira.saas.modules.architecture.interfaces.rest;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/architecture")
public class DddArchitectureCatalogController {

    private static final String VIEW_ARCHITECTURE_PERMISSION = "system:config:view";
    private static final String STATUS_ENABLED = "ENABLED";

    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;
    private final boolean enforceTrustedUserResolution;

    public DddArchitectureCatalogController(SecurityContextFacade securityContextFacade, PermissionGuard permissionGuard) {
        this(securityContextFacade, permissionGuard, null, false);
    }

    @Autowired
    public DddArchitectureCatalogController(
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this(securityContextFacade, permissionGuard, systemInternalApi, true);
    }

    private DddArchitectureCatalogController(
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi,
            boolean enforceTrustedUserResolution
    ) {
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/contexts")
    public ApiResponse<ArchitectureCatalogResponse> contexts() {
        require(VIEW_ARCHITECTURE_PERMISSION);
        return ApiResponse.success(ArchitectureCatalogResponse.defaultCatalog(), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (systemInternalApi == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, userSnapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
        }
        String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
        if (!StringUtils.hasText(currentUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(currentUsername);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    public record ArchitectureCatalogResponse(
            String architecture,
            List<BoundedContextResponse> contexts,
            List<String> invariants
    ) {

        static ArchitectureCatalogResponse defaultCatalog() {
            return new ArchitectureCatalogResponse(
                    "ddd-modular-monolith",
                    List.of(
                            context("IAM", "system-service", "core", "User, Role, Permission, Department, PermissionSnapshot"),
                            context("Auth", "auth-service", "supporting", "AuthSession, LoginChallenge, SecondFactor, PasskeyCredential"),
                            context("Platform", "system-service", "supporting", "Config, Dict, Audit, RuntimeAppearance, Monitor, OnlineSession"),
                            context("Message", "message-service", "supporting", "Notice, ReadState, DeliveryLog, RealtimeTicket"),
                            context("File", "file-service", "generic", "FileObject, StorageSpace, UploadSession, FileProcessingTask"),
                            context("Plugin", "plugin-service", "supporting", "PluginDefinition, PluginVersion, PluginAvailability, RuntimePolicy"),
                            context("Localization", "localization-service", "supporting", "Language, Namespace, Entry, Translation, Release"),
                            context("Payment", "payment-service", "supporting", "PaymentOrder, Refund, ProviderConfig, WebhookEvent"),
                            context("AI", "system-service", "enhancement", "AiEmployee, Skill, LlmService, KnowledgeBase, Conversation"),
                            context("Job", "job-executor", "adapter", "RelayTask")
                    ),
                    List.of(
                            "Commands write only owner aggregates and publish domain events.",
                            "Queries use read models, cache, or projections instead of loading full aggregates.",
                            "Domain code must not depend on Spring, MyBatis, Redis, HTTP SDKs, or servlet APIs.",
                            "Cross-context access must use contracts, events, projections, or cache snapshots."
                    )
            );
        }

        private static BoundedContextResponse context(
                String name,
                String ownerModule,
                String domainType,
                String primaryModels
        ) {
            return new BoundedContextResponse(
                    name,
                    ownerModule,
                    domainType,
                    primaryModels,
                    List.of(name.toUpperCase() + "_EVENTS"),
                    "context:scope:version"
            );
        }
    }

    public record BoundedContextResponse(
            String name,
            String ownerModule,
            String domainType,
            String primaryModels,
            List<String> eventFamilies,
            String readModelCacheKey
    ) {
    }
}
