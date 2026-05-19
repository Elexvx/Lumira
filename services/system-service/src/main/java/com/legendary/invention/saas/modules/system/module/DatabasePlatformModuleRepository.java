package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;
import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DatabasePlatformModuleRepository {

    private static final Logger log = LoggerFactory.getLogger(DatabasePlatformModuleRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabasePlatformModuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlatformModuleVO> listModules() {
        try {
            Map<String, PlatformModuleVO> modules = new LinkedHashMap<>();
            jdbcTemplate.query("""
                    select module_code, module_name, module_type, lifecycle_status, source_type,
                           description, owner_service, admin_route_path, api_prefixes, permission_keys,
                           builtin, created_at
                    from platform_module_definition
                    where deleted = 0
                    order by sort_no asc, id asc
                    """, rs -> {
                PlatformModuleVO module = new PlatformModuleVO();
                module.setModuleCode(rs.getString("module_code"));
                module.setModuleName(rs.getString("module_name"));
                module.setModuleType(rs.getString("module_type"));
                module.setLifecycleStatus(rs.getString("lifecycle_status"));
                module.setSourceType(rs.getString("source_type"));
                module.setDescription(rs.getString("description"));
                module.setOwnerService(rs.getString("owner_service"));
                module.setAdminRoutePath(rs.getString("admin_route_path"));
                module.setApiPrefixes(splitLines(rs.getString("api_prefixes")));
                module.setPermissionKeys(splitLines(rs.getString("permission_keys")));
                module.setDependencies(List.of());
                module.setRegistrationSourceOrder(List.of(module.getSourceType()));
                module.setRegisteredAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime().toString());
                module.setBuiltin(rs.getBoolean("builtin"));
                modules.put(module.getModuleCode(), module);
            });

            jdbcTemplate.query("""
                    select module_code, dependency_module_code
                    from platform_module_dependency
                    where deleted = 0
                    order by sort_no asc, id asc
                    """, rs -> {
                PlatformModuleVO module = modules.get(rs.getString("module_code"));
                if (module != null) {
                    module.setDependencies(append(module.getDependencies(), rs.getString("dependency_module_code")));
                }
            });
            return List.copyOf(modules.values());
        } catch (DataAccessException exception) {
            log.warn("Failed to load persisted platform modules, fallback to static catalog: {}", exception.getMessage());
            return List.of();
        }
    }

    public void createModule(SystemDTO.ModuleValidationRequest request, Long operatorId) {
        jdbcTemplate.update("""
                insert into platform_module_definition (
                    module_code, module_name, module_type, lifecycle_status, source_type,
                    description, owner_service, admin_route_path, api_prefixes, permission_keys,
                    builtin, sort_no, created_by, updated_by, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1000, ?, ?, 0)
                """,
                request.getModuleCode(),
                request.getModuleName(),
                request.getModuleType(),
                request.getLifecycleStatus(),
                request.getSourceType(),
                request.getDescription(),
                request.getOwnerService() == null || request.getOwnerService().isBlank() ? "system-service" : request.getOwnerService(),
                request.getAdminRoutePath(),
                joinLines(request.getApiPrefixes()),
                joinLines(request.getPermissionKeys()),
                operatorId,
                operatorId
        );

        List<String> dependencies = request.getDependencies() == null ? List.of() : request.getDependencies();
        for (int index = 0; index < dependencies.size(); index++) {
            String dependency = dependencies.get(index);
            if (dependency == null || dependency.isBlank()) {
                continue;
            }
            jdbcTemplate.update("""
                    insert into platform_module_dependency (
                        module_code, dependency_module_code, sort_no, created_by, updated_by, deleted
                    ) values (?, ?, ?, ?, ?, 0)
                    """,
                    request.getModuleCode(),
                    dependency.trim(),
                    index + 1,
                    operatorId,
                    operatorId
            );
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
