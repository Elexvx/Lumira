package com.lumira.saas.infrastructure.readmodel;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReadModelVersionService {

    private final MyBatisQueryOperations jdbcTemplate;

    public ReadModelVersionService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long currentVersion(String contextName, String scope) {
        return jdbcTemplate.queryForObject(
                """
                        select version
                        from ddd_read_model_version
                        where context_name = ? and scope = ?
                        limit 1
                        """,
                Long.class,
                normalize(contextName),
                normalize(scope)
        );
    }

    public Map<ReadModelScopeKey, Long> currentVersions(List<ReadModelScopeKey> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Map.of();
        }
        List<ReadModelScopeKey> normalizedScopes = scopes.stream()
                .filter(Objects::nonNull)
                .map(scope -> new ReadModelScopeKey(normalize(scope.contextName()), normalize(scope.scope())))
                .distinct()
                .toList();
        if (normalizedScopes.isEmpty()) {
            return Map.of();
        }
        String predicate = normalizedScopes.stream()
                .map(ignored -> "(context_name = ? and scope = ?)")
                .collect(Collectors.joining(" or "));
        List<Object> params = new ArrayList<>(normalizedScopes.size() * 2);
        for (ReadModelScopeKey scope : normalizedScopes) {
            params.add(scope.contextName());
            params.add(scope.scope());
        }
        List<ReadModelScopeVersion> rows = jdbcTemplate.query(
                """
                        select context_name, scope, version
                        from ddd_read_model_version
                        where
                        """
                        + predicate,
                (rs, rowNum) -> new ReadModelScopeVersion(
                        rs.getString("context_name"),
                        rs.getString("scope"),
                        rs.getObject("version", Long.class)
                ),
                params.toArray()
        );
        Map<ReadModelScopeKey, Long> versions = new LinkedHashMap<>();
        for (ReadModelScopeKey scope : normalizedScopes) {
            versions.put(scope, 0L);
        }
        for (ReadModelScopeVersion row : rows) {
            versions.put(new ReadModelScopeKey(normalize(row.contextName()), normalize(row.scope())), row.version() == null ? 0L : row.version());
        }
        return Map.copyOf(versions);
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

    public long bump(String contextName, String scope, String eventKey) {
        jdbcTemplate.update(
                """
                        insert into ddd_read_model_version (
                            context_name, scope, version, last_event_key, rebuilt_at
                        ) values (?, ?, 1, ?, ?)
                        on duplicate key update
                            version = case
                                when values(last_event_key) is not null and last_event_key = values(last_event_key)
                                    then version
                                else version + 1
                            end,
                            last_event_key = case
                                when values(last_event_key) is not null and last_event_key = values(last_event_key)
                                    then last_event_key
                                else values(last_event_key)
                            end,
                            rebuilt_at = case
                                when values(last_event_key) is not null and last_event_key = values(last_event_key)
                                    then rebuilt_at
                                else values(rebuilt_at)
                            end
                        """,
                normalize(contextName),
                normalize(scope),
                StringUtils.hasText(eventKey) ? eventKey.trim() : null,
                LocalDateTime.now()
        );
        Long version = jdbcTemplate.queryForObject(
                """
                        select version
                        from ddd_read_model_version
                        where context_name = ? and scope = ?
                        limit 1
                        """,
                Long.class,
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

    public record ReadModelScopeKey(String contextName, String scope) {
    }

    private record ReadModelScopeVersion(String contextName, String scope, Long version) {
    }
}
