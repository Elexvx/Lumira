package com.lumira.saas.modules.iam.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.readmodel.ReadModelEventKey;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService.ReadModelScopeKey;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** Redis-runtime-first authorization version store with database rehydration on cache miss. */
@Service
public class AuthorizationVersionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationVersionStore.class);
    private static final String CONTEXT = "IAM";
    private static final String SUBJECT_PREFIX = "authorization:subject:";
    private static final String BINDING_PREFIX = "authorization:binding:";
    private static final String ROLE_PREFIX = "authorization:role:";
    private static final String DATA_POLICY_ROLE_PREFIX = "authorization:data-policy:role:";
    private static final String DATA_POLICY_GLOBAL = "authorization:data-policy:global";
    private static final String REDIS_PREFIX = "lumira:runtime:authz-version:";
    private static final Duration REDIS_TTL = Duration.ofDays(30);

    private final ReadModelVersionService database;
    private final CacheTemplate runtimeRedis;
    private final OwnerRuntimeMetrics metrics;

    public AuthorizationVersionStore(
            ReadModelVersionService database,
            CacheTemplate runtimeRedis,
            OwnerRuntimeMetrics metrics
    ) {
        this.database = database;
        this.runtimeRedis = runtimeRedis;
        this.metrics = metrics;
    }

    public String currentVector(String subject, Set<Long> roleIds) {
        String normalizedSubject = requireSubject(subject);
        List<Dimension> dimensions = dimensions(normalizedSubject, roleIds);
        Map<Dimension, Long> current = currentVersions(dimensions);
        Map<Long, Long> roles = new LinkedHashMap<>();
        if (roleIds != null) {
            roleIds.stream().filter(id -> id != null && id > 0).sorted()
                    .forEach(id -> roles.put(id, current.get(new Dimension(ROLE_PREFIX + id))));
        }
        return new AuthorizationVersionVector(
                normalizedSubject,
                current.get(new Dimension(SUBJECT_PREFIX + normalizedSubject)),
                current.get(new Dimension(BINDING_PREFIX + normalizedSubject)),
                roles,
                dataPolicyVersions(roleIds, current)
        ).encode();
    }

    public boolean isCurrent(String encoded) {
        final AuthorizationVersionVector expected;
        try {
            expected = AuthorizationVersionVector.parse(encoded);
        } catch (RuntimeException exception) {
            return false;
        }
        Map<Dimension, Long> current = currentVersions(dimensions(expected.subject(), expected.roleVersions().keySet()));
        if (!current.get(new Dimension(SUBJECT_PREFIX + expected.subject())).equals(expected.subjectVersion())
                || !current.get(new Dimension(BINDING_PREFIX + expected.subject())).equals(expected.bindingVersion())
                || !current.get(new Dimension(DATA_POLICY_GLOBAL)).equals(expected.dataPolicyVersions().getOrDefault(0L, -1L))) {
            return false;
        }
        for (Map.Entry<Long, Long> role : expected.roleVersions().entrySet()) {
            if (!current.get(new Dimension(ROLE_PREFIX + role.getKey())).equals(role.getValue())) {
                return false;
            }
        }
        for (Map.Entry<Long, Long> dataPolicy : expected.dataPolicyVersions().entrySet()) {
            if (dataPolicy.getKey() > 0
                    && !current.get(new Dimension(DATA_POLICY_ROLE_PREFIX + dataPolicy.getKey())).equals(dataPolicy.getValue())) {
                return false;
            }
        }
        return true;
    }

    public void bumpSubject(String subject) {
        bump(SUBJECT_PREFIX + requireSubject(subject), "iam.subject.changed");
    }

    public void bumpBinding(String subject) {
        bump(BINDING_PREFIX + requireSubject(subject), "iam.binding.changed");
    }

    public void bumpRole(long roleId) {
        if (roleId <= 0) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        bump(ROLE_PREFIX + roleId, "iam.role.changed");
    }

    public void bumpRoleDataPolicy(long roleId) {
        if (roleId <= 0) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        bump(DATA_POLICY_ROLE_PREFIX + roleId, "iam.role-data-policy.changed");
    }

    public void bumpDataPolicy() {
        bump(DATA_POLICY_GLOBAL, "iam.data-policy.changed");
    }

    private void bump(String scope, String eventName) {
        registerRollbackCacheCleanup(scope);
        try {
            long version = database.bump(CONTEXT, scope, ReadModelEventKey.unique(eventName));
            runtimeRedis.put(redisKey(scope), String.valueOf(version), REDIS_TTL);
        } catch (BizException exception) {
            metrics.recordAuthorizationVersionUnavailable();
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordAuthorizationVersionUnavailable();
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version update is unavailable");
        }
    }

    /**
     * Database version bumps participate in the caller's IAM transaction, while Redis does not.
     * If a later dimension bump fails (for example binding after subject), remove every version
     * published by the aborted transaction so the next read rehydrates from the rolled-back
     * authoritative database state instead of rejecting sessions against a phantom version.
     */
    private void registerRollbackCacheCleanup(String scope) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        String key = redisKey(scope);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    runtimeRedis.remove(key);
                } catch (RuntimeException exception) {
                    metrics.recordAuthorizationVersionUnavailable();
                    log.error(
                            "Failed to remove rolled-back IAM authorization version cache scope={} reason={}",
                            scope,
                            exception.getClass().getSimpleName()
                    );
                }
            }
        });
    }

    private Map<Dimension, Long> currentVersions(List<Dimension> dimensions) {
        List<String> keys = dimensions.stream().map(dimension -> redisKey(dimension.scope())).toList();
        final List<String> cached;
        try {
            cached = runtimeRedis.multiGet(keys);
        } catch (RuntimeException exception) {
            metrics.recordAuthorizationVersionUnavailable();
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version cache is unavailable");
        }
        Map<Dimension, Long> result = new LinkedHashMap<>();
        List<Dimension> missing = new ArrayList<>();
        for (int index = 0; index < dimensions.size(); index++) {
            String value = cached != null && index < cached.size() ? cached.get(index) : null;
            if (!StringUtils.hasText(value)) {
                missing.add(dimensions.get(index));
                continue;
            }
            try {
                result.put(dimensions.get(index), Long.parseLong(value));
            } catch (NumberFormatException exception) {
                missing.add(dimensions.get(index));
            }
        }
        if (!missing.isEmpty()) {
            rehydrate(missing, result);
        }
        return result;
    }

    private void rehydrate(List<Dimension> missing, Map<Dimension, Long> result) {
        try {
            Map<ReadModelScopeKey, Long> authoritative = database.currentVersions(
                    missing.stream().map(dimension -> new ReadModelScopeKey(CONTEXT, dimension.scope())).toList()
            );
            for (Dimension dimension : missing) {
                long version = authoritative.getOrDefault(new ReadModelScopeKey(CONTEXT, dimension.scope()), 0L);
                runtimeRedis.put(redisKey(dimension.scope()), String.valueOf(version), REDIS_TTL);
                result.put(dimension, version);
            }
            metrics.recordAuthorizationVersionRehydrate();
        } catch (BizException exception) {
            metrics.recordAuthorizationVersionUnavailable();
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordAuthorizationVersionUnavailable();
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
        }
    }

    private List<Dimension> dimensions(String subject, Set<Long> roleIds) {
        List<Dimension> dimensions = new ArrayList<>();
        dimensions.add(new Dimension(SUBJECT_PREFIX + subject));
        dimensions.add(new Dimension(BINDING_PREFIX + subject));
        if (roleIds != null) {
            roleIds.stream().filter(id -> id != null && id > 0).sorted()
                    .map(id -> new Dimension(ROLE_PREFIX + id)).forEach(dimensions::add);
            roleIds.stream().filter(id -> id != null && id > 0).sorted()
                    .map(id -> new Dimension(DATA_POLICY_ROLE_PREFIX + id)).forEach(dimensions::add);
        }
        dimensions.add(new Dimension(DATA_POLICY_GLOBAL));
        return List.copyOf(dimensions);
    }

    private String redisKey(String scope) {
        return REDIS_PREFIX + scope;
    }

    private Map<Long, Long> dataPolicyVersions(Set<Long> roleIds, Map<Dimension, Long> current) {
        Map<Long, Long> versions = new LinkedHashMap<>();
        versions.put(0L, current.get(new Dimension(DATA_POLICY_GLOBAL)));
        if (roleIds != null) {
            roleIds.stream().filter(id -> id != null && id > 0).sorted()
                    .forEach(id -> versions.put(id, current.get(new Dimension(DATA_POLICY_ROLE_PREFIX + id))));
        }
        return Map.copyOf(versions);
    }

    private String requireSubject(String subject) {
        if (!StringUtils.hasText(subject) || subject.trim().length() > 128) {
            throw new IllegalArgumentException("subject is required");
        }
        return subject.trim();
    }

    private record Dimension(String scope) {
    }
}
