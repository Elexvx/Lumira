import type { DragEvent } from 'react';
import type { MenuNode, MenuRecord } from '@/types/api';

export type MenuDropPosition = 'before' | 'inside' | 'after';
export type MenuTreeRecord = MenuRecord & { level?: number };

export const toRuntimeMenuNodes = (menus: MenuRecord[]): MenuNode[] =>
  menus.map((menu) => ({
    id: menu.id,
    tenantId: menu.tenantId,
    parentId: menu.parentId ?? undefined,
    menuCode: menu.menuCode,
    name: menu.menuName,
    path: menu.path ?? '',
    component: menu.component ?? undefined,
    icon: menu.icon ?? undefined,
    permissionKey: menu.permissionKey ?? undefined,
    sortNo: menu.sortNo,
    children: menu.children?.length ? toRuntimeMenuNodes(menu.children) : undefined,
  }));

export const filterMenus = (menus: MenuRecord[], keyword: string, menuCode: string, permissionKey: string, level = 0): MenuTreeRecord[] => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  const normalizedMenuCode = menuCode.trim().toLowerCase();
  const normalizedPermissionKey = permissionKey.trim().toLowerCase();

  return menus
    .map((menu) => {
      const matched =
        (!normalizedKeyword || menu.menuName.toLowerCase().includes(normalizedKeyword)) &&
        (!normalizedMenuCode || menu.menuCode.toLowerCase().includes(normalizedMenuCode)) &&
        (!normalizedPermissionKey || (menu.permissionKey || '').toLowerCase().includes(normalizedPermissionKey));
      const children = menu.children ? filterMenus(menu.children, keyword, menuCode, permissionKey, level + 1) : [];
      if (matched || children.length) {
        return {
          ...menu,
          level,
          children: children.length ? children : undefined,
        };
      }
      return null;
    })
    .filter(Boolean) as MenuRecord[];
};

export const flattenMenus = (menus: MenuRecord[], level = 0, result: Array<MenuRecord & { level: number }> = []) => {
  menus.forEach((menu) => {
    const { children: _children, ...rest } = menu;
    result.push({ ...rest, level });
    if (menu.children?.length) {
      flattenMenus(menu.children, level + 1, result);
    }
  });
  return result;
};

export const flattenVisibleMenus = (
  menus: MenuTreeRecord[],
  expandedRowKeys: number[],
  includeAllChildren = false,
  level = 0,
  result: MenuTreeRecord[] = [],
) => {
  menus.forEach((menu) => {
    const { children: _children, ...rest } = menu;
    const currentMenu = { ...rest, level };
    result.push(currentMenu);

    if (menu.children?.length && (includeAllChildren || expandedRowKeys.includes(menu.id))) {
      flattenVisibleMenus(menu.children as MenuTreeRecord[], expandedRowKeys, includeAllChildren, level + 1, result);
    }
  });

  return result;
};

export const sortMenuTree = (menus: MenuRecord[]): MenuRecord[] =>
  [...menus]
    .sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0) || left.id - right.id)
    .map((menu) => ({
      ...menu,
      children: menu.children?.length ? sortMenuTree(menu.children) : undefined,
    }));

export const normalizeMenuTreeOrder = (menus: MenuRecord[], parentId = 0): MenuRecord[] =>
  menus.map((menu, index) => ({
    ...menu,
    parentId,
    sortNo: index,
    children: menu.children?.length ? normalizeMenuTreeOrder(menu.children, menu.id) : undefined,
  }));

export const flattenMenuOrder = (menus: MenuRecord[], parentId = 0, result: Array<{ id: number; parentId?: number | null; sortNo: number }> = []) => {
  menus.forEach((menu, index) => {
    result.push({
      id: menu.id,
      parentId,
      sortNo: index,
    });
    if (menu.children?.length) {
      flattenMenuOrder(menu.children, menu.id, result);
    }
  });
  return result;
};

const extractMenuNode = (menus: MenuRecord[], menuId: number): { menus: MenuRecord[]; node?: MenuRecord } => {
  const nextMenus: MenuRecord[] = [];
  let extractedNode: MenuRecord | undefined;

  for (const menu of menus) {
    if (menu.id === menuId) {
      extractedNode = menu;
      continue;
    }

    if (menu.children?.length) {
      const childResult = extractMenuNode(menu.children, menuId);
      if (childResult.node) {
        extractedNode = childResult.node;
        nextMenus.push({
          ...menu,
          children: childResult.menus.length ? childResult.menus : undefined,
        });
        continue;
      }
    }

    nextMenus.push(menu);
  }

  return { menus: nextMenus, node: extractedNode };
};

const insertMenuNode = (
  menus: MenuRecord[],
  targetId: number,
  node: MenuRecord,
  position: MenuDropPosition,
): { menus: MenuRecord[]; inserted: boolean } => {
  const nextMenus: MenuRecord[] = [];
  let inserted = false;

  for (const menu of menus) {
    if (position === 'before' && menu.id === targetId) {
      nextMenus.push(node, menu);
      inserted = true;
      continue;
    }

    if (position === 'after' && menu.id === targetId) {
      nextMenus.push(menu, node);
      inserted = true;
      continue;
    }

    if (position === 'inside' && menu.id === targetId) {
      if (menu.menuType === 'BUTTON') {
        nextMenus.push(menu);
        continue;
      }
      nextMenus.push({
        ...menu,
        children: [...(menu.children || []), node],
      });
      inserted = true;
      continue;
    }

    if (menu.children?.length) {
      const childResult = insertMenuNode(menu.children, targetId, node, position);
      if (childResult.inserted) {
        nextMenus.push({
          ...menu,
          children: childResult.menus,
        });
        inserted = true;
        continue;
      }
    }

    nextMenus.push(menu);
  }

  return { menus: nextMenus, inserted };
};

export const moveMenuNode = (menus: MenuRecord[], draggedId: number, targetId: number, position: MenuDropPosition) => {
  if (draggedId === targetId) {
    return null;
  }

  const extracted = extractMenuNode(menus, draggedId);
  if (!extracted.node) {
    return null;
  }

  const inserted = insertMenuNode(extracted.menus, targetId, extracted.node, position);
  if (!inserted.inserted) {
    return null;
  }

  return normalizeMenuTreeOrder(inserted.menus);
};

export const getDropPosition = (event: DragEvent<HTMLTableRowElement>, record: MenuRecord): MenuDropPosition => {
  const row = event.currentTarget;
  const bounds = row.getBoundingClientRect();
  const offsetY = event.clientY - bounds.top;
  const ratio = bounds.height <= 0 ? 0.5 : offsetY / bounds.height;

  if (ratio <= 0.25) {
    return 'before';
  }
  if (ratio >= 0.75) {
    return 'after';
  }

  return record.menuType === 'BUTTON' ? 'after' : 'inside';
};
