package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PlatformModuleCatalog {

    private static final String ENABLED = "ENABLED";
    private static final String BUILTIN = "BUILTIN";
    private static final String FOUNDATION = "FOUNDATION";
    private static final String CAPABILITY = "CAPABILITY";
    private static final String SCENE = "SCENE";
    private static final String ADAPTER = "ADAPTER";

    private static final List<PlatformModuleVO> MODULES = List.of(
            foundation(
                    "system",
                    "系统管理",
                    "承载用户、角色、菜单、权限、配置、审计入口和系统控制面。",
                    "system-service",
                    "/settings",
                    List.of("/api/v1/system/**"),
                    List.of("system:view", "system:menu:view", "system:role:view", "system:user:view")
            ),
            foundation(
                    "auth",
                    "认证与会话",
                    "承载登录、Token、刷新、二次验证、通行密钥和会话恢复能力。",
                    "auth-service",
                    null,
                    List.of("/api/v1/auth/**"),
                    List.of()
            ),
            foundation(
                    "file",
                    "文件中心",
                    "承载文件对象、上传下载、存储空间和业务附件能力。",
                    "file-service",
                    "/settings/files/all",
                    List.of("/api/v1/files/**", "/api/uploads/**"),
                    List.of("system:file:view", "system:file:manage")
            ),
            foundation(
                    "message",
                    "消息中心",
                    "承载站内信、WebSocket、消息归档、outbox 和通知投递能力。",
                    "message-service",
                    "/settings/notifications",
                    List.of("/api/v1/message/**", "/ws/message"),
                    List.of("message:message:view", "message:message:write", "system:notification:view")
            ),
            foundation(
                    "localization",
                    "本地化中心",
                    "承载语言包、翻译条目、发布回滚和运行时多语言能力。",
                    "localization-service",
                    "/settings/localization",
                    List.of("/api/v1/localization/**"),
                    List.of("localization:view", "localization:publish")
            ),
            capability(
                    "site",
                    "官网与 CMS",
                    "承载站点、导航、轮播、内容、提交记录和公开 API。",
                    "system-service/site-frontend",
                    "/site",
                    List.of("/api/v1/site/**", "/api/v1/public/site/**"),
                    List.of("site:view", "site:settings", "site:navigation", "site:carousel", "site:content", "site:submission"),
                    List.of("system", "file", "message")
            ),
            capability(
                    "plugin",
                    "插件运行时",
                    "承载插件上传、安装、启停、版本、运行时、菜单权限声明和插件 API 网关。",
                    "plugin-service",
                    "/settings/plugins",
                    List.of("/api/v1/plugins/**", "/api/p/{pluginCode}/**"),
                    List.of("plugin:management:view", "plugin:management:install", "plugin:management:enable", "plugin:management:disable"),
                    List.of("system", "file")
            ),
            optional(
                    "ai",
                    "AI 工作台",
                    CAPABILITY,
                    "承载数字员工、LLM 服务、技能、对话和工具调用能力。",
                    "system-service",
                    "/ai",
                    List.of("/api/ai/**"),
                    List.of("ai:view", "ai:chat:send", "ai:knowledge:view", "ai:knowledge:query", "ai:knowledge:share"),
                    List.of("system", "file")
            ),
            planned(
                    "form",
                    "表单能力",
                    CAPABILITY,
                    "计划从官网表单抽象出的通用动态表单定义能力。",
                    List.of("site")
            ),
            planned(
                    "submission",
                    "提交能力",
                    CAPABILITY,
                    "计划从官网提交记录抽象出的投稿、报名、申请等通用提交能力。",
                    List.of("form", "file", "message")
            ),
            planned(
                    "journal",
                    "期刊场景",
                    SCENE,
                    "计划用于验证投稿、内容处理和发布的第一个场景模块。",
                    List.of("form", "submission", "file", "message", "site")
            ),
            planned(
                    "competition",
                    "比赛场景",
                    SCENE,
                    "计划用于验证报名、作品提交和结果发布的场景模块。",
                    List.of("form", "submission", "file", "message", "site")
            ),
            optional(
                    "sms",
                    "短信适配",
                    ADAPTER,
                    "通过插件运行时接入的短信验证码和通知供应商适配能力。",
                    "plugin-service",
                    "/plugins/sms",
                    List.of("/api/p/sms/**"),
                    List.of("plugin:sms:view", "plugin:sms:manage"),
                    List.of("plugin", "auth")
            )
    );

    private PlatformModuleCatalog() {
    }

    public static List<PlatformModuleVO> listModules() {
        return evaluateReadiness(MODULES);
    }

    public static Optional<PlatformModuleVO> findModule(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return Optional.empty();
        }
        return evaluateReadiness(MODULES).stream()
                .filter(module -> moduleCode.equals(module.getModuleCode()))
                .findFirst();
    }

    public static List<PlatformModuleVO> evaluateReadiness(List<PlatformModuleVO> modules) {
        List<PlatformModuleVO> safeModules = modules == null ? List.of() : modules.stream()
                .filter(module -> module != null && module.getModuleCode() != null && !module.getModuleCode().isBlank())
                .toList();
        Map<String, PlatformModuleVO> moduleMap = safeModules.stream()
                .collect(Collectors.toMap(PlatformModuleVO::getModuleCode, item -> item, (left, right) -> left, LinkedHashMap::new));

        return safeModules.stream()
                .map(module -> enrichModule(module, moduleMap))
                .toList();
    }

    private static PlatformModuleVO enrichModule(PlatformModuleVO source, Map<String, PlatformModuleVO> moduleMap) {
        PlatformModuleVO module = copyOf(source);
        List<String> missingDependencies = new ArrayList<>();
        List<String> inactiveDependencies = new ArrayList<>();
        List<String> readinessIssues = new ArrayList<>();

        for (String dependency : defaultList(module.getDependencies())) {
            PlatformModuleVO dependencyModule = moduleMap.get(dependency);
            if (dependencyModule == null) {
                missingDependencies.add(dependency);
            } else if (!ENABLED.equals(dependencyModule.getLifecycleStatus())) {
                inactiveDependencies.add(dependency);
            }
        }

        if ("PLANNED".equals(module.getLifecycleStatus())) {
            readinessIssues.add("模块仍处于规划状态，尚未完成运行时实现");
        } else if ("DISABLED".equals(module.getLifecycleStatus())) {
            readinessIssues.add("模块当前已停用");
        } else if ("DEPRECATED".equals(module.getLifecycleStatus())) {
            readinessIssues.add("模块已进入废弃状态，不建议继续启用");
        }
        if (!missingDependencies.isEmpty()) {
            readinessIssues.add("缺少依赖模块: " + String.join(", ", missingDependencies));
        }
        if (!inactiveDependencies.isEmpty()) {
            readinessIssues.add("依赖模块未启用: " + String.join(", ", inactiveDependencies));
        }

        module.setMissingDependencies(List.copyOf(missingDependencies));
        module.setInactiveDependencies(List.copyOf(inactiveDependencies));
        module.setDependencySatisfied(missingDependencies.isEmpty() && inactiveDependencies.isEmpty());
        module.setReadinessIssues(List.copyOf(readinessIssues));
        module.setReadyToEnable(readinessIssues.isEmpty());
        if (module.getRegistrationSourceOrder() == null || module.getRegistrationSourceOrder().isEmpty()) {
            module.setRegistrationSourceOrder(List.of(defaultText(module.getSourceType(), BUILTIN)));
        }
        return module;
    }

    private static PlatformModuleVO copyOf(PlatformModuleVO source) {
        PlatformModuleVO module = new PlatformModuleVO();
        module.setModuleCode(source.getModuleCode());
        module.setModuleName(source.getModuleName());
        module.setModuleType(source.getModuleType());
        module.setLifecycleStatus(source.getLifecycleStatus());
        module.setSourceType(source.getSourceType());
        module.setDescription(source.getDescription());
        module.setOwnerService(source.getOwnerService());
        module.setAdminRoutePath(source.getAdminRoutePath());
        module.setApiPrefixes(defaultList(source.getApiPrefixes()));
        module.setPermissionKeys(defaultList(source.getPermissionKeys()));
        module.setDependencies(defaultList(source.getDependencies()));
        module.setOverriddenByDatabase(source.isOverriddenByDatabase());
        module.setRegistrationSourceOrder(defaultList(source.getRegistrationSourceOrder()).isEmpty()
                ? List.of(defaultText(source.getSourceType(), BUILTIN))
                : defaultList(source.getRegistrationSourceOrder()));
        module.setRegisteredAt(source.getRegisteredAt());
        module.setBuiltin(source.isBuiltin());
        return module;
    }

    private static List<String> defaultList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static PlatformModuleVO foundation(
            String moduleCode,
            String moduleName,
            String description,
            String ownerService,
            String adminRoutePath,
            List<String> apiPrefixes,
            List<String> permissionKeys
    ) {
        return module(moduleCode, moduleName, FOUNDATION, ENABLED, description, ownerService, adminRoutePath, apiPrefixes, permissionKeys, List.of(), true);
    }

    private static PlatformModuleVO capability(
            String moduleCode,
            String moduleName,
            String description,
            String ownerService,
            String adminRoutePath,
            List<String> apiPrefixes,
            List<String> permissionKeys,
            List<String> dependencies
    ) {
        return module(moduleCode, moduleName, CAPABILITY, ENABLED, description, ownerService, adminRoutePath, apiPrefixes, permissionKeys, dependencies, true);
    }

    private static PlatformModuleVO optional(
            String moduleCode,
            String moduleName,
            String moduleType,
            String description,
            String ownerService,
            String adminRoutePath,
            List<String> apiPrefixes,
            List<String> permissionKeys,
            List<String> dependencies
    ) {
        return module(moduleCode, moduleName, moduleType, ENABLED, description, ownerService, adminRoutePath, apiPrefixes, permissionKeys, dependencies, true);
    }

    private static PlatformModuleVO planned(
            String moduleCode,
            String moduleName,
            String moduleType,
            String description,
            List<String> dependencies
    ) {
        return module(moduleCode, moduleName, moduleType, "PLANNED", description, "pending", null, List.of(), List.of(), dependencies, false);
    }

    private static PlatformModuleVO module(
            String moduleCode,
            String moduleName,
            String moduleType,
            String status,
            String description,
            String ownerService,
            String adminRoutePath,
            List<String> apiPrefixes,
            List<String> permissionKeys,
            List<String> dependencies,
            boolean builtin
    ) {
        PlatformModuleVO module = new PlatformModuleVO();
        module.setModuleCode(moduleCode);
        module.setModuleName(moduleName);
        module.setModuleType(moduleType);
        module.setLifecycleStatus(status);
        module.setSourceType(BUILTIN);
        module.setDescription(description);
        module.setOwnerService(ownerService);
        module.setAdminRoutePath(adminRoutePath);
        module.setApiPrefixes(apiPrefixes);
        module.setPermissionKeys(permissionKeys);
        module.setDependencies(dependencies);
        module.setBuiltin(builtin);
        return module;
    }
}
