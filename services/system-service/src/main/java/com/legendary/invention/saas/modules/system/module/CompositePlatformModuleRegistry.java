package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CompositePlatformModuleRegistry implements PlatformModuleRegistry {

    private final StaticPlatformModuleRegistry staticRegistry = new StaticPlatformModuleRegistry();
    private final DatabasePlatformModuleRepository databaseRepository;

    public CompositePlatformModuleRegistry(DatabasePlatformModuleRepository databaseRepository) {
        this.databaseRepository = databaseRepository;
    }

    @Override
    public List<PlatformModuleVO> listModules() {
        Map<String, PlatformModuleVO> modules = new LinkedHashMap<>();
        staticRegistry.listModules().forEach(module -> {
            module.setRegistrationSourceOrder(List.of(module.getSourceType()));
            modules.put(module.getModuleCode(), module);
        });
        databaseRepository.listModules().forEach(module -> {
            PlatformModuleVO existing = modules.get(module.getModuleCode());
            if (existing != null) {
                module.setOverriddenByDatabase("DATABASE".equals(module.getSourceType()));
                module.setRegistrationSourceOrder(mergeSourceOrder(existing.getRegistrationSourceOrder(), module.getSourceType()));
            } else {
                module.setRegistrationSourceOrder(List.of(module.getSourceType()));
            }
            modules.put(module.getModuleCode(), module);
        });
        return PlatformModuleCatalog.evaluateReadiness(List.copyOf(modules.values()));
    }

    @Override
    public Optional<PlatformModuleVO> findModule(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return Optional.empty();
        }
        return listModules().stream()
                .filter(module -> moduleCode.equals(module.getModuleCode()))
                .findFirst();
    }

    private static List<String> mergeSourceOrder(List<String> existingOrder, String nextSource) {
        java.util.ArrayList<String> sources = new java.util.ArrayList<>();
        if (existingOrder != null) {
            existingOrder.stream()
                    .filter(source -> source != null && !source.isBlank())
                    .forEach(sources::add);
        }
        if (nextSource != null && !nextSource.isBlank()) {
            sources.add(nextSource);
        }
        return List.copyOf(sources);
    }
}
