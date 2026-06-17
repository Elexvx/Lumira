package com.lumira.saas.modules.ai.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

interface AiIamQueryFacade {

    UserSearchResult searchUsers(Long tenantId, String keyword, String status, int limit);

    record UserSearchResult(List<Map<String, Object>> items, long total) {
    }
}

@Service
class DefaultAiIamQueryFacade implements AiIamQueryFacade {

    private static final int MAX_SEARCH_LIMIT = 100;

    private final MyBatisQueryOperations jdbcTemplate;

    DefaultAiIamQueryFacade(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserSearchResult searchUsers(Long tenantId, String keyword, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        StringBuilder filterSql = new StringBuilder("""
                from sys_user u
                join sys_user_tenant ut
                  on ut.user_id = u.id
                 and ut.tenant_id = ?
                 and ut.deleted = 0
                where u.deleted = 0
                """);
        appendUserSearchFilters(filterSql, args, keyword, status);

        List<Object> queryArgs = new java.util.ArrayList<>(args);
        StringBuilder sql = new StringBuilder("""
                select u.id, u.username, u.nickname, u.real_name as realName, u.mobile, u.email,
                       u.status, u.created_at as createdAt, u.updated_at as updatedAt
                """);
        sql.append(filterSql);
        sql.append(" order by u.id desc limit ?");
        queryArgs.add(safeLimit);
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql.toString(), queryArgs.toArray()).stream()
                .map(this::maskedUser)
                .toList();
        long total = users.size() < safeLimit
                ? users.size()
                : nullToZero(jdbcTemplate.queryForObject("select count(1) " + filterSql, Long.class, args.toArray()));
        return new UserSearchResult(users, total);
    }

    private void appendUserSearchFilters(StringBuilder sql, List<Object> args, String keyword, String status) {
        if (StringUtils.hasText(keyword)) {
            sql.append("""
                     and (
                       u.username like ? or u.nickname like ? or u.real_name like ?
                       or u.mobile like ? or u.email like ?
                     )
                    """);
            String pattern = like(keyword);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and u.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
    }

    private Map<String, Object> maskedUser(Map<String, Object> source) {
        Map<String, Object> user = new LinkedHashMap<>(source);
        user.put("mobile", maskMobile(user.get("mobile")));
        user.put("email", maskEmail(user.get("email")));
        return user;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String maskMobile(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() < 7) {
            return "***";
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private String maskEmail(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int at = text.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return text.charAt(0) + "***" + text.substring(at);
    }
}
