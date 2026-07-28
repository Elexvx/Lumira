package com.lumira.saas.modules.system.permission;

import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SystemPermissionTreeAssembler {

    private static final String NODE_TYPE_CATALOG = "CATALOG";
    private static final String NODE_TYPE_PAGE = "PAGE";
    private static final String NODE_TYPE_ALIAS = "ALIAS";
    private static final Set<String> NON_PAGE_ACTION_SUFFIXES = Set.of(
            "create",
            "update",
            "delete",
            "status",
            "manage",
            "submit",
            "approve",
            "export",
            "score",
            "review",
            "archive",
            "publish",
            "offline",
            "grant-menu",
            "permissions",
            "write",
            "kick",
            "ban"
    );
    private static final Map<String, List<String>> EXPLICIT_ACTION_PREFIXES_BY_PAGE_PERMISSION = Map.ofEntries(
            Map.entry("ai:view", List.of("ai:employee:", "ai:llm:", "ai:skill:", "ai:tool:")),
            Map.entry("audit:view", List.of("audit:")),
            Map.entry("download:center:view", List.of("download:center:")),
            Map.entry("plugin:management:view", List.of("plugin:management:")),
            Map.entry("system:file:view", List.of("system:file:upload", "system:file:publish", "system:file:delete")),
            Map.entry("system:file:manage", List.of("system:file:manage:")),
            Map.entry("system:department:view", List.of("system:department:")),
            Map.entry("system:monitor:view", List.of("system:monitor:")),
            Map.entry("system:update:view", List.of("system:update:")),
            Map.entry("system:notification:view", List.of("system:notification:", "message:message:")),
            Map.entry("system:verification:view", List.of("system:verification:")),
            Map.entry("payment:view", List.of("payment:config:", "payment:order:", "payment:refund:", "payment:webhook:"))
    );
    private static final Set<String> LEGACY_PERMISSION_TREE_ALIAS_PATHS = Set.of(
            "/audit/overview",
            "/system/overview",
            "/system/users",
            "/system/departments",
            "/system/online-users",
            "/system/roles",
            "/profile/center",
            "/user-center/permissions",
            "/iam/overview"
    );

    public List<SystemVO.PermissionTreeVO> build(List<SystemVO.MenuVO> menus, List<SystemVO.PermissionVO> permissions) {
        if (CollectionUtils.isEmpty(menus)) {
            return List.of();
        }
        Map<String, SystemVO.PermissionVO> permissionMap = buildPermissionMap(permissions);
        Map<String, List<SystemVO.PermissionActionVO>> actionPermissionsByPageKey = buildActionPermissionsByPageKey(permissions, permissionMap);
        List<SystemVO.PermissionTreeVO> tree = new ArrayList<>();
        for (SystemVO.MenuVO menu : menus) {
            SystemVO.PermissionTreeVO node = buildPermissionTreeNode(menu, actionPermissionsByPageKey, permissionMap);
            if (node != null) {
                tree.add(node);
            }
        }
        return tree;
    }

    private SystemVO.PermissionTreeVO buildPermissionTreeNode(
            SystemVO.MenuVO menu,
            Map<String, List<SystemVO.PermissionActionVO>> actionPermissionsByPageKey,
            Map<String, SystemVO.PermissionVO> permissionMap
    ) {
        if (menu == null || "BUTTON".equalsIgnoreCase(menu.getMenuType())) {
            return null;
        }
        List<SystemVO.PermissionTreeVO> children = new ArrayList<>();
        if (!CollectionUtils.isEmpty(menu.getChildren())) {
            for (SystemVO.MenuVO child : menu.getChildren()) {
                SystemVO.PermissionTreeVO childNode = buildPermissionTreeNode(child, actionPermissionsByPageKey, permissionMap);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
        }
        if (isAdminOnlySettingsPath(menu) && children.isEmpty()) {
            return null;
        }

        String nodeType = resolvePermissionTreeNodeType(menu);
        boolean selectable = NODE_TYPE_PAGE.equals(nodeType) && StringUtils.hasText(menu.getPermissionKey());
        if (!selectable && children.isEmpty() && !NODE_TYPE_CATALOG.equals(nodeType)) {
            return null;
        }

        SystemVO.PermissionTreeVO node = new SystemVO.PermissionTreeVO();
        node.setPageKey(menu.getId() != null ? String.valueOf(menu.getId()) : StringUtils.hasText(menu.getPath()) ? menu.getPath() : menu.getMenuCode());
        node.setPageName(menu.getMenuName());
        node.setNodeType(nodeType);
        node.setRoutePath((NODE_TYPE_PAGE.equals(nodeType) || NODE_TYPE_CATALOG.equals(nodeType)) ? menu.getPath() : null);
        node.setIcon(menu.getIcon());
        node.setPermissionKey(menu.getPermissionKey());
        node.setSelectable(selectable);
        node.setChildren(children.isEmpty() ? null : children);
        if (selectable) {
            node.setPermissionGroup(resolvePermissionGroup(menu.getPermissionKey()));
            node.setSourceType(resolvePermissionSourceType(menu.getPermissionKey()));
            node.setActionPermissions(resolveMenuButtonActions(menu, permissionMap, actionPermissionsByPageKey));
        }
        return node;
    }

    private String resolvePermissionTreeNodeType(SystemVO.MenuVO menu) {
        if (menu == null) {
            return NODE_TYPE_ALIAS;
        }
        if ("CATALOG".equalsIgnoreCase(menu.getMenuType())) {
            return NODE_TYPE_CATALOG;
        }
        if (isLegacyPermissionTreeAliasPath(menu.getPath()) || isRedirectComponent(menu.getComponent())) {
            return NODE_TYPE_ALIAS;
        }
        return NODE_TYPE_PAGE;
    }

    private boolean isLegacyPermissionTreeAliasPath(String path) {
        return StringUtils.hasText(path) && LEGACY_PERMISSION_TREE_ALIAS_PATHS.contains(path);
    }

    private boolean isRedirectComponent(String component) {
        return StringUtils.hasText(component) && component.startsWith("redirect:");
    }

    private boolean isAdminOnlySettingsPath(SystemVO.MenuVO menu) {
        String path = menu == null ? null : menu.getPath();
        if (!StringUtils.hasText(path)) {
            return false;
        }
        if (StringUtils.hasText(menu.getPermissionKey()) && menu.getPermissionKey().startsWith("plugin:")) {
            return false;
        }
        String normalizedPath = path.trim();
        if ("/settings/payment".equals(normalizedPath)) {
            return false;
        }
        return "/settings".equals(normalizedPath) || normalizedPath.startsWith("/settings/");
    }

    private Map<String, SystemVO.PermissionVO> buildPermissionMap(List<SystemVO.PermissionVO> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return Map.of();
        }
        return permissions.stream()
                .filter(permission -> StringUtils.hasText(permission.getPermissionKey()))
                .collect(Collectors.toMap(SystemVO.PermissionVO::getPermissionKey, permission -> permission, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, List<SystemVO.PermissionActionVO>> buildActionPermissionsByPageKey(
            List<SystemVO.PermissionVO> permissions,
            Map<String, SystemVO.PermissionVO> permissionMap
    ) {
        if (CollectionUtils.isEmpty(permissions) || CollectionUtils.isEmpty(permissionMap)) {
            return Map.of();
        }

        Map<String, List<SystemVO.PermissionActionVO>> result = new LinkedHashMap<>();
        for (SystemVO.PermissionVO permission : permissions) {
            String permissionKey = permission.getPermissionKey();
            if (!StringUtils.hasText(permissionKey)) {
                continue;
            }
            String pagePermissionKey = resolvePagePermissionKey(permissionKey, permissionMap);
            if (!StringUtils.hasText(pagePermissionKey)) {
                continue;
            }
            List<String> actionPrefixes = resolveActionPrefixes(pagePermissionKey);
            List<SystemVO.PermissionActionVO> actions = permissionMap.values().stream()
                    .filter(candidate -> !permissionKey.equals(candidate.getPermissionKey()))
                    .filter(candidate -> isActionPermissionForPage(candidate.getPermissionKey(), actionPrefixes))
                    .map(candidate -> {
                        SystemVO.PermissionActionVO action = new SystemVO.PermissionActionVO();
                        action.setPermissionKey(candidate.getPermissionKey());
                        action.setPermissionName(candidate.getPermissionName());
                        action.setPermissionGroup(candidate.getPermissionGroup());
                        action.setSourceType(candidate.getSourceType());
                        return action;
                    })
                    .sorted(Comparator.comparing(SystemVO.PermissionActionVO::getPermissionKey))
                    .toList();
            if (!actions.isEmpty()) {
                result.put(pagePermissionKey, actions);
            }
        }

        return result;
    }

    private List<SystemVO.PermissionActionVO> resolveMenuButtonActions(
            SystemVO.MenuVO menu,
            Map<String, SystemVO.PermissionVO> permissionMap,
            Map<String, List<SystemVO.PermissionActionVO>> inferredActionsByPageKey
    ) {
        List<SystemVO.PermissionActionVO> buttonActions = buildMenuButtonActions(menu, permissionMap);
        if (!buttonActions.isEmpty()) {
            return buttonActions;
        }
        return inferredActionsByPageKey.getOrDefault(menu.getPermissionKey(), List.of());
    }

    private List<SystemVO.PermissionActionVO> buildMenuButtonActions(SystemVO.MenuVO menu, Map<String, SystemVO.PermissionVO> permissionMap) {
        if (menu == null || CollectionUtils.isEmpty(menu.getChildren())) {
            return List.of();
        }
        return menu.getChildren().stream()
                .filter(child -> child != null && "BUTTON".equalsIgnoreCase(child.getMenuType()))
                .filter(child -> StringUtils.hasText(child.getPermissionKey()))
                .map(child -> toPermissionAction(child, permissionMap))
                .sorted(Comparator.comparing(SystemVO.PermissionActionVO::getPermissionKey, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private SystemVO.PermissionActionVO toPermissionAction(SystemVO.MenuVO buttonMenu, Map<String, SystemVO.PermissionVO> permissionMap) {
        SystemVO.PermissionVO permission = permissionMap.get(buttonMenu.getPermissionKey());
        SystemVO.PermissionActionVO action = new SystemVO.PermissionActionVO();
        action.setPermissionKey(buttonMenu.getPermissionKey());
        action.setPermissionName(permission != null && StringUtils.hasText(permission.getPermissionName())
                ? permission.getPermissionName()
                : buttonMenu.getMenuName());
        action.setPermissionGroup(permission != null && StringUtils.hasText(permission.getPermissionGroup())
                ? permission.getPermissionGroup()
                : resolvePermissionGroup(buttonMenu.getPermissionKey()));
        action.setSourceType(permission != null && StringUtils.hasText(permission.getSourceType())
                ? permission.getSourceType()
                : resolvePermissionSourceType(buttonMenu.getPermissionKey()));
        return action;
    }

    private boolean isActionPermissionForPage(String permissionKey, List<String> actionPrefixes) {
        if (!StringUtils.hasText(permissionKey) || CollectionUtils.isEmpty(actionPrefixes)) {
            return false;
        }
        for (String actionPrefix : actionPrefixes) {
            if (permissionKey.startsWith(actionPrefix)) {
                return true;
            }
        }
        return false;
    }

    private String resolvePagePermissionKey(String permissionKey, Map<String, SystemVO.PermissionVO> permissionMap) {
        if (!StringUtils.hasText(permissionKey)) {
            return null;
        }
        if (permissionKey.endsWith(":view") || EXPLICIT_ACTION_PREFIXES_BY_PAGE_PERMISSION.containsKey(permissionKey)) {
            return permissionKey;
        }
        int lastColon = permissionKey.lastIndexOf(':');
        if (lastColon <= 0) {
            return null;
        }
        String suffix = permissionKey.substring(lastColon + 1);
        if (!NON_PAGE_ACTION_SUFFIXES.contains(suffix)) {
            return permissionKey;
        }
        String candidate = permissionKey.substring(0, lastColon);
        return permissionMap.containsKey(candidate) ? null : permissionKey;
    }

    private List<String> resolveActionPrefixes(String pagePermissionKey) {
        if (!StringUtils.hasText(pagePermissionKey)) {
            return List.of();
        }
        List<String> explicitPrefixes = EXPLICIT_ACTION_PREFIXES_BY_PAGE_PERMISSION.get(pagePermissionKey);
        if (!CollectionUtils.isEmpty(explicitPrefixes)) {
            return explicitPrefixes;
        }
        if (!pagePermissionKey.endsWith(":view")) {
            return List.of(pagePermissionKey + ":");
        }
        return List.of(pagePermissionKey.substring(0, pagePermissionKey.length() - ":view".length()) + ":");
    }

    private String resolvePermissionGroup(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return null;
        }
        int firstColon = permissionKey.indexOf(':');
        return firstColon > 0 ? permissionKey.substring(0, firstColon) : permissionKey;
    }

    private String resolvePermissionSourceType(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return null;
        }
        return permissionKey.startsWith("plugin:") ? "PLUGIN" : "CORE";
    }
}
