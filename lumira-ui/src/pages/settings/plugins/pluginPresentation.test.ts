import { describe, expect, it } from 'vitest';

import enUS from '@/locales/en-US/pagePlugins';
import zhCN from '@/locales/zh-CN/pagePlugins';
import type { PluginDefinition } from '@/types/api';
import { localizeBuiltinPluginDefinition } from './pluginPresentation';

const definition = (pluginCode: string): PluginDefinition => ({
  pluginCode,
  pluginName: 'Manifest name',
  pluginType: 'BUSINESS',
  description: 'Manifest description',
  pluginApiVersion: '1',
  status: 'ACTIVE',
  builtinFlag: 1,
  sortNo: 1,
});

const formatter = (messages: Record<string, string>) => (id: string, fallback: string) =>
  messages[id] || fallback;

describe('localizeBuiltinPluginDefinition', () => {
  it('localizes built-in plugin metadata into Chinese', () => {
    expect(localizeBuiltinPluginDefinition(
      definition('sensitive-words'),
      formatter(zhCN),
    )).toMatchObject({
      pluginName: '敏感词插件',
      description: '提供敏感词词库维护、请求内容拦截和批量导入能力。',
    });

    expect(localizeBuiltinPluginDefinition(
      definition('work-order-feedback'),
      formatter(zhCN),
    )).toMatchObject({
      pluginName: '工单反馈插件',
      description: '支持用户提交富文本反馈，并由管理员跟进处理。',
    });
  });

  it('keeps the English presentation available when English is active', () => {
    expect(localizeBuiltinPluginDefinition(
      definition('sensitive-words'),
      formatter(enUS),
    )).toMatchObject({
      pluginName: 'Sensitive Words Plugin',
      description: 'Provides sensitive word dictionary maintenance, request content blocking, and import capabilities.',
    });
  });

  it('preserves third-party plugin metadata', () => {
    const plugin = definition('custom-plugin');
    expect(localizeBuiltinPluginDefinition(plugin, formatter(zhCN))).toBe(plugin);
  });
});
