package com.lumira.file.service;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.CompetitionStorageSpaceRequest;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service("fileInternalApi")
@Primary
public class FileInternalApiService implements FileInternalApi {

    private static final int MAX_INTERNAL_FILE_SEARCH_LIMIT = 100;
    private static final int MAX_ARTIFACT_TYPE_LENGTH = 64;
    private static final int INTERNAL_PROXY_SESSION_VERSION = 1;

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final ObjectProvider<SystemInternalApi> systemInternalApi;

    public FileInternalApiService(
            @Lazy FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            ObjectProvider<SystemInternalApi> systemInternalApi
    ) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.systemInternalApi = systemInternalApi;
    }

    @Override
    public void ensureCompetitionStorageSpace(CompetitionStorageSpaceRequest request) {
        fileManagementAppService.ensureCompetitionStorageSpace(request);
    }

    @Override
    public FileObjectDTO uploadImage(MultipartFile file, String category, String remark, String bucket) {
        return fileManagementAppService.uploadPublicImage(currentUser(), file, category, remark, bucket);
    }

    @Override
    public FileObjectDTO uploadImageForUser(
            MultipartFile file,
            String category,
            String remark,
            String bucket,
            Long userId,
            String userUuid,
            String username
    ) {
        return uploadImageForUser(file, category, remark, bucket, userId, userUuid, username, null);
    }

    @Override
    public FileObjectDTO uploadImageForUser(
            MultipartFile file,
            String category,
            String remark,
            String bucket,
            Long userId,
            String userUuid,
            String username,
            Long simulatedRoleId
    ) {
        return fileManagementAppService.uploadPublicImage(asInternalUser(userId, userUuid, username, simulatedRoleId), file, category, remark, bucket);
    }

    @Override
    public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
        return fileManagementAppService.uploadDocument(currentUser(), file, category, tags, remark, bucket);
    }

    @Override
    public FileObjectDTO uploadDocumentForUser(
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            Long userId,
            String userUuid,
            String username
    ) {
        return uploadDocumentForUser(file, category, tags, remark, bucket, userId, userUuid, username, null);
    }

    @Override
    public FileObjectDTO uploadDocumentForUser(
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            Long userId,
            String userUuid,
            String username,
            Long simulatedRoleId
    ) {
        return fileManagementAppService.uploadDocument(asInternalUser(userId, userUuid, username, simulatedRoleId), file, category, tags, remark, bucket);
    }

    @Override
    public FileContentDTO readFileContentForUser(Long fileId, Long userId, String userUuid, String username, boolean sharedScope) {
        return readFileContentForUser(fileId, userId, userUuid, username, sharedScope, null);
    }

    @Override
    public FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            Long simulatedRoleId
    ) {
        requirePositiveId(fileId, "fileId");
        return fileManagementAppService.readFileContent(asInternalUser(userId, userUuid, username, simulatedRoleId), fileId, sharedScope, false);
    }

    @Override
    public FileContentDTO readFileContentForAuthorizedBusinessReference(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String referenceType,
            Long referenceId,
            Long simulatedRoleId
    ) {
        requirePositiveId(fileId, "fileId");
        requirePositiveId(referenceId, "referenceId");
        String normalizedReferenceType = requireSafeToken(referenceType, "referenceType", 128);
        return fileManagementAppService.readAuthorizedBusinessFileContent(
                asInternalUser(userId, userUuid, username, simulatedRoleId),
                fileId,
                normalizedReferenceType,
                referenceId
        );
    }

    @Override
    public FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType,
            boolean sharedScope
    ) {
        return readProcessingArtifactForUser(fileId, userId, userUuid, username, artifactType, sharedScope, null);
    }

    @Override
    public FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType,
            boolean sharedScope,
            Long simulatedRoleId
    ) {
        requirePositiveId(fileId, "fileId");
        String normalizedArtifactType = requireSafeToken(artifactType, "artifactType", MAX_ARTIFACT_TYPE_LENGTH);
        return fileManagementAppService.readProcessingArtifact(
                asInternalUser(userId, userUuid, username, simulatedRoleId),
                fileId,
                normalizedArtifactType,
                sharedScope,
                false
        );
    }

    @Override
    public FileObjectDTO getFileForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            boolean downloadCenterScope
    ) {
        return getFileForUser(fileId, userId, userUuid, username, sharedScope, downloadCenterScope, null);
    }

    @Override
    public FileObjectDTO getFileForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            boolean downloadCenterScope,
            Long simulatedRoleId
    ) {
        requirePositiveId(fileId, "fileId");
        return fileManagementAppService.getFile(asInternalUser(userId, userUuid, username, simulatedRoleId), fileId, sharedScope, downloadCenterScope);
    }

    @Override
    public List<FileObjectDTO> searchFilesForUser(
            Long userId,
            String userUuid,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit
    ) {
        return searchFilesForUser(userId, userUuid, username, keyword, contentType, status, sharedScope, limit, null);
    }

    @Override
    public List<FileObjectDTO> searchFilesForUser(
            Long userId,
            String userUuid,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit,
            Long simulatedRoleId
    ) {
        requireInternalSearchLimit(limit);
        return fileManagementAppService.searchFilesForInternalTool(
                asInternalUser(userId, userUuid, username, simulatedRoleId),
                keyword,
                contentType,
                status,
                sharedScope,
                limit
        );
    }

    private void requireInternalSearchLimit(int limit) {
        if (limit < 1 || limit > MAX_INTERNAL_FILE_SEARCH_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid internal file search limit");
        }
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private CurrentUser asInternalUser(Long userId, String userUuid, String username, Long simulatedRoleId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid) || !StringUtils.hasText(username)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid acting user is required");
        }
        SystemUserSnapshotDTO snapshot = resolveTrustedUserSnapshot(userId);
        String trustedUsername = StringUtils.hasText(snapshot.username()) ? snapshot.username().trim() : null;
        if (!StringUtils.hasText(trustedUsername) || !trustedUsername.equals(username.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user is disabled");
        }
        String trustedUserUuid = requireTrustedUserUuid(snapshot);
        if (!trustedUserUuid.equals(userUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user identity mismatch");
        }
        Long normalizedSimulatedRoleId = normalizeSimulatedRoleId(simulatedRoleId);
        PermissionSnapshotDTO permissionSnapshot = snapshotPermissions(userId, trustedUserUuid, normalizedSimulatedRoleId);
        CurrentUser internalUser = new CurrentUser(
                userId,
                trustedUsername,
                null,
                internalProxySessionId(userId),
                INTERNAL_PROXY_SESSION_VERSION,
                true,
                trustedPermissions(permissionSnapshot),
                trustedLongSet(permissionSnapshot.roleIds()),
                permissionSnapshot.primaryDeptId(),
                trustedLongSet(permissionSnapshot.deptIds()),
                trustedLongSet(permissionSnapshot.descendantDeptIds()),
                trustedDataScopes(permissionSnapshot)
        );
        internalUser.setUserUuid(trustedUserUuid);
        internalUser.setPermissionsVersion(requirePermissionSnapshotVersion(permissionSnapshot));
        internalUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        internalUser.setSimulatedRoleId(normalizedSimulatedRoleId);
        return internalUser;
    }

    private String internalProxySessionId(Long userId) {
        return "internal-file-user-" + userId;
    }

    private SystemUserSnapshotDTO resolveTrustedUserSnapshot(Long userId) {
        SystemInternalApi internalApi = systemInternalApi == null ? null : systemInternalApi.getIfAvailable();
        if (internalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = internalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user does not exist");
        }
        return snapshot;
    }

    private PermissionSnapshotDTO snapshotPermissions(Long userId, String userUuid, Long simulatedRoleId) {
        SystemInternalApi internalApi = systemInternalApi == null ? null : systemInternalApi.getIfAvailable();
        if (internalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
        }
        PermissionSnapshotDTO snapshot = simulatedRoleId == null
                ? internalApi.permissionSnapshot(userId, userUuid)
                : internalApi.simulatedRolePermissionSnapshot(userId, userUuid, simulatedRoleId);
        if (snapshot == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user permissions are unavailable");
        }
        return snapshot;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private String requireTrustedUserUuid(SystemUserSnapshotDTO snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.userUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user uuid is required");
        }
        return snapshot.userUuid().trim();
    }

    private String requirePermissionSnapshotVersion(PermissionSnapshotDTO snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user permissions are invalid");
        }
        return snapshot.version().trim();
    }

    private Set<String> trustedPermissions(PermissionSnapshotDTO snapshot) {
        return snapshot == null || snapshot.permissions() == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(snapshot.permissions()));
    }

    private Set<Long> trustedLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private List<com.lumira.common.security.data.DataPermissionRule> trustedDataScopes(PermissionSnapshotDTO snapshot) {
        return snapshot == null || snapshot.dataScopes() == null
                ? List.of()
                : List.copyOf(snapshot.dataScopes());
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " must be a positive number");
        }
    }

    private String requireSafeToken(String value, String name, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is too long");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-' && ch != '.') {
                throw new BizException(ErrorCode.BAD_REQUEST, name + " is invalid");
            }
        }
        return normalized;
    }
}
