package com.lumira.saas.modules.system.online;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class OnlineSessionEventIdentityVerifier {

    private final MyBatisQueryOperations jdbcTemplate;

    public OnlineSessionEventIdentityVerifier(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasTrustedIdentity(OnlineSessionEvent event) {
        if (event == null || OnlineSessionEvent.ACTION_HEARTBEAT.equals(event.getAction())) {
            return true;
        }
        if (event.getUserId() == null || event.getUserId() <= 0 || !StringUtils.hasText(event.getUserUuid())) {
            return false;
        }
        List<UserIdentityRow> rows = jdbcTemplate.query(
                """
                        select uuid, status
                        from sys_user
                        where id = ? and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new UserIdentityRow(
                        rs.getString("uuid"),
                        rs.getString("status")
                ),
                event.getUserId()
        );
        if (rows.isEmpty()) {
            return false;
        }
        UserIdentityRow row = rows.get(0);
        if (!StringUtils.hasText(row.userUuid()) || !row.userUuid().trim().equals(event.getUserUuid().trim())) {
            return false;
        }
        return StringUtils.hasText(row.status()) && "ENABLED".equalsIgnoreCase(row.status().trim());
    }

    private record UserIdentityRow(String userUuid, String status) {
    }
}
