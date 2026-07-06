package com.lumira.api.system;

public record PasswordLoginVerificationDTO(
        SystemUserSnapshotDTO user,
        Boolean passwordMatched,
        Boolean requiresPasswordChange
) {
}
