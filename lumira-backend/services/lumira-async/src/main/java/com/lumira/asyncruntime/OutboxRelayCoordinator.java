package com.lumira.asyncruntime;

import com.lumira.file.event.FileOutboxRelay;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.payment.service.PaymentOutboxRelay;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.modules.plugin.event.PluginOutboxRelay;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * Low-latency owner relay loop. XXL-JOB remains a recovery trigger, but normal
 * publication no longer waits for an external HTTP scheduling round trip.
 */
@Component
@ConditionalOnProperty(prefix = "lumira.event.relay-loop", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayCoordinator {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayCoordinator.class);

    private final ObjectProvider<PlatformEventOutboxRelay> platformRelay;
    private final ObjectProvider<FileOutboxRelay> fileRelay;
    private final ObjectProvider<PaymentOutboxRelay> paymentRelay;
    private final ObjectProvider<PluginOutboxRelay> pluginRelay;
    private final ObjectProvider<PlatformEventOutboxService> messageOutbox;
    private final ObjectProvider<MessageEventDeliveryService> messageDelivery;
    private final ObjectProvider<MessageProperties> messageProperties;
    private final MeterRegistry meterRegistry;

    public OutboxRelayCoordinator(
            ObjectProvider<PlatformEventOutboxRelay> platformRelay,
            ObjectProvider<FileOutboxRelay> fileRelay,
            ObjectProvider<PaymentOutboxRelay> paymentRelay,
            ObjectProvider<PluginOutboxRelay> pluginRelay,
            ObjectProvider<PlatformEventOutboxService> messageOutbox,
            ObjectProvider<MessageEventDeliveryService> messageDelivery,
            ObjectProvider<MessageProperties> messageProperties,
            MeterRegistry meterRegistry
    ) {
        this.platformRelay = platformRelay;
        this.fileRelay = fileRelay;
        this.paymentRelay = paymentRelay;
        this.pluginRelay = pluginRelay;
        this.messageOutbox = messageOutbox;
        this.messageDelivery = messageDelivery;
        this.messageProperties = messageProperties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            initialDelayString = "${lumira.event.relay-loop.initial-delay-ms:3000}",
            fixedDelayString = "${lumira.event.relay-loop.fixed-delay-ms:500}"
    )
    public void relay() {
        run("platform", () -> platformRelay.getObject().dispatchPendingEvents());
        run("file", () -> fileRelay.getObject().dispatchPendingEvents());
        run("payment", () -> paymentRelay.getObject().dispatchPendingEvents());
        run("plugin", () -> pluginRelay.getObject().dispatchPendingEvents());
        run("message", this::relayMessages);
    }

    private int relayMessages() {
        return messageOutbox.getObject().dispatchPending(
                messageDelivery.getObject(),
                messageProperties.getObject().getOutboxRelayBatchSize()
        );
    }

    private void run(String owner, IntSupplier action) {
        try {
            int published = Math.max(0, action.getAsInt());
            if (published > 0) {
                meterRegistry.counter("lumira.event.relay.published", "owner", owner).increment(published);
            }
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ignored) {
            // An owner can be intentionally absent from a runtime assembly.
        } catch (RuntimeException exception) {
            meterRegistry.counter("lumira.event.relay.failure", "owner", owner).increment();
            log.warn("outbox relay failed owner={}: {}", owner, exception.getMessage());
        }
    }
}
