package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiAssistantEmployeeRepository;
import com.lumira.saas.modules.ai.repository.AiConversationPersistenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AiConversationService {

    Long ensureConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, String title);

    Long recordMessage(Long ownerUserId, String ownerUserUuid, Long conversationId, String role, String content);

    void recordMessageAttachments(CurrentUser currentUser, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments);
}

@Service
@Primary
class JdbcAiConversationService implements AiConversationService {
    private static final String PERMISSION_AI_CHAT_SEND = "ai:chat:send";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final int MAX_MESSAGE_ATTACHMENTS = 10;

    private final AiConversationPersistenceRepository conversationRepository;
    private final FileInternalApi fileInternalApi;
    private final AiAssistantEmployeeResolver aiAssistantEmployeeResolver;
    private final SystemInternalApi systemInternalApi;
    private final AiTrustedSessionResolver sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    JdbcAiConversationService(AiConversationPersistenceRepository conversationRepository, FileInternalApi fileInternalApi) {
        this(conversationRepository, fileInternalApi, null, null, null, false);
    }

    JdbcAiConversationService(
            AiConversationPersistenceRepository conversationRepository,
            FileInternalApi fileInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(conversationRepository, fileInternalApi, null, sessionAuthenticationService, null, false);
    }

    JdbcAiConversationService(
            AiConversationPersistenceRepository conversationRepository,
            FileInternalApi fileInternalApi,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(conversationRepository, fileInternalApi, systemInternalApi, sessionAuthenticationService, null, true);
    }

    @Autowired
    JdbcAiConversationService(
            AiConversationPersistenceRepository conversationRepository,
            FileInternalApi fileInternalApi,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            AiAssistantEmployeeRepository assistantEmployeeRepository
    ) {
        this(conversationRepository, fileInternalApi, systemInternalApi, sessionAuthenticationService, assistantEmployeeRepository, true);
    }

    private JdbcAiConversationService(
            AiConversationPersistenceRepository conversationRepository,
            FileInternalApi fileInternalApi,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            AiAssistantEmployeeRepository assistantEmployeeRepository,
            boolean enforceTrustedUserResolution
    ) {
        this.conversationRepository = conversationRepository;
        this.fileInternalApi = fileInternalApi;
        this.aiAssistantEmployeeResolver = new AiAssistantEmployeeResolver(assistantEmployeeRepository);
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Override
    @Transactional
    public Long ensureConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, Long conversationId, String title) {
        String trustedOwnerUserUuid = requireUserUuid(ownerUserUuid);
        Long resolvedEmployeeId = employeeId != null && employeeId > 0
                ? employeeId
                : aiAssistantEmployeeResolver.getOrCreateAssistantEmployee().getId();
        if (conversationId != null) {
            Long foundId = conversationRepository.findOwnedConversationId(ownerUserId, trustedOwnerUserUuid, conversationId)
                    .orElse(null);
            if (foundId != null) {
                int updated = conversationRepository.updateConversationEmployee(
                        ownerUserId, trustedOwnerUserUuid, foundId, resolvedEmployeeId, LocalDateTime.now());
                if (updated <= 0) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
                }
                return foundId;
            }
        }

        String resolvedTitle = StringUtils.hasText(title) ? title : "新会话";
        String conversationCode = "conv_" + UUID.randomUUID().toString().replace("-", "");
        Long createdId = conversationRepository.createConversation(
                ownerUserId, trustedOwnerUserUuid, resolvedEmployeeId, conversationCode, resolvedTitle, LocalDateTime.now());
        if (createdId == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
        }
        return createdId;
    }

    @Override
    @Transactional
    public Long recordMessage(Long ownerUserId, String ownerUserUuid, Long conversationId, String role, String content) {
        String trustedOwnerUserUuid = requireUserUuid(ownerUserUuid);
        Long messageId = conversationRepository.appendMessage(
                ownerUserId, trustedOwnerUserUuid, conversationId, role, content, LocalDateTime.now());
        if (messageId == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Conversation changed, please retry");
        }
        return messageId;
    }

    @Override
    @Transactional
    public void recordMessageAttachments(CurrentUser currentUser, Long conversationId, Long messageId, List<AiDTO.ChatAttachmentItem> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        CurrentUser runtimeUser = requireAttachmentOperator(currentUser);
        requirePositiveId(conversationId, "Conversation id is required");
        requirePositiveId(messageId, "Message id is required");
        if (attachments.size() > MAX_MESSAGE_ATTACHMENTS) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Too many message attachments");
        }

        for (AiDTO.ChatAttachmentItem attachment : attachments) {
            if (attachment == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Attachment is required");
            }
            Long fileId = requirePositiveId(attachment.getFileId(), "Attachment file id is required");
            FileSnapshot snapshot = queryFileSnapshot(runtimeUser, fileId);
            int updated = conversationRepository.upsertAttachment(
                    new AiConversationPersistenceRepository.AttachmentSnapshot(
                            conversationId,
                            messageId,
                            runtimeUser.getUserId(),
                            runtimeUser.getUserUuid(),
                            snapshot.fileId(),
                            snapshot.originalFileName(),
                            snapshot.fileExtension(),
                            snapshot.mimeType(),
                            snapshot.fileSizeBytes(),
                            snapshot.publicUrl(),
                            snapshot.previewUrl(),
                            snapshot.downloadUrl(),
                            snapshot.previewMode()
                    ),
                    LocalDateTime.now()
            );
            if (updated <= 0) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Message attachment changed, please retry");
            }
        }
    }

    private CurrentUser requireAttachmentOperator(CurrentUser currentUser) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(runtimeUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        if (!hasPermission(runtimeUser, PERMISSION_AI_CHAT_SEND)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_AI_CHAT_SEND);
        }
        return runtimeUser;
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        if (currentUser == null || currentUser.getPermissions() == null) {
            return false;
        }
        return currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(permissionKey);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return currentUser;
        }
        if (systemInternalApi == null && enforceTrustedUserResolution) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        if (systemInternalApi != null) {
            Long userId = currentUser.getUserId();
            String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
            if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid()) || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user username is unavailable");
            }
            Long simulatedRoleId = currentUser.getSimulatedRoleId();
            if (simulatedRoleId != null && simulatedRoleId <= 0) {
                simulatedRoleId = null;
            }
            PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                    ? systemInternalApi.permissionSnapshot(userId, normalizedUserUuid)
                    : systemInternalApi.simulatedRolePermissionSnapshot(userId, normalizedUserUuid, simulatedRoleId);
            if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user permissions are unavailable");
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(userSnapshot.username().trim());
            currentUser.setSimulatedRoleId(simulatedRoleId);
            currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
            currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
            currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
            currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
            currentUser.setDescendantDeptIds(permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds()));
            currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
            currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
            currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        }
        return currentUser;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(AiTrustedSessionResolver.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }

    private FileSnapshot queryFileSnapshot(CurrentUser currentUser, Long fileId) {
        Long trustedFileId = requirePositiveId(fileId, "Attachment file id is required");
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        FileObjectDTO file = fileInternalApi.getFileForUser(
                trustedFileId,
                trustedUserId(currentUser),
                trustedUserUuid(currentUser),
                trustedUsername(currentUser),
                false,
                false,
                currentUser.getSimulatedRoleId()
        );
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
        return new FileSnapshot(
                file.id(),
                file.originalFileName(),
                file.fileExtension(),
                file.mimeType(),
                file.fileSizeBytes(),
                file.publicUrl(),
                StringUtils.hasText(file.previewUrl()) ? file.previewUrl() : file.publicUrl(),
                StringUtils.hasText(file.downloadUrl()) ? file.downloadUrl() : file.publicUrl(),
                file.previewMode()
        );
    }

    private Long trustedUserId(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Attachment owner is required");
        }
        return currentUser.getUserId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        trustedUserId(currentUser);
        return currentUser.getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        trustedUserId(currentUser);
        return currentUser.getUserUuid();
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
        return id;
    }

    private String requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Conversation owner uuid is required");
        }
        return userUuid.trim();
    }

    private record FileSnapshot(
            Long fileId,
            String originalFileName,
            String fileExtension,
            String mimeType,
            Long fileSizeBytes,
            String publicUrl,
            String previewUrl,
            String downloadUrl,
            String previewMode
    ) {
    }
}
