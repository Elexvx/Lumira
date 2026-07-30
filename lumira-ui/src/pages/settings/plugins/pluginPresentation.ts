import type { PluginDefinition } from '@/types/api';

type PluginTextFormatter = (id: string, fallback: string) => string;

const BUILTIN_PLUGIN_MESSAGES: Record<string, { name: string; description: string }> = {
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
