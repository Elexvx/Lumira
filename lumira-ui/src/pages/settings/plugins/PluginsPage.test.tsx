import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { PluginAvailability, PluginDefinition } from '@/types/api';
import { PluginCardsGrid } from './PluginCardsGrid';

vi.mock('@/i18n/formatMessage', () => ({
  formatMessage: ({ defaultMessage }: { defaultMessage: string }) => defaultMessage,
}));

const definitions: PluginDefinition[] = [
  {
    pluginCode: 'sensitive-words',
    pluginName: 'Sensitive words',
    pluginType: 'builtin',
    pluginApiVersion: '1',
    status: 'ENABLED',
    builtinFlag: 1,
    sortNo: 1,
  },
  {
    pluginCode: 'work-order-feedback',
    pluginName: 'Work order feedback',
    pluginType: 'builtin',
    pluginApiVersion: '1',
    status: 'ENABLED',
    builtinFlag: 1,
    sortNo: 2,
  },
];

const availability = new Map<string, PluginAvailability>(definitions.map((plugin) => [
  plugin.pluginCode,
  {
    pluginCode: plugin.pluginCode,
    pluginName: plugin.pluginName,
    version: '1.0.0',
    manifestPath: '/plugins/manifest.json',
  },
]));

describe('PluginCardsGrid', () => {
  it('does not render standalone management actions for injected plugin pages', () => {
    const markup = renderToStaticMarkup(
      <PluginCardsGrid
        isMobile={false}
        loading={false}
        definitions={definitions}
        currentAvailableMap={availability}
        getPreferredEnableVersion={() => ({ version: '1.0.0' })}
        mutationLoading={false}
        canEnable
        canDisable
        onToggleEnable={() => undefined}
        onOpenDetails={() => undefined}
        onUninstall={() => undefined}
      />,
    );

    expect(markup).toContain('Sensitive words');
    expect(markup).toContain('Work order feedback');
    expect(markup.match(/>Details</g)).toHaveLength(2);
    expect(markup).not.toContain('>Manage<');
  });
});
