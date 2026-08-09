package com.lumira.saas.modules.iam.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.repository.IamUserRepository;

/** Compatibility bridge for legacy unit fixtures; production injects the typed IAM repository. */
public final class IamUserPersistenceAdapters {
    private IamUserPersistenceAdapters() {}

    public static IamUserRepository from(Object persistence) {
        if (persistence instanceof IamUserRepository repository) {
            return repository;
        }
        if (persistence instanceof MyBatisQueryOperations operations) {
            return new JdbcIamUserRepository(operations);
        }
        throw new IllegalArgumentException("IamUserRepository is required");
    }
}
