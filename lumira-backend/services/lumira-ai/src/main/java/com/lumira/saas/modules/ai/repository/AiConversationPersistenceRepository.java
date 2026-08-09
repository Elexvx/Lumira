package com.lumira.saas.modules.ai.repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Owns persistence for conversations, messages, and their file snapshots. */
public interface AiConversationPersistenceRepository {

    Optional<Long> findOwnedConversationId(Long ownerUserId, String ownerUserUuid, Long conversationId);

    int updateConversationEmployee(Long ownerUserId, String ownerUserUuid, Long conversationId, Long employeeId, LocalDateTime now);

    Long createConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, String conversationCode, String title, LocalDateTime now);

    Long appendMessage(Long ownerUserId, String ownerUserUuid, Long conversationId, String role, String content, LocalDateTime now);

    int upsertAttachment(AttachmentSnapshot attachment, LocalDateTime now);

    record AttachmentSnapshot(
            Long conversationId,
            Long messageId,
            Long ownerUserId,
            String ownerUserUuid,
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
