package com.legendary.invention.saas.modules.system.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.module.entity.PlatformModuleDefinitionEntity;
import com.legendary.invention.saas.modules.system.module.entity.PlatformModuleDependencyEntity;
import com.legendary.invention.saas.modules.system.module.mapper.PlatformModuleDefinitionMapper;
import com.legendary.invention.saas.modules.system.module.mapper.PlatformModuleDependencyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DatabasePlatformModuleRepository {

    private static final Logger log = LoggerFactory.getLogger(DatabasePlatformModuleRepository.class);
    private static final String DEFAULT_MODULE_TYPE = "CAPABILITY";
    private static final String DEFAULT_LIFECYCLE_STATUS = "PLANNED";
    private static final String DEFAULT_SOURCE_TYPE = "DATABASE";
    private static final String DEFAULT_OWNER_SERVICE = "system-service";

    private final PlatformModuleDefinitionMapper definitionMapper;
    private final PlatformModuleDependencyMapper dependencyMapper;

    public DatabasePlatformModuleRepository(PlatformModuleDefinitionMapper definitionMapper, PlatformModuleDependencyMapper dependencyMapper) {
        this.definitionMapper = definitionMapper;
        this.dependencyMapper = dependencyMapper;
    }

    public List<PlatformModuleVO> listModules() {
        try {
            Map<String, PlatformModuleVO> modules = new LinkedHashMap<>();
            definitionMapper.selectList(new LambdaQueryWrapper<PlatformModuleDefinitionEntity>()
                    .eq(PlatformModuleDefinitionEntity::getDeleted, 0)
                    .orderByAsc(PlatformModuleDefinitionEntity::getSortNo, PlatformModuleDefinitionEntity::getId)
            ).forEach(entity -> {
                if (entity.getModuleCode() == null || entity.getModuleCode().isBlank()) {
                    log.warn("Skip persisted platform module with blank module_code, id={}", entity.getId());
                    return;
                }
                PlatformModuleVO module = new PlatformModuleVO();
                module.setModuleCode(entity.getModuleCode().trim());
                module.setModuleName(defaultText(entity.getModuleName(), module.getModuleCode()));
                module.setModuleType(defaultText(entity.getModuleType(), DEFAULT_MODULE_TYPE));
                module.setLifecycleStatus(defaultText(entity.getLifecycleStatus(), DEFAULT_LIFECYCLE_STATUS));
                module.setSourceType(defaultText(entity.getSourceType(), DEFAULT_SOURCE_TYPE));
                module.setDescription(entity.getDescription());
                module.setOwnerService(defaultText(entity.getOwnerService(), DEFAULT_OWNER_SERVICE));
                module.setAdminRoutePath(entity.getAdminRoutePath());
                module.setApiPrefixes(splitLines(entity.getApiPrefixes()));
                module.setPermissionKeys(splitLines(entity.getPermissionKeys()));
                module.setDependencies(List.of());
                module.setRegistrationSourceOrder(List.of(module.getSourceType()));
                module.setRegisteredAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
                module.setBuiltin(Boolean.TRUE.equals(entity.getBuiltin()));
                modules.put(module.getModuleCode(), module);
            });

            dependencyMapper.selectList(new LambdaQueryWrapper<PlatformModuleDependencyEntity>()
                    .eq(PlatformModuleDependencyEntity::getDeleted, 0)
                    .orderByAsc(PlatformModuleDependencyEntity::getSortNo, PlatformModuleDependencyEntity::getId)
            ).forEach(entity -> {
                PlatformModuleVO module = modules.get(entity.getModuleCode());
                if (module != null) {
                    module.setDependencies(append(module.getDependencies(), entity.getDependencyModuleCode()));
                }
            });
            return List.copyOf(modules.values());
        } catch (DataAccessException exception) {
            log.warn("Failed to load persisted platform modules, fallback to static catalog: {}", exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("Failed to normalize persisted platform modules, fallback to static catalog: {}", exception.getMessage());
            return List.of();
        }
    }

    public void createModule(SystemDTO.ModuleValidationRequest request, Long operatorId) {
        PlatformModuleDefinitionEntity module = new PlatformModuleDefinitionEntity();
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleType(request.getModuleType());
        module.setLifecycleStatus(request.getLifecycleStatus());
        module.setSourceType(request.getSourceType());
        module.setDescription(request.getDescription());
        module.setOwnerService(request.getOwnerService() == null || request.getOwnerService().isBlank() ? "system-service" : request.getOwnerService());
        module.setAdminRoutePath(request.getAdminRoutePath());
        module.setApiPrefixes(joinLines(request.getApiPrefixes()));
        module.setPermissionKeys(joinLines(request.getPermissionKeys()));
        module.setBuiltin(false);
        module.setSortNo(1000);
        module.setCreatedBy(operatorId);
        module.setUpdatedBy(operatorId);
        module.setDeleted(0);
        definitionMapper.insert(module);

        List<String> dependencies = request.getDependencies() == null ? List.of() : request.getDependencies();
        for (int index = 0; index < dependencies.size(); index++) {
            String dependency = dependencies.get(index);
            if (dependency == null || dependency.isBlank()) {
                continue;
            }
            PlatformModuleDependencyEntity entity = new PlatformModuleDependencyEntity();
            entity.setModuleCode(request.getModuleCode());
            entity.setDependencyModuleCode(dependency.trim());
            entity.setSortNo(index + 1);
            entity.setCreatedBy(operatorId);
            entity.setUpdatedBy(operatorId);
            entity.setDeleted(0);
            dependencyMapper.insert(entity);
        }
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("\\n"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static List<String> append(List<String> source, String value) {
        if (value == null || value.isBlank()) {
            return source;
        }
        java.util.ArrayList<String> next = new java.util.ArrayList<>(source);
        next.add(value);
        return List.copyOf(next);
    }

    private static String joinLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join("\n", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList());
    }
}
