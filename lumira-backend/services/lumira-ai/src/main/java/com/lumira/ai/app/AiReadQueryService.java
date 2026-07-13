package com.lumira.ai.app;

import com.lumira.ai.vo.AiConversationVO;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.AiKnowledgeBaseVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import com.lumira.ai.vo.AiMessageAttachmentVO;
import com.lumira.ai.vo.AiMessageVO;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.ai.vo.PageResponse;
import com.lumira.ai.repository.AiConversationReadRepository;
import com.lumira.ai.repository.AiKnowledgeReadRepository;
import com.lumira.ai.repository.AiEmployeeReadRepository;
import com.lumira.ai.repository.AiToolCatalogRepository;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiReadQueryService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_CONVERSATION_MESSAGES = 500;
    private static final String AI_VIEW = "ai:view";
    private static final String AI_CHAT_SEND = "ai:chat:send";
    private static final String AI_KNOWLEDGE_VIEW = "ai:knowledge:view";
    private static final String AI_TOOL_VIEW = "ai:tool:view";

    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final AiToolCatalogRepository toolCatalogRepository;
    private final AiEmployeeReadRepository employeeReadRepository;
    private final AiConversationReadRepository conversationReadRepository;
    private final AiKnowledgeReadRepository knowledgeReadRepository;

    @Autowired
    public AiReadQueryService(
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            AiToolCatalogRepository toolCatalogRepository,
            AiEmployeeReadRepository employeeReadRepository,
            AiConversationReadRepository conversationReadRepository,
            AiKnowledgeReadRepository knowledgeReadRepository
    ) {
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.toolCatalogRepository = toolCatalogRepository;
        this.employeeReadRepository = employeeReadRepository;
        this.conversationReadRepository = conversationReadRepository;
        this.knowledgeReadRepository = knowledgeReadRepository;
    }

    public PageResponse<AiEmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        requireAnyPermission(currentUser, AI_VIEW, AI_CHAT_SEND);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiEmployeeVO> records = employeeReadRepository.findPage(bounds.limitPlusOne(), bounds.offset());
        return cappedPage(records, bounds);
    }

    public AiEmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        requireAnyPermission(currentUser, AI_VIEW, AI_CHAT_SEND);
        return employeeReadRepository.findFirstEnabled().orElse(null);
    }

    public PageResponse<AiConversationVO> listConversations(CurrentUser currentUser, Long employeeId, long pageNo, long pageSize) {
        requirePermission(currentUser, AI_CHAT_SEND);
        Long userId = currentUserId(currentUser);
        String userUuid = currentUserUuid(currentUser);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiConversationVO> records = conversationReadRepository.findConversations(
                userId, userUuid, employeeId, bounds.limitPlusOne(), bounds.offset());
        return cappedPage(records, bounds);
    }

    public List<AiMessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        requirePermission(currentUser, AI_CHAT_SEND);
        Long userId = currentUserId(currentUser);
        requireConversation(userId, currentUserUuid(currentUser), conversationId);
        List<AiMessageVO> messages = conversationReadRepository.findMessages(conversationId, MAX_CONVERSATION_MESSAGES);
        Map<Long, List<AiMessageAttachmentVO>> attachments = conversationReadRepository.findAttachmentsByMessage(conversationId);
        return messages.stream()
                .map(message -> message.withAttachments(attachments.getOrDefault(message.id(), List.of())))
                .toList();
    }

    public PageResponse<AiKnowledgeBaseVO> listKnowledgeBases(
            CurrentUser currentUser,
            String keyword,
            String status,
            String scope,
            long pageNo,
            long pageSize
    ) {
        requirePermission(currentUser, AI_KNOWLEDGE_VIEW);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiKnowledgeBaseVO> records = knowledgeReadRepository.findKnowledgeBases(
                knowledgeAccess(currentUser), keyword, status, scope, bounds.limitPlusOne(), bounds.offset());
        return cappedPage(records, bounds);
    }

    public AiKnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, AI_KNOWLEDGE_VIEW);
        return knowledgeReadRepository.findAccessibleKnowledgeBase(id, knowledgeAccess(currentUser))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    public AiKnowledgeBaseVO requireManageableKnowledgeBase(CurrentUser currentUser, Long id) {
        return knowledgeReadRepository.findManageableKnowledgeBase(id, knowledgeAccess(currentUser))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Knowledge base not found or manageable access denied"));
    }

    public PageResponse<AiKnowledgeDocumentVO> listKnowledgeDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize) {
        requirePermission(currentUser, AI_KNOWLEDGE_VIEW);
        getKnowledgeBase(currentUser, knowledgeBaseId);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiKnowledgeDocumentVO> records = knowledgeReadRepository.findDocuments(
                knowledgeBaseId, bounds.limitPlusOne(), bounds.offset());
        return cappedPage(records, bounds);
    }

    public List<AiToolVO> listTools(CurrentUser currentUser) {
        requirePermission(currentUser, AI_TOOL_VIEW);
        return allTools().stream()
                .filter(tool -> canViewTool(currentUser, tool))
                .sorted((left, right) -> left.toolCode().compareTo(right.toolCode()))
                .toList();
    }

    AiKnowledgeDocumentVO getManageableKnowledgeDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        requireManageableKnowledgeBase(currentUser, knowledgeBaseId);
        return knowledgeReadRepository.findDocument(knowledgeBaseId, documentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Knowledge document not found"));
    }

    List<AiToolVO> allTools() {
        return toolCatalogRepository.findEnabledTools();
    }

    public void requireEnabledEmployee(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "AI employee is required");
        }
        if (!employeeReadRepository.existsEnabled(employeeId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "AI employee not found or disabled");
        }
    }

    private void requireConversation(Long ownerUserId, String ownerUserUuid, Long conversationId) {
        if (!conversationReadRepository.existsOwnedConversation(ownerUserId, ownerUserUuid, conversationId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
    }

    private AiKnowledgeReadRepository.AccessContext knowledgeAccess(CurrentUser currentUser) {
        Long userId = currentUserId(currentUser);
        Set<Long> departments = new LinkedHashSet<>(currentUser.getDeptIds() == null ? Set.of() : currentUser.getDeptIds());
        if (currentUser.getPrimaryDeptId() != null) {
            departments.add(currentUser.getPrimaryDeptId());
        }
        return new AiKnowledgeReadRepository.AccessContext(
                userId,
                currentUserUuid(currentUser),
                currentUser.getRoleIds() == null ? Set.of() : Set.copyOf(currentUser.getRoleIds()),
                Set.copyOf(departments),
                trustedPermissions(currentUser).contains("*")
        );
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        Set<String> permissions = trustedPermissions(currentUser);
        if (!permissions.contains("*") && !permissions.contains(permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
    }

    private void requireAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions.contains("*")) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            if (permissions.contains(permissionKey)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(" or ", permissionKeys));
    }

    private <T> PageResponse<T> cappedPage(List<T> records, PageBounds bounds) {
        boolean hasMore = records.size() > bounds.pageSize();
        List<T> boundedRecords = hasMore ? records.subList(0, Math.toIntExact(bounds.pageSize())) : records;
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(boundedRecords);
        response.setPageNo(bounds.pageNo());
        response.setPageSize(bounds.pageSize());
        response.setHasMore(hasMore);
        response.setTotal(hasMore ? bounds.offset() + boundedRecords.size() + 1L : bounds.offset() + boundedRecords.size());
        return response;
    }

    private PageBounds pageBounds(long pageNo, long pageSize) {
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.min(Math.max(1L, pageSize), MAX_PAGE_SIZE);
        return new PageBounds(safePageNo, safePageSize, (safePageNo - 1L) * safePageSize);
    }

    private Long currentUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private String currentUserUuid(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getUserUuid();
    }

    private boolean hasAllPermission(CurrentUser currentUser) {
        return trustedPermissions(currentUser).contains("*");
    }

    private boolean canViewTool(CurrentUser currentUser, AiToolVO tool) {
        if (tool == null || !StringUtils.hasText(tool.requiredPermission())) {
            return true;
        }
        if (hasAllPermission(currentUser)) {
            return true;
        }
        return trustedPermissions(currentUser).contains(tool.requiredPermission());
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (systemInternalApiProvider == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userSnapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user does not exist");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid()) || !userSnapshot.userUuid().trim().equals(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity mismatch");
        }
        if (!StringUtils.hasText(userSnapshot.status()) || !"ENABLED".equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled");
        }
        String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
        if (!StringUtils.hasText(currentUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO snapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, normalizedUserUuid)
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, normalizedUserUuid, simulatedRoleId);
        if (snapshot == null || !StringUtils.hasText(snapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setUsername(currentUsername);
        currentUser.setPermissions(trustedPermissionSet(snapshot.permissions()));
        currentUser.setRoleIds(trustedLongSet(snapshot.roleIds()));
        currentUser.setPrimaryDeptId(snapshot.primaryDeptId());
        currentUser.setDeptIds(trustedLongSet(snapshot.deptIds()));
        currentUser.setDescendantDeptIds(trustedLongSet(snapshot.descendantDeptIds()));
        currentUser.setDataScopes(snapshot.dataScopes() == null ? List.of() : List.copyOf(snapshot.dataScopes()));
        currentUser.setPermissionsVersion(snapshot.version().trim());
        currentUser.setDefaultHomePath(snapshot.defaultHomePath());
    }

    private Set<String> trustedPermissionSet(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (StringUtils.hasText(permission)) {
                normalized.add(permission.trim());
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private Set<Long> trustedLongSet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private record PageBounds(long pageNo, long pageSize, long offset) {
        long limitPlusOne() {
            return pageSize + 1L;
        }
    }
}
