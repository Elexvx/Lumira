import { request, type RequestOptions } from '@/services/common/request';
import type { DepartmentRecord, MenuRecord, PagedResult, PermissionRecord, PermissionTreeRecord, RoleDataScope, RoleDetail, RoleRecord } from '@/types/api';

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

export const iamService = {
  roles: (params: RoleListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<RoleRecord>>('/v1/system/roles', {
      method: 'GET',
      params,
      ...options,
    }),
  roleDetail: (id: number, options: RequestOptions = {}) =>
    request<RoleDetail>(`/v1/system/roles/${id}`, {
      method: 'GET',
      ...options,
    }),
  defaultRegistrationRole: (options: RequestOptions = {}) =>
    request<RoleDetail>('/v1/system/roles/default-registration-role', {
      method: 'GET',
      ...options,
    }),
  updateDefaultRegistrationRole: (payload: DefaultRegistrationRolePayload, options: RequestOptions = {}) =>
    request<RoleDetail>('/v1/system/roles/default-registration-role', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  createRole: (payload: RoleMutationPayload, options: RequestOptions = {}) =>
    request<RoleDetail>('/v1/system/roles', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateRole: (id: number, payload: RoleMutationPayload, options: RequestOptions = {}) =>
    request<RoleDetail>(`/v1/system/roles/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteRole: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/roles/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  updateRolePermissions: (id: number, permissionKeys: string[], options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/roles/${id}/permissions`, {
      method: 'PUT',
      data: { permissionKeys },
      ...options,
    }),
  permissions: (options: RequestOptions = {}) =>
    request<PermissionRecord[]>('/v1/system/permissions', {
      method: 'GET',
      ...options,
    }),
  permissionTree: (options: RequestOptions = {}) =>
    request<PermissionTreeRecord[]>('/v1/system/permissions/tree', {
      method: 'GET',
      ...options,
    }),
  departments: (options: RequestOptions = {}) =>
    request<DepartmentRecord[]>('/v1/system/departments', {
      method: 'GET',
      ...options,
    }),
  departmentDetail: (id: number, options: RequestOptions = {}) =>
    request<DepartmentRecord>(`/v1/system/departments/${id}`, {
      method: 'GET',
      ...options,
    }),
  createDepartment: (payload: DepartmentMutationPayload, options: RequestOptions = {}) =>
    request<DepartmentRecord>('/v1/system/departments', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateDepartment: (id: number, payload: DepartmentMutationPayload, options: RequestOptions = {}) =>
    request<DepartmentRecord>(`/v1/system/departments/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteDepartment: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/departments/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  menus: (options: RequestOptions = {}) =>
    request<MenuRecord[]>('/v1/system/menus', {
      method: 'GET',
      ...options,
    }),
  menuDetail: (id: number, options: RequestOptions = {}) =>
    request<MenuRecord>(`/v1/system/menus/${id}`, {
      method: 'GET',
      ...options,
    }),
  createMenu: (payload: MenuMutationPayload, options: RequestOptions = {}) =>
    request<MenuRecord>('/v1/system/menus', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateMenu: (id: number, payload: MenuMutationPayload, options: RequestOptions = {}) =>
    request<MenuRecord>(`/v1/system/menus/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  reorderMenus: (payload: MenuReorderPayload, options: RequestOptions = {}) =>
    request<boolean>('/v1/system/menus/reorder', {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  changeMenuStatus: (id: number, status: string, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/menus/${id}/status`, {
      method: 'PATCH',
      data: { status },
      ...options,
    }),
  deleteMenu: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/system/menus/${id}`, {
      method: 'DELETE',
      ...options,
    }),
};
