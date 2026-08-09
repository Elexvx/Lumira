package com.lumira.saas.modules.system.role.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;

/** Compatibility bridge for legacy unit fixtures; production wiring injects the typed role repository. */
public final class SystemRoleManagementPersistenceAdapters {
    private SystemRoleManagementPersistenceAdapters() {}

    public static SystemRoleManagementRepository from(Object persistence) {
        if (persistence instanceof SystemRoleManagementRepository repository) {
            return repository;
        }
        if (persistence instanceof MyBatisQueryOperations operations) {
            return new JdbcSystemRoleManagementRepository(operations);
        }
        throw new IllegalArgumentException("SystemRoleManagementRepository is required");
    }
}
