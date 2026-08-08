package com.lumira.saas.modules.competition.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Async-side Competition assembly. The shared payment consumer depends only on
 * the common CompetitionPaymentEventHandler contract.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@Import(CompetitionPaymentEventHandler.class)
public class CompetitionAsyncAssemblyConfiguration {
}
