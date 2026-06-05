import type { RoleDataScope } from '@/types/api';

export interface RoleListQuery extends Record<string, unknown> {
  keyword?: string;
  roleCode?: string;
  roleName?: string;
  roleType?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface RoleMutationPayload {
  roleCode: string;
  roleName: string;
  roleType: string;
  defaultHomePath?: string;
  permissionKeys?: string[];
  dataScopes?: RoleDataScope[];
}

export interface DefaultRegistrationRolePayload {
  roleId: number;
}

export interface MenuMutationPayload {
  parentId?: number | null;
  menuCode: string;
  menuName: string;
  menuType: string;
  path?: string;
  component?: string;
  icon?: string;
  sortNo?: number;
  permissionKey?: string;
  status: string;
}

export interface MenuOrderItem {
  id: number;
  parentId?: number | null;
  sortNo: number;
}

export interface MenuReorderPayload {
  items: MenuOrderItem[];
}

export interface DepartmentMutationPayload {
  parentId?: number | null;
  deptCode: string;
  deptName: string;
  sortNo?: number;
  status: string;
}
