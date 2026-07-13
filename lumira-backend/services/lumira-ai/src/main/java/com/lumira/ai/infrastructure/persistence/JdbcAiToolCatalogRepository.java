package com.lumira.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.repository.AiToolCatalogRepository;
import com.lumira.ai.vo.AiToolVO;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiToolCatalogRepository implements AiToolCatalogRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final JdbcTemplate database;
    private final ObjectMapper objectMapper;

    public JdbcAiToolCatalogRepository(JdbcTemplate database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AiToolVO> findEnabledTools() {
        return database.query("""
                select skill_code, skill_name, category, description, risk_level, read_only,
                       need_confirm, permission_key, input_schema_json
                from ai_skill
                where enabled = 1 and is_deleted = 0
                order by category asc, skill_code asc
                """, (rs, rowNum) -> new AiToolVO(
                rs.getString("skill_code"), rs.getString("skill_name"), rs.getString("category"),
                rs.getString("description"), rs.getString("risk_level"), rs.getBoolean("read_only"),
                rs.getBoolean("need_confirm"), rs.getString("permission_key"),
                parseSchema(rs.getString("input_schema_json"))
        ));
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid AI tool input schema in database", exception);
        }
    }
}
