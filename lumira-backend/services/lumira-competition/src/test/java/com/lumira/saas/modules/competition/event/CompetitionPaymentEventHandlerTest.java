package com.lumira.saas.modules.competition.event;

import com.lumira.api.event.EventConsumptionPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionPaymentEventHandlerTest {

    @Test
    void confirmsRegistrationInsideIdempotencyGuard() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EventConsumptionPort guard = mock(EventConsumptionPort.class);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        }).when(guard).executeOnce(any(), any());
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq(9L), eq("ORD-9"), eq(1001L), eq("user-uuid-1001")))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(1, org.springframework.jdbc.core.RowMapper.class);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getLong("competition_id")).thenReturn(7L);
                    when(rs.getString("code")).thenReturn("aiadc2026");
                    when(rs.getString("participant_no")).thenReturn(null);
                    return mapper.mapRow(rs, 0);
                });
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(3L);
        when(jdbc.update(anyString(), eq("AIADC2026-0003"), eq(9L), eq("ORD-9"), eq(1001L), eq("user-uuid-1001")))
                .thenReturn(1);

        CompetitionPaymentEventHandler handler = new CompetitionPaymentEventHandler(jdbc, guard);

        assertThat(handler.handleOrderPaid("evt-9", "ORD-9", 9L, 1001L, "user-uuid-1001")).isTrue();
        verify(jdbc).update(anyString(), eq("AIADC2026-0003"), eq(9L), eq("ORD-9"), eq(1001L), eq("user-uuid-1001"));
        verify(guard).executeOnce(any(), any());
    }
}
