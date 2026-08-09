package com.lumira.saas.modules.competition.controller.internal;

import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.api.competition.CompetitionPaymentEventRequest;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.InternalServiceTokenPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionPaymentEventInternalControllerTest {
    private static final String TOKEN = "competition-owner-job-token";

    @Test
    void delegatesThePaymentSideEffectToTheCompetitionOwnerContract() {
        CompetitionPaymentEventHandler handler = mock(CompetitionPaymentEventHandler.class);
        when(handler.handleOrderPaid("event-1", "ORD-1", 9L, 1001L, "user-uuid-1001")).thenReturn(true);
        CompetitionPaymentEventInternalController controller = new CompetitionPaymentEventInternalController(handler, TOKEN);

        var response = controller.handlePaymentOrderPaid(TOKEN,
                new CompetitionPaymentEventRequest("event-1", "ORD-1", 9L, 1001L, "user-uuid-1001"));

        assertThat(response.getData()).isTrue();
        verify(handler).handleOrderPaid("event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");
    }

    @Test
    void rejectsAnUntrustedAsyncCallerBeforeReachingTheOwnerHandler() {
        CompetitionPaymentEventHandler handler = mock(CompetitionPaymentEventHandler.class);
        CompetitionPaymentEventInternalController controller = new CompetitionPaymentEventInternalController(handler, TOKEN);

        assertThatThrownBy(() -> controller.handlePaymentOrderPaid("wrong-token",
                new CompetitionPaymentEventRequest("event-1", "ORD-1", 9L, 1001L, "user-uuid-1001")))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void usesACompetitionScopedJobRouteCoveredByTheExistingInternalTokenPolicy() {
        RequestMapping mapping = CompetitionPaymentEventInternalController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/internal/jobs/competition");
        assertThat(InternalServiceTokenPolicy.tokenForPath(
                "/internal/jobs/competition/payment-order-paid",
                null, null, null, null, null, null, null, TOKEN
        )).isEqualTo(TOKEN);
    }
}
