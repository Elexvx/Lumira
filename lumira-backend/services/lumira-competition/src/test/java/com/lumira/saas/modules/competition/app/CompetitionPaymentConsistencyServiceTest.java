package com.lumira.saas.modules.competition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.saas.modules.competition.repository.RegistrationQueryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CompetitionPaymentConsistencyServiceTest {

    @Test
    void detectsPaidOrdersWhoseRegistrationRemainsPendingAndPublishesGauge() {
        RegistrationQueryRepository repository = mock(RegistrationQueryRepository.class);
        PaymentInternalApi paymentApi = mock(PaymentInternalApi.class);
        ObjectProvider<PaymentInternalApi> provider = provider(paymentApi);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        var candidate = candidate("PAY-2026-1");
        when(repository.findStalePendingPaymentCandidates(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(candidate));
        when(paymentApi.getOrder(1001L, "user-uuid-1001", "PAY-2026-1"))
                .thenReturn(order("PAY-2026-1", "PAID"));
        CompetitionPaymentConsistencyService service = new CompetitionPaymentConsistencyService(
                repository, provider, meterRegistry, 60L, 100
        );

        var snapshot = service.refresh();

        assertThat(snapshot.status()).isEqualTo("UP");
        assertThat(snapshot.candidatesChecked()).isEqualTo(1L);
        assertThat(snapshot.mismatchCount()).isEqualTo(1L);
        assertThat(snapshot.mismatches()).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.registrationId()).isEqualTo(71L);
            assertThat(mismatch.paymentOrderNo()).isEqualTo("PAY-2026-1");
            assertThat(mismatch.paymentStatus()).isEqualTo("PAID");
        });
        assertThat(meterRegistry.get("competition.payment.paid.registration.pending").gauge().value())
                .isEqualTo(1.0d);
    }

    @Test
    void replayChecksBothOwnerStatesBeforeDelegatingToPaymentOutbox() {
        RegistrationQueryRepository repository = mock(RegistrationQueryRepository.class);
        PaymentInternalApi paymentApi = mock(PaymentInternalApi.class);
        when(repository.findPendingPaymentCandidateByOrder("PAY-2026-2")).thenReturn(candidate("PAY-2026-2"));
        when(paymentApi.getOrder(1001L, "user-uuid-1001", "PAY-2026-2"))
                .thenReturn(order("PAY-2026-2", "PAID"));
        when(paymentApi.replayPaidOrderEvent("PAY-2026-2")).thenReturn(true);
        CompetitionPaymentConsistencyService service = new CompetitionPaymentConsistencyService(
                repository, provider(paymentApi), new SimpleMeterRegistry(), 60L, 100
        );

        var result = service.replayPaidRegistrationEvent(" PAY-2026-2 ");

        assertThat(result.status()).isEqualTo("REPLAYED");
        assertThat(result.registrationId()).isEqualTo(71L);
        verify(paymentApi).replayPaidOrderEvent("PAY-2026-2");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PaymentInternalApi> provider(PaymentInternalApi api) {
        ObjectProvider<PaymentInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(api);
        return provider;
    }

    private RegistrationQueryRepository.PendingPaymentCandidate candidate(String orderNo) {
        return new RegistrationQueryRepository.PendingPaymentCandidate(
                71L,
                "REG-2026-71",
                11L,
                "2026 AIADC",
                orderNo,
                1001L,
                "user-uuid-1001",
                LocalDateTime.now().minusMinutes(5)
        );
    }

    private PaymentOrderDTO order(String orderNo, String status) {
        return new PaymentOrderDTO(
                orderNo,
                "builtin_mock",
                "provider-order",
                "Registration",
                10_000L,
                "CNY",
                status,
                null,
                "127.0.0.1",
                null,
                null,
                Map.of(),
                null,
                null,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                LocalDateTime.now().minusMinutes(4)
        );
    }
}
