package com.lumira.asyncruntime;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicitly assembles only async orchestration and owner-facing contracts. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import({
        ControlPlaneOwnerRelayClientConfiguration.class,
        AsyncOutboxRelayController.class,
        OutboxRelayCoordinator.class,
        RemoteCompetitionPaymentEventHandler.class,
        PaymentEventStreamConsumer.class,
        AlertingWorkerLoop.class
})
public class LumiraAsyncOwnerRelayAssemblyConfiguration {
}
