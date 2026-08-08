package com.lumira.saas.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.EventCatalogProjectionHandler;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SystemEventCatalogProjectionBridgeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SystemEventCatalogProjectionBridgeConfiguration.class)
            .withPropertyValues("lumira.runtime.control-plane-enabled=true");

    @Test
    void bridgeResolvesCatalogHandlerLazilyAfterControlPlaneAssembly() {
        contextRunner
                .withBean(EventCatalogProjectionHandler.class, () -> event -> true)
                .run(context -> {
                    assertThat(context).hasSingleBean(PlatformEventConsumer.class);
                    PlatformEventConsumer consumer = context.getBean(PlatformEventConsumer.class);
                    assertThat(consumer.supports(catalogEvent())).isTrue();
                });
    }

    @Test
    void bridgeDoesNotClaimCatalogEventsWhenCatalogOwnerIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PlatformEventConsumer.class);
            assertThat(context.getBean(PlatformEventConsumer.class).supports(catalogEvent())).isFalse();
        });
    }

    private PlatformEventOutboxEntity catalogEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(81L);
        event.setEventType(EventCatalogEventTypes.CATALOG_ITEM_UPSERTED);
        event.setPayloadJson("{\"attributes\":{\"sourceType\":\"ACTIVITY\"}}");
        return event;
    }
}
