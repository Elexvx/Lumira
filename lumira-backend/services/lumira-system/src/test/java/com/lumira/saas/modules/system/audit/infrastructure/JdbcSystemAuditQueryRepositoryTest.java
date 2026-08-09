package com.lumira.saas.modules.system.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.api.ai.AiAuditReadPort;
import com.lumira.saas.modules.system.audit.repository.SystemAuditQueryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JdbcSystemAuditQueryRepositoryTest {

    @Test
    void rendersAiOwnerAuditRecordsThroughTheNarrowReadPort() {
        AtomicReference<AiAuditReadPort.AiToolAuditSearch> captured = new AtomicReference<>();
        AiAuditReadPort port = search -> {
            captured.set(search);
            return new AiAuditReadPort.AiToolAuditPage(List.of(new AiAuditReadPort.AiToolAuditRecord(
                    41L, 7L, 9L, "system.user.search", "Find user", "EXECUTE", 0, 1,
                    "SUCCESS", "ok", "{}", "{}", LocalDateTime.of(2026, 8, 1, 12, 0)
            )), 1L, 2L, 20L);
        };
        JdbcSystemAuditQueryRepository repository = new JdbcSystemAuditQueryRepository(null, port);

        var page = repository.findAiCallLogs(new SystemAuditQueryRepository.AiCallSearch(
                9L, "user", "SUCCESS", null, null, 2L, 20L
        ));

        assertThat(captured.get()).isEqualTo(new AiAuditReadPort.AiToolAuditSearch(
                9L, "user", "SUCCESS", null, null, 2L, 20L
        ));
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).singleElement().satisfies(record -> {
            assertThat(record.getId()).isEqualTo(41L);
            assertThat(record.getModuleName()).isEqualTo("AI");
            assertThat(record.getActionName()).isEqualTo("Find user");
            assertThat(record.getOperationType()).isEqualTo("CALL");
        });
    }
}
