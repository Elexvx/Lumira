package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiEmployeeReadRepository;
import com.lumira.ai.vo.AiEmployeeVO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiEmployeeReadRepository implements AiEmployeeReadRepository {

    private static final String SELECT = """
            select e.id, e.username, e.nickname, e.position, e.avatar_key,
                   e.description, e.greeting, e.default_llm_service_id,
                   e.enabled, e.sort_order, e.create_time, e.update_time,
                   s.title as default_llm_service_title
            from ai_employee e
            left join ai_llm_service s on s.id = e.default_llm_service_id and s.is_deleted = 0
            """;
    private final JdbcTemplate database;

    public JdbcAiEmployeeReadRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public List<AiEmployeeVO> findPage(long limit, long offset) {
        return database.query(SELECT + " where e.is_deleted = 0 order by e.sort_order asc, e.id desc limit ? offset ?",
                this::mapEmployee, limit, offset);
    }

    @Override
    public Optional<AiEmployeeVO> findFirstEnabled() {
        return database.query(SELECT + " where e.is_deleted = 0 and e.enabled = 1 order by e.sort_order asc, e.id desc limit 1",
                this::mapEmployee).stream().findFirst();
    }

    @Override
    public boolean existsEnabled(Long employeeId) {
        Integer count = database.queryForObject(
                "select count(1) from ai_employee where id = ? and enabled = 1 and is_deleted = 0",
                Integer.class, employeeId);
        return count != null && count > 0;
    }

    private AiEmployeeVO mapEmployee(ResultSet rs, int rowNum) throws SQLException {
        AiEmployeeVO employee = new AiEmployeeVO();
        employee.setId(rs.getLong("id"));
        employee.setUsername(rs.getString("username"));
        employee.setNickname(rs.getString("nickname"));
        employee.setPosition(rs.getString("position"));
        employee.setAvatarKey(rs.getString("avatar_key"));
        employee.setDescription(rs.getString("description"));
        employee.setGreeting(rs.getString("greeting"));
        long serviceId = rs.getLong("default_llm_service_id");
        employee.setDefaultLlmServiceId(rs.wasNull() ? null : serviceId);
        employee.setDefaultLlmServiceTitle(rs.getString("default_llm_service_title"));
        employee.setEnabled(rs.getBoolean("enabled"));
        employee.setSortOrder(rs.getInt("sort_order"));
        employee.setCreateTime(localDateTime(rs, "create_time"));
        employee.setUpdateTime(localDateTime(rs, "update_time"));
        return employee;
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
