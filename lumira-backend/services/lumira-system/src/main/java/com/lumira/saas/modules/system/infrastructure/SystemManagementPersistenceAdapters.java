package com.lumira.saas.modules.system.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.system.audit.infrastructure.JdbcSystemAuditQueryRepository;
import com.lumira.saas.modules.system.config.infrastructure.JdbcSystemConfigurationManagementRepository;
import com.lumira.saas.modules.system.dict.infrastructure.JdbcSystemDictionaryManagementRepository;
import com.lumira.saas.modules.system.menu.infrastructure.JdbcSystemMenuManagementRepository;
import com.lumira.saas.modules.system.profile.infrastructure.JdbcSystemCurrentUserProfileRepository;

/** Compatibility assembly for isolated legacy tests; production injects the typed bundle. */
public final class SystemManagementPersistenceAdapters {
    private SystemManagementPersistenceAdapters() {}

    public static SystemManagementPersistenceDependencies from(Object persistence) {
        // A handful of facade-only unit tests intentionally construct the
        // application service without a database. They never cross a
        // persistence boundary, so retain that isolated-test shape without
        // letting production wiring use an untyped dependency.
        if (persistence == null) {
            return new SystemManagementPersistenceDependencies(null, null, null, null, null, null);
        }
        if (persistence instanceof SystemManagementPersistenceDependencies dependencies) {
            return dependencies;
        }
        if (persistence instanceof MyBatisQueryOperations operations) {
            return new SystemManagementPersistenceDependencies(
                    new JdbcSystemCurrentUserProfileRepository(operations),
                    new JdbcSystemMenuManagementRepository(operations),
                    new JdbcSystemDictionaryManagementRepository(operations),
                    new JdbcSystemConfigurationManagementRepository(operations),
                    new JdbcSystemAuditQueryRepository(operations),
                    new ReadModelVersionService(operations)
            );
        }
        throw new IllegalArgumentException("SystemManagementPersistenceDependencies is required");
    }
}
