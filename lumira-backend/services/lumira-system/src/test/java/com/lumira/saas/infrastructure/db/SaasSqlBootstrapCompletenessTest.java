package com.lumira.saas.infrastructure.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SaasSqlBootstrapCompletenessTest {

    private static final Pattern TENANT_SURFACE =
            Pattern.compile("(?i)(`?tenant_id`?|\\bsys_tenant\\b|\\btenant\\b)");

    @Test
    void consolidatedSaasSqlSeedsCompleteRoleOnlyIamBootstrap() throws IOException {
        String sql = readSaasSql();
        String normalizedSql = normalizeWhitespace(sql);

        assertThat(TENANT_SURFACE.matcher(sql).find())
                .as("sql/saas.sql must be the role-only fresh-init entrypoint without tenant schema or wording")
                .isFalse();
        assertThat(sql)
                .contains("CREATE TABLE `sys_menu`")
                .contains("CREATE TABLE `sys_permission`")
                .contains("CREATE TABLE `sys_role`")
                .contains("CREATE TABLE `sys_role_permission`")
                .contains("CREATE TABLE `sys_user_role`")
                .contains("CREATE TABLE `ddd_read_model_version`");

        Set<String> permissionKeys = permissionKeys(extractValuesBlock(sql, "sys_permission"));
        assertThat(permissionKeys)
                .hasSizeGreaterThanOrEqualTo(130)
                .contains(
                        "ai:tool-policy:view",
                        "ai:tool-policy:manage",
                        "ai:view",
                        "ai:chat:send",
                        "ai:employee:create",
                        "ai:llm:update",
                        "ai:knowledge:view",
                        "ai:knowledge:document:index",
                        "ai:tool:view",
                        "ai:tool:execute",
                        "dashboard:view",
                        "download:center:create",
                        "download:center:delete",
                        "localization:create",
                        "localization:update",
                        "localization:publish",
                        "localization:rollback",
                        "localization:sync",
                        "message:message:view",
                        "message:message:read",
                        "message:message:write",
                        "message:message:retract",
                        "system:view",
                        "system:file:manage:delete",
                        "system:file:publish",
                        "system:menu:view",
                        "system:notification:write",
                        "system:user:view",
                        "workflow:view",
                        "workflow:config",
                        "workflow:approve",
                        "plugin:management:view",
                        "plugin:management:upload",
                        "plugin:management:install",
                        "plugin:management:upgrade",
                        "plugin:management:rollback",
                        "plugin:management:enable",
                        "plugin:management:disable",
                        "plugin:management:logs",
                        "aiadc:competition:view",
                        "expert:view",
                        "payment:view"
                );

        Map<Long, String> menuCodeById = menuCodeById(extractValuesBlock(sql, "sys_menu"));
        assertThat(menuCodeById)
                .hasSizeGreaterThanOrEqualTo(89)
                .containsEntry(-955L, "dashboard.home")
                .containsEntry(-1001L, "settings.menus")
                .containsEntry(-951L, "system.users")
                .containsEntry(-960L, "team.delete");
        assertThat(new LinkedHashSet<>(menuCodeById.values()))
                .as("seeded menu codes must be unique so ON DUPLICATE KEY cannot overwrite unrelated pages")
                .hasSize(menuCodeById.size());

        assertThat(normalizedSql)
                .contains("VALUES (1001, 'admin'")
                .contains("VALUES (1001, '*', 'ALL', 0, 0, 0)")
                .contains("SELECT 1001, p.`permission_key`, 0, 0, 0 FROM `sys_permission` p WHERE p.`deleted` = 0");
        assertThat(normalizedSql)
                .contains("INSERT INTO `sys_user_role` (`user_id`, `user_uuid`, `role_id`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`)")
                .contains("VALUES (1001, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001), 1001, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0)")
                .contains("VALUES (1002, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002), 1002, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0)")
                .contains("`updated_by_uuid` = VALUES(`updated_by_uuid`)");
        assertThat(extractValuesBlock(sql, "sys_role_permission"))
                .as("admin bootstrap should enumerate concrete permission keys instead of wildcard role permissions")
                .doesNotContain("'*'");
        assertThat(rolePermissionSelectKeys(sql, 1002L))
                .as("default common user role should not expose management surfaces")
                .containsExactlyInAnyOrder(
                        "dashboard:view",
                        "profile:view",
                        "system:file:view",
                        "system:file:upload",
                        "aiadc:registration:view",
                        "aiadc:registration:create",
                        "aiadc:registration:update",
                        "aiadc:registration:pay",
                        "aiadc:activity:create",
                        "aiadc:material:view",
                        "aiadc:material:submit",
                        "aiadc:stage:view"
                )
                .doesNotContain(
                        "aiadc:competition:view",
                        "aiadc:activity:view",
                        "aiadc:project:view",
                        "aiadc:project:create",
                        "team:view",
                        "team:create",
                        "team:member:view",
                        "expert:view",
                        "expert:create",
                        "expert:update",
                        "expert:delete",
                        "download:center:view",
                        "user:center:view",
                        "message:message:view",
                        "message:message:read",
                        "ai:view",
                        "ai:chat:send"
                );

        assertThat(normalizedSql)
                .contains("INSERT INTO `ddd_read_model_version`")
                .contains("('IAM', 'permission-snapshot', 1, 'sql-bootstrap', NOW())")
                .contains("('platform', 'runtime-appearance', 1, 'sql-bootstrap', NOW())");
    }

    private static Set<String> permissionKeys(String valuesBlock) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\(\\s*'([^']+)'\\s*,").matcher(valuesBlock);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Set<String> rolePermissionSelectKeys(String sql, long roleId) {
        Pattern pattern = Pattern.compile(
                "SELECT\\s+" + roleId + "\\s*,\\s*p\\.`permission_key`[\\s\\S]*?p\\.`permission_key`\\s+IN\\s*\\(([\\s\\S]*?)\\)\\s*ON\\s+DUPLICATE\\s+KEY\\s+UPDATE",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find())
                .as("expected role permission SELECT bootstrap block for role " + roleId)
                .isTrue();
        Set<String> keys = new LinkedHashSet<>();
        Matcher keyMatcher = Pattern.compile("'([^']+)'").matcher(matcher.group(1));
        while (keyMatcher.find()) {
            keys.add(keyMatcher.group(1));
        }
        return keys;
    }

    private static Map<Long, String> menuCodeById(String valuesBlock) {
        Map<Long, String> menuCodeById = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*'([^']+)'").matcher(valuesBlock);
        while (matcher.find()) {
            Long id = Long.valueOf(matcher.group(1));
            menuCodeById.put(id, matcher.group(3));
        }
        return menuCodeById;
    }

    private static String extractValuesBlock(String sql, String tableName) {
        Pattern pattern = Pattern.compile(
                "INSERT\\s+INTO\\s+`" + Pattern.quote(tableName) + "`[\\s\\S]*?\\)\\s*VALUES\\s*([\\s\\S]*?)ON\\s+DUPLICATE\\s+KEY\\s+UPDATE",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find())
                .as("expected INSERT ... VALUES bootstrap block for " + tableName)
                .isTrue();
        return matcher.group(1);
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String readSaasSql() throws IOException {
        return Files.readString(resolvePath("../../sql/saas.sql", "sql/saas.sql"), StandardCharsets.UTF_8);
    }

    private static Path resolvePath(String... candidates) {
        for (String candidate : candidates) {
            Path direct = Path.of(candidate);
            if (Files.exists(direct)) {
                return direct;
            }
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (String candidate : candidates) {
                Path path = current.resolve(candidate);
                if (Files.exists(path)) {
                    return path;
                }
            }
            current = current.getParent();
        }
        return Path.of(candidates[0]);
    }
}
