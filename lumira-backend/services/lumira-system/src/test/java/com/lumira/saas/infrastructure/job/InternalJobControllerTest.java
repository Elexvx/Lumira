package com.lumira.saas.infrastructure.job;

import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
