package com.lumira.saas.modules.ai.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Prevents AI application services from reacquiring direct persistence access. */
class AiPersistenceBoundaryRegressionTest {

    private static final Path AI_SOURCE = Path.of("src/main/java/com/lumira/saas/modules/ai");
    private static final Pattern SQL_STATEMENT = Pattern.compile(
            "(?is)\\b(?:select\\s+.+?\\s+from|insert\\s+into|delete\\s+from|update\\s+[a-z_]+\\s+set)\\b"
    );

    @Test
    void applicationServicesMustStayFreeOfDirectPersistenceApisAndSql() throws IOException {
        for (String file : new String[]{
                "AiToolPolicyService.java",
                "AiConversationService.java",
                "AiToolOrchestrationService.java",
                "AiKnowledgeBaseAppService.java",
                "AiNativeToolRuntimeService.java",
                "AiEmployeeRuntimeService.java",
                "AiManagementAppService.java"
        }) {
            String source = read("app/" + file);

            assertThat(source)
                    .as(file)
                    .doesNotContain(
                            "MyBatisQueryOperations",
                            "JdbcTemplate",
                            "NamedParameterJdbcTemplate",
                            "BeanPropertyRowMapper",
                            "SqlRow",
                            "jdbcTemplate",
                            "database.query",
                            "database.update"
                    );
            assertThat(SQL_STATEMENT.matcher(source).find()).as(file).isFalse();
        }
    }

    @Test
    void everyAiApplicationBoundaryMustUseItsPortAndAdapterContract() throws IOException {
        assertPortContract("AiToolPolicyService.java", "AiToolPolicyRepository", "JdbcAiToolPolicyRepository.java");
        assertPortContract("AiConversationService.java", "AiConversationPersistenceRepository", "JdbcAiConversationPersistenceRepository.java");
        assertPortContract("AiToolOrchestrationService.java", "AiToolPlanRepository", "JdbcAiToolPlanRepository.java");
        assertPortContract("AiKnowledgeBaseAppService.java", "AiKnowledgeBasePersistencePort", "JdbcAiKnowledgeBasePersistenceAdapter.java");
        assertPortContract("AiNativeToolRuntimeService.java", "AiNativeToolRuntimeRepository", "JdbcAiNativeToolRuntimeRepository.java");
        assertPortContract("AiEmployeeRuntimeService.java", "AiEmployeeRuntimeRepository", "JdbcAiEmployeeRuntimeRepository.java");
        assertPortContract("AiManagementAppService.java", "AiManagementPersistencePort", "JdbcAiManagementPersistenceAdapter.java");
    }

    private void assertPortContract(String applicationFile, String port, String adapterFile) throws IOException {
        assertThat(read("app/" + applicationFile)).contains(port);
        assertThat(read("infrastructure/" + adapterFile)).contains("implements " + port);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(AI_SOURCE.resolve(relativePath));
    }
}
