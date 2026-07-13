package com.lumira.saas.modules.draft.repository;

import java.util.Optional;

public interface UserDraftRepository {
    Optional<UserDraft> find(Long userId, String userUuid, String draftKey);

    void save(Long userId, String userUuid, String draftKey, String payloadJson);

    void delete(Long userId, String userUuid, String draftKey);

    record UserDraft(String payloadJson, java.time.LocalDateTime updatedAt) {}
}
