package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleValidationVO;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PlatformModuleDefinitionValidator {

    private static final Set<String> MODULE_TYPES = Set.of("FOUNDATION", "CAPABILITY", "SCENE", "ADAPTER", "PLUGIN");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of("ENABLED", "DISABLED", "PLANNED", "DEPRECATED");
    private static final Set<String> SOURCE_TYPES = Set.of("BUILTIN", "DATABASE", "PLUGIN", "MANIFEST");

    public PlatformModuleValidationVO validate(SystemDTO.ModuleValidationRequest request, List<PlatformModuleVO> existingModules) {
        Map<String, PlatformModuleVO> moduleMap = existingModules.stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item, (left, right) -> right, LinkedHashMap::new));
        String moduleCode = normalize(request.getModuleCode());
        List<String> dependencies = normalizeList(request.getDependencies());
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingDependencies = new ArrayList<>();
        List<String> inactiveDependencies = new ArrayList<>();

        boolean duplicateModuleCode = moduleMap.containsKey(moduleCode);
        if (duplicateModuleCode && !Boolean.TRUE.equals(request.getOverwriteExisting())) {
            issues.add("模块编码已存在，请确认是否覆盖已有定义");
        }

        if (!MODULE_TYPES.contains(request.getModuleType())) {
            issues.add("模块类型不合法: " + request.getModuleType());
        }
        if (!LIFECYCLE_STATUSES.contains(request.getLifecycleStatus())) {
            issues.add("生命周期状态不合法: " + request.getLifecycleStatus());
        }
        String sourceType = request.getSourceType() == null || request.getSourceType().isBlank() ? "DATABASE" : request.getSourceType();
        if (!SOURCE_TYPES.contains(sourceType)) {
            issues.add("来源类型不合法: " + sourceType);
        }
        if (dependencies.contains(moduleCode)) {
            issues.add("模块不能依赖自身");
        }

        for (String dependency : dependencies) {
            PlatformModuleVO dependencyModule = moduleMap.get(dependency);
            if (dependencyModule == null) {
                missingDependencies.add(dependency);
            } else if (!"ENABLED".equals(dependencyModule.getLifecycleStatus())) {
                inactiveDependencies.add(dependency);
            }
        }
        if (!missingDependencies.isEmpty()) {
            issues.add("缺少依赖模块: " + String.join(", ", missingDependencies));
        }
        if (!inactiveDependencies.isEmpty()) {
            warnings.add("依赖模块当前未启用: " + String.join(", ", inactiveDependencies));
        }

        List<String> cyclePath = detectCycle(moduleCode, dependencies, moduleMap);
        if (!cyclePath.isEmpty()) {
            issues.add("检测到循环依赖: " + String.join(" -> ", cyclePath));
        }

        PlatformModuleValidationVO result = new PlatformModuleValidationVO();
        result.setDuplicateModuleCode(duplicateModuleCode);
        result.setIssues(List.copyOf(issues));
        result.setWarnings(List.copyOf(warnings));
        result.setMissingDependencies(List.copyOf(missingDependencies));
        result.setInactiveDependencies(List.copyOf(inactiveDependencies));
        result.setCyclePath(List.copyOf(cyclePath));
        result.setValid(issues.isEmpty());
        return result;
    }

    private static List<String> detectCycle(String moduleCode, List<String> dependencies, Map<String, PlatformModuleVO> moduleMap) {
        Map<String, List<String>> graph = moduleMap.values().stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, PlatformModuleVO::getDependencies, (left, right) -> right, LinkedHashMap::new));
        graph.put(moduleCode, dependencies);
        for (String dependency : dependencies) {
            List<String> path = findPath(dependency, moduleCode, graph, new LinkedHashSet<>());
            if (!path.isEmpty()) {
                ArrayList<String> cycle = new ArrayList<>();
                cycle.add(moduleCode);
                cycle.addAll(path);
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> findPath(String current, String target, Map<String, List<String>> graph, LinkedHashSet<String> visited) {
        if (!visited.add(current)) {
            return List.of();
        }
        if (current.equals(target)) {
            return List.of(current);
        }
        for (String next : graph.getOrDefault(current, List.of())) {
            List<String> path = findPath(next, target, graph, visited);
            if (!path.isEmpty()) {
                ArrayDeque<String> resolved = new ArrayDeque<>(path);
                resolved.addFirst(current);
                return List.copyOf(resolved);
            }
        }
        visited.remove(current);
        return List.of();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(PlatformModuleDefinitionValidator::normalize)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
