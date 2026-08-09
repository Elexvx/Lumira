package com.lumira.api.expert;

/** Expert-owner eligibility snapshot used by Competition assignment decisions. */
public record ExpertSnapshot(
        Long expertId,
        Long userId,
        String userUuid,
        String status,
        String approvalStatus,
        String accountStatus
) {
}
