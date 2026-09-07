package com.lumira.saas.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuthorizationVersionStoreTest {

    @Test
    void changingOneRoleDoesNotInvalidateSessionsForAnotherRole() {
        CacheTemplate redis = mock(CacheTemplate.class);
        Map<String, String> versions = new HashMap<>();
        versions.put("lumira:runtime:authz-version:authorization:subject:user-a", "1");
        versions.put("lumira:runtime:authz-version:authorization:binding:user-a", "1");
        versions.put("lumira:runtime:authz-version:authorization:subject:user-b", "1");
        versions.put("lumira:runtime:authz-version:authorization:binding:user-b", "1");
        versions.put("lumira:runtime:authz-version:authorization:role:10", "4");
        versions.put("lumira:runtime:authz-version:authorization:role:20", "7");
        versions.put("lumira:runtime:authz-version:authorization:data-policy:role:10", "2");
        versions.put("lumira:runtime:authz-version:authorization:data-policy:role:20", "3");
        versions.put("lumira:runtime:authz-version:authorization:data-policy:global", "5");
        when(redis.multiGet(any())).thenAnswer(invocation -> ((List<String>) invocation.getArgument(0)).stream()
                .map(versions::get)
                .toList());
        AuthorizationVersionStore store = new AuthorizationVersionStore(
                mock(ReadModelVersionService.class), redis, mock(OwnerRuntimeMetrics.class)
        );
        String role10Session = store.currentVector("user-a", Set.of(10L));
        String role20Session = store.currentVector("user-b", Set.of(20L));

        versions.put("lumira:runtime:authz-version:authorization:role:10", "5");

        assertThat(store.isCurrent(role10Session)).isFalse();
        assertThat(store.isCurrent(role20Session)).isTrue();
    }

    @Test
    void redisFailureIsFailClosedInsteadOfReadingAroundRuntimeStore() {
        CacheTemplate redis = mock(CacheTemplate.class);
        when(redis.multiGet(any())).thenThrow(new IllegalStateException("redis unavailable"));
        AuthorizationVersionStore store = new AuthorizationVersionStore(
                mock(ReadModelVersionService.class), redis, mock(OwnerRuntimeMetrics.class)
        );

        assertThatThrownBy(() -> store.currentVector("user-a", Set.of(10L)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rolledBackTransactionRemovesVersionPublishedBeforeALaterDimensionFails() {
        ReadModelVersionService database = mock(ReadModelVersionService.class);
        CacheTemplate redis = mock(CacheTemplate.class);
        when(database.bump(any(), any(), any())).thenReturn(2L);
        AuthorizationVersionStore store = new AuthorizationVersionStore(
                database, redis, mock(OwnerRuntimeMetrics.class)
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            store.bumpSubject("user-a");
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(redis).remove("lumira:runtime:authz-version:authorization:subject:user-a");
    }
}
