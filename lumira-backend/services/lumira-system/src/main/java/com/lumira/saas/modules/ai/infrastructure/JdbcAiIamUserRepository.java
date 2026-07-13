package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiIamUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcAiIamUserRepository implements AiIamUserRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiIamUserRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<Map<String, Object>> search(String keyword, String status, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select u.id, u.username, u.nickname, u.real_name as realName, u.mobile, u.email,
                       u.status, u.created_at as createdAt, u.updated_at as updatedAt
                from sys_user u
                where u.deleted = 0
                """);
        appendFilters(sql, args, keyword, status);
        sql.append(" order by u.id desc limit ?");
        args.add(limit);
        return database.queryForList(sql.toString(), args.toArray());
    }

    @Override
    public long count(String keyword, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select count(1) from sys_user u where u.deleted = 0");
        appendFilters(sql, args, keyword, status);
        Long count = database.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String keyword, String status) {
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (u.username like ? or u.nickname like ? or u.real_name like ? or u.mobile like ? or u.email like ?)");
            String pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) {
                args.add(pattern);
            }
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and u.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
    }
}
