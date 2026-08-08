package com.lumira.saas.infrastructure.job;

import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalJobControllerTest {

    private static final String TOKEN = "job-token";

    @Test
    void onlineSessionHeartbeatShouldBeNoopWhenStreamServiceIsNotAvailable() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                TOKEN
        );

        controller.onlineSessionHeartbeat(TOKEN);

        verifyNoInteractions(outboxRelay);
    }

    @Test
    void onlineSessionHeartbeatShouldCallStreamServiceWhenAvailable() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        OnlineSessionStreamService onlineSessionStreamService = mock(OnlineSessionStreamService.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("onlineSessionStreamService", onlineSessionStreamService);
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                TOKEN
        );

        controller.onlineSessionHeartbeat(TOKEN);

        verify(onlineSessionStreamService).heartbeat();
        verifyNoInteractions(outboxRelay);
    }
    @Test
    void replayOutboxShouldRejectInvalidIdBeforeRelayCall() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.replayOutbox(0L, TOKEN))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxRelay);
    }

    @Test
    void relayOutboxShouldRejectOversizedTokenBeforeRelayCall() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.relayOutbox("a".repeat(513)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxRelay);
    }

    @Test
    void relayOutboxShouldRejectWhenScopedSystemTokenMissing() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                ""
        );

        assertThatThrownBy(() -> controller.relayOutbox(TOKEN))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxRelay);
    }

    @Test
    void relayOutboxShouldPreferDedicatedJobTokenOverSystemToken() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                TOKEN
        );

        assertThatThrownBy(() -> controller.relayOutbox("system-token"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxRelay);
    }

    @Test
    void relayOutboxShouldRejectSystemTokenWhenDedicatedJobTokenIsMissing() {
        PlatformEventOutboxRelay outboxRelay = mock(PlatformEventOutboxRelay.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        InternalJobController controller = new InternalJobController(
                outboxRelay,
                beanFactory.getBeanProvider(OnlineSessionStreamService.class),
                ""
        );

        assertThatThrownBy(() -> controller.onlineSessionHeartbeat(TOKEN))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxRelay);
    }

}
