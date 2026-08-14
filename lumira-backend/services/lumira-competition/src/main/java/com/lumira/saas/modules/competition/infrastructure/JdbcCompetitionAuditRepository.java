package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.CompetitionAuditRepository;
import com.lumira.saas.modules.competition.vo.CompetitionAuditVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JDBC adapter for the competition_config_audit read model. */
@Repository
public class JdbcCompetitionAuditRepository implements CompetitionAuditRepository {
    private final CompetitionSqlOperations database;

    public JdbcCompetitionAuditRepository(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public AuditPage findRecords(String competitionUuid, String module, String action, long offset, long limit) {
        StringBuilder where = new StringBuilder(" from competition_config_audit where competition_uuid = ? and deleted = 0");
        List<Object> parameters = new ArrayList<>();
        parameters.add(competitionUuid);
        if (StringUtils.hasText(module)) {
            where.append(" and module = ?");
            parameters.add(module.trim());
        }
        if (StringUtils.hasText(action)) {
            where.append(" and action = ?");
            parameters.add(action.trim());
        }
        Long total = database.queryForObject("select count(1)" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(offset);
        pageParameters.add(limit);
        List<CompetitionAuditVO.Record> records = database.query(
                "select id, competition_uuid as competitionUuid, operator_user_id as operatorUserId, "
                        + "operator_user_uuid as operatorUserUuid, action, module, detail_message as detailMessage, created_at as createdAt"
                        + where + " order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionAuditVO.Record.class),
                pageParameters.toArray()
        );
        return new AuditPage(records, total == null ? 0L : total);
    }
}
