import type { PluginManifest } from '@/plugins/types';

export const validatePluginManifest = (input: PluginManifest): PluginManifest => {
  if (!input?.pluginCode || !input?.version || !input?.entry || !Array.isArray(input?.assets)) {
    throw new Error('插件 manifest 缺少必要字段');
  }
  if (!input.assets.includes(input.entry)) {
    throw new Error('插件 manifest 的 entry 必须包含在 assets 中');
  }
  if (input.sharedDeps && !input.sharedDeps.includes('react')) {
    throw new Error('插件 manifest 必须声明 react 共享依赖');
  }
  return input;
};
