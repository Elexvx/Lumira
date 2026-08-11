package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.RegistrationQueryRepository;
import com.lumira.saas.modules.competition.repository.RegistrationWriteRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRegistrationPersistenceAdapterTest {

    @Test
    void exposesBothRegistrationReadAndWritePorts() {
        JdbcRegistrationPersistenceAdapter adapter = new JdbcRegistrationPersistenceAdapter(new RecordingDatabase());

        assertThat(adapter).isInstanceOf(RegistrationQueryRepository.class);
        assertThat(adapter).isInstanceOf(RegistrationWriteRepository.class);
    }

    @Test
    void createStageDoesNotReadGeneratedIdWhenWriteWasRejected() {
        RecordingDatabase database = new RecordingDatabase();
        database.updateResult = 0;
        JdbcRegistrationPersistenceAdapter adapter = new JdbcRegistrationPersistenceAdapter(database);

        Long id = adapter.createStage(stageCommand());

        assertThat(id).isNull();
        assertThat(database.lastUpdateSql).contains("insert into competition_stage");
        assertThat(database.lastInsertIdQueryCount).isZero();
    }

    @Test
    void createStagePreservesAuditIdentityAndReturnsGeneratedId() {
        RecordingDatabase database = new RecordingDatabase();
        database.lastInsertId = 71L;
        JdbcRegistrationPersistenceAdapter adapter = new JdbcRegistrationPersistenceAdapter(database);

        Long id = adapter.createStage(stageCommand());

        assertThat(id).isEqualTo(71L);
        assertThat(database.lastUpdateSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(database.lastUpdateArgs).contains(1001L, "user-uuid-1001");
        assertThat(database.lastInsertIdQueryCount).isEqualTo(1);
    }

    @Test
    void claimPaymentTasksReturnsOnlyTheClaimedIdentityBoundTask() {
        RecordingDatabase database = new RecordingDatabase();
        database.taskRows = List.of(new LinkedHashMap<>(Map.of(
                "id", 301L,
                "registrationId", 41L,
                "providerCode", "alipay",
                "ownerUserUuid", "user-uuid-1001",
                "claimToken", "claim-1"
        )));
        JdbcRegistrationPersistenceAdapter adapter = new JdbcRegistrationPersistenceAdapter(database);

        List<RegistrationWriteRepository.PaymentOrderTask> tasks = adapter.claimPaymentOrderTasks(
                5,
                "claim-1",
                LocalDateTime.of(2026, 8, 8, 12, 0),
                LocalDateTime.of(2026, 8, 8, 12, 5)
        );

        assertThat(database.lastUpdateSql)
                .contains("r.owner_user_uuid = t.owner_user_uuid")
                .doesNotContain("sys_user", "u.uuid = r.owner_user_uuid");
        assertThat(tasks).containsExactly(new RegistrationWriteRepository.PaymentOrderTask(
                301L, 41L, "alipay", null, null, null, "user-uuid-1001", null, null, "claim-1"
        ));
    }

    private RegistrationWriteRepository.CreateStageCommand stageCommand() {
        return new RegistrationWriteRepository.CreateStageCommand(
                11L,
                "PRELIMINARY",
                "Preliminary",
                null,
                null,
                null,
                null,
                "DRAFT",
                100,
                "COUNT",
                BigDecimal.ONE,
                null,
                1001L,
                "user-uuid-1001"
        );
    }

    private static final class RecordingDatabase extends CompetitionSqlOperations {
        private int updateResult = 1;
        private Long lastInsertId = 1L;
        private String lastUpdateSql;
        private Object[] lastUpdateArgs = new Object[0];
        private int lastInsertIdQueryCount;
        private List<Map<String, Object>> taskRows = List.of();

        @Override
        public int update(String sql, Object... args) {
            lastUpdateSql = sql;
            lastUpdateArgs = args;
            return updateResult;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueryCount += 1;
                return requiredType.cast(lastInsertId);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return sql.contains("from competition_payment_order_task") ? taskRows : List.of();
        }
    }
}
