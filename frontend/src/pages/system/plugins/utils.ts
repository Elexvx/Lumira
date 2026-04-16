import type { PluginDefinition, PluginVersion, TenantPlugin } from '@/types/api';

export const isInstalledVersion = (installStatus?: string) => (installStatus || '').toUpperCase() === 'INSTALLED';

export const getPreferredEnableVersion = (pluginCode: string, versionMap: Record<string, PluginVersion[]>) => {
  const versions = versionMap[pluginCode] || [];
  return (
    versions.find((item) => isInstalledVersion(item.installStatus) && item.isActive === 1) ||
    versions.find((item) => isInstalledVersion(item.installStatus)) ||
    versions.find((item) => item.isActive === 1)
  );
};

export const buildAvailablePluginMap = (availablePlugins: TenantPlugin[]) =>
  new Map(availablePlugins.map((item) => [item.pluginCode, item]));

export const filterPluginDefinitions = (definitions: PluginDefinition[], keyword: string) => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  return definitions.filter((item) => {
    if (!normalizedKeyword) {
      return true;
    }
    return item.pluginName.toLowerCase().includes(normalizedKeyword) || item.pluginCode.toLowerCase().includes(normalizedKeyword);
  });
};
