package com.lumira.saas.modules.competition.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Narrow runtime assembly for the competition-management persistence adapter.
 */
@Configuration(proxyBeanMethods = false)
@Import({
        JdbcCompetitionManagementRepository.class,
        JdbcCompetitionSettingsRepository.class,
        JdbcCompetitionStageRepository.class
})
public class CompetitionManagementPersistenceAssemblyConfiguration {
}
