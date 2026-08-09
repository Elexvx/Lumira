package com.lumira.saas.modules.system.user.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.user.repository.SystemUserManagementRepository;

/** Compatibility bridge for legacy tests; production wiring injects the typed user repository. */
public final class SystemUserManagementPersistenceAdapters {
    private SystemUserManagementPersistenceAdapters() {}

    public static SystemUserManagementRepository from(Object persistence) {
        if (persistence instanceof SystemUserManagementRepository repository) {
            return repository;
        }
        if (persistence instanceof MyBatisQueryOperations operations) {
            return new JdbcSystemUserManagementRepository(operations);
        }
        throw new IllegalArgumentException("SystemUserManagementRepository is required");
    }
}
