package com.lumira.common.security.authorization;

public record SubjectRef(
        Long subjectId,
        String subjectType,
        Long refId,
        Long tenantId
) {
    public static SubjectRef humanUser(Long tenantId, Long userId) {
        return new SubjectRef(null, "HUMAN_USER", userId, tenantId);
    }

    public static SubjectRef digitalEmployee(Long tenantId, Long employeeId) {
        return new SubjectRef(null, "DIGITAL_EMPLOYEE", employeeId, tenantId);
    }
}
