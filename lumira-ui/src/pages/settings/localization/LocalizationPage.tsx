import { HistoryOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
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
import {
  clearRuntimeLocalizationBundleCache,
  loadRuntimeLocalizationBundle,
} from '@/i18n/runtimeLocalization';
import { request } from '@/services/common/request';
import type { LocalizationEntryPayload } from '@/services/localization/types';
import type { LocalizationLanguage, LocalizationNamespace, LocalizationRelease } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { copyTextToClipboard } from '@/utils/clipboard';
import { databaseMessage } from '@/i18n/databaseMessage';

const DEFAULT_LOCALE = 'zh-CN';

const t = databaseMessage;

const localeLabel = (language: LocalizationLanguage) =>
  language.nativeName || language.languageName || language.localeCode;

const sortLanguages = (items: LocalizationLanguage[]) =>
  [...items].sort((left, right) =>
    (left.sortNo ?? 0) - (right.sortNo ?? 0) || left.localeCode.localeCompare(right.localeCode));

const EntryStatusOptions = [
  { label: t('ui.settings.localization.localization.enabled'), value: 'ENABLED' },
  { label: t('ui.settings.localization.localization.disabled'), value: 'DISABLED' },
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

const LocalizationPage = () => {
  const { actionPermission, responsive, searchConfig, buttonSize } = usePagePermissionActions();
  const tableActionRef = useRef<ActionType>(null);
  const searchValuesRef = useRef<{ namespaceCode?: string }>({ namespaceCode: 'all' });
  const [languages, setLanguages] = useState<LocalizationLanguage[]>([]);
  const [namespaces, setNamespaces] = useState<LocalizationNamespace[]>([]);
  const [releases, setReleases] = useState<LocalizationRelease[]>([]);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [entryDrawerOpen, setEntryDrawerOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<import('@/types/api').LocalizationEntry | null>(null);
  const [entrySaving, setEntrySaving] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [savingEntryId, setSavingEntryId] = useState<number | null>(null);
  const [drafts, setDrafts] = useState<EntryDrafts>({});
  const [entryForm] = Form.useForm<LocalizationEntryPayload>();
  const languageColumns = useMemo(() => {
    const enabled = languages.filter((item) => item.status !== 'DISABLED');
    const merged = new Map<string, LocalizationLanguage>();
    for (const language of enabled) {
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
    () => [{ label: t('ui.settings.localization.localization.allModules'), value: 'all' }].concat(
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
      showErrorMessage(error, t('ui.settings.localization.localization.failedToLoadLocalizationData'));
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
    clearRuntimeLocalizationBundleCache();
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
        message.success(t('ui.settings.localization.localization.saved'));
        await refreshBundles();
      } finally {
        setSavingEntryId(null);
      }
    },
    [getDraftValue, languageColumns, refreshBundles],
  );
  const copyKey = useCallback(async (messageKey: string) => {
    await copyTextToClipboard(messageKey);
    message.success(t('ui.settings.localization.localization.copied'));
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
      message.success(t('ui.settings.localization.localization.saved'));
      setEntryDrawerOpen(false);
      setEditingEntry(null);
      await refreshBundles();
    } finally {
      setEntrySaving(false);
    }
  }, [editingEntry, entryForm, refreshBundles]);
  const publishEntries = useCallback(async () => {
    setPublishing(true);
    try {
      await Promise.all(
        languageColumns.map((language) =>
          request<LocalizationRelease>('/v1/localization/publish', {
            method: 'POST',
            data: { localeCode: language.localeCode, note: t('ui.settings.localization.localization.localizationCenterRelease') },
            autoRedirectOnUnauthorized: false,
            timeoutMs: 30000,
          }),
        ),
      );
      message.success(t('ui.settings.localization.localization.published'));
      await refreshBundles();
    } finally {
      setPublishing(false);
    }
  }, [languageColumns, refreshBundles]);
  const openHistoryDrawer = useCallback(() => setHistoryDrawerOpen(true), []);
  const columns = useMemo<ProColumns<import('@/types/api').LocalizationEntry>[]>(
    () => [
      {
        title: t('ui.settings.localization.localization.currentLocale'),
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
        title: t('ui.settings.localization.localization.module'),
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
        title: t('ui.settings.localization.localization.keyword'),
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: {
          allowClear: true,
          placeholder: t('ui.settings.localization.localization.searchKeySourceTextOrSource'),
        },
      },
      {
        title: t('ui.settings.localization.localization.translationStatus'),
        dataIndex: 'translationStatus',
        hideInTable: true,
        valueType: 'select',
        initialValue: 'all',
        valueEnum: {
          all: { text: t('ui.settings.localization.localization.all') },
          PENDING: { text: t('ui.settings.localization.localization.pendingTranslation') },
        },
        search: {
          transform: (value: string) => ({ translationStatus: value === 'all' ? undefined : value }),
        },
      },
      {
        title: t('ui.settings.localization.localization.no'),
        valueType: 'index',
        width: 'var(--saas-spacing-64)',
        align: 'center',
        render: (_: unknown, __: import('@/types/api').LocalizationEntry, index: number) => index + 1,
      },
      {
        title: t('ui.settings.localization.localization.key'),
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
        title: t('ui.settings.localization.localization.module'),
        width: 'var(--saas-spacing-150)',
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) => createElement(Tag, null, record.namespaceName || record.namespaceCode),
      },
      {
        title: t('ui.settings.localization.localization.actions'),
        valueType: 'option',
        width: 'var(--saas-spacing-210)',
        fixed: responsive.isMobile ? undefined : 'right',
        render: (_: unknown, record: import('@/types/api').LocalizationEntry) =>
          createElement(TableActionBar, {
            isMobile: responsive.isMobile,
            items: actionPermission.buildTableActions([
              {
                key: 'save',
                label: t('ui.settings.localization.localization.save'),
                permission: 'localization:update',
                disabled: !hasDraft(record) || savingEntryId === record.id,
                onClick: () => void saveRow(record),
              },
              {
                key: 'edit',
                label: t('ui.settings.localization.localization.edit'),
                permission: 'localization:update',
                onClick: () => openEntryDrawer(record),
              },
              {
                key: 'copy',
                label: t('ui.settings.localization.localization.copyKey'),
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

      return result;
    },
    [primaryLocale],
  );
  const tableRequest = useMemo(() => buildTableRequest(requestEntries), [requestEntries]);
  const toolbarActions = actionPermission.buildToolbarActions([
    {
      value: createElement(Button, { key: 'publish', type: 'primary', size: buttonSize, icon: createElement(SaveOutlined, {}), loading: publishing, disabled: !actionPermission.can('localization:publish'), onClick: () => void publishEntries() }, t('ui.settings.localization.localization.publish')),
    },
    {
      value: createElement(Button, { key: 'create', type: 'primary', size: buttonSize, icon: createElement(PlusOutlined, {}), disabled: !actionPermission.can('localization:create'), onClick: () => openEntryDrawer() }, t('ui.settings.localization.localization.addEntry')),
    },
    {
      value: createElement(Button, { key: 'history', size: buttonSize, icon: createElement(HistoryOutlined, {}), onClick: openHistoryDrawer }, t('ui.settings.localization.localization.versionHistory')),
    },
  ]);

  return (
    <ManagementPage title={t('ui.settings.localization.localization.localization')}>
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
        title={editingEntry ? t('ui.settings.localization.localization.editEntry') : t('ui.settings.localization.localization.addEntry')}
        open={entryDrawerOpen}
        onClose={() => setEntryDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: t('ui.settings.localization.localization.cancel'), onClick: () => setEntryDrawerOpen(false) },
          { key: 'save', label: t('ui.settings.localization.localization.save'), type: 'primary', loading: entrySaving, onClick: () => void saveEntry() },
        ]}
      >
        <Form form={entryForm} layout="vertical">
          <Form.Item name="namespaceCode" label={t('ui.settings.localization.localization.module')} rules={[{ required: true }]}>
            <Select options={namespaceOptions.filter((item) => item.value !== 'all')} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="messageKey" label={t('ui.settings.localization.localization.key')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="defaultMessage" label={t('ui.settings.localization.localization.defaultText')} rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          {languageOptions.map((language) => (
            <Form.Item key={language.value} name={['translations', language.value]} label={language.label}>
              <Input.TextArea rows={3} />
            </Form.Item>
          ))}
          <Form.Item name="sourceLocale" label={t('ui.settings.localization.localization.sourceLocale')}>
            <Select options={languageOptions} />
          </Form.Item>
          <Form.Item name="sourceType" label={t('ui.settings.localization.localization.sourceType')}>
            <Select options={[{ label: 'UI', value: 'UI' }, { label: 'ROUTE', value: 'ROUTE' }, { label: 'BACKEND', value: 'BACKEND' }, { label: 'TEMPLATE', value: 'TEMPLATE' }]} />
          </Form.Item>
          <Form.Item name="sourceRef" label={t('ui.settings.localization.localization.source')}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label={t('ui.settings.localization.localization.status')}>
            <Select options={EntryStatusOptions} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title={t('ui.settings.localization.localization.versionHistory')} open={historyDrawerOpen} onClose={() => setHistoryDrawerOpen(false)}>
        <List
          dataSource={releases}
          locale={{ emptyText: t('ui.settings.localization.localization.noReleaseRecordsYet') }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <span>{`${item.localeCode} · v${item.releaseVersion}`}</span>
                    {item.active ? <Tag color="green">{t('ui.settings.localization.localization.active')}</Tag> : null}
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
