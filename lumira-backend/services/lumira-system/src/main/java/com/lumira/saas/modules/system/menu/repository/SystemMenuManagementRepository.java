package com.lumira.saas.modules.system.menu.repository;

import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;

/** Read/write boundary for managed (non-builtin policy is enforced above this port) menus and permissions. */
public interface SystemMenuManagementRepository {
    List<SystemVO.PermissionVO> findPermissions();

    List<SystemVO.MenuVO> findMenus();

    List<PluginMenu> findActivePluginMenus();

    List<SystemVO.MenuVO> findEnabledMenus();

    SystemVO.MenuVO findActiveMenu(Long menuId);

    boolean hasActiveChild(Long menuId);

    boolean hasActivePermissionReference(String permissionKey);

    int reorder(MenuOrder command);

    int updateStatus(MenuVersion version, String status, Actor actor, LocalDateTime updatedAt);

    int softDelete(MenuVersion version, Actor actor, LocalDateTime updatedAt);

    MenuSaveResult save(MenuSave command);

    record Actor(Long userId, String userUuid) {}

    record MenuVersion(Long id, String menuCode, String menuType) {}

    record MenuOrder(MenuVersion version, Long parentId, Integer sortNo, Actor actor, LocalDateTime updatedAt) {}

    record MenuSave(
            MenuVersion existing,
            Long parentId,
            String menuCode,
            String menuName,
            String menuType,
            String path,
            String component,
            String icon,
            Integer sortNo,
            String permissionKey,
            String status,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record MenuSaveResult(int writeCount, Long menuId) {}

    record PluginMenu(
            String menuCode,
            String menuName,
            String path,
            String icon,
            String permissionKey,
            String parentMenuCode,
            Integer sortNo,
            String pluginCode
    ) {}
}
