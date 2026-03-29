import { request, type RequestOptions } from '@/services/common/request';
import type {
  MenuNode,
  PluginDefinition,
  PluginRuntimeLog,
  PluginUploadResult,
  PluginVersion,
  TenantPlugin,
} from '@/types/api';

export interface PluginInstallPayload {
  pluginCode: string;
  version: string;
}

export interface PluginEnablePayload {
  tenantId: number;
  pluginCode: string;
  version?: string;
  configJson?: string;
}

export interface PluginDisablePayload {
  tenantId: number;
  pluginCode: string;
}

export interface PluginRollbackPayload {
  pluginCode: string;
  targetVersion: string;
}

export const pluginService = {
  definitions: (options: RequestOptions = {}) =>
    request<PluginDefinition[]>('/v1/plugins/definitions', {
      method: 'GET',
      ...options,
    }),
  versions: (pluginCode: string, options: RequestOptions = {}) =>
    request<PluginVersion[]>(`/v1/plugins/${pluginCode}/versions`, {
      method: 'GET',
      ...options,
    }),
  validation: (pluginCode: string, version: string, options: RequestOptions = {}) =>
    request<string>(`/v1/plugins/${pluginCode}/${version}/validation`, {
      method: 'GET',
      ...options,
    }),
  upload: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<PluginUploadResult>('/v1/plugins/upload', {
      method: 'POST',
      headers: {},
      data: formData,
    });
  },
  install: (payload: PluginInstallPayload, options: RequestOptions = {}) =>
    request<PluginVersion>('/v1/plugins/install', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  upgrade: (payload: PluginInstallPayload, options: RequestOptions = {}) =>
    request<PluginVersion>('/v1/plugins/upgrade', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  rollback: (payload: PluginRollbackPayload, options: RequestOptions = {}) =>
    request<PluginVersion>('/v1/plugins/rollback', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  enable: (payload: PluginEnablePayload, options: RequestOptions = {}) =>
    request<boolean>('/v1/plugins/enable', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  disable: (payload: PluginDisablePayload, options: RequestOptions = {}) =>
    request<boolean>('/v1/plugins/disable', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  runtimeLogs: (pluginCode: string, options: RequestOptions = {}) =>
    request<PluginRuntimeLog[]>(`/v1/plugins/${pluginCode}/logs`, {
      method: 'GET',
      ...options,
    }),
  currentAvailable: (options: RequestOptions = {}) =>
    request<TenantPlugin[]>('/v1/plugins/current/available', {
      method: 'GET',
      ...options,
    }),
  currentMenus: (options: RequestOptions = {}) =>
    request<MenuNode[]>('/v1/plugins/current/menus', {
      method: 'GET',
      ...options,
    }),
  currentPermissions: (options: RequestOptions = {}) =>
    request<string[]>('/v1/plugins/current/permissions', {
      method: 'GET',
      ...options,
    }),
};
