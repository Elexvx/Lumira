package com.lumira.saas.modules.audit.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.repository.OperationAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationAuditServiceTest {

    @Test
    void logRecordsTrustedUserUuid() {
        OperationAuditRepository repository = mock(OperationAuditRepository.class);
        when(repository.insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class))).thenReturn(1);
        OperationAuditService service = new OperationAuditService(repository, objectProvider(null));

        service.log(1001L, "user-uuid-1001", "admin", "system", "update", "UPDATE", "SUCCESS", "updated");

        ArgumentCaptor<AuditOperationLogEntity> captor = ArgumentCaptor.forClass(AuditOperationLogEntity.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getUserUuid()).isEqualTo("user-uuid-1001");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(captor.getValue().getCreatedByUuid()).isEqualTo("user-uuid-1001");
    }

    @Test
    void logShouldRejectPositiveUserIdWithoutUserUuid() {
        OperationAuditRepository repository = mock(OperationAuditRepository.class);
        OperationAuditService service = new OperationAuditService(repository, objectProvider(null));

        assertThatThrownBy(() -> service.log(1001L, null, "admin", "system", "update", "UPDATE", "SUCCESS", "updated"))
                .isInstanceOf(BizException.class);

        verify(repository, never()).insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class));
    }

    @Test
    void logShouldRejectWhenAuditInsertMisses() {
        OperationAuditRepository repository = mock(OperationAuditRepository.class);
        when(repository.insert(org.mockito.ArgumentMatchers.any(AuditOperationLogEntity.class))).thenReturn(0);
        OperationAuditService service = new OperationAuditService(repository, objectProvider(null));

        assertThatThrownBy(() -> service.log(1001L, "user-uuid-1001", "admin", "system", "update", "UPDATE", "SUCCESS", "updated"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Operation audit changed");
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }
        };
    }
}
