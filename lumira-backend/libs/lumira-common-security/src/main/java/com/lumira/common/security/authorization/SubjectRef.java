package com.lumira.common.security.authorization;

public record SubjectRef(
        Long subjectId,
        String subjectType,
        Long refId
) {
    public static SubjectRef humanUser(Long userId) {
        return new SubjectRef(null, "HUMAN_USER", userId);
    }

    public static SubjectRef digitalEmployee(Long employeeId) {
        return new SubjectRef(null, "DIGITAL_EMPLOYEE", employeeId);
    }
}
