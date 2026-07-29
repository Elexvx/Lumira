package com.lumira.saas.modules.activity.infrastructure.persistence;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcActivityRegistrationRepositoryTest {

    @Test
    void selfScopeShouldFilterByOwnerUserIdAndUuid() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcActivityRegistrationRepository repository = new JdbcActivityRegistrationRepository(database);

        repository.listVisible(2001L, "user-uuid-2001", false);

        assertThat(database.sql)
                .contains("r.owner_user_id = ?")
                .contains("r.owner_user_uuid = ?");
        assertThat(database.args).containsExactly(2001L, "user-uuid-2001");
    }

    @Test
    void allScopeShouldNotApplyOwnerFilter() {
        RecordingQueryOperations database = new RecordingQueryOperations();
        JdbcActivityRegistrationRepository repository = new JdbcActivityRegistrationRepository(database);

        repository.listVisible(2001L, "user-uuid-2001", true);

        assertThat(database.sql)
                .doesNotContain("r.owner_user_id = ?")
                .doesNotContain("r.owner_user_uuid = ?");
        assertThat(database.args).isEmpty();
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String sql;
        private Object[] args = new Object[0];

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            return List.of();
        }
    }
}
