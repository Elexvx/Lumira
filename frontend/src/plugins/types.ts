import type { CurrentUser } from '@/types/api';

export interface PluginManifest {
  pluginCode: string;
  version: string;
  entry: string;
  assets: string[];
  styles?: string[];
  routes?: string[];
  sharedDeps?: string[];
}

export interface PluginContext {
  pluginCode: string;
  version: string;
  routePath: string;
  currentUser?: CurrentUser;
  requestId: string;
}

export interface PluginModule {
  mount: (container: HTMLElement, context: PluginContext) => void | Promise<void>;
  unmount?: (container: HTMLElement) => void | Promise<void>;
  getMenus?: () => unknown[];
  getRoutes?: () => string[];
  getPermissions?: () => string[];
}

export interface PluginLoadResult {
  manifest: PluginManifest;
  module: PluginModule;
}

declare global {
  interface Window {
    __SAAS_PLUGIN_BUNDLES__?: Record<string, PluginModule>;
    SaaSSharedDeps?: Record<string, unknown>;
  }
}
