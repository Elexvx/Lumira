package com.legendary.invention.saas.modules.plugin.mapper;

import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.RowMapper;

import com.legendary.invention.saas.infrastructure.persistence.mybatis.SqlRow;

public final class PluginRowMappers {

    private PluginRowMappers() {
    }

    public static RowMapper<PluginVersionEntity> pluginVersion() {
        return PluginRowMappers::mapPluginVersion;
    }

    private static PluginVersionEntity mapPluginVersion(SqlRow rs, int rowNum) {
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
        return entity;
    }
}
