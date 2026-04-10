import React from 'react';
import ReactDOM from 'react-dom/client';
import { message } from 'antd';
import { history } from 'umi';
import { clearAuthSession } from '@/auth/session';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import { resolveApiErrorFeedback, resolveHttpStatusFeedback, type FeedbackType } from '@/services/common/errorFeedback';
import { validatePluginManifest } from '@/plugins/manifest';
import { pluginRegistry } from '@/plugins/registry';
import type { PluginLoadResult, PluginManifest, PluginModule } from '@/plugins/types';

const MANIFEST_URL = (pluginCode: string) => `/api/v1/plugins/current/${pluginCode}/manifest`;
const ASSET_URL = (pluginCode: string, relativePath: string) => `/api/v1/plugins/current/${pluginCode}/assets/${relativePath}`;

window.SaaSSharedDeps = {
  React,
  ReactDOM,
};

export interface PluginLoadFeedback {
  type: FeedbackType;
  message: string;
  redirectToLogin?: boolean;
}

export class PluginLoadError extends Error {
  type: FeedbackType;
  redirectToLogin?: boolean;

  constructor(message: string, options: { type: FeedbackType; redirectToLogin?: boolean } = { type: 'error' }) {
    super(message);
    this.name = 'PluginLoadError';
    this.type = options.type;
    this.redirectToLogin = options.redirectToLogin;
  }
}

export const loadPlugin = async (pluginCode: string): Promise<PluginLoadResult> => {
  const manifest = validatePluginManifest(await fetchJson<PluginManifest>(MANIFEST_URL(pluginCode)));
  const cacheModule = pluginRegistry.getModule(pluginCode, manifest.version);
  if (cacheModule) {
    return {
      manifest,
      module: cacheModule,
    };
  }
  for (const stylePath of manifest.styles ?? []) {
    await injectStyle(pluginCode, stylePath);
  }
  for (const assetPath of manifest.assets) {
    await injectScript(pluginCode, assetPath);
  }
  const module = window.__SAAS_PLUGIN_BUNDLES__?.[`${pluginCode}@${manifest.version}`];
  if (!module) {
    throw new Error('插件入口未成功注册');
  }
  const result = {
    manifest,
    module,
  };
  pluginRegistry.register(result);
  return result;
};

const fetchJson = async <T>(url: string): Promise<T> => {
  const response = await fetch(url, {
    headers: buildHeaders(),
  });
  if (!response.ok) {
    throw await buildLoadError(response, '加载插件资源失败，请稍后重试');
  }
  return (await response.json()) as T;
};

const injectScript = async (pluginCode: string, relativePath: string) => {
  const response = await fetch(ASSET_URL(pluginCode, relativePath), {
    headers: buildHeaders(),
  });
  if (!response.ok) {
    throw await buildLoadError(response, `插件脚本加载失败: ${relativePath}`);
  }
  const source = await response.text();
  await executeSource(`${pluginCode}:${relativePath}`, source);
};

const injectStyle = async (pluginCode: string, relativePath: string) => {
  const response = await fetch(ASSET_URL(pluginCode, relativePath), {
    headers: buildHeaders(),
  });
  if (!response.ok) {
    throw await buildLoadError(response, `插件样式加载失败: ${relativePath}`);
  }
  const content = await response.text();
  const styleElement = document.createElement('style');
  styleElement.dataset.pluginAsset = `${pluginCode}:${relativePath}`;
  styleElement.dataset.pluginCode = pluginCode;
  styleElement.textContent = content;
  document.head.appendChild(styleElement);
};

const executeSource = async (key: string, source: string) => {
  const blob = new Blob([source], { type: 'text/javascript' });
  const blobUrl = URL.createObjectURL(blob);
  try {
    await new Promise<void>((resolve, reject) => {
      const scriptElement = document.createElement('script');
      scriptElement.async = true;
      scriptElement.src = blobUrl;
      scriptElement.dataset.pluginAsset = key;
      scriptElement.dataset.pluginCode = key.split(':')[0];
      scriptElement.onload = () => resolve();
      scriptElement.onerror = () => reject(new Error(`插件脚本执行失败: ${key}`));
      document.body.appendChild(scriptElement);
    });
  } finally {
    URL.revokeObjectURL(blobUrl);
  }
};

const buildLoadError = async (response: Response, fallbackMessage: string) => {
  const payload = await parseJsonResponse(response);
  if (payload && typeof payload === 'object' && typeof payload.code === 'string' && typeof payload.message === 'string') {
    const feedback = resolveApiErrorFeedback(payload, tokenManager.hasToken());
    return new PluginLoadError(feedback.message, {
      type: feedback.type,
      redirectToLogin: feedback.redirectToLogin,
    });
  }

  const feedback = resolveHttpStatusFeedback(response.status, tokenManager.hasToken(), fallbackMessage);
  return new PluginLoadError(feedback.message, {
    type: feedback.type,
    redirectToLogin: feedback.redirectToLogin,
  });
};

const parseJsonResponse = async (response: Response) => {
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return null;
  }

  try {
    return await response.clone().json();
  } catch {
    return null;
  }
};

const buildHeaders = () => {
  const headers: Record<string, string> = {
    'X-Request-Id': crypto.randomUUID(),
  };
  const accessToken = tokenManager.getAccessToken();
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  const tenantId = tenantContext.getTenantId();
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  return headers;
};

export const notifyPluginLoadError = (error: unknown) => {
  const feedback = resolvePluginLoadError(error);
  if (feedback.redirectToLogin) {
    clearAuthSession();
    history.replace('/user/login');
  }
  message[feedback.type](feedback.message);
  return feedback;
};

export const resolvePluginLoadError = (error: unknown): PluginLoadFeedback => {
  if (error instanceof PluginLoadError) {
    return {
      type: error.type,
      message: error.message,
      redirectToLogin: error.redirectToLogin,
    };
  }

  if (error instanceof Error && error.message) {
    return {
      type: 'error',
      message: error.message,
    };
  }

  return {
    type: 'error',
    message: '插件加载失败，请稍后重试',
  };
};

export const getRegisteredPluginModule = (pluginCode: string, version: string): PluginModule | undefined =>
  pluginRegistry.getModule(pluginCode, version);

export const cleanupPluginAssets = (pluginCode: string) => {
  document
    .querySelectorAll<HTMLElement>(`[data-plugin-code="${pluginCode}"]`)
    .forEach((element) => {
      element.remove();
    });
};
