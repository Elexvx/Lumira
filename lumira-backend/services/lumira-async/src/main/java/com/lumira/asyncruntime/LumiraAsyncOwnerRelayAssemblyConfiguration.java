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
        RecoveryFenceRegistry.class,
        OutboxRelayCoordinator.class,
        RemoteCompetitionPaymentEventHandler.class,
        PaymentEventStreamConsumer.class,
        PaymentDeadLetterRecoveryController.class,
        AlertingWorkerLoop.class,
        AsyncRuntimeDrainCoordinator.class,
        AsyncRuntimeControlController.class
})
public class LumiraAsyncOwnerRelayAssemblyConfiguration {
}
