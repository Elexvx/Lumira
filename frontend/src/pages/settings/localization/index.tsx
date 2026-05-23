import { DeleteOutlined, EditOutlined, HistoryOutlined, PlusOutlined, SaveOutlined, SyncOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, List, Modal, Segmented, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { adaptPageResult } from '@/features/table/proTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { loadRuntimeLocalizationBundle } from '@/i18n/runtimeLocalization';
import { localizationService, type LocalizationEntryPayload } from '@/services/localization';
import type { LocalizationEntry, LocalizationLanguage, LocalizationNamespace, LocalizationRelease } from '@/types/api';
import { copyTextToClipboard } from '@/utils/clipboard';
import { buildLocalizationSyncPayload } from './sourceScanner';

const DEFAULT_LOCALE = 'zh-CN';
const PINNED_LOCALES = ['zh-CN', 'en-US'];

type EntryDrafts = Record<number, Record<string, string>>;
type EntryFormValues = LocalizationEntryPayload;

const fallbackLanguages: LocalizationLanguage[] = [
  { id: -1, localeCode: 'zh-CN', languageName: '简体中文', nativeName: '简体中文', status: 'ENABLED', defaultLanguage: true },
  { id: -2, localeCode: 'en-US', languageName: 'English', nativeName: 'English', status: 'ENABLED' },
];

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '待翻译', value: 'PENDING' },
];

const entryStatusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

const localeLabel = (language: LocalizationLanguage) => {
  if (language.localeCode === 'zh-CN') {
    return '中文';
  }
  if (language.localeCode === 'en-US') {
    return '英文';
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

const normalizeTranslations = (translations?: Record<string, string | null | undefined>) =>
  Object.fromEntries(Object.entries(translations || {}).map(([key, value]) => [key, typeof value === 'string' ? value : '']));

const translationValue = (record: LocalizationEntry, localeCode: string) => {
  const value = record.translations?.[localeCode];
  if (value != null) {
    return value;
  }
  return record.sourceLocale === localeCode ? record.defaultMessage : '';
};

const LocalizationPage = () => {
  const { actionPermission, responsive, buttonSize } = usePagePermissionActions();
  const tableActionRef = useRef<ActionType>(null);
  const [languages, setLanguages] = useState<LocalizationLanguage[]>([]);
  const [namespaces, setNamespaces] = useState<LocalizationNamespace[]>([]);
  const [releases, setReleases] = useState<LocalizationRelease[]>([]);
  const [namespaceCode, setNamespaceCode] = useState('all');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>('all');
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [savingEntryId, setSavingEntryId] = useState<number | null>(null);
  const [drafts, setDrafts] = useState<EntryDrafts>({});
  const [entryDrawerOpen, setEntryDrawerOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<LocalizationEntry | null>(null);
  const [entrySaving, setEntrySaving] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [entryForm] = Form.useForm<EntryFormValues>();

  const languageColumns = useMemo(() => {
    const enabled = languages.filter((item) => item.status !== 'DISABLED');
    const merged = new Map<string, LocalizationLanguage>();
    for (const language of [...fallbackLanguages, ...enabled]) {
      merged.set(language.localeCode, language);
    }
    return sortLanguages(Array.from(merged.values()));
  }, [languages]);

  const primaryLocale = languageColumns[0]?.localeCode || DEFAULT_LOCALE;

  const namespaceOptions = [{ label: '全部模块', value: 'all' }].concat(
    namespaces.map((item) => ({
      label: `${item.namespaceName} (${item.namespaceCode})`,
      value: item.namespaceCode,
    })),
  );

  const loadMeta = async () => {
    setLoadingMeta(true);
    try {
      const [languageList, namespaceList, releaseList] = await Promise.all([
        localizationService.languages({ autoRedirectOnUnauthorized: false, silent: true }),
        localizationService.namespaces({ localeCode: primaryLocale }, { autoRedirectOnUnauthorized: false, silent: true }),
        localizationService.releases(primaryLocale, { autoRedirectOnUnauthorized: false, silent: true }),
      ]);
      setLanguages(languageList);
      setNamespaces(namespaceList);
      setReleases(releaseList);
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '本地化数据加载失败');
    } finally {
      setLoadingMeta(false);
    }
  };

  useEffect(() => {
    void loadMeta();
  }, []);

  const refreshBundles = async () => {
    await Promise.all(languageColumns.map((language) => loadRuntimeLocalizationBundle(language.localeCode)));
    tableActionRef.current?.reload();
    await loadMeta();
  };

  const getDraftValue = (record: LocalizationEntry, localeCode: string) => drafts[record.id]?.[localeCode] ?? translationValue(record, localeCode);

  const hasDraft = (record: LocalizationEntry) => Boolean(drafts[record.id] && Object.keys(drafts[record.id]).length > 0);

  const changeDraft = (record: LocalizationEntry, localeCode: string, value: string) => {
    setDrafts((current) => ({
      ...current,
      [record.id]: {
        ...current[record.id],
        [localeCode]: value,
      },
    }));
  };

  const saveRow = async (record: LocalizationEntry) => {
    const translations = normalizeTranslations({
      ...(record.translations || {}),
      ...Object.fromEntries(languageColumns.map((language) => [language.localeCode, getDraftValue(record, language.localeCode)])),
    });
    setSavingEntryId(record.id);
    try {
      await localizationService.updateEntry(
        record.id,
        {
          namespaceCode: record.namespaceCode,
          messageKey: record.messageKey,
          defaultMessage: translations[record.sourceLocale] || translations[DEFAULT_LOCALE] || record.defaultMessage || record.messageKey,
          sourceLocale: record.sourceLocale || DEFAULT_LOCALE,
          sourceType: record.sourceType || 'UI',
          sourceRef: record.sourceRef || undefined,
          status: record.status,
          translations,
        },
        { autoRedirectOnUnauthorized: false },
      );
      setDrafts((current) => {
        const next = { ...current };
        delete next[record.id];
        return next;
      });
      message.success('已保存');
      await refreshBundles();
    } finally {
      setSavingEntryId(null);
    }
  };

  const deleteEntry = (record: LocalizationEntry) => {
    Modal.confirm({
      title: '删除译文',
      content: record.messageKey,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await localizationService.deleteEntry(record.id, { autoRedirectOnUnauthorized: false });
        message.success('已删除');
        tableActionRef.current?.reload();
      },
    });
  };

  const openEntryDrawer = (record?: LocalizationEntry) => {
    setEditingEntry(record || null);
    entryForm.resetFields();
    entryForm.setFieldsValue({
      namespaceCode: record?.namespaceCode || (namespaceCode === 'all' ? undefined : namespaceCode),
      messageKey: record?.messageKey || '',
      defaultMessage: record?.defaultMessage || '',
      sourceLocale: record?.sourceLocale || DEFAULT_LOCALE,
      sourceType: record?.sourceType || 'UI',
      sourceRef: record?.sourceRef || '',
      status: record?.status || 'ENABLED',
      translations: normalizeTranslations(record?.translations),
    });
    setEntryDrawerOpen(true);
  };

  const saveEntry = async () => {
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
        await localizationService.updateEntry(editingEntry.id, payload, { autoRedirectOnUnauthorized: false });
      } else {
        await localizationService.createEntry(payload, { autoRedirectOnUnauthorized: false });
      }
      message.success('已保存');
      setEntryDrawerOpen(false);
      setEditingEntry(null);
      await refreshBundles();
    } finally {
      setEntrySaving(false);
    }
  };

  const syncEntries = async () => {
    setSyncing(true);
    try {
      await localizationService.sync(buildLocalizationSyncPayload(), { autoRedirectOnUnauthorized: false });
      message.success('已同步');
      await refreshBundles();
    } finally {
      setSyncing(false);
    }
  };

  const publishEntries = async () => {
    setPublishing(true);
    try {
      await Promise.all(
        languageColumns.map((language) =>
          localizationService.publish({ localeCode: language.localeCode, note: '本地化中心发布' }, { autoRedirectOnUnauthorized: false }),
        ),
      );
      message.success('已发布');
      await refreshBundles();
    } finally {
      setPublishing(false);
    }
  };

  const columns = useMemo<ProColumns<LocalizationEntry>[]>(
    () => [
      {
        title: '',
        width: 64,
        align: 'center',
        render: (_, __, index) => index + 1,
      },
      {
        title: '标识符',
        dataIndex: 'messageKey',
        width: 360,
        fixed: responsive.isMobile ? undefined : 'left',
        ellipsis: true,
        render: (_, record) => <Typography.Text copyable>{record.messageKey}</Typography.Text>,
      },
      ...languageColumns.map<ProColumns<LocalizationEntry>>((language) => ({
        title: localeLabel(language),
        dataIndex: ['translations', language.localeCode],
        width: 320,
        render: (_, record) => (
          <Input.TextArea
            value={getDraftValue(record, language.localeCode)}
            rows={2}
            disabled={!actionPermission.can('localization:update')}
            onChange={(event) => changeDraft(record, language.localeCode, event.target.value)}
          />
        ),
      })),
      {
        title: '模块',
        width: 150,
        render: (_, record) => <Tag>{record.namespaceName || record.namespaceCode}</Tag>,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 210,
        fixed: responsive.isMobile ? undefined : 'right',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'save',
                label: '保存',
                icon: <SaveOutlined />,
                permission: 'localization:update',
                disabled: !hasDraft(record) || savingEntryId === record.id,
                onClick: () => void saveRow(record),
              },
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                permission: 'localization:update',
                onClick: () => openEntryDrawer(record),
              },
              {
                key: 'delete',
                label: '删除译文',
                icon: <DeleteOutlined />,
                danger: true,
                permission: 'localization:delete',
                onClick: () => deleteEntry(record),
              },
              {
                key: 'copy',
                label: '复制标识符',
                onClick: async () => {
                  await copyTextToClipboard(record.messageKey);
                  message.success('已复制');
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, drafts, languageColumns, responsive.isMobile, savingEntryId],
  );

  return (
    <ManagementPage title="本地化">
      <Spin spinning={loadingMeta}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space wrap>
              <Space>
                <Typography.Text strong>当前语言</Typography.Text>
                <Tag>简体中文</Tag>
              </Space>
              <Select
                value={namespaceCode}
                options={namespaceOptions}
                onChange={(value) => {
                  setNamespaceCode(value);
                  tableActionRef.current?.reload();
                }}
                style={{ width: responsive.isMobile ? '100%' : 180 }}
              />
              <Input.Search
                allowClear
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onSearch={() => tableActionRef.current?.reload()}
                placeholder="关键字"
                style={{ width: responsive.isMobile ? '100%' : 280 }}
              />
              <Segmented value={status} onChange={(value) => setStatus(String(value))} options={statusOptions} />
            </Space>
            <Space wrap>
              <Button icon={<DeleteOutlined />} disabled>
                删除译文
              </Button>
              <Button icon={<SyncOutlined />} loading={syncing} onClick={() => void syncEntries()}>
                同步
              </Button>
              <Button type="primary" icon={<SaveOutlined />} loading={publishing} onClick={() => void publishEntries()}>
                发布
              </Button>
            </Space>
          </Space>

          <ManagementTable<LocalizationEntry>
            actionRef={tableActionRef}
            rowKey="id"
            columns={columns}
            isMobile={responsive.isMobile}
            search={false}
            params={{
              localeCode: primaryLocale,
              namespaceCode: namespaceCode === 'all' ? undefined : namespaceCode,
              keyword: keyword || undefined,
              translationStatus: status === 'all' ? undefined : status,
            }}
            request={async (params, sorter) =>
              adaptPageResult(
                await localizationService.entries(
                  {
                    localeCode: primaryLocale,
                    namespaceCode: namespaceCode === 'all' ? undefined : namespaceCode,
                    keyword: keyword || undefined,
                    translationStatus: status === 'all' ? undefined : status,
                    pageNo: Number(params.current) || 1,
                    pageSize: Number(params.pageSize) || 20,
                    sortField: Object.keys(sorter || {}).find((key) => ['ascend', 'descend'].includes(String((sorter as Record<string, unknown>)[key]))) || undefined,
                    sortOrder: Object.values(sorter || {}).find((value) => value === 'ascend' || value === 'descend') as string | undefined,
                  },
                  { autoRedirectOnUnauthorized: false, silent: true },
                ),
              )
            }
            toolBarRender={() =>
              actionPermission.buildToolbarActions([
                {
                  permission: 'localization:create',
                  value: (
                    <Button key="create" type="primary" size={buttonSize} icon={<PlusOutlined />} onClick={() => openEntryDrawer()}>
                      新增词条
                    </Button>
                  ),
                },
                {
                  value: (
                    <Button key="history" size={buttonSize} icon={<HistoryOutlined />} onClick={() => setHistoryDrawerOpen(true)}>
                      版本历史
                    </Button>
                  ),
                },
              ])
            }
          />
        </Space>
      </Spin>

      <ManagementDrawer
        title={editingEntry ? '编辑词条' : '新增词条'}
        open={entryDrawerOpen}
        onClose={() => setEntryDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: () => setEntryDrawerOpen(false) },
          { key: 'save', label: '保存', type: 'primary', loading: entrySaving, onClick: () => void saveEntry() },
        ]}
      >
        <Form form={entryForm} layout="vertical">
          <Form.Item name="namespaceCode" label="模块" rules={[{ required: true }]}>
            <Select options={namespaceOptions.filter((item) => item.value !== 'all')} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="messageKey" label="标识符" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="defaultMessage" label="默认文案" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          {languageColumns.map((language) => (
            <Form.Item key={language.localeCode} name={['translations', language.localeCode]} label={localeLabel(language)}>
              <Input.TextArea rows={3} />
            </Form.Item>
          ))}
          <Form.Item name="sourceLocale" label="源语言">
            <Select options={languageColumns.map((item) => ({ label: `${localeLabel(item)} (${item.localeCode})`, value: item.localeCode }))} />
          </Form.Item>
          <Form.Item name="sourceType" label="来源类型">
            <Select options={[{ label: 'UI', value: 'UI' }, { label: 'ROUTE', value: 'ROUTE' }, { label: 'BACKEND', value: 'BACKEND' }, { label: 'TEMPLATE', value: 'TEMPLATE' }]} />
          </Form.Item>
          <Form.Item name="sourceRef" label="来源">
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={entryStatusOptions} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer title="版本历史" open={historyDrawerOpen} onClose={() => setHistoryDrawerOpen(false)}>
        <List
          dataSource={releases}
          locale={{ emptyText: '暂无发布记录' }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    <span>{`${item.localeCode} · v${item.releaseVersion}`}</span>
                    {item.active ? <Tag color="green">当前生效</Tag> : null}
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
