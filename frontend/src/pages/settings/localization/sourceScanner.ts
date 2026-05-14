import { backendRouteMeta } from '@/routes/meta';
import zhCN from '@/locales/zh-CN';
import enUS from '@/locales/en-US';
import type { LocalizationSyncPayload } from '@/services/localization';

const NAMESPACE_LABELS: Record<string, string> = {
  common: '公共',
  nav: '导航',
  page: '页面',
  message: '消息',
  theme: '主题',
  tenant: '平台',
  auth: '认证',
  system: '系统',
  app: '应用',
};

const SOURCE_REF_BY_NAMESPACE: Record<string, string> = {
  nav: 'frontend/src/routes/meta.ts',
  common: 'frontend/src/locales/zh-CN.ts',
  page: 'frontend/src/locales/zh-CN.ts',
  message: 'frontend/src/locales/zh-CN.ts',
  theme: 'frontend/src/locales/zh-CN.ts',
  tenant: 'frontend/src/locales/zh-CN.ts',
  auth: 'frontend/src/locales/zh-CN.ts',
  system: 'frontend/src/locales/zh-CN.ts',
  app: 'frontend/src/locales/zh-CN.ts',
};

const routeKeySet = new Set(backendRouteMeta.map((item) => item.name).filter(Boolean));

const resolveNamespaceCode = (key: string) => key.split('.')[0] || 'common';

const resolveNamespaceName = (namespaceCode: string) => NAMESPACE_LABELS[namespaceCode] || namespaceCode;

const resolveSourceType = (key: string) => (key.startsWith('nav.') ? 'ROUTE' : 'UI');

const resolveSourceRef = (key: string, namespaceCode: string) => {
  if (routeKeySet.has(key) || namespaceCode === 'nav') {
    return 'frontend/src/routes/meta.ts';
  }

  return SOURCE_REF_BY_NAMESPACE[namespaceCode] || 'frontend/src/locales/zh-CN.ts';
};

export const buildLocalizationSyncPayload = (): LocalizationSyncPayload => {
  const zhMessages = zhCN as Record<string, string>;
  const enMessages = enUS as Record<string, string>;
  const keys = new Set<string>([...Object.keys(zhMessages), ...Object.keys(enMessages)]);

  const items = Array.from(keys)
    .sort()
    .map((key) => {
      const namespaceCode = resolveNamespaceCode(key);
      const translations: Record<string, string> = {};
      if (zhMessages[key]) {
        translations['zh-CN'] = zhMessages[key];
      }
      if (enMessages[key]) {
        translations['en-US'] = enMessages[key];
      }

      return {
        namespaceCode,
        namespaceName: resolveNamespaceName(namespaceCode),
        messageKey: key,
        defaultMessage: zhMessages[key] || enMessages[key] || key,
        sourceLocale: 'zh-CN',
        sourceType: resolveSourceType(key),
        sourceRef: resolveSourceRef(key, namespaceCode),
        status: 'ENABLED',
        translations,
      };
    });

  return {
    sourceLocale: 'zh-CN',
    items,
  };
};
