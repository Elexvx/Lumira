package com.lumira.alerting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.model.AlertingModels;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertingJobServiceTest {
    @Test
    void remainsIdleWhilePluginIsDisabled() {
        AlertingRepository repository = mock(AlertingRepository.class);
        when(repository.pluginEnabled()).thenReturn(false);
        when(repository.health()).thenReturn(health(false));
        AlertingJobService service = service(repository);

        AlertingModels.JobRunResult result = service.runOnce();

        assertThat(result.pluginEnabled()).isFalse();
        assertThat(result.evaluatedRules()).isZero();
        verify(repository, never()).acquireLease(anyString(), eq(45));
    }

    @Test
    void createsDurableFiringEventForBreachedBusinessRule() {
        AlertingRepository repository = mock(AlertingRepository.class);
        AlertingModels.RuleView rule = new AlertingModels.RuleView(
                11L, "支付事件异常", "BUSINESS_EVENT", "business.payment.paid", "GT",
                BigDecimal.ONE, 300, 0, "CRITICAL", 21L, "值班组", true, Map.of(),
                null, null, 1, LocalDateTime.now()
        );
        when(repository.pluginEnabled()).thenReturn(true);
        when(repository.acquireLease(anyString(), eq(45))).thenReturn(true);
        when(repository.dueRules(100)).thenReturn(List.of(rule));
        when(repository.businessSignalValue("business.payment.paid", 300)).thenReturn(BigDecimal.TEN);
        when(repository.activeInstance(11L)).thenReturn(Optional.empty());
        when(repository.createPendingInstance(11L, BigDecimal.TEN)).thenReturn(31L);
        when(repository.promoteToFiring(31L, BigDecimal.TEN)).thenReturn(true);
        when(repository.isSilenced(11L)).thenReturn(false);
        when(repository.repeatCandidates(100)).thenReturn(List.of());
        when(repository.claimDeliveries(anyString(), eq(1))).thenReturn(List.of());
        when(repository.health()).thenReturn(health(true));
        AlertingJobService service = service(repository);

        AlertingModels.JobRunResult result = service.runOnce();

        assertThat(result.evaluatedRules()).isEqualTo(1);
        verify(repository).createEventAndDeliveries(eq(31L), eq(21L), eq("FIRING"), anyString());
        verify(repository).recordEvaluation(11L, null);
    }

    @Test
    void pausesClaimedDeliveryWhenPluginIsDisabledBeforeSend() {
        AlertingRepository repository = mock(AlertingRepository.class);
        AlertingRepository.DeliveryJob job = new AlertingRepository.DeliveryJob(
                41L, 31L, 21L, 11L, "CHAT", "conversation-1", 0, "FIRING", "{}"
        );
        when(repository.pluginEnabled()).thenReturn(true, true, false, false);
        when(repository.acquireLease(anyString(), eq(45))).thenReturn(true);
        when(repository.dueRules(100)).thenReturn(List.of());
        when(repository.repeatCandidates(100)).thenReturn(List.of());
        when(repository.newClaimToken()).thenReturn("claim-1");
        when(repository.claimDeliveries(anyString(), eq(1))).thenReturn(List.of(job));
        when(repository.health()).thenReturn(health(false));
        AlertingJobService service = service(repository);

        AlertingModels.JobRunResult result = service.runOnce();

        assertThat(result.claimedDeliveries()).isEqualTo(1);
        assertThat(result.sentDeliveries()).isZero();
        verify(repository).pauseClaimedDelivery(41L);
        verify(repository, never()).findChannel(11L);
    }

    private static AlertingJobService service(AlertingRepository repository) {
        return new AlertingJobService(
                repository,
                mock(AlertingSecretCrypto.class),
                mock(AlertDeliveryGateway.class),
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                "http://127.0.0.1:9090",
                "http://127.0.0.1:8001",
                "test-worker"
        );
    }

    private static AlertingModels.HealthView health(boolean enabled) {
        return new AlertingModels.HealthView(enabled, "NEVER_SEEN", null, 0, 0, 0, 0, null);
    }
}
