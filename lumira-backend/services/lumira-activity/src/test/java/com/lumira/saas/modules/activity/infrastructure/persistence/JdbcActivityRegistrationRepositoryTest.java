package com.lumira.saas.modules.activity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class JdbcActivityRegistrationRepositoryTest {

    @Test
    void selfScopeFiltersByOwnerUserIdAndUuid() {
        RecordingActivitySqlOperations database = new RecordingActivitySqlOperations();
        JdbcActivityRegistrationRepository repository = new JdbcActivityRegistrationRepository(database);

        repository.listVisible(2001L, "user-uuid-2001", false);

        assertThat(database.sql)
                .contains("r.owner_user_id = ?")
                .contains("r.owner_user_uuid = ?");
        assertThat(database.args).containsExactly(2001L, "user-uuid-2001");
    }

    @Test
    void allScopeDoesNotApplyOwnerFilter() {
        RecordingActivitySqlOperations database = new RecordingActivitySqlOperations();
        JdbcActivityRegistrationRepository repository = new JdbcActivityRegistrationRepository(database);

        repository.listVisible(2001L, "user-uuid-2001", true);

        assertThat(database.sql)
                .doesNotContain("r.owner_user_id = ?")
                .doesNotContain("r.owner_user_uuid = ?");
        assertThat(database.args).isEmpty();
    }

    private static final class RecordingActivitySqlOperations implements ActivitySqlOperations {
        private String sql;
        private Object[] args = new Object[0];

        @Override
        public int update(String sql, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            throw new UnsupportedOperationException();
        }
    }
}
