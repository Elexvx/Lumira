package com.lumira.saas.infrastructure.readmodel;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ReadModelVersionService {

    private final MyBatisQueryOperations jdbcTemplate;

    public ReadModelVersionService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long getOrInitialize(Long tenantId, String contextName, String scope) {
        Long version = currentVersion(tenantId, contextName, scope);
        if (version != null) {
            return version;
        }
        return bump(tenantId, contextName, scope, "initialize");
    }

    public Long currentVersion(Long tenantId, String contextName, String scope) {
        return jdbcTemplate.queryForObject(
                """
                        select version
                        from ddd_read_model_version
                        where (tenant_id = ? or (tenant_id is null and ? is null))
                          and context_name = ? and scope = ?
                        limit 1
                        """,
                Long.class,
                tenantId,
                tenantId,
                normalize(contextName),
                normalize(scope)
        );
    }

    public Long latestVersion(String contextName, String scope) {
        return jdbcTemplate.queryForObject(
                """
                        select max(version)
                        from ddd_read_model_version
                        where context_name = ? and scope = ?
                        """,
                Long.class,
                normalize(contextName),
                normalize(scope)
        );
    }

    public LocalDateTime latestRebuiltAt(String contextName, String scope) {
        return jdbcTemplate.queryForObject(
                """
                        select max(rebuilt_at)
                        from ddd_read_model_version
                        where context_name = ? and scope = ?
                        """,
                LocalDateTime.class,
                normalize(contextName),
                normalize(scope)
        );
    }

    public long bump(Long tenantId, String contextName, String scope, String eventKey) {
        jdbcTemplate.update(
                """
                        insert into ddd_read_model_version (
                            tenant_id, context_name, scope, version, last_event_key, rebuilt_at
                        ) values (?, ?, ?, 1, ?, ?)
                        on duplicate key update version = version + 1,
                            last_event_key = values(last_event_key), rebuilt_at = values(rebuilt_at)
                        """,
                tenantId,
                normalize(contextName),
                normalize(scope),
                StringUtils.hasText(eventKey) ? eventKey.trim() : null,
                LocalDateTime.now()
        );
        Long version = jdbcTemplate.queryForObject(
                """
                        select version
                        from ddd_read_model_version
                        where (tenant_id = ? or (tenant_id is null and ? is null))
                          and context_name = ? and scope = ?
                        limit 1
                        """,
                Long.class,
                tenantId,
                tenantId,
                normalize(contextName),
                normalize(scope)
        );
        return version == null ? 1L : version;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return value.trim();
    }
}
