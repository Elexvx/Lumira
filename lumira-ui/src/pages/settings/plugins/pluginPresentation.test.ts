import { describe, expect, it } from 'vitest';

import type { PluginDefinition } from '@/types/api';
import { localizeBuiltinPluginDefinition, localizePluginValue } from './pluginPresentation';

const zhCN: Record<string, string> = {
  'page.plugins.builtin.sensitiveWords.name': '敏感词插件',
  'page.plugins.builtin.sensitiveWords.description': '提供敏感词词库维护、请求内容拦截和批量导入能力。',
  'page.plugins.builtin.workOrderFeedback.name': '工单反馈插件',
  'page.plugins.builtin.workOrderFeedback.description': '支持用户提交富文本反馈，并由管理员跟进处理。',
  'page.plugins.value.installed': '已安装',
};

const enUS: Record<string, string> = {
  'page.plugins.builtin.sensitiveWords.name': 'Sensitive Words Plugin',
  'page.plugins.builtin.sensitiveWords.description': 'Provides sensitive word dictionary maintenance, request content blocking, and import capabilities.',
  'page.plugins.builtin.workOrderFeedback.name': 'Work Order Feedback',
  'page.plugins.builtin.workOrderFeedback.description': 'Allows users to submit rich-text feedback and administrators to follow up.',
  'page.plugins.value.success': 'Succeeded',
};

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

describe('localizePluginValue', () => {
  it('localizes known runtime values and normalizes their casing', () => {
    expect(localizePluginValue('installed', formatter(zhCN))).toBe('已安装');
    expect(localizePluginValue('SUCCESS', formatter(enUS))).toBe('Succeeded');
  });

  it('preserves unknown values and renders empty values consistently', () => {
    expect(localizePluginValue('CUSTOM_STATUS', formatter(zhCN))).toBe('CUSTOM_STATUS');
    expect(localizePluginValue(undefined, formatter(zhCN))).toBe('-');
  });
});
