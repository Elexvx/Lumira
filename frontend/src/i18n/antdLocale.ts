import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

export const resolveAntdLocale = () => {
  const locale = normalizeLocale(getLocale());
  return locale.startsWith('en') ? enUS : zhCN;
};
