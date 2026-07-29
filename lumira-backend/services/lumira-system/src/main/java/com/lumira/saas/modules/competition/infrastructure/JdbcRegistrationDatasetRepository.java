package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.repository.RegistrationDatasetRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistrationDatasetRepository implements RegistrationDatasetRepository {

    private static final String DATASET_SCHEMA = """
            {"version":1,"rowType":"COMPETITION_REGISTRATION","columns":["registration","team","project","members","materials"]}
            """.trim();

    private final MyBatisQueryOperations database;

    public JdbcRegistrationDatasetRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public int createDataset(
            Long competitionId,
            String competitionTitle,
            Long operatorId,
            String operatorUuid
    ) {
        return database.update(
                """
                        insert into competition_registration_dataset (
                            competition_id, dataset_code, dataset_name, schema_json, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, 'ENABLED', ?, ?, ?, ?, 0)
                        """,
                competitionId,
                "competition-registration-" + competitionId,
                competitionTitle + " · 报名数据表",
                DATASET_SCHEMA,
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
    }

    @Override
    public int linkRegistration(
            Long competitionId,
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid
    ) {
        return database.update(
                """
                        insert into competition_registration_dataset_row (
                            dataset_id, registration_id, owner_user_id, owner_user_uuid,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select dataset.id, ?, ?, ?, ?, ?, ?, ?, 0
                          from competition_registration_dataset dataset
                         where dataset.competition_id = ? and dataset.status = 'ENABLED' and dataset.deleted = 0
                        on duplicate key update
                            dataset_id = values(dataset_id),
                            owner_user_id = values(owner_user_id),
                            owner_user_uuid = values(owner_user_uuid),
                            updated_by = values(updated_by),
                            updated_by_uuid = values(updated_by_uuid),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                registrationId,
                ownerUserId,
                ownerUserUuid,
                ownerUserId,
                ownerUserUuid,
                ownerUserId,
                ownerUserUuid,
                competitionId
        );
    }

    @Override
    public int unlinkRegistration(
            Long registrationId,
            Long operatorId,
            String operatorUuid
    ) {
        return database.update(
                """
                        update competition_registration_dataset_row
                           set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                         where registration_id = ? and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                registrationId
        );
    }

    @Override
    public boolean isLinked(Long competitionId, Long registrationId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                          from competition_registration_dataset dataset
                          join competition_registration_dataset_row dataset_row
                            on dataset_row.dataset_id = dataset.id
                           and dataset_row.registration_id = ?
                           and dataset_row.deleted = 0
                         where dataset.competition_id = ?
                           and dataset.status = 'ENABLED'
                           and dataset.deleted = 0
                        """,
                Long.class,
                registrationId,
                competitionId
        );
        return count != null && count > 0;
    }
}
