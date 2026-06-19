import { pluginRegistry } from '@/plugins/registry';
import { cleanupPluginAssets, loadPlugin } from '@/plugins/loader';
import type { PluginContext } from '@/plugins/types';

export const mountPlugin = async (
  pluginCode: string,
  container: HTMLElement,
  context: PluginContext,
) => {
  const result = await loadPlugin(pluginCode);
  await result.module.mount(container, context);
  pluginRegistry.markMounted(pluginCode, result.manifest.version, container);
  return result;
};

export const unmountPlugin = async (pluginCode: string, version: string, container: HTMLElement) => {
  const module = pluginRegistry.getModule(pluginCode, version);
  if (!module?.unmount) {
    container.innerHTML = '';
    pluginRegistry.clearMounted(pluginCode, version);
    cleanupPluginAssets(pluginCode);
    return;
  }
  await module.unmount(container);
  pluginRegistry.clearMounted(pluginCode, version);
  cleanupPluginAssets(pluginCode);
};
