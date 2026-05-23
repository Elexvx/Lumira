import { enUSIntl, zhCNIntl, type IntlType } from '@ant-design/pro-components';
import { resolveRuntimeLocale } from '@/i18n/locale';

const TABLE_TOOLBAR_LABELS: Record<string, Record<string, string>> = {
  'zh-CN': {
    'tableToolBar.density': '间距',
    'tableToolBar.columnDisplay': '设置展示字段',
    'tableToolBar.columnSetting': '设置展示字段',
  },
  'en-US': {
    'tableToolBar.density': 'Spacing',
    'tableToolBar.columnDisplay': 'Display fields',
    'tableToolBar.columnSetting': 'Display fields',
  },
};

const withTableToolbarLabels = (intl: IntlType, labels: Record<string, string>): IntlType => ({
  ...intl,
  getMessage: (id, defaultMessage) => labels[id] || intl.getMessage(id, defaultMessage),
});

export const resolveProComponentsIntl = () => {
  const locale = resolveRuntimeLocale();
  const baseIntl = locale.startsWith('en') ? enUSIntl : zhCNIntl;
  const labels = locale.startsWith('en') ? TABLE_TOOLBAR_LABELS['en-US'] : TABLE_TOOLBAR_LABELS['zh-CN'];

  return withTableToolbarLabels(baseIntl, labels);
};
