package com.yourcompany.saas.modules.plugin.service;

import com.yourcompany.saas.modules.plugin.dto.PluginDTO;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginPermissionRelEntity;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
import com.yourcompany.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.yourcompany.saas.modules.plugin.vo.PluginVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PluginPersistenceService {

    private final JdbcTemplate jdbcTemplate;

    public PluginPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PluginVersionEntity saveUploadedPackage(
            PluginDTO.PluginPackageMetadata metadata,
            Path stagedZipPath,
            Path stagedPackageRoot,
            String validationReportJson,
            String packageChecksum,
            String signaturePath,
            Long operatorId
    ) {
        upsertDefinition(metadata, operatorId);
        jdbcTemplate.update(
                """
                        insert into sys_plugin_version (
                            plugin_code,
                            version,
                            package_path,
                            checksum,
                            signature_path,
                            min_platform_version,
                            install_status,
                            load_status,
                            health_status,
                            is_active,
                            rollbackable,
                            metadata_json,
                            validation_report_json,
                            staged_path,
                            created_by,
                            updated_by,
                            deleted
                        ) values (?, ?, ?, ?, ?, ?, 'VERIFIED', 'UNLOADED', 'UNKNOWN', 0, 0, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            package_path = values(package_path),
                            checksum = values(checksum),
                            signature_path = values(signature_path),
                            min_platform_version = values(min_platform_version),
                            install_status = values(install_status),
                            load_status = values(load_status),
                            health_status = values(health_status),
                            metadata_json = values(metadata_json),
                            validation_report_json = values(validation_report_json),
                            staged_path = values(staged_path),
                            updated_by = values(updated_by),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                metadata.getPluginCode(),
                metadata.getVersion(),
                stagedZipPath.toString(),
                packageChecksum,
                signaturePath,
                metadata.getMinPlatformVersion(),
                JsonUtils.toJson(metadata),
                validationReportJson,
                stagedPackageRoot.toString(),
                operatorId,
                operatorId
        );
        return findVersion(metadata.getPluginCode(), metadata.getVersion()).orElseThrow();
    }

    public Optional<PluginVersionEntity> findVersion(String pluginCode, String version) {
        List<PluginVersionEntity> result = jdbcTemplate.query(
                """
                        select *
                        from sys_plugin_version
                        where plugin_code = ?
                          and version = ?
                          and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> mapVersion(rs),
                pluginCode,
                version
        );
        return result.stream().findFirst();
    }

    public List<PluginVO.PluginDefinitionVO> listDefinitions() {
        return jdbcTemplate.query(
                """
                        select plugin_code, plugin_name, plugin_type, description, author, plugin_api_version, status, builtin_flag, sort_no
                        from sys_plugin_definition
                        where deleted = 0
                        order by sort_no asc, plugin_code asc
                        """,
                (rs, rowNum) -> {
                    PluginVO.PluginDefinitionVO vo = new PluginVO.PluginDefinitionVO();
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setPluginName(rs.getString("plugin_name"));
                    vo.setPluginType(rs.getString("plugin_type"));
                    vo.setDescription(rs.getString("description"));
                    vo.setAuthor(rs.getString("author"));
                    vo.setPluginApiVersion(rs.getString("plugin_api_version"));
                    vo.setStatus(rs.getString("status"));
                    vo.setBuiltinFlag(rs.getInt("builtin_flag"));
                    vo.setSortNo(rs.getInt("sort_no"));
                    return vo;
                }
        );
    }

    public List<PluginVO.PluginVersionVO> listVersions(String pluginCode) {
        return jdbcTemplate.query(
                """
                        select plugin_code, version, install_status, load_status, health_status, is_active, rollbackable,
                               min_platform_version, frontend_manifest_path, validation_report_json, installed_at, created_at
                        from sys_plugin_version
                        where plugin_code = ?
                          and deleted = 0
                        order by created_at desc
                        """,
                (rs, rowNum) -> {
                    PluginVO.PluginVersionVO vo = new PluginVO.PluginVersionVO();
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setVersion(rs.getString("version"));
                    vo.setInstallStatus(rs.getString("install_status"));
                    vo.setLoadStatus(rs.getString("load_status"));
                    vo.setHealthStatus(rs.getString("health_status"));
                    vo.setIsActive(rs.getInt("is_active"));
                    vo.setRollbackable(rs.getInt("rollbackable"));
                    vo.setMinPlatformVersion(rs.getString("min_platform_version"));
                    vo.setFrontendManifestPath(rs.getString("frontend_manifest_path"));
                    vo.setValidationReportJson(rs.getString("validation_report_json"));
                    vo.setInstalledAt(toLocalDateTime(rs.getTimestamp("installed_at")));
                    vo.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
                    return vo;
                },
                pluginCode
        );
    }

    public List<PluginVO.PluginRuntimeLogVO> listRuntimeLogs(String pluginCode) {
        return jdbcTemplate.query(
                """
                        select id, tenant_id, plugin_code, plugin_version, operation_type, lifecycle_status, result_status,
                               detail_message, request_id, trace_id, failure_stack, created_at
                        from sys_plugin_runtime_log
                        where plugin_code = ?
                          and deleted = 0
                        order by id desc
                        limit 200
                        """,
                (rs, rowNum) -> {
                    PluginVO.PluginRuntimeLogVO vo = new PluginVO.PluginRuntimeLogVO();
                    vo.setId(rs.getLong("id"));
                    vo.setTenantId(rs.getObject("tenant_id", Long.class));
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setPluginVersion(rs.getString("plugin_version"));
                    vo.setOperationType(rs.getString("operation_type"));
                    vo.setLifecycleStatus(rs.getString("lifecycle_status"));
                    vo.setResultStatus(rs.getString("result_status"));
                    vo.setDetailMessage(rs.getString("detail_message"));
                    vo.setRequestId(rs.getString("request_id"));
                    vo.setTraceId(rs.getString("trace_id"));
                    vo.setFailureStack(rs.getString("failure_stack"));
                    vo.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
                    return vo;
                },
                pluginCode
        );
    }

    @Transactional
    public void markInstalled(
            String pluginCode,
            String version,
            Path artifactPath,
            Path frontendManifestPath,
            Path backendJarPath,
            String installStatus,
            String loadStatus,
            String healthStatus,
            Integer rollbackable
    ) {
        jdbcTemplate.update(
                """
                        update sys_plugin_version
                        set artifact_path = ?,
                            frontend_manifest_path = ?,
                            backend_jar_path = ?,
                            install_status = ?,
                            load_status = ?,
                            health_status = ?,
                            rollbackable = ?,
                            installed_at = current_timestamp,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and version = ?
                          and deleted = 0
                        """,
                artifactPath.toString(),
                frontendManifestPath.toString(),
                backendJarPath.toString(),
                installStatus,
                loadStatus,
                healthStatus,
                rollbackable,
                pluginCode,
                version
        );
    }

    @Transactional
    public void updateVersionStatus(
            String pluginCode,
            String version,
            String installStatus,
            String loadStatus,
            String healthStatus
    ) {
        jdbcTemplate.update(
                """
                        update sys_plugin_version
                        set install_status = ?,
                            load_status = ?,
                            health_status = ?,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and version = ?
                          and deleted = 0
                        """,
                installStatus,
                loadStatus,
                healthStatus,
                pluginCode,
                version
        );
    }

    @Transactional
    public void activateVersion(String pluginCode, String version) {
        jdbcTemplate.update(
                "update sys_plugin_version set is_active = 0, rollbackable = case when version <> ? then 1 else rollbackable end, updated_at = current_timestamp where plugin_code = ? and deleted = 0",
                version,
                pluginCode
        );
        jdbcTemplate.update(
                "update sys_plugin_version set is_active = 1, rollbackable = 1, updated_at = current_timestamp where plugin_code = ? and version = ? and deleted = 0",
                pluginCode,
                version
        );
        jdbcTemplate.update(
                """
                        update sys_plugin_tenant
                        set plugin_version = ?,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and enabled = 1
                          and deleted = 0
                        """,
                version,
                pluginCode
        );
    }

    @Transactional
    public void replaceDependencies(String pluginCode, List<PluginDTO.PluginDependencyDeclaration> dependencies, Long operatorId) {
        jdbcTemplate.update("delete from sys_plugin_dependency where plugin_code = ?", pluginCode);
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginDependencyDeclaration dependency : dependencies) {
            jdbcTemplate.update(
                    """
                            insert into sys_plugin_dependency (
                                plugin_code, depends_on_plugin_code, min_version, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, 0)
                            """,
                    pluginCode,
                    dependency.getPluginCode(),
                    dependency.getMinVersion(),
                    operatorId,
                    operatorId
            );
        }
    }

    @Transactional
    public void replacePermissionRelations(
            String pluginCode,
            String version,
            List<PluginDTO.PluginPermissionDeclaration> permissions,
            Long operatorId
    ) {
        jdbcTemplate.update("delete from sys_plugin_permission_rel where plugin_code = ? and plugin_version = ?", pluginCode, version);
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginPermissionDeclaration permission : permissions) {
            jdbcTemplate.update(
                    """
                            insert into sys_plugin_permission_rel (
                                plugin_code, plugin_version, permission_key, permission_name, permission_group, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    pluginCode,
                    version,
                    permission.getPermissionKey(),
                    permission.getPermissionName(),
                    permission.getPermissionGroup(),
                    operatorId,
                    operatorId
            );
        }
    }

    @Transactional
    public void replaceMenuRelations(
            String pluginCode,
            String version,
            List<PluginDTO.PluginMenuDeclaration> menus,
            Long operatorId
    ) {
        jdbcTemplate.update("delete from sys_plugin_menu_rel where plugin_code = ? and plugin_version = ?", pluginCode, version);
        if (menus == null || menus.isEmpty()) {
            return;
        }
        for (PluginDTO.PluginMenuDeclaration menu : menus) {
            jdbcTemplate.update(
                    """
                            insert into sys_plugin_menu_rel (
                                plugin_code, plugin_version, menu_code, menu_name, route_path, icon, permission_key, parent_menu_code, sort_no, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    pluginCode,
                    version,
                    menu.getMenuCode(),
                    menu.getMenuName(),
                    menu.getRoutePath(),
                    menu.getIcon(),
                    menu.getPermissionKey(),
                    menu.getParentMenuCode(),
                    menu.getSortNo() == null ? 0 : menu.getSortNo(),
                    operatorId,
                    operatorId
            );
        }
    }

    public List<PluginMenuRelEntity> listMenuRelations(String pluginCode, String version) {
        return jdbcTemplate.query(
                """
                        select plugin_code, plugin_version, menu_code, menu_name, route_path, icon, permission_key, parent_menu_code, sort_no
                        from sys_plugin_menu_rel
                        where plugin_code = ?
                          and plugin_version = ?
                          and deleted = 0
                        order by sort_no asc, id asc
                        """,
                (rs, rowNum) -> {
                    PluginMenuRelEntity entity = new PluginMenuRelEntity();
                    entity.setPluginCode(rs.getString("plugin_code"));
                    entity.setPluginVersion(rs.getString("plugin_version"));
                    entity.setMenuCode(rs.getString("menu_code"));
                    entity.setMenuName(rs.getString("menu_name"));
                    entity.setRoutePath(rs.getString("route_path"));
                    entity.setIcon(rs.getString("icon"));
                    entity.setPermissionKey(rs.getString("permission_key"));
                    entity.setParentMenuCode(rs.getString("parent_menu_code"));
                    entity.setSortNo(rs.getInt("sort_no"));
                    return entity;
                },
                pluginCode,
                version
        );
    }

    public List<PluginPermissionRelEntity> listPermissionRelations(String pluginCode, String version) {
        return jdbcTemplate.query(
                """
                        select plugin_code, plugin_version, permission_key, permission_name, permission_group
                        from sys_plugin_permission_rel
                        where plugin_code = ?
                          and plugin_version = ?
                          and deleted = 0
                        order by id asc
                        """,
                (rs, rowNum) -> {
                    PluginPermissionRelEntity entity = new PluginPermissionRelEntity();
                    entity.setPluginCode(rs.getString("plugin_code"));
                    entity.setPluginVersion(rs.getString("plugin_version"));
                    entity.setPermissionKey(rs.getString("permission_key"));
                    entity.setPermissionName(rs.getString("permission_name"));
                    entity.setPermissionGroup(rs.getString("permission_group"));
                    return entity;
                },
                pluginCode,
                version
        );
    }

    @Transactional
    public void enablePluginForTenant(Long tenantId, String pluginCode, String version, String configJson, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_plugin_tenant (
                            tenant_id, plugin_code, plugin_version, enabled, config_json, created_by, updated_by, deleted
                        ) values (?, ?, ?, 1, ?, ?, ?, 0)
                        on duplicate key update
                            plugin_version = values(plugin_version),
                            enabled = 1,
                            config_json = values(config_json),
                            updated_by = values(updated_by),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                tenantId,
                pluginCode,
                version,
                configJson,
                operatorId,
                operatorId
        );
    }

    @Transactional
    public void disablePluginForTenant(Long tenantId, String pluginCode, Long operatorId) {
        jdbcTemplate.update(
                """
                        update sys_plugin_tenant
                        set enabled = 0,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where tenant_id = ?
                          and plugin_code = ?
                          and deleted = 0
                        """,
                operatorId,
                tenantId,
                pluginCode
        );
    }

    @Transactional
    public void uninstallPlugin(String pluginCode, Long operatorId) {
        jdbcTemplate.update(
                """
                        update sys_plugin_tenant
                        set enabled = 0,
                            deleted = 1,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and deleted = 0
                        """,
                operatorId,
                pluginCode
        );
        jdbcTemplate.update(
                """
                        update sys_plugin_version
                        set install_status = 'UNINSTALLED',
                            load_status = 'UNLOADED',
                            health_status = 'UNKNOWN',
                            is_active = 0,
                            deleted = 1,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and deleted = 0
                        """,
                operatorId,
                pluginCode
        );
        jdbcTemplate.update("update sys_plugin_menu_rel set deleted = 1, updated_by = ?, updated_at = current_timestamp where plugin_code = ? and deleted = 0", operatorId, pluginCode);
        jdbcTemplate.update("update sys_plugin_permission_rel set deleted = 1, updated_by = ?, updated_at = current_timestamp where plugin_code = ? and deleted = 0", operatorId, pluginCode);
        jdbcTemplate.update("update sys_plugin_dependency set deleted = 1, updated_by = ?, updated_at = current_timestamp where plugin_code = ? and deleted = 0", operatorId, pluginCode);
        jdbcTemplate.update(
                """
                        update sys_plugin_definition
                        set status = 'DISABLED',
                            deleted = 1,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where plugin_code = ?
                          and deleted = 0
                        """,
                operatorId,
                pluginCode
        );
    }

    public Optional<PluginTenantEntity> findTenantPlugin(Long tenantId, String pluginCode) {
        List<PluginTenantEntity> result = jdbcTemplate.query(
                """
                        select tenant_id, plugin_code, plugin_version, enabled, config_json
                        from sys_plugin_tenant
                        where tenant_id = ?
                          and plugin_code = ?
                          and deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> {
                    PluginTenantEntity entity = new PluginTenantEntity();
                    entity.setTenantId(rs.getLong("tenant_id"));
                    entity.setPluginCode(rs.getString("plugin_code"));
                    entity.setPluginVersion(rs.getString("plugin_version"));
                    entity.setEnabled(rs.getInt("enabled"));
                    entity.setConfigJson(rs.getString("config_json"));
                    return entity;
                },
                tenantId,
                pluginCode
        );
        return result.stream().findFirst();
    }

    public List<PluginVO.TenantPluginVO> listTenantPlugins(Long tenantId) {
        return jdbcTemplate.query(
                """
                        select d.plugin_code,
                               d.plugin_name,
                               t.plugin_version,
                               v.frontend_manifest_path
                        from sys_plugin_tenant t
                        join sys_plugin_definition d
                          on d.plugin_code = t.plugin_code
                         and d.deleted = 0
                        join sys_plugin_version v
                          on v.plugin_code = t.plugin_code
                         and v.version = t.plugin_version
                         and v.deleted = 0
                        where t.tenant_id = ?
                          and t.enabled = 1
                          and t.deleted = 0
                        order by d.sort_no asc, d.plugin_code asc
                        """,
                (rs, rowNum) -> {
                    PluginVO.TenantPluginVO vo = new PluginVO.TenantPluginVO();
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setPluginName(rs.getString("plugin_name"));
                    vo.setVersion(rs.getString("plugin_version"));
                    vo.setManifestPath(rs.getString("frontend_manifest_path"));
                    return vo;
                },
                tenantId
        );
    }

    public List<Long> listTenantIdsForPlugin(String pluginCode) {
        return jdbcTemplate.query(
                """
                        select distinct tenant_id
                        from sys_plugin_tenant
                        where plugin_code = ?
                          and deleted = 0
                        """,
                (rs, rowNum) -> rs.getLong("tenant_id"),
                pluginCode
        );
    }

    public List<PluginVersionEntity> listInstalledVersions(String pluginCode) {
        return jdbcTemplate.query(
                """
                        select *
                        from sys_plugin_version
                        where plugin_code = ?
                          and deleted = 0
                        order by created_at desc
                        """,
                (rs, rowNum) -> mapVersion(rs),
                pluginCode
        );
    }

    @Transactional
    public void insertRuntimeLog(
            Long tenantId,
            String pluginCode,
            String version,
            String operationType,
            String lifecycleStatus,
            String resultStatus,
            String detailMessage,
            String requestId,
            String traceId,
            String failureStack,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                        insert into sys_plugin_runtime_log (
                            tenant_id,
                            plugin_code,
                            plugin_version,
                            operation_type,
                            lifecycle_status,
                            result_status,
                            detail_message,
                            request_id,
                            trace_id,
                            failure_stack,
                            created_by,
                            deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                pluginCode,
                version,
                operationType,
                lifecycleStatus,
                resultStatus,
                detailMessage,
                requestId,
                traceId,
                failureStack,
                operatorId
        );
    }

    public void registerTenantPermissions(Long tenantId, String pluginCode, String version) {
        List<PluginPermissionRelEntity> permissions = listPermissionRelations(pluginCode, version);
        if (permissions.isEmpty()) {
            return;
        }
        for (PluginPermissionRelEntity permission : permissions) {
            jdbcTemplate.update(
                    """
                            insert into sys_permission (
                                tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, 'PLUGIN', ?, 0, 0, 0)
                            on duplicate key update
                                permission_name = values(permission_name),
                                permission_group = values(permission_group),
                                plugin_code = values(plugin_code),
                                updated_at = current_timestamp,
                                deleted = 0
                            """,
                    tenantId,
                    permission.getPermissionKey(),
                    permission.getPermissionName(),
                    permission.getPermissionGroup(),
                    pluginCode
            );
        }
        List<Long> adminRoleIds = jdbcTemplate.query(
                """
                        select id
                        from sys_role
                        where tenant_id = ?
                          and role_code = 'ADMIN'
                          and deleted = 0
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                tenantId
        );
        for (Long roleId : adminRoleIds) {
            for (PluginPermissionRelEntity permission : permissions) {
                jdbcTemplate.update(
                        """
                                insert into sys_role_permission (
                                    tenant_id, role_id, permission_key, created_by, updated_by, deleted
                                ) values (?, ?, ?, 0, 0, 0)
                                on duplicate key update
                                    updated_at = current_timestamp,
                                    deleted = 0
                                """,
                        tenantId,
                        roleId,
                        permission.getPermissionKey()
                );
            }
        }
    }

    private void upsertDefinition(PluginDTO.PluginPackageMetadata metadata, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_plugin_definition (
                            plugin_code,
                            plugin_name,
                            plugin_type,
                            description,
                            author,
                            plugin_api_version,
                            builtin_flag,
                            status,
                            sort_no,
                            created_by,
                            updated_by,
                            deleted
                        ) values (?, ?, ?, ?, ?, ?, 0, 'ENABLED', 0, ?, ?, 0)
                        on duplicate key update
                            plugin_name = values(plugin_name),
                            plugin_type = values(plugin_type),
                            description = values(description),
                            author = values(author),
                            plugin_api_version = values(plugin_api_version),
                            status = values(status),
                            updated_by = values(updated_by),
                            updated_at = current_timestamp,
                            deleted = 0
                        """,
                metadata.getPluginCode(),
                metadata.getPluginName(),
                metadata.getKind(),
                metadata.getDescription(),
                metadata.getAuthor(),
                metadata.getPluginApiVersion(),
                operatorId,
                operatorId
        );
    }

    private PluginVersionEntity mapVersion(ResultSet rs) throws SQLException {
        PluginVersionEntity entity = new PluginVersionEntity();
        entity.setId(rs.getLong("id"));
        entity.setPluginCode(rs.getString("plugin_code"));
        entity.setVersion(rs.getString("version"));
        entity.setPackagePath(rs.getString("package_path"));
        entity.setArtifactPath(rs.getString("artifact_path"));
        entity.setFrontendManifestPath(rs.getString("frontend_manifest_path"));
        entity.setBackendJarPath(rs.getString("backend_jar_path"));
        entity.setChecksum(rs.getString("checksum"));
        entity.setSignaturePath(rs.getString("signature_path"));
        entity.setMinPlatformVersion(rs.getString("min_platform_version"));
        entity.setInstallStatus(rs.getString("install_status"));
        entity.setLoadStatus(rs.getString("load_status"));
        entity.setHealthStatus(rs.getString("health_status"));
        entity.setIsActive(rs.getInt("is_active"));
        entity.setRollbackable(rs.getInt("rollbackable"));
        entity.setMetadataJson(rs.getString("metadata_json"));
        entity.setValidationReportJson(rs.getString("validation_report_json"));
        entity.setStagedPath(rs.getString("staged_path"));
        entity.setInstalledAt(toLocalDateTime(rs.getTimestamp("installed_at")));
        return entity;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static final class JsonUtils {
        private JsonUtils() {
        }

        private static String toJson(Object value) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } catch (Exception exception) {
                return "{}";
            }
        }
    }
}
