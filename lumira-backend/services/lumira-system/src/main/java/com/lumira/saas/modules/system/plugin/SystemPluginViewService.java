package com.lumira.saas.modules.system.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.plugin.dto.PluginDTO;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class SystemPluginViewService {

    private static final Logger log = LoggerFactory.getLogger(SystemPluginViewService.class);

    private static final String PLUGIN_CONTEXT = "plugin";
    private static final String PLUGIN_BOOTSTRAP_SCOPE = "bootstrap";
    private static final String AVAILABLE_PLUGINS_CACHE_KEY = "available-plugins";
    private static final Duration AVAILABLE_PLUGINS_CACHE_TTL = Duration.ofSeconds(15);

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ReadModelVersionService readModelVersionService;
    private final Map<String, CachedManifest> manifestCache = new ConcurrentHashMap<>();
    private final Cache<String, CachedAvailablePlugins> availablePluginsCache = CacheBuilder.newBuilder()
            .maximumSize(4)
            .expireAfterWrite(AVAILABLE_PLUGINS_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS)
            .build();

    public SystemPluginViewService(MyBatisQueryOperations jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public SystemPluginViewService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ReadModelVersionService readModelVersionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.readModelVersionService = readModelVersionService;
    }

    public List<PluginVO.PluginAvailabilityVO> availablePlugins() {
        long now = System.currentTimeMillis();
        CachedAvailablePlugins cached = availablePluginsCache.getIfPresent(AVAILABLE_PLUGINS_CACHE_KEY);
        Long bootstrapVersion = loadPluginBootstrapVersion(cached);
        if (isAvailablePluginsCacheCurrent(cached, bootstrapVersion, now)) {
            return copyAvailablePlugins(cached.plugins());
        }

        List<PluginVO.PluginAvailabilityVO> plugins = jdbcTemplate.query(
                """
                        select d.plugin_code,
                               d.plugin_name,
                               v.version as plugin_version,
                               v.frontend_manifest_path
                        from sys_plugin_definition d
                        join sys_plugin_version v
                          on v.plugin_code = d.plugin_code
                         and v.is_active = 1
                         and v.deleted = 0
                        where d.status = 'ENABLED'
                          and d.deleted = 0
                        order by d.sort_no asc, d.plugin_code asc
                        """,
                (rs, rowNum) -> {
                    PluginVO.PluginAvailabilityVO vo = new PluginVO.PluginAvailabilityVO();
                    vo.setPluginCode(rs.getString("plugin_code"));
                    vo.setPluginName(rs.getString("plugin_name"));
                    vo.setVersion(rs.getString("plugin_version"));
                    vo.setManifestPath(rs.getString("frontend_manifest_path"));
                    return vo;
                }
        );
        Map<PluginMenuCacheKey, List<Map<String, Object>>> menusByPlugin = loadActivePluginMenus();
        List<PluginVO.PluginAvailabilityVO> resolved = plugins.parallelStream()
                .map(plugin -> enrichPlugin(
                        plugin,
                        menusByPlugin.getOrDefault(
                                new PluginMenuCacheKey(plugin.getPluginCode(), plugin.getVersion()),
                                List.of()
                        )
                ))
                .toList();
        availablePluginsCache.put(
                AVAILABLE_PLUGINS_CACHE_KEY,
                new CachedAvailablePlugins(
                        bootstrapVersion,
                        System.currentTimeMillis() + AVAILABLE_PLUGINS_CACHE_TTL.toMillis(),
                        copyAvailablePlugins(resolved)
                )
        );
        return resolved;
    }

    public List<Map<String, Object>> pluginMenus(List<String> permissions) {
        List<Map<String, Object>> menus = new ArrayList<>();
        Set<String> permissionSet = permissionSet(permissions);
        for (PluginVO.PluginAvailabilityVO plugin : availablePlugins()) {
            for (Map<String, Object> menu : plugin.getMenus()) {
                String permissionKey = (String) menu.get("permissionKey");
                if (!StringUtils.hasText(permissionKey) || permissionSet.contains(permissionKey)) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    private void loadFrontendManifest(PluginVO.PluginAvailabilityVO plugin) {
        if (!StringUtils.hasText(plugin.getManifestPath())) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of());
            return;
        }
        try {
            CachedManifest manifest = loadCachedManifest(Path.of(plugin.getManifestPath()));
            plugin.setSharedDeps(manifest.sharedDeps());
            plugin.setRoutes(manifest.routes());
        } catch (Exception exception) {
            plugin.setSharedDeps(List.of());
            plugin.setRoutes(List.of());
        }
    }

    private PluginVO.PluginAvailabilityVO enrichPlugin(
            PluginVO.PluginAvailabilityVO plugin,
            List<Map<String, Object>> menus
    ) {
        plugin.setMenus(copyMenus(menus));
        loadFrontendManifest(plugin);
        return plugin;
    }

    private boolean isAvailablePluginsCacheCurrent(
            CachedAvailablePlugins cached,
            Long bootstrapVersion,
            long now
    ) {
        if (cached == null || cached.plugins() == null) {
            return false;
        }
        if (bootstrapVersion != null) {
            return Objects.equals(cached.version(), bootstrapVersion);
        }
        return cached.expiresAtEpochMillis() > now;
    }

    private Long loadPluginBootstrapVersion(CachedAvailablePlugins cached) {
        if (readModelVersionService == null) {
            return null;
        }
        try {
            Long version = readModelVersionService.currentVersion(PLUGIN_CONTEXT, PLUGIN_BOOTSTRAP_SCOPE);
            if (version != null) {
                return version;
            }
        } catch (Throwable throwable) {
            log.debug("Failed to read plugin bootstrap version", throwable);
        }
        return cached == null ? null : cached.version();
    }

    private Set<String> permissionSet(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        if (permissions instanceof Set<?> set) {
            @SuppressWarnings("unchecked")
            Set<String> typed = (Set<String>) set;
            return typed;
        }
        return new HashSet<>(permissions);
    }

    private CachedManifest loadCachedManifest(Path manifestPath) throws java.io.IOException {
        String cacheKey = manifestPath.toAbsolutePath().normalize().toString();
        long modifiedAt = Files.getLastModifiedTime(manifestPath).toMillis();
        CachedManifest cached = manifestCache.get(cacheKey);
        if (cached != null && cached.modifiedAt() == modifiedAt) {
            return cached;
        }
        PluginDTO.FrontendPluginManifest manifest = objectMapper.readValue(manifestPath.toFile(), PluginDTO.FrontendPluginManifest.class);
        CachedManifest next = new CachedManifest(
                modifiedAt,
                immutableList(manifest.getSharedDeps()),
                immutableList(manifest.getRoutes())
        );
        manifestCache.put(cacheKey, next);
        return next;
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private List<PluginVO.PluginAvailabilityVO> copyAvailablePlugins(List<PluginVO.PluginAvailabilityVO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PluginVO.PluginAvailabilityVO> copies = new ArrayList<>(source.size());
        for (PluginVO.PluginAvailabilityVO plugin : source) {
            copies.add(copyPlugin(plugin));
        }
        return copies;
    }

    private PluginVO.PluginAvailabilityVO copyPlugin(PluginVO.PluginAvailabilityVO source) {
        PluginVO.PluginAvailabilityVO copy = new PluginVO.PluginAvailabilityVO();
        copy.setPluginCode(source.getPluginCode());
        copy.setPluginName(source.getPluginName());
        copy.setVersion(source.getVersion());
        copy.setManifestPath(source.getManifestPath());
        copy.setSharedDeps(source.getSharedDeps() == null ? List.of() : new ArrayList<>(source.getSharedDeps()));
        copy.setRoutes(source.getRoutes() == null ? List.of() : new ArrayList<>(source.getRoutes()));
        copy.setMenus(copyMenus(source.getMenus()));
        copy.setLifecycleStatus(source.getLifecycleStatus());
        copy.setSchemaStatus(source.getSchemaStatus());
        copy.setSupportsHotDisable(source.getSupportsHotDisable());
        copy.setSupportsDataPurge(source.getSupportsDataPurge());
        copy.setRuntimeContributions(
                source.getRuntimeContributions() == null ? List.of() : new ArrayList<>(source.getRuntimeContributions())
        );
        return copy;
    }

    private List<Map<String, Object>> copyMenus(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copies = new ArrayList<>(source.size());
        for (Map<String, Object> menu : source) {
            copies.add(new LinkedHashMap<>(menu));
        }
        return copies;
    }

    private Map<PluginMenuCacheKey, List<Map<String, Object>>> loadActivePluginMenus() {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                        select m.plugin_code,
                               m.plugin_version,
                               m.menu_code,
                               m.menu_name,
                               m.route_path,
                               m.icon,
                               m.permission_key,
                               m.parent_menu_code,
                               m.sort_no
                        from sys_plugin_menu_rel m
                        join sys_plugin_version v
                          on v.plugin_code = m.plugin_code
                         and v.version = m.plugin_version
                         and v.is_active = 1
                         and v.deleted = 0
                        join sys_plugin_definition d
                          on d.plugin_code = m.plugin_code
                         and d.status = 'ENABLED'
                         and d.deleted = 0
                        where m.deleted = 0
                        order by d.sort_no asc, d.plugin_code asc, m.sort_no asc, m.id asc
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pluginCode", rs.getString("plugin_code"));
                    item.put("pluginVersion", rs.getString("plugin_version"));
                    item.put("menuCode", rs.getString("menu_code"));
                    item.put("parentMenuCode", rs.getString("parent_menu_code"));
                    item.put("name", rs.getString("menu_name"));
                    item.put("path", rs.getString("route_path"));
                    item.put("icon", rs.getString("icon"));
                    item.put("permissionKey", rs.getString("permission_key"));
                    item.put("sortNo", rs.getInt("sort_no"));
                    return item;
                }
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<PluginMenuCacheKey, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String pluginCode = row == null ? null : (String) row.get("pluginCode");
            String pluginVersion = row == null ? null : (String) row.get("pluginVersion");
            if (!StringUtils.hasText(pluginCode) || !StringUtils.hasText(pluginVersion)) {
                continue;
            }
            Map<String, Object> menu = new LinkedHashMap<>(row);
            menu.remove("pluginVersion");
            PluginMenuCacheKey cacheKey = new PluginMenuCacheKey(pluginCode, pluginVersion);
            grouped.computeIfAbsent(cacheKey, ignored -> new ArrayList<>()).add(menu);
        }
        return grouped;
    }

    private record CachedManifest(long modifiedAt, List<String> sharedDeps, List<String> routes) {
    }

    private record CachedAvailablePlugins(
            Long version,
            long expiresAtEpochMillis,
            List<PluginVO.PluginAvailabilityVO> plugins
    ) {
    }

    private record PluginMenuCacheKey(String pluginCode, String pluginVersion) {
    }
}
