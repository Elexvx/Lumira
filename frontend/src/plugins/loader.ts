import { pluginRegistry } from '@/plugins/registry';
import type { PluginLoadResult, PluginManifest, PluginModule } from '@/plugins/types';
import { message } from '@/theme/antdFeedbackBridge';
import { resolveApiErrorFeedback, resolveHttpStatusFeedback, type FeedbackType } from '@/services/common/errorFeedback';
import { captureAuthRequestSnapshot } from '@/auth/unauthorized';
import { buildUnauthorizedRuntimeState } from '@/auth/unauthorized';
import { performLogout } from '@/auth/sessionLifecycle';
import { shouldSuppressUnauthorizedSideEffects, type AuthRequestSnapshot } from '@/auth/unauthorizedDecision';
import { resolveBuiltinMessage } from '@/i18n/messages';

const getRegisteredPluginModule = (pluginCode: string, version: string): PluginModule | undefined =>
  pluginRegistry.getModule(pluginCode, version);

const cleanupPluginAssets = (pluginCode: string) => {
  document
    .querySelectorAll<HTMLElement>(`[data-plugin-code="${pluginCode}"]`)
    .forEach((element) => {
      element.remove();
    });
};

const buildHeaders = (accessToken: string) => {
  const headers: Record<string, string> = {
    'X-Request-Id': crypto.randomUUID(),
  };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return headers;
};

export interface PluginLoadFeedback {
  type: FeedbackType;
  message: string;
  redirectToLogin?: boolean;
  requestAccessToken?: string;
  authSnapshot?: AuthRequestSnapshot;
}

export class PluginLoadError extends Error {
  type: PluginLoadFeedback['type'];
  redirectToLogin?: boolean;
  requestAccessToken?: string;
  authSnapshot?: AuthRequestSnapshot;

  constructor(
    message: string,
    options: {
      type: PluginLoadFeedback['type'];
      redirectToLogin?: boolean;
      requestAccessToken?: string;
      authSnapshot?: AuthRequestSnapshot;
    } = { type: 'error' },
  ) {
    super(message);
    this.name = 'PluginLoadError';
    this.type = options.type;
    this.redirectToLogin = options.redirectToLogin;
    this.requestAccessToken = options.requestAccessToken;
    this.authSnapshot = options.authSnapshot;
  }
}

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

export const buildPluginLoadError = async (
  response: Response,
  fallbackMessage: string,
  authSnapshot: AuthRequestSnapshot,
) => {
  const payload = await parseJsonResponse(response);
  if (payload && typeof payload === 'object' && typeof payload.code === 'string' && typeof payload.message === 'string') {
    const feedback = resolveApiErrorFeedback(payload, authSnapshot.hasAuthToken);
    return new PluginLoadError(feedback.message, {
      type: feedback.type,
      redirectToLogin: feedback.redirectToLogin,
      requestAccessToken: authSnapshot.accessToken,
      authSnapshot,
    });
  }

  const feedback = resolveHttpStatusFeedback(response.status, authSnapshot.hasAuthToken, fallbackMessage);
  return new PluginLoadError(feedback.message, {
    type: feedback.type,
    redirectToLogin: feedback.redirectToLogin,
    requestAccessToken: authSnapshot.accessToken,
    authSnapshot,
  });
};

export const resolvePluginLoadError = (error: unknown): PluginLoadFeedback => {
  if (error instanceof PluginLoadError) {
    return {
      type: error.type,
      message: error.message,
      redirectToLogin: error.redirectToLogin,
      requestAccessToken: error.requestAccessToken,
      authSnapshot: error.authSnapshot,
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
    message: resolveBuiltinMessage('common.pluginLoadFailed', '插件加载失败，请稍后重试'),
  };
};

export const notifyPluginLoadError = (error: unknown) => {
  const feedback = resolvePluginLoadError(error);
  if (feedback.redirectToLogin) {
    const runtime = buildUnauthorizedRuntimeState();
    if (feedback.authSnapshot && shouldSuppressUnauthorizedSideEffects(feedback.authSnapshot, runtime)) {
      return feedback;
    }
    message[feedback.type](feedback.message);
    void performLogout({ reason: 'forced_expired' });
    return feedback;
  }
  message[feedback.type](feedback.message);
  return feedback;
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
      scriptElement.onerror = () =>
        reject(new Error(`${resolveBuiltinMessage('common.pluginScriptExecutionFailed', '插件脚本执行失败')}: ${key}`));
      document.body.appendChild(scriptElement);
    });
  } finally {
    URL.revokeObjectURL(blobUrl);
  }
};

const injectStyle = async (pluginCode: string, relativePath: string) => {
  const authSnapshot = captureAuthRequestSnapshot();
  const requestAccessToken = authSnapshot.accessToken;
  const response = await fetch(`/api/v1/plugins/current/${pluginCode}/assets/${relativePath}`, {
    headers: buildHeaders(requestAccessToken),
  });
  if (!response.ok) {
    throw await buildPluginLoadError(
      response,
      `${resolveBuiltinMessage('common.pluginStyleLoadFailed', '插件样式加载失败')}: ${relativePath}`,
      authSnapshot,
    );
  }
  const content = await response.text();
  const styleElement = document.createElement('style');
  styleElement.dataset.pluginAsset = `${pluginCode}:${relativePath}`;
  styleElement.dataset.pluginCode = pluginCode;
  styleElement.textContent = content;
  document.head.appendChild(styleElement);
};

const injectScript = async (pluginCode: string, relativePath: string) => {
  const authSnapshot = captureAuthRequestSnapshot();
  const requestAccessToken = authSnapshot.accessToken;
  const response = await fetch(`/api/v1/plugins/current/${pluginCode}/assets/${relativePath}`, {
    headers: buildHeaders(requestAccessToken),
  });
  if (!response.ok) {
    throw await buildPluginLoadError(
      response,
      `${resolveBuiltinMessage('common.pluginScriptLoadFailed', '插件脚本加载失败')}: ${relativePath}`,
      authSnapshot,
    );
  }
  const source = await response.text();
  await executeSource(`${pluginCode}:${relativePath}`, source);
};

const loadPluginManifest = async (pluginCode: string, fetchJson: <T>(url: string) => Promise<T>) => {
  return await fetchJson<PluginManifest>(`/api/v1/plugins/current/${pluginCode}/manifest`);
};

const fetchPluginJson = async <T>(url: string) => {
  const authSnapshot = captureAuthRequestSnapshot();
  const requestAccessToken = authSnapshot.accessToken;
  const response = await fetch(url, {
    headers: buildHeaders(requestAccessToken),
  });
  if (!response.ok) {
    throw await buildPluginLoadError(response, resolveBuiltinMessage('common.pluginResourceLoadFailed', '加载插件资源失败，请稍后重试'), authSnapshot);
  }
  return (await response.json()) as T;
};

const validatePluginManifest = (input: PluginManifest): PluginManifest => {
  if (!input?.pluginCode || !input?.version || !input?.entry || !Array.isArray(input?.assets)) {
    throw new Error(resolveBuiltinMessage('common.pluginManifestMissingFields', '插件 manifest 缺少必要字段'));
  }
  if (!input.assets.includes(input.entry)) {
    throw new Error(resolveBuiltinMessage('common.pluginManifestEntryMissing', '插件 manifest 的 entry 必须包含在 assets 中'));
  }
  if (input.sharedDeps && !input.sharedDeps.includes('react')) {
    throw new Error(resolveBuiltinMessage('common.pluginManifestReactDepRequired', '插件 manifest 必须声明 react 共享依赖'));
  }
  return input;
};

export const loadPlugin = async (pluginCode: string): Promise<PluginLoadResult> => {
  const manifest = validatePluginManifest(await loadPluginManifest(pluginCode, fetchPluginJson));
  const cacheModule = getRegisteredPluginModule(pluginCode, manifest.version);
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
    throw new Error(resolveBuiltinMessage('common.pluginEntryNotRegistered', '插件入口未成功注册'));
  }
  const result = {
    manifest,
    module,
  };
  pluginRegistry.register(result);
  return result;
};

export { cleanupPluginAssets, getRegisteredPluginModule };
