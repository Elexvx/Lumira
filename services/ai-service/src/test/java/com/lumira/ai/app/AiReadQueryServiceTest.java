package com.lumira.ai.app;

import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReadQueryServiceTest {

    @Test
    void listEmployeesUsesBoundedPageAndCappedCount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyVarargs())).thenReturn(List.of());
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        var response = service.listEmployees(user(Set.of("ai:view")), 0, 500);

        assertThat(response.getPageNo()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getTotal()).isZero();
        ArgumentCaptor<Object> tenantCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> limitCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> offsetCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).query(anyString(), anyRowMapper(), tenantCaptor.capture(), limitCaptor.capture(), offsetCaptor.capture());
        assertThat(tenantCaptor.getValue()).isEqualTo(1001L);
        assertThat(limitCaptor.getValue()).isEqualTo(101L);
        assertThat(offsetCaptor.getValue()).isEqualTo(0L);
    }

    @Test
    void listToolsExposesReadOnlyAndConfirmedTools() {
        AiReadQueryService service = new AiReadQueryService(mock(JdbcTemplate.class));

        var tools = service.listTools(user(Set.of("ai:tool:view")));

        assertThat(tools)
                .extracting(tool -> tool.toolCode())
                .contains("system.permission.snapshot", "system.user.create", "file.object.search");
        assertThat(tools)
                .anySatisfy(tool -> {
                    assertThat(tool.toolCode()).isEqualTo("system.user.create");
                    assertThat(tool.needConfirm()).isTrue();
                    assertThat(tool.readOnly()).isFalse();
                });
    }

    @Test
    void listConversationMessagesUsesExistsProbeInsteadOfCount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("select 1"), anyVarargs()))
                .thenReturn(List.of(java.util.Map.of("exists", 1)));
        when(jdbcTemplate.query(anyString(), anyRowMapper(), anyVarargs())).thenReturn(List.of());
        AiReadQueryService service = new AiReadQueryService(jdbcTemplate);

        var messages = service.listConversationMessages(user(Set.of("ai:conversation:view")), 99L);

        assertThat(messages).isEmpty();
        verify(jdbcTemplate).queryForList(contains("select 1"), anyVarargs());
        verify(jdbcTemplate, never()).queryForObject(contains("count(1)"), org.mockito.ArgumentMatchers.<Class<Integer>>any(), anyVarargs());
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> anyRowMapper() {
        return org.mockito.ArgumentMatchers.any(RowMapper.class);
    }

    private Object[] anyVarargs() {
        return org.mockito.ArgumentMatchers.any(Object[].class);
    }

    private CurrentUser user(Set<String> permissions) {
        return new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, permissions);
    }
}
