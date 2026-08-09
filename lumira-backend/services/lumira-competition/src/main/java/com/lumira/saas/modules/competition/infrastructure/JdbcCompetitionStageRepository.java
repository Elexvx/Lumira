package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.CompetitionStageRepository;
import org.springframework.stereotype.Repository;

/** MyBatis/JDBC adapter for competition stage windows and participant forms. */
@Repository
public class JdbcCompetitionStageRepository implements CompetitionStageRepository {

    private final CompetitionSqlOperations database;

    public JdbcCompetitionStageRepository(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public int updateStageWindow(StageWindowUpdate command) {
        Actor actor = command.actor();
        return database.update(
                """
                        update competition_stage
                        set stage_name = coalesce(?, stage_name),
                            material_submit_start = ?, material_submit_end = ?,
                            review_start = ?, review_end = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where competition_id = ? and stage_code = ? and deleted = 0
                        """,
                command.stageName(),
                command.materialStart(),
                command.materialEnd(),
                command.reviewStart(),
                command.reviewEnd(),
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.competitionId(),
                command.stageCode()
        );
    }

    @Override
    public StageFormSynchronizationResult synchronizeStageForm(StageFormSynchronization command) {
        Actor actor = command.actor();
        Long stageId = database.queryForObject(
                "select id from competition_stage where competition_id = ? and stage_code = ? and deleted = 0 order by id asc limit 1",
                Long.class,
                command.competitionId(),
                command.stageCode()
        );
        if (!command.enabled()) {
            if (stageId == null) {
                return new StageFormSynchronizationResult(0, 0, false, false);
            }
            int formWriteCount = database.update(
                    "update competition_stage_form set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where stage_id = ? and deleted = 0",
                    actor.userId(), actor.userUuid(), command.updatedAt(), stageId
            );
            int stageWriteCount = database.update(
                    "update competition_stage set status = 'DISABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and competition_id = ? and deleted = 0",
                    actor.userId(), actor.userUuid(), command.updatedAt(), stageId, command.competitionId()
            );
            return new StageFormSynchronizationResult(stageWriteCount, formWriteCount, false, false);
        }

        boolean createdStage = stageId == null;
        int stageWriteCount;
        if (createdStage) {
            stageWriteCount = database.update(
                    "insert into competition_stage (competition_id, stage_code, stage_name, status, sort, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted) values (?, ?, ?, 'ENABLED', ?, ?, ?, ?, ?, 0)",
                    command.competitionId(), command.stageCode(), command.stageName(), command.sort(),
                    actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid()
            );
            stageId = stageWriteCount == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            if (stageId == null) {
                return new StageFormSynchronizationResult(stageWriteCount, 0, true, false);
            }
        } else {
            stageWriteCount = database.update(
                    "update competition_stage set stage_name = ?, status = 'ENABLED', sort = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and competition_id = ? and deleted = 0",
                    command.stageName(), command.sort(), actor.userId(), actor.userUuid(), command.updatedAt(), stageId, command.competitionId()
            );
        }

        Long formId = database.queryForObject(
                "select id from competition_stage_form where stage_id = ? and deleted = 0 order by version desc, id desc limit 1",
                Long.class,
                stageId
        );
        if (formId == null) {
            int formWriteCount = database.update(
                    "insert into competition_stage_form (competition_id, stage_id, form_name, form_schema_json, version, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted) values (?, ?, ?, ?, 1, 'ENABLED', ?, ?, ?, ?, 0)",
                    command.competitionId(), stageId, command.stageName() + "材料", command.formSchemaJson(),
                    actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid()
            );
            return new StageFormSynchronizationResult(stageWriteCount, formWriteCount, createdStage, true);
        }
        int formWriteCount = database.update(
                "update competition_stage_form set form_name = ?, form_schema_json = ?, version = version + 1, status = 'ENABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and stage_id = ? and deleted = 0",
                command.stageName() + "材料", command.formSchemaJson(), actor.userId(), actor.userUuid(), command.updatedAt(), formId, stageId
        );
        return new StageFormSynchronizationResult(stageWriteCount, formWriteCount, createdStage, false);
    }
}
