package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.registration.api.RegistrationCandidateSnapshotDTO;
import com.lumira.registration.api.RegistrationReviewInternalApi;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * In-process Registration-to-Review adapter for the modular monolith.
 *
 * <p>This is the only adapter Review application code needs to replace with a
 * remote authenticated client when Registration becomes a physical service.</p>
 */
@Service
public class RegistrationReviewInternalApiAdapter implements RegistrationReviewInternalApi {
    private final CompetitionSqlOperations database;

    public RegistrationReviewInternalApiAdapter(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public boolean stageBelongsToCompetition(Long competitionId, Long stageId) {
        return database.exists(
                """
                        select 1
                          from aiadc_competition competition
                          join competition_stage stage
                            on stage.competition_id = competition.id
                           and stage.id = ?
                           and stage.deleted = 0
                         where competition.id = ? and competition.deleted = 0
                         limit 1
                        """,
                stageId,
                competitionId
        );
    }

    @Override
    public List<RegistrationCandidateSnapshotDTO> loadEligibleCandidateSnapshots(
            Long competitionId,
            List<Long> registrationIds
    ) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(competitionId);
        StringBuilder registrationFilter = new StringBuilder();
        if (registrationIds != null && !registrationIds.isEmpty()) {
            registrationFilter.append(" and registration.id in (");
            for (int index = 0; index < registrationIds.size(); index += 1) {
                if (index > 0) {
                    registrationFilter.append(',');
                }
                registrationFilter.append('?');
                parameters.add(registrationIds.get(index));
            }
            registrationFilter.append(')');
        }
        return database.query(
                """
                        select registration.id as registrationId,
                               registration.registration_no as registrationNo,
                               registration.competition_id as competitionId,
                               registration.team_id as teamId,
                               registration.project_id as projectId,
                               registration.owner_user_id as ownerUserId,
                               registration.owner_user_uuid as ownerUserUuid,
                               registration.status,
                               registration.registration_snapshot_json as registrationSnapshotJson,
                               registration.team_snapshot_json as teamSnapshotJson,
                               registration.project_snapshot_json as projectSnapshotJson,
                               registration.member_snapshot_json as memberSnapshotJson,
                               registration.collection_schema_snapshot_json as collectionSchemaSnapshotJson,
                               coalesce((
                                   select json_arrayagg(json_object(
                                       'submissionId', submission.id,
                                       'stageId', submission.stage_id,
                                       'submissionStatus', submission.status,
                                       'submittedAt', submission.submitted_at,
                                       'fieldKey', material_value.field_key,
                                       'fieldType', material_value.field_type,
                                       'textValue', material_value.text_value,
                                       'fileId', material_value.file_id,
                                       'jsonValue', material_value.json_value
                                   ))
                                     from registration_material_submission submission
                                     left join registration_material_value material_value
                                       on material_value.submission_id = submission.id
                                      and material_value.deleted = 0
                                    where submission.registration_id = registration.id
                                      and submission.deleted = 0
                               ), json_array()) as materialSnapshotJson
                          from competition_registration registration
                          join competition_registration_dataset_row dataset_row
                            on dataset_row.registration_id = registration.id
                           and dataset_row.deleted = 0
                          join competition_registration_dataset dataset
                            on dataset.id = dataset_row.dataset_id
                           and dataset.competition_id = registration.competition_id
                           and dataset.status = 'ENABLED'
                           and dataset.deleted = 0
                         where registration.competition_id = ?
                           and registration.status in ('PAID', 'CONFIRMED')
                           and registration.deleted = 0
                        """ + registrationFilter + " order by registration.created_at asc, registration.id asc",
                (row, rowNum) -> new RegistrationCandidateSnapshotDTO(
                        row.getLong("registrationId"),
                        row.getString("registrationNo"),
                        row.getLong("competitionId"),
                        row.getLong("teamId"),
                        row.getLong("projectId"),
                        row.getLong("ownerUserId"),
                        row.getString("ownerUserUuid"),
                        row.getString("status"),
                        row.getString("registrationSnapshotJson"),
                        row.getString("teamSnapshotJson"),
                        row.getString("projectSnapshotJson"),
                        row.getString("memberSnapshotJson"),
                        row.getString("collectionSchemaSnapshotJson"),
                        row.getString("materialSnapshotJson")
                ),
                parameters.toArray()
        );
    }
}
