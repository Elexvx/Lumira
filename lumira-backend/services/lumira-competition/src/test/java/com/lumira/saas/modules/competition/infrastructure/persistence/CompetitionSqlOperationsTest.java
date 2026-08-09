package com.lumira.saas.modules.competition.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompetitionSqlOperationsTest {

    @Test
    void springAssemblyUsesTheJdbcConstructorInsteadOfTheTestDoubleConstructor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update("update review_assignment set status = ?", "EXPIRED")).thenReturn(3);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
            context.register(CompetitionSqlOperations.class);
            context.refresh();

            assertThat(context.getBean(CompetitionSqlOperations.class)
                    .update("update review_assignment set status = ?", "EXPIRED"))
                    .isEqualTo(3);
        }
    }
}
