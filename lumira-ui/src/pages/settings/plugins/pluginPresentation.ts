import type { PluginDefinition } from '@/types/api';

type PluginTextFormatter = (id: string, fallback: string) => string;

const PLUGIN_VALUE_MESSAGES: Record<string, string> = {
  ACTIVE: 'page.plugins.value.active',
  AUTO: 'page.plugins.value.auto',
  DISABLE: 'page.plugins.value.disable',
  DISABLED: 'page.plugins.value.disabled',
  ENABLE: 'page.plugins.value.enable',
  ENABLED: 'page.plugins.value.enabled',
  FAILED: 'page.plugins.value.failed',
  HEALTHY: 'page.plugins.value.healthy',
  INSTALL: 'page.plugins.value.install',
  INSTALLED: 'page.plugins.value.installed',
  LOADED: 'page.plugins.value.loaded',
  MIGRATED: 'page.plugins.value.migrated',
  NONE: 'page.plugins.value.none',
  READY: 'page.plugins.value.ready',
  REMOVED: 'page.plugins.value.removed',
  ROLLBACK: 'page.plugins.value.rollback',
  ROLLED_BACK: 'page.plugins.value.rolledBack',
  SUCCESS: 'page.plugins.value.success',
  UNINSTALL: 'page.plugins.value.uninstall',
  UNLOADED: 'page.plugins.value.unloaded',
  UPGRADE: 'page.plugins.value.upgrade',
  UPLOAD: 'page.plugins.value.upload',
  VERIFIED: 'page.plugins.value.verified',
};

const BUILTIN_PLUGIN_MESSAGES: Record<string, { name: string; description: string }> = {
  'builtin-mock-payment': {
    name: 'page.plugins.builtin.builtinMockPayment.name',
    description: 'page.plugins.builtin.builtinMockPayment.description',
  },
  'builtin-mock-sms': {
    name: 'page.plugins.builtin.builtinMockSms.name',
    description: 'page.plugins.builtin.builtinMockSms.description',
  },
  'sensitive-words': {
    name: 'page.plugins.builtin.sensitiveWords.name',
    description: 'page.plugins.builtin.sensitiveWords.description',
  },
  'work-order-feedback': {
    name: 'page.plugins.builtin.workOrderFeedback.name',
    description: 'page.plugins.builtin.workOrderFeedback.description',
  },
};

export const localizeBuiltinPluginDefinition = (
  plugin: PluginDefinition,
  format: PluginTextFormatter,
): PluginDefinition => {
  const messages = BUILTIN_PLUGIN_MESSAGES[plugin.pluginCode];
  if (!messages) {
    return plugin;
  }

  return {
    ...plugin,
    pluginName: format(messages.name, plugin.pluginName),
    description: format(messages.description, plugin.description || ''),
  };
};

export const localizePluginValue = (
  value: string | null | undefined,
  format: PluginTextFormatter,
) => {
  const normalizedValue = value?.trim();
  if (!normalizedValue) {
    return '-';
  }

  const messageId = PLUGIN_VALUE_MESSAGES[normalizedValue.toUpperCase()];
  return messageId ? format(messageId, normalizedValue) : normalizedValue;
};
