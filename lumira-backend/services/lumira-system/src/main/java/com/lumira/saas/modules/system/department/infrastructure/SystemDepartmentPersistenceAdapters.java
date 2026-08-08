package com.lumira.saas.modules.system.department.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.department.repository.SystemDepartmentRepository;

/** Compatibility bridge for legacy unit fixtures while production injects the typed port. */
public final class SystemDepartmentPersistenceAdapters {
    private SystemDepartmentPersistenceAdapters() {}

    public static SystemDepartmentRepository from(Object persistence) {
        if (persistence instanceof SystemDepartmentRepository repository) {
            return repository;
        }
        if (persistence instanceof MyBatisQueryOperations operations) {
            return new JdbcSystemDepartmentRepository(operations);
        }
        throw new IllegalArgumentException("SystemDepartmentRepository is required");
    }
}
