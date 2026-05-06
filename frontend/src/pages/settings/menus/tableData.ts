import { filterMenus, flattenMenus, flattenVisibleMenus } from '@/pages/settings/menus/treeUtils';
import type { MenuRecord } from '@/types/api';

export const buildMenuTableData = (
  menuTree: MenuRecord[],
  expandedRowKeys: number[],
  params: Record<string, unknown>,
): MenuRecord[] => {
  const keyword = String(params.menuName || params.keyword || '');
  const menuCode = String(params.menuCode || '');
  const permissionKey = String(params.permissionKey || '');
  const filtered = filterMenus(menuTree, keyword, menuCode, permissionKey);
  const hasSearch = Boolean(keyword.trim() || menuCode.trim() || permissionKey.trim());
  return hasSearch ? flattenMenus(filtered) : flattenVisibleMenus(filtered, expandedRowKeys);
};
