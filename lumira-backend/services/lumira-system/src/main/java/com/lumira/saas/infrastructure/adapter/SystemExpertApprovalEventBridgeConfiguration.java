package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.expert.ExpertApprovalEventHandler;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the bridge only when the Expert bounded context is in the aggregate runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
public class SystemExpertApprovalEventBridgeConfiguration {

    @Bean
    PlatformEventConsumer expertApprovalEventConsumer(ObjectProvider<ExpertApprovalEventHandler> expertApprovalEventHandler) {
        return new SystemExpertApprovalEventConsumerAdapter(expertApprovalEventHandler);
    }
}
