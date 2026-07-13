package com.lumira.ai.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.infrastructure.persistence.JdbcAiConversationReadRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiEmployeeReadRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiKnowledgeReadRepository;
import com.lumira.ai.infrastructure.persistence.JdbcAiToolCatalogRepository;
import com.lumira.ai.repository.AiToolCatalogRepository;
import com.lumira.api.client.SystemInternalApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

final class AiReadQueryServiceFixture {
    private AiReadQueryServiceFixture() { }

    static AiReadQueryService create(JdbcTemplate database) {
        return create(database, null);
    }

    static AiReadQueryService create(JdbcTemplate database, ObjectProvider<SystemInternalApi> systemApi) {
        return create(database, systemApi, new JdbcAiToolCatalogRepository(database, new ObjectMapper()));
    }

    static AiReadQueryService create(JdbcTemplate database, ObjectProvider<SystemInternalApi> systemApi, AiToolCatalogRepository tools) {
        return new AiReadQueryService(
                systemApi,
                tools,
                new JdbcAiEmployeeReadRepository(database),
                new JdbcAiConversationReadRepository(database),
                new JdbcAiKnowledgeReadRepository(database)
        );
    }
}
