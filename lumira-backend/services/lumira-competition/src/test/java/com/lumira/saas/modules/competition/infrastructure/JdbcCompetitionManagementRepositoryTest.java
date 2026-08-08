package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.repository.CompetitionStageRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCompetitionManagementRepositoryTest {

    @Test
    void competitionLifecycleAdapterOwnsGeneratedKeyAndOptimisticStatusPredicate() {
        CompetitionSqlOperations database = mock(CompetitionSqlOperations.class);
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        when(database.queryForObject(eq("select last_insert_id()"), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(71L);
        JdbcCompetitionManagementRepository repository = new JdbcCompetitionManagementRepository(database);
        CompetitionDTO.CompetitionUpsertRequest competition = competition();
        CompetitionManagementRepository.Actor actor = new CompetitionManagementRepository.Actor(1001L, "user-uuid-1001");

        CompetitionManagementRepository.CompetitionCreateResult created = repository.createCompetition(
                new CompetitionManagementRepository.CompetitionCreate("competition-uuid", "C202608080001", competition, actor)
        );
        int updated = repository.updateCompetition(
                new CompetitionManagementRepository.CompetitionUpdate(
                        71L,
                        "competition-uuid",
                        "C202608080001",
                        "draft",
                        "published",
                        competition,
                        actor,
                        LocalDateTime.of(2026, 8, 8, 10, 0)
                )
        );

        assertThat(created.competitionId()).isEqualTo(71L);
        assertThat(created.writeCount()).isEqualTo(1);
        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database, times(2)).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("insert into aiadc_competition"));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains(
                "where id = ? and uuid = ? and competition_no = ? and status = ? and deleted = 0"
        ));
    }

    @Test
    void settingsAdapterKeepsUuidVersionAndTombstonePredicatesInThePersistenceBoundary() {
        CompetitionSqlOperations database = mock(CompetitionSqlOperations.class);
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        JdbcCompetitionSettingsRepository repository = new JdbcCompetitionSettingsRepository(database);
        CompetitionSettingsRepository.Actor actor = new CompetitionSettingsRepository.Actor(1001L, "user-uuid-1001");

        int updated = repository.updateConfigItem(
                new CompetitionSettingsRepository.ConfigItemUpdate(
                        31L,
                        "competition-uuid",
                        21L,
                        "AGREEMENT",
                        "commitment",
                        "Commitment",
                        "{}",
                        "I agree",
                        10,
                        true,
                        true,
                        actor,
                        LocalDateTime.of(2026, 8, 8, 10, 0)
                )
        );
        int deleted = repository.softDeleteConfigItems(
                new CompetitionSettingsRepository.ConfigItemSoftDelete(
                        "competition-uuid",
                        21L,
                        List.of(31L, 32L),
                        actor,
                        LocalDateTime.of(2026, 8, 8, 10, 0)
                )
        );

        assertThat(updated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database, times(2)).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains(
                "where id = ? and competition_uuid = ? and config_set_id = ? and item_type = ? and item_key = ? and deleted = 0"
        ));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains(
                "competition_uuid = ?", "config_set_id = ?", "id in (", "?,?"
        ));
    }

    @Test
    void stageAdapterKeepsGeneratedParticipantFormSynchronizationInThePersistenceBoundary() {
        CompetitionSqlOperations database = mock(CompetitionSqlOperations.class);
        when(database.queryForObject(anyString(), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(41L, 51L);
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        JdbcCompetitionStageRepository repository = new JdbcCompetitionStageRepository(database);

        CompetitionStageRepository.StageFormSynchronizationResult result = repository.synchronizeStageForm(
                new CompetitionStageRepository.StageFormSynchronization(
                        11L,
                        "PRELIMINARY",
                        "初赛",
                        10,
                        "{\"fields\":[]}",
                        true,
                        new CompetitionStageRepository.Actor(1001L, "user-uuid-1001"),
                        LocalDateTime.of(2026, 8, 8, 10, 0)
                )
        );

        assertThat(result.createdStage()).isFalse();
        assertThat(result.createdForm()).isFalse();
        assertThat(result.stageWriteCount()).isEqualTo(1);
        assertThat(result.formWriteCount()).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database, times(2)).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("update competition_stage set stage_name"));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("update competition_stage_form set form_name"));
    }

    private CompetitionDTO.CompetitionUpsertRequest competition() {
        CompetitionDTO.CompetitionUpsertRequest competition = new CompetitionDTO.CompetitionUpsertRequest();
        competition.setCode("competition-code");
        competition.setLocale("zh");
        competition.setTitle("Innovation challenge");
        competition.setStatus("published");
        competition.setFeeMode("TEAM");
        competition.setEntryFeeMinor(0L);
        competition.setCurrency("CNY");
        competition.setFeatured(false);
        competition.setSort(100);
        return competition;
    }
}
