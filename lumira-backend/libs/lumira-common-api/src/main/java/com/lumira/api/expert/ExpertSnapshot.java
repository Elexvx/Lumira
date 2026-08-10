package com.lumira.api.expert;

/** Expert-owner snapshot used by Competition assignment and reviewer-roster decisions. */
public record ExpertSnapshot(
        Long expertId,
        Long userId,
        String userUuid,
        String name,
        String email,
        String status,
        String approvalStatus,
        String accountStatus
) {
}
