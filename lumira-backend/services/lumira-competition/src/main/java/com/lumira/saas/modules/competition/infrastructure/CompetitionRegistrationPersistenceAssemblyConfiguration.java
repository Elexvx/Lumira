package com.lumira.saas.modules.competition.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Narrow runtime assembly for the competition-registration persistence adapter.
 */
@Configuration(proxyBeanMethods = false)
@Import(JdbcRegistrationPersistenceAdapter.class)
public class CompetitionRegistrationPersistenceAssemblyConfiguration {
}
