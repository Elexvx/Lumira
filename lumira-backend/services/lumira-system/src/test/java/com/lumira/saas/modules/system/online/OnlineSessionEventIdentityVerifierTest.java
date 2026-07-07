package com.lumira.saas.modules.system.online;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
class OnlineSessionEventIdentityVerifierTest {

    @Test
    void hasTrustedIdentityShouldAcceptMatchingEnabledUser() {
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of(row("user-uuid-1001", "ENABLED")))
        );

        assertThat(verifier.hasTrustedIdentity(event("user-uuid-1001"))).isTrue();
    }

    @Test
    void hasTrustedIdentityShouldRejectUuidMismatch() {
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of(row("user-uuid-other", "ENABLED")))
        );

        assertThat(verifier.hasTrustedIdentity(event("user-uuid-1001"))).isFalse();
    }

    @Test
    void hasTrustedIdentityShouldRejectDisabledUser() {
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of(row("user-uuid-1001", "DISABLED")))
        );

        assertThat(verifier.hasTrustedIdentity(event("user-uuid-1001"))).isFalse();
    }

    @Test
    void hasTrustedIdentityShouldRejectMissingStatus() {
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of(row("user-uuid-1001", null)))
        );

        assertThat(verifier.hasTrustedIdentity(event("user-uuid-1001"))).isFalse();
    }

    @Test
    void hasTrustedIdentityShouldRejectMissingUser() {
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of())
        );

        assertThat(verifier.hasTrustedIdentity(event("user-uuid-1001"))).isFalse();
    }

    @Test
    void hasTrustedIdentityShouldAllowHeartbeatWithoutHumanIdentity() {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_HEARTBEAT);
        OnlineSessionEventIdentityVerifier verifier = new OnlineSessionEventIdentityVerifier(
                new FakeQueryOperations(List.of())
        );

        assertThat(verifier.hasTrustedIdentity(event)).isTrue();
    }

    private OnlineSessionEvent event(String userUuid) {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_UPSERT);
        event.setUserId(1001L);
        event.setUserUuid(userUuid);
        event.setSessionId("session-1001");
        return event;
    }

    private static SqlRow row(String userUuid, String status) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("uuid", userUuid);
        values.put("status", status);
        return new SqlRow(values);
    }

    private static final class FakeQueryOperations extends MyBatisQueryOperations {
        private final List<SqlRow> rows;

        private FakeQueryOperations(List<SqlRow> rows) {
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return rows.stream()
                    .map(row -> map(rowMapper, row))
                    .toList();
        }

        private <T> T map(RowMapper<T> rowMapper, SqlRow row) {
            try {
                return rowMapper.mapRow(row, 0);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
