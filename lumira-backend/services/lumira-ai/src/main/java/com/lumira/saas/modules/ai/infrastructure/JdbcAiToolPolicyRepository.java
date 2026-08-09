package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.BeanPropertyRowMapper;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiToolPolicyRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiToolPolicyRepository implements AiToolPolicyRepository {

    private static final String POLICY_COLUMNS = """
            id, policy_name as policyName, tool_code as toolCode,
            action_type as actionType, risk_level as riskLevel, match_type as matchType,
            match_value as matchValue, verdict, message, enabled,
            create_time as createTime, update_time as updateTime
            """;

    private final MyBatisQueryOperations database;

    public JdbcAiToolPolicyRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<AiVO.ToolPolicyVO> findPage(long limit, long offset) {
        return database.query("""
                select %s
                from ai_tool_policy
                where is_deleted = 0
                order by id desc
                limit ? offset ?
                """.formatted(POLICY_COLUMNS), new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class), limit, offset);
    }

    @Override
    public long countActive() {
        Long count = database.queryForObject("select count(1) from ai_tool_policy where is_deleted = 0", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Long create(PolicyMutation mutation, LocalDateTime now) {
        int inserted = database.update("""
                insert into ai_tool_policy (
                    policy_name, tool_code, action_type, risk_level, match_type,
                    match_value, verdict, message, enabled, is_deleted, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                mutation.policyName(), mutation.toolCode(), mutation.actionType(), mutation.riskLevel(),
                mutation.matchType(), mutation.matchValue(), mutation.verdict(), mutation.message(),
                mutation.enabled() ? 1 : 0, now, now);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public Optional<AiVO.ToolPolicyVO> findActiveById(Long id) {
        return database.query("""
                select %s
                from ai_tool_policy
                where id = ? and is_deleted = 0
                limit 1
                """.formatted(POLICY_COLUMNS), new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class), id)
                .stream().findFirst();
    }

    @Override
    public int update(Long id, AiVO.ToolPolicyVO expected, PolicyMutation mutation, LocalDateTime now) {
        return database.update("""
                update ai_tool_policy
                set policy_name = ?, tool_code = ?, action_type = ?, risk_level = ?, match_type = ?,
                    match_value = ?, verdict = ?, message = ?, enabled = ?, update_time = ?
                where id = ? and policy_name = ? and tool_code = ? and enabled = ? and is_deleted = 0
                """,
                mutation.policyName(), mutation.toolCode(), mutation.actionType(), mutation.riskLevel(),
                mutation.matchType(), mutation.matchValue(), mutation.verdict(), mutation.message(),
                mutation.enabled() ? 1 : 0, now, id, expected.getPolicyName(), expected.getToolCode(),
                Boolean.TRUE.equals(expected.getEnabled()) ? 1 : 0);
    }

    @Override
    public int updateEnabled(Long id, AiVO.ToolPolicyVO expected, boolean enabled, LocalDateTime now) {
        return database.update("""
                update ai_tool_policy set enabled = ?, update_time = ?
                where id = ? and policy_name = ? and tool_code = ? and enabled = ? and is_deleted = 0
                """, enabled ? 1 : 0, now, id, expected.getPolicyName(), expected.getToolCode(),
                Boolean.TRUE.equals(expected.getEnabled()) ? 1 : 0);
    }

    @Override
    public int softDelete(Long id, AiVO.ToolPolicyVO expected, LocalDateTime now) {
        return database.update("""
                update ai_tool_policy set is_deleted = 1, update_time = ?
                where id = ? and policy_name = ? and tool_code = ? and enabled = ? and is_deleted = 0
                """, now, id, expected.getPolicyName(), expected.getToolCode(),
                Boolean.TRUE.equals(expected.getEnabled()) ? 1 : 0);
    }

    @Override
    public List<AiVO.ToolPolicyVO> findEnabled() {
        return database.query("""
                select %s
                from ai_tool_policy
                where enabled = 1 and is_deleted = 0
                order by id asc
                """.formatted(POLICY_COLUMNS), new BeanPropertyRowMapper<>(AiVO.ToolPolicyVO.class));
    }
}
