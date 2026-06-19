import { HistoryOutlined, PlusOutlined, SaveOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Form, Input, List, Select, Space, Spin, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { createElement, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest, DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { TableActionBar } from '@/features/table/TableActionBar';
import { loadRuntimeLocalizationBundle } from '@/i18n/runtimeLocalization';
import { backendRouteMeta } from '@/routes/meta';
import { request } from '@/services/common/request';
import zhCN from '@/locales/zh-CN';
import enUS from '@/locales/en-US';
import type {
  LocalizationSyncPayload,
  LocalizationEntryPayload,
} from '@/services/localization/types';
import type { LocalizationLanguage, LocalizationNamespace, LocalizationRelease } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { copyTextToClipboard } from '@/utils/clipboard';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const fallbackLanguages: LocalizationLanguage[] = [
  { id: -1, localeCode: 'zh-CN', languageName: 'Simplified Chinese', nativeName: '简体中文', status: 'ENABLED', defaultLanguage: true },
  { id: -2, localeCode: 'en-US', languageName: 'English', nativeName: 'English', status: 'ENABLED' },
];

const DEFAULT_LOCALE = 'zh-CN';
const PINNED_LOCALES = ['zh-CN', 'en-US'];

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const localeLabel = (language: LocalizationLanguage) => {
  if (language.localeCode === 'zh-CN') {
    return t('中文', 'Chinese');
  }
  if (language.localeCode === 'en-US') {
    return t('英文', 'English');
  }
  return language.nativeName || language.languageName || language.localeCode;
};

const sortLanguages = (items: LocalizationLanguage[]) =>
  [...items].sort((left, right) => {
    const leftPinned = PINNED_LOCALES.indexOf(left.localeCode);
    const rightPinned = PINNED_LOCALES.indexOf(right.localeCode);
    if (leftPinned !== -1 || rightPinned !== -1) {
      return (leftPinned === -1 ? 999 : leftPinned) - (rightPinned === -1 ? 999 : rightPinned);
    }
    return (left.sortNo ?? 0) - (right.sortNo ?? 0) || left.localeCode.localeCompare(right.localeCode);
  });

const EntryStatusOptions = [
  { label: t('启用', 'Enabled'), value: 'ENABLED' },
  { label: t('停用', 'Disabled'), value: 'DISABLED' },
];

type EntryDrafts = Record<number, Record<string, string>>;

const normalizeTranslations = (translations?: Record<string, string | null | undefined>) =>
  Object.fromEntries(Object.entries(translations || {}).map(([key, value]) => [key, typeof value === 'string' ? value : '']));

const translationValue = (record: import('@/types/api').LocalizationEntry, localeCode: string) => {
  const value = record.translations?.[localeCode];
  if (value != null) {
    return value;
  }
  return record.sourceLocale === localeCode ? record.defaultMessage : '';
};

const NAMESPACE_LABELS: Record<string, string> = {
  common: t('公共', 'Common'),
  nav: t('导航', 'Navigation'),
  page: t('页面', 'Pages'),
  message: t('消息', 'Messages'),
  theme: t('主题', 'Theme'),
  tenant: t('平台', 'Platform'),
  auth: t('认证', 'Auth'),
  system: t('系统', 'System'),
  app: t('应用', 'App'),
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


const buildLocalizationSyncPayload = (): LocalizationSyncPayload => {
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

const LocalizationPage = () => {
  const { actionPermission, responsive, searchConfig, buttonSize } = usePagePermissionActions();
  const tableActionRef = useRef<ActionType>(null);
  const searchValuesRef = useRef<{ namespaceCode?: string }>({ namespaceCode: 'all' });
  const initialSyncAttemptedRef = useRef(false);
  const [languages, setLanguages] = useState<LocalizationLanguage[]>([]);
  const [namespaces, setNamespaces] = useState<LocalizationNamespace[]>([]);
  const [releases, setReleases] = useState<LocalizationRelease[]>([]);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [entryDrawerOpen, setEntryDrawerOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<import('@/types/api').LocalizationEntry | null>(null);
  const [entrySaving, setEntrySaving] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [savingEntryId, setSavingEntryId] = useState<number | null>(null);
  const [drafts, setDrafts] = useState<EntryDrafts>({});
  const [entryForm] = Form.useForm<LocalizationEntryPayload>();
  const canSyncLocalization = actionPermission.can('localization:sync');
  const languageColumns = useMemo(() => {
    const enabled = languages.filter((item) => item.status !== 'DISABLED');
    const merged = new Map<string, LocalizationLanguage>();
    for (const language of [...fallbackLanguages, ...enabled]) {
      merged.set(language.localeCode, language);
    }
    return sortLanguages(Array.from(merged.values()));
  }, [languages]);
  const primaryLocale = languageColumns[0]?.localeCode || DEFAULT_LOCALE;
  const languageOptions = useMemo(
    () =>
      languageColumns.map((language) => ({
        label: `${localeLabel(language)} (${language.localeCode})`,
        value: language.localeCode,
      })),
    [languageColumns],
  );
  const namespaceOptions = useMemo(
    () => [{ label: t('全部模块', 'All modules'), value: 'all' }].concat(
      namespaces.map((item) => ({
        label: `${item.namespaceName} (${item.namespaceCode})`,
        value: item.namespaceCode,
      })),
    ),
    [namespaces],
  );
  const refreshMeta = useCallback(async () => {
    setLoadingMeta(true);
    try {
      const [languageList, namespaceList, releaseList] = await Promise.all([
        request<LocalizationLanguage[]>('/v1/localization/languages', {
          method: 'GET',
          ...API_OPTS.SILENT_NO_REDIRECT,
        }),
        request<LocalizationNamespace[]>('/v1/localization/namespaces', {
          method: 'GET',
          params: { localeCode: primaryLocale },
          ...API_OPTS.SILENT_NO_REDIRECT,
        }),
        request<LocalizationRelease[]>('/v1/localization/releases', {
          method: 'GET',
          params: { localeCode: primaryLocale },
          ...API_OPTS.SILENT_NO_REDIRECT,
        }),
      ]);
      setLanguages(languageList);
      setNamespaces(namespaceList);
      setReleases(releaseList);
    } catch (error) {
      showErrorMessage(error, t('本地化数据加载失败', 'Failed to load localization data'));
    } finally {
      setLoadingMeta(false);
    }
  }, [primaryLocale]);
  useEffect(() => {
    void refreshMeta();
  }, [refreshMeta]);
  const getDraftValue = useCallback(
    (record: import('@/types/api').LocalizationEntry, localeCode: string) => drafts[record.id]?.[localeCode] ?? translationValue(record, localeCode),
    [drafts],
  );
  const hasDraft = useCallback((record: import('@/types/api').LocalizationEntry) => Boolean(drafts[record.id] && Object.keys(drafts[record.id]).length > 0), [drafts]);
  const changeDraft = useCallback((record: import('@/types/api').LocalizationEntry, localeCode: string, value: string) => {
    setDrafts((current) => ({
      ...current,
      [record.id]: {
        ...current[record.id],
        [localeCode]: value,
      },
    }));
  }, []);
  const refreshBundles = useCallback(async () => {
    await Promise.all(languageColumns.map((language) => loadRuntimeLocalizationBundle(language.localeCode)));
    tableActionRef.current?.reload();
    await refreshMeta();
  }, [languageColumns, refreshMeta]);
  const saveRow = useCallback(
    async (record: import('@/types/api').LocalizationEntry) => {
      const translations = normalizeTranslations({
        ...(record.translations || {}),
        ...Object.fromEntries(languageColumns.map((language) => [language.localeCode, getDraftValue(record, language.localeCode)])),
      });
      setSavingEntryId(record.id);
      try {
        await request<import('@/types/api').LocalizationEntry>(
          `/v1/localization/entries/${record.id}`,
          {
            method: 'PUT',
            data: {
              namespaceCode: record.namespaceCode,
              messageKey: record.messageKey,
              defaultMessage: translations[record.sourceLocale] || translations[DEFAULT_LOCALE] || record.defaultMessage || record.messageKey,
              sourceLocale: record.sourceLocale || DEFAULT_LOCALE,
              sourceType: record.sourceType || 'UI',
              sourceRef: record.sourceRef || undefined,
              status: record.status,
              translations,
            },
            ...API_OPTS.NO_REDIRECT,
          },
        );
        setDrafts((current) => {
          const next = { ...current };
          delete next[record.id];
          return next;
        });
        message.success(t('已保存', 'Saved'));
        await refreshBundles();
      } finally {
        setSavingEntryId(null);
      }
    },
    [getDraftValue, languageColumns, refreshBundles],
  );
  const copyKey = useCallback(async (messageKey: string) => {
    await copyTextToClipboard(messageKey);
    message.success(t('已复制', 'Copied'));
  }, []);
  const openEntryDrawer = useCallback(
    (record?: import('@/types/api').LocalizationEntry | null) => {
      setEditingEntry(record || null);
      entryForm.resetFields();
      const currentNamespaceCode = searchValuesRef.current.namespaceCode;
      entryForm.setFieldsValue({
        namespaceCode: record?.namespaceCode || (currentNamespaceCode === 'all' ? undefined : currentNamespaceCode),
        messageKey: record?.messageKey || '',
        defaultMessage: record?.defaultMessage || '',
        sourceLocale: record?.sourceLocale || DEFAULT_LOCALE,
        sourceType: record?.sourceType || 'UI',
        sourceRef: record?.sourceRef || '',
        status: record?.status || 'ENABLED',
        translations: normalizeTranslations(record?.translations),
      });
      setEntryDrawerOpen(true);
    },
    [entryForm],
  );
  const saveEntry = useCallback(async () => {
    setEntrySaving(true);
    try {
      const values = await entryForm.validateFields();
      const translations = normalizeTranslations(values.translations);
      const sourceLocale = values.sourceLocale || DEFAULT_LOCALE;
      const payload: LocalizationEntryPayload = {
        namespaceCode: values.namespaceCode,
        messageKey: values.messageKey,
        defaultMessage: values.defaultMessage || translations[sourceLocale] || translations[DEFAULT_LOCALE] || values.messageKey,
        sourceLocale,
        sourceType: values.sourceType || 'UI',
        sourceRef: values.sourceRef,
        status: values.status || 'ENABLED',
        translations,
      };
      if (editingEntry) {
        await request<import('@/types/api').LocalizationEntry>(`/v1/localization/entries/${editingEntry.id}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
      } else {
        await request<import('@/types/api').LocalizationEntry>('/v1/localization/entries', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
      }
      message.success(t('已保存', 'Saved'));
      setEntryDrawerOpen(false);
      setEditingEntry(null);
      await refreshBundles();
    } finally {
      setEntrySaving(false);
    }
  }, [editingEntry, entryForm, refreshBundles]);
  const syncLocalizationSource = useCallback(
    () =>
      request<import('@/types/api').LocalizationSyncResult>('/v1/localization/sync', {
        method: 'POST',
        data: buildLocalizationSyncPayload(),
        autoRedirectOnUnauthorized: false,
        timeoutMs: 60000,
      }),
    [],
  );
  const syncEntries = useCallback(async () => {
    setSyncing(true);
    try {
      await syncLocalizationSource();
      message.success(t('已同步', 'Synced'));
      await refreshBundles();
    } finally {
      setSyncing(false);
    }
  }, [refreshBundles, syncLocalizationSource]);
  const publishEntries = useCallback(async () => {
    setPublishing(true);
    try {
      await Promise.all(
        languageColumns.map((language) =>
          request<LocalizationRelease>('/v1/localization/publish', {
            method: 'POST',
            data: { localeCode: language.localeCode, note: t('本地化中心发布', 'Localization center release') },
            autoRedirectOnUnauthorized: false,
            timeoutMs: 30000,
          }),
        ),
      );
      message.success(t('已发布', 'Published'));
      await refreshBundles();
    } finally {
      setPublishing(false);
    }
  }, [languageColumns, refreshBundles]);
  const openHistoryDrawer = useCallback(() => setHistoryDrawerOpen(true), []);
  const columns = useMemo<ProColumns<import('@/types/api').LocalizationEntry>[]>(
    () => [
      {
        title: t('当前语言', 'Current locale'),
        dataIndex: 'localeCode',
        hideInTable: true,
        valueType: 'select',
        initialValue: primaryLocale,
        fieldProps: {
          options: languageColumns.map((language) => ({
            label: `${localeLabel(language)} (${language.localeCode})`,
            value: language.localeCode,
          })),
          disabled: true,
        },
        search: {
          transform: (value: string) => ({ localeCode: value || primaryLocale }),
        },
      },
      {
        title: t('模块', 'Module'),
        dataIndex: 'namespaceCode',
        hideInTable: true,
        valueType: 'select',
        initialValue: 'all',
        fieldProps: {
          options: namespaceOptions,
          showSearch: true,
          optionFilterProp: 'label',
        },
        search: {
          transform: (value: string) => ({ namespaceCode: value === 'all' ? undefined : value }),
        },
      },
      {
        title: t('关键字', 'Keyword'),
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: {
          allowClear: true,
          placeholder: t('搜索键名、原文或来源', 'Search key, source text, or source'),
        },
      },
      {
        title: t('翻译状态', 'Translation status'),
        dataIndex: 'translationStatus',
        hideInTable: true,
        valueType: 'select',
        initialValue: 'all',
        valueEnum: {
          all: { text: t('全部', 'All') },
          PENDING: { text: t('待翻译', 'Pending translation') },
        },
        search: {
          transform: (value: string) => ({ translationStatus: value === 'all' ? undefined : value }),
        },
      },
      {
        title: '',
        width: 'var(--saas-spacing-64)',
        align: 'center',
        render: (_: unknown, __: import('@/types/api').LocalizationEntry, index: number) => index + 1,
      },
      {
        title: t('标识符', 'Key'),
        dataIndex: 'messageKey',
        width: 'var(--saas-spacing-360)',
        fixed: responsive.isMobile ? undefined : 'left',
        ellipsis: true,
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) => createElement(Typography.Text, { copyable: true }, record.messageKey),
      },
      ...languageColumns.map<ProColumns<import('@/types/api').LocalizationEntry>>((language) => ({
        title: localeLabel(language),
        dataIndex: ['translations', language.localeCode],
        width: 'var(--saas-spacing-320)',
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) =>
          createElement(Input.TextArea, {
            value: getDraftValue(record, language.localeCode),
            rows: 2,
            disabled: !hasDraft(record) || savingEntryId === record.id,
            onChange: (event) => changeDraft(record, language.localeCode, event.target.value),
          }),
      })),
      {
        title: t('模块', 'Module'),
        width: 'var(--saas-spacing-150)',
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) => createElement(Tag, null, record.namespaceName || record.namespaceCode),
      },
      {
        title: t('操作', 'Actions'),
        valueType: 'option',
        width: 'var(--saas-spacing-210)',
        fixed: responsive.isMobile ? undefined : 'right',
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) =>
          createElement(TableActionBar, {
            isMobile: responsive.isMobile,
            items: actionPermission.buildTableActions([
              {
                key: 'save',
                label: t('保存', 'Save'),
                permission: 'localization:update',
                disabled: !hasDraft(record) || savingEntryId === record.id,
                onClick: () => void saveRow(record),
              },
              {
                key: 'edit',
                label: t('编辑', 'Edit'),
                permission: 'localization:update',
                onClick: () => openEntryDrawer(record),
              },
              {
                key: 'copy',
                label: t('复制标识符', 'Copy key'),
                onClick: async () => {
                  await copyKey(record.messageKey);
                },
              },
            ]),
          }),
      },
    ],
    [
      actionPermission,
      changeDraft,
      copyKey,
      getDraftValue,
      hasDraft,
      languageColumns,
      namespaceOptions,
      openEntryDrawer,
      primaryLocale,
      responsive.isMobile,
      saveRow,
      savingEntryId,
    ],
  );
  const requestEntries = useCallback(
    async (params: Record<string, unknown>, sorter: Record<string, unknown> = {}) => {
      const localeCode = typeof params.localeCode === 'string' && params.localeCode ? params.localeCode : primaryLocale;
      const namespaceValue = typeof params.namespaceCode === 'string' ? params.namespaceCode : undefined;
      const namespaceCode = namespaceValue && namespaceValue !== 'all' ? namespaceValue : undefined;
      const keyword = typeof params.keyword === 'string' && params.keyword.trim() ? params.keyword.trim() : undefined;
      const translationStatusValue = typeof params.translationStatus === 'string' ? params.translationStatus : undefined;
      const translationStatus = translationStatusValue && translationStatusValue !== 'all' ? translationStatusValue : undefined;
      searchValuesRef.current = { namespaceCode: namespaceCode || 'all' };

      const pageNo = Number(params.current) || 1;
      const pageSize = Number(params.pageSize) || DEFAULT_TABLE_PAGE_SIZE;
      const sortField = Object.keys(sorter || {}).find((key) => ['ascend', 'descend'].includes(String((sorter as Record<string, unknown>)[key]))) || undefined;
      const sortOrder = Object.values(sorter || {}).find((value) => value === 'ascend' || value === 'descend') as string | undefined;
      const requestParams = {
        localeCode,
        namespaceCode,
        keyword,
        translationStatus,
        pageNo,
        pageSize,
        sortField,
        sortOrder,
      };
      const result = await request<import('@/types/api').PagedResult<import('@/types/api').LocalizationEntry>>('/v1/localization/entries', {
        method: 'GET',
        params: requestParams,
        ...API_OPTS.SILENT_NO_REDIRECT,
      });

      const shouldInitialize =
        !initialSyncAttemptedRef.current &&
        canSyncLocalization &&
        pageNo === 1 &&
        !namespaceCode &&
        !keyword &&
        !translationStatus &&
        result.total === 0;

      if (!shouldInitialize) {
        return result;
      }

      initialSyncAttemptedRef.current = true;
      setSyncing(true);
      try {
        await syncLocalizationSource();
        await refreshMeta();
        return request<import('@/types/api').PagedResult<import('@/types/api').LocalizationEntry>>('/v1/localization/entries', {
          method: 'GET',
          params: requestParams,
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
      } finally {
        setSyncing(false);
      }
    },
    [canSyncLocalization, primaryLocale, refreshMeta, syncLocalizationSource],
  );
  const tableRequest = useMemo(() => buildTableRequest(requestEntries), [requestEntries]);
  const toolbarActions = actionPermission.buildToolbarActions([
    {
      value: createElement(Button, { key: 'sync', size: buttonSize, icon: createElement(SyncOutlined, {}), loading: syncing, disabled: !canSyncLocalization, onClick: () => void syncEntries() }, t('同步', 'Sync')),
    },
    {
      value: createElement(Button, { key: 'publish', type: 'primary', size: buttonSize, icon: createElement(SaveOutlined, {}), loading: publishing, disabled: !actionPermission.can('localization:publish'), onClick: () => void publishEntries() }, t('发布', 'Publish')),
    },
    {
      value: createElement(Button, { key: 'create', type: 'primary', size: buttonSize, icon: createElement(PlusOutlined, {}), disabled: !actionPermission.can('localization:create'), onClick: () => openEntryDrawer() }, t('新增词条', 'Add entry')),
    },
    {
      value: createElement(Button, { key: 'history', size: buttonSize, icon: createElement(HistoryOutlined, {}), onClick: openHistoryDrawer }, t('版本历史', 'Version history')),
    },
  ]);

  return (
    <ManagementPage title={t('本地化', 'Localization')}>
      <ManagementPageBody>
        <Spin spinning={loadingMeta}>
          <ManagementTable<import('@/types/api').LocalizationEntry>
            actionRef={tableActionRef}
            rowKey="id"
            columns={columns}
            isMobile={responsive.isMobile}
            search={searchConfig}
            request={tableRequest}
            toolBarRender={() => toolbarActions}
          />
        </Spin>
      </ManagementPageBody>

      <ManagementDrawer
        title={editingEntry ? t('编辑词条', 'Edit entry') : t('新增词条', 'Add entry')}
        open={entryDrawerOpen}
        onClose={() => setEntryDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: t('取消', 'Cancel'), onClick: () => setEntryDrawerOpen(false) },
          { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: entrySaving, onClick: () => void saveEntry() },
        ]}
      >
        <Form form={entryForm} layout="vertical">
          <Form.Item name="namespaceCode" label={t('模块', 'Module')} rules={[{ required: true }]}>
            <Select options={namespaceOptions.filter((item) => item.value !== 'all')} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="messageKey" label={t('标识符', 'Key')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="defaultMessage" label={t('默认文案', 'Default text')} rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          {languageOptions.map((language) => (
            <Form.Item key={language.value} name={['translations', language.value]} label={language.label}>
              <Input.TextArea rows={3} />
            </Form.Item>
          ))}
          <Form.Item name="sourceLocale" label={t('源语言', 'Source locale')}>
            <Select options={languageOptions} />
          </Form.Item>
          <Form.Item name="sourceType" label={t('来源类型', 'Source type')}>
            <Select options={[{ label: 'UI', value: 'UI' }, { label: 'ROUTE', value: 'ROUTE' }, { label: 'BACKEND', value: 'BACKEND' }, { label: 'TEMPLATE', value: 'TEMPLATE' }]} />
          </Form.Item>
          <Form.Item name="sourceRef" label={t('来源', 'Source')}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label={t('状态', 'Status')}>
            <Select options={EntryStatusOptions} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={t('版本历史', 'Version history')} open={historyDrawerOpen} onClose={() => setHistoryDrawerOpen(false)}>
        <List
          dataSource={releases}
          locale={{ emptyText: t('暂无发布记录', 'No release records yet') }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <span>{`${item.localeCode} · v${item.releaseVersion}`}</span>
                    {item.active ? <Tag color="green">{t('当前生效', 'Active')}</Tag> : null}
                  </Space>
                }
                description={item.publishedAt || '-'}
              />
            </List.Item>
          )}
        />
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default LocalizationPage;
