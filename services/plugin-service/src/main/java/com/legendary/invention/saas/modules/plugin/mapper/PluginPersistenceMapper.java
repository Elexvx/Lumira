package com.legendary.invention.saas.modules.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginDependencyEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginDefinitionEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginMenuRelEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginPermissionRelEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginRuntimeLogEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginTenantEntity;
import com.legendary.invention.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.legendary.invention.saas.modules.plugin.vo.PluginVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginPersistenceMapper extends BaseMapper<PluginVersionEntity> {

    void upsertDefinition(@Param("entity") PluginDefinitionEntity entity);

    void upsertVersion(@Param("entity") PluginVersionEntity entity);

    PluginVersionEntity findVersion(@Param("pluginCode") String pluginCode, @Param("version") String version);

    List<PluginVO.PluginDefinitionVO> listDefinitions();

    List<PluginVO.PluginVersionVO> listVersions(@Param("pluginCode") String pluginCode);

    List<PluginVO.PluginVersionVO> listAllVersions();

    List<PluginVO.PluginRuntimeLogVO> listRuntimeLogs(@Param("pluginCode") String pluginCode);

    void markInstalled(
            @Param("pluginCode") String pluginCode,
            @Param("version") String version,
            @Param("artifactPath") String artifactPath,
            @Param("frontendManifestPath") String frontendManifestPath,
            @Param("backendJarPath") String backendJarPath,
            @Param("installStatus") String installStatus,
            @Param("loadStatus") String loadStatus,
            @Param("healthStatus") String healthStatus,
            @Param("rollbackable") Integer rollbackable
    );

    void updateVersionStatus(
            @Param("pluginCode") String pluginCode,
            @Param("version") String version,
            @Param("installStatus") String installStatus,
            @Param("loadStatus") String loadStatus,
            @Param("healthStatus") String healthStatus
    );

    void deactivateOtherVersions(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void activateVersion(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void updateEnabledTenantsVersion(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void deleteDependencies(@Param("pluginCode") String pluginCode);

    void insertDependency(@Param("entity") PluginDependencyEntity entity);

    void deletePermissionRelations(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void insertPermissionRelation(@Param("entity") PluginPermissionRelEntity entity);

    void deleteMenuRelations(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void insertMenuRelation(@Param("entity") PluginMenuRelEntity entity);

    List<PluginMenuRelEntity> listMenuRelations(@Param("pluginCode") String pluginCode, @Param("version") String version);

    List<PluginPermissionRelEntity> listPermissionRelations(@Param("pluginCode") String pluginCode, @Param("version") String version);

    void enablePluginForTenant(@Param("entity") PluginTenantEntity entity);

    void disablePluginForTenant(@Param("tenantId") Long tenantId, @Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void markTenantsDeletedByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void uninstallVersionsByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void markMenuRelationsDeletedByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void markPermissionRelationsDeletedByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void markDependenciesDeletedByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void markDefinitionDeletedByPlugin(@Param("pluginCode") String pluginCode, @Param("operatorId") Long operatorId);

    void deleteRuntimeLogsByPlugin(@Param("pluginCode") String pluginCode);

    void deleteTenantsByPlugin(@Param("pluginCode") String pluginCode);

    void deleteVersionsByPlugin(@Param("pluginCode") String pluginCode);

    void deleteMenuRelationsByPlugin(@Param("pluginCode") String pluginCode);

    void deletePermissionRelationsByPlugin(@Param("pluginCode") String pluginCode);

    void deleteDependenciesByPlugin(@Param("pluginCode") String pluginCode);

    void deleteDefinitionByPlugin(@Param("pluginCode") String pluginCode);

    PluginTenantEntity findTenantPlugin(@Param("tenantId") Long tenantId, @Param("pluginCode") String pluginCode);

    List<PluginVO.TenantPluginVO> listTenantPlugins(@Param("tenantId") Long tenantId);

    List<Long> listTenantIdsForPlugin(@Param("pluginCode") String pluginCode);

    List<PluginVersionEntity> listInstalledVersions(@Param("pluginCode") String pluginCode);

    void insertRuntimeLog(@Param("entity") PluginRuntimeLogEntity entity);

    void upsertSystemPermission(@Param("tenantId") Long tenantId, @Param("pluginCode") String pluginCode, @Param("permission") PluginPermissionRelEntity permission);

    List<Long> listAdminRoleIds(@Param("tenantId") Long tenantId);

    void upsertRolePermission(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("permissionKey") String permissionKey);
}
