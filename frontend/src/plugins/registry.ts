import type { PluginLoadResult, PluginModule } from '@/plugins/types';

class PluginRegistry {
  private manifests = new Map<string, PluginLoadResult['manifest']>();
  private modules = new Map<string, PluginModule>();
  private mounted = new Map<string, HTMLElement>();

  getKey(pluginCode: string, version: string) {
    return `${pluginCode}@${version}`;
  }

  register(loadResult: PluginLoadResult) {
    const key = this.getKey(loadResult.manifest.pluginCode, loadResult.manifest.version);
    this.manifests.set(key, loadResult.manifest);
    this.modules.set(key, loadResult.module);
  }

  getModule(pluginCode: string, version: string) {
    return this.modules.get(this.getKey(pluginCode, version));
  }

  getManifest(pluginCode: string, version: string) {
    return this.manifests.get(this.getKey(pluginCode, version));
  }

  markMounted(pluginCode: string, version: string, container: HTMLElement) {
    this.mounted.set(this.getKey(pluginCode, version), container);
  }

  clearMounted(pluginCode: string, version: string) {
    this.mounted.delete(this.getKey(pluginCode, version));
  }

  getMounted(pluginCode: string, version: string) {
    return this.mounted.get(this.getKey(pluginCode, version));
  }
}

export const pluginRegistry = new PluginRegistry();
