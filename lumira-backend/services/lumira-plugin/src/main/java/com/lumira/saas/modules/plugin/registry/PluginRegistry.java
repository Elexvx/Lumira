package com.lumira.saas.modules.plugin.registry;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PluginRegistry {

    private final Map<String, Map<String, PluginRuntimeDescriptor>> runtimeMap = new ConcurrentHashMap<>();
    private final Map<String, String> activeVersionMap = new ConcurrentHashMap<>();

    public void register(PluginRuntimeDescriptor descriptor) {
        runtimeMap.computeIfAbsent(descriptor.getPluginCode(), key -> new ConcurrentHashMap<>())
                .put(descriptor.getVersion(), descriptor);
    }

    public Optional<PluginRuntimeDescriptor> find(String pluginCode, String version) {
        return Optional.ofNullable(runtimeMap.getOrDefault(pluginCode, Map.of()).get(version));
    }

    public PluginRuntimeDescriptor requireActive(String pluginCode) {
        String activeVersion = activeVersionMap.get(pluginCode);
        if (activeVersion == null) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件未加载激活版本");
        }
        return find(pluginCode, activeVersion)
                .orElseThrow(() -> new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "插件运行时不存在"));
    }

    public void activate(String pluginCode, String version) {
        if (!find(pluginCode, version).isPresent()) {
            throw new BizException(ErrorCode.PLUGIN_RUNTIME_ERROR, "待激活插件版本未加载");
        }
        activeVersionMap.put(pluginCode, version);
    }

    public Optional<String> findActiveVersion(String pluginCode) {
        return Optional.ofNullable(activeVersionMap.get(pluginCode));
    }

    public void unload(String pluginCode, String version) throws Exception {
        Map<String, PluginRuntimeDescriptor> versions = runtimeMap.get(pluginCode);
        if (versions == null) {
            return;
        }
        PluginRuntimeDescriptor descriptor = versions.remove(version);
        if (descriptor != null) {
            descriptor.close();
        }
        if (versions.isEmpty()) {
            runtimeMap.remove(pluginCode);
        }
        if (version.equals(activeVersionMap.get(pluginCode))) {
            activeVersionMap.remove(pluginCode);
            versions.values().stream()
                    .max(Comparator.comparing(PluginRuntimeDescriptor::getVersion))
                    .ifPresent(item -> activeVersionMap.put(pluginCode, item.getVersion()));
        }
    }

    public List<PluginRuntimeDescriptor> listLoaded(String pluginCode) {
        return runtimeMap.getOrDefault(pluginCode, Map.of()).values().stream().toList();
    }
}
