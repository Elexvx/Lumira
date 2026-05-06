import { DeleteOutlined, EditOutlined, HistoryOutlined, PlusOutlined, RollbackOutlined, SaveOutlined, SyncOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Card, Checkbox, Form, Input, List, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { formatMessage, useIntl } from '@umijs/max';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { adaptPageResult } from '@/features/table/proTable';
import { loadRuntimeLocalizationBundle } from '@/i18n/runtimeLocalization';
import { normalizeLocale } from '@/i18n/locale';
import { localizationService, type LocalizationEntryPayload } from '@/services/localization';
import type { LocalizationEntry, LocalizationLanguage, LocalizationNamespace, LocalizationRelease } from '@/types/api';
import { buildLocalizationSyncPayload } from '@/pages/localization/sourceScanner';
import { copyTextToClipboard } from '@/utils/clipboard';

const STATUS_OPTIONS = [
  { label: '全部', value: 'all' },
  { label: '已翻译', value: 'TRANSLATED' },
  { label: '待翻译', value: 'PENDING' },
];

const ENTRY_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

const resolveCoverageText = (value?: number | string | null) => {
  if (value == null || value === '') {
    return '-';
  }
  if (typeof value === 'string') {
    return value.endsWith('%') ? value : `${value}%`;
  }
  return `${value}%`;
};

const resolveTagColor = (status?: string | null) => {
  if (!status) {
    return 'default';
  }
  if (status === 'TRANSLATED' || status === 'ENABLED') {
    return 'green';
  }
  if (status === 'PENDING') {
    return 'gold';
  }
  return 'default';
};

const LocalizationPage = () => {
  const intl = useIntl();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const tableActionRef = useRef<ActionType>(null);
  const [languages, setLanguages] = useState<LocalizationLanguage[]>([]);
  const [namespaces, setNamespaces] = useState<LocalizationNamespace[]>([]);
  const [releases, setReleases] = useState<LocalizationRelease[]>([]);
  const [selectedLocale, setSelectedLocale] = useState('zh-CN');
  const [selectedNamespace, setSelectedNamespace] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [keyword, setKeyword] = useState('');
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [languageDrawerOpen, setLanguageDrawerOpen] = useState(false);
  const [languageSaving, setLanguageSaving] = useState(false);
  const [editingLanguage, setEditingLanguage] = useState<LocalizationLanguage | null>(null);
  const [entryDrawerOpen, setEntryDrawerOpen] = useState(false);
  const [entrySaving, setEntrySaving] = useState(false);
  const [editingEntry, setEditingEntry] = useState<LocalizationEntry | null>(null);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [entryForm] = Form.useForm<LocalizationEntryPayload & { currentTranslation?: string }>();
  const [languageForm] = Form.useForm<{
    localeCode: string;
    languageName: string;
    nativeName?: string;
    fallbackLocale?: string;
    sortNo?: number;
    status?: string;
    defaultLanguage?: boolean;
  }>();

  const selectedLanguage = useMemo(
    () => languages.find((item) => item.localeCode === selectedLocale) || languages[0],
    [languages, selectedLocale],
  );

  const selectedNamespaceName = useMemo(() => {
    if (selectedNamespace === 'all') {
      return intl.formatMessage({ id: 'page.localization.allNamespaces', defaultMessage: '全部命名空间' });
    }
    return namespaces.find((item) => item.namespaceCode === selectedNamespace)?.namespaceName || selectedNamespace;
  }, [intl, namespaces, selectedNamespace]);

  const loadMeta = async () => {
    setLoadingMeta(true);
    try {
      const [languageList, namespaceList, releaseList] = await Promise.all([
        localizationService.languages({ autoRedirectOnUnauthorized: false }),
        localizationService.namespaces({ localeCode: selectedLocale }, { autoRedirectOnUnauthorized: false }),
        localizationService.releases(selectedLocale, { autoRedirectOnUnauthorized: false }),
      ]);
      setLanguages(languageList);
      setNamespaces(namespaceList);
      setReleases(releaseList);
      if (!languageList.some((item) => item.localeCode === selectedLocale) && languageList.length > 0) {
        setSelectedLocale(languageList[0].localeCode);
      }
      if (selectedNamespace !== 'all' && !namespaceList.some((item) => item.namespaceCode === selectedNamespace)) {
        setSelectedNamespace('all');
      }
    } catch {
      // Global request interceptor already handles feedback.
    } finally {
      setLoadingMeta(false);
    }
  };

  useEffect(() => {
    void loadMeta();
  }, [selectedLocale]);

  const refreshCurrentBundle = async () => {
    await loadRuntimeLocalizationBundle(selectedLocale);
    tableActionRef.current?.reload();
    await loadMeta();
  };

  const openLanguageDrawer = (record?: LocalizationLanguage) => {
    setEditingLanguage(record || null);
    languageForm.resetFields();
    languageForm.setFieldsValue({
      localeCode: record?.localeCode || '',
      languageName: record?.languageName || '',
      nativeName: record?.nativeName || '',
      fallbackLocale: record?.fallbackLocale || 'zh-CN',
      sortNo: record?.sortNo ?? languages.length + 1,
      status: record?.status || 'ENABLED',
      defaultLanguage: record?.defaultLanguage || false,
    });
    setLanguageDrawerOpen(true);
  };

  const saveLanguage = async () => {
    setLanguageSaving(true);
    try {
      const values = await languageForm.validateFields();
      const payload = {
        localeCode: values.localeCode,
        languageName: values.languageName,
        nativeName: values.nativeName,
        fallbackLocale: values.fallbackLocale,
        sortNo: values.sortNo,
        status: values.status,
        defaultLanguage: values.defaultLanguage,
      };
      if (editingLanguage) {
        await localizationService.updateLanguage(editingLanguage.id, payload, { autoRedirectOnUnauthorized: false });
      } else {
        await localizationService.createLanguage(payload, { autoRedirectOnUnauthorized: false });
      }
      message.success(intl.formatMessage({ id: 'page.localization.languageSaved', defaultMessage: '语言已保存' }));
      setLanguageDrawerOpen(false);
      setEditingLanguage(null);
      await loadMeta();
    } finally {
      setLanguageSaving(false);
    }
  };

  const openEntryDrawer = (record?: LocalizationEntry) => {
    setEditingEntry(record || null);
    entryForm.resetFields();
    entryForm.setFieldsValue({
      namespaceCode: record?.namespaceCode || (selectedNamespace === 'all' ? undefined : selectedNamespace),
      messageKey: record?.messageKey || '',
      defaultMessage: record?.defaultMessage || '',
      sourceLocale: record?.sourceLocale || selectedLocale,
      sourceType: record?.sourceType || 'UI',
      sourceRef: record?.sourceRef || '',
      status: record?.status || 'ENABLED',
      localeCode: selectedLocale,
      translatedMessage: record?.currentTranslation || '',
    });
    setEntryDrawerOpen(true);
  };

  const saveEntry = async () => {
    setEntrySaving(true);
    try {
      const values = await entryForm.validateFields();
      const payload: LocalizationEntryPayload = {
        namespaceCode: values.namespaceCode,
        messageKey: values.messageKey,
        defaultMessage: values.defaultMessage,
        sourceLocale: values.sourceLocale,
        sourceType: values.sourceType,
        sourceRef: values.sourceRef,
        status: values.status,
        localeCode: values.localeCode || selectedLocale,
        translatedMessage: values.translatedMessage,
        translations: values.translations || {},
      };
      if (editingEntry) {
        await localizationService.updateEntry(editingEntry.id, payload, { autoRedirectOnUnauthorized: false });
      } else {
        await localizationService.createEntry(payload, { autoRedirectOnUnauthorized: false });
      }
      message.success(intl.formatMessage({ id: 'page.localization.entrySaved', defaultMessage: '词条已保存' }));
      setEntryDrawerOpen(false);
      setEditingEntry(null);
      tableActionRef.current?.reload();
      await loadMeta();
      await loadRuntimeLocalizationBundle(selectedLocale);
    } finally {
      setEntrySaving(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      const payload = buildLocalizationSyncPayload();
      await localizationService.sync(payload, { autoRedirectOnUnauthorized: false });
      message.success(intl.formatMessage({ id: 'page.localization.syncSuccess', defaultMessage: '已同步源码词条' }));
      await refreshCurrentBundle();
    } finally {
      setSyncing(false);
    }
  };

  const handlePublish = async () => {
    setPublishing(true);
    try {
      await localizationService.publish(
        {
          localeCode: selectedLocale,
          note: intl.formatMessage({ id: 'page.localization.publishNote', defaultMessage: '本地化中心发布' }),
        },
        { autoRedirectOnUnauthorized: false },
      );
      message.success(intl.formatMessage({ id: 'page.localization.publishSuccess', defaultMessage: '翻译版本已发布' }));
      await refreshCurrentBundle();
    } finally {
      setPublishing(false);
    }
  };

  const handleRollback = async (release: LocalizationRelease) => {
    await localizationService.rollback({ releaseId: release.id }, { autoRedirectOnUnauthorized: false });
    message.success(intl.formatMessage({ id: 'page.localization.rollbackSuccess', defaultMessage: '翻译版本已回滚' }));
    await refreshCurrentBundle();
  };

  const columns = useMemo<ProColumns<LocalizationEntry>[]>(
    () => [
      {
        title: intl.formatMessage({ id: 'page.localization.namespace', defaultMessage: '命名空间' }),
        dataIndex: 'namespaceName',
        width: 160,
        render: (_, record) => <Tag>{record.namespaceName}</Tag>,
      },
      {
        title: intl.formatMessage({ id: 'page.localization.key', defaultMessage: '键名' }),
        dataIndex: 'messageKey',
        width: 280,
        ellipsis: true,
      },
      {
        title: intl.formatMessage({ id: 'page.localization.defaultMessage', defaultMessage: '原文' }),
        dataIndex: 'defaultMessage',
        width: 280,
        ellipsis: true,
      },
      {
        title: intl.formatMessage({ id: 'page.localization.translation', defaultMessage: '译文' }),
        dataIndex: 'currentTranslation',
        width: 280,
        ellipsis: true,
        render: (_, record) =>
          record.currentTranslation ? (
            <Typography.Text>{record.currentTranslation}</Typography.Text>
          ) : (
            <Typography.Text type="secondary">{intl.formatMessage({ id: 'page.localization.untranslated', defaultMessage: '待翻译' })}</Typography.Text>
          ),
      },
      {
        title: intl.formatMessage({ id: 'page.localization.status', defaultMessage: '状态' }),
        dataIndex: 'translationStatus',
        width: 120,
        render: (_, record) => <Tag color={resolveTagColor(record.translationStatus)}>{record.translationStatus}</Tag>,
      },
      {
        title: intl.formatMessage({ id: 'page.localization.sourceRef', defaultMessage: '来源' }),
        dataIndex: 'sourceRef',
        width: 260,
        ellipsis: true,
      },
      {
        title: intl.formatMessage({ id: 'page.localization.usageCount', defaultMessage: '引用数' }),
        dataIndex: 'usageCount',
        width: 100,
      },
      {
        title: intl.formatMessage({ id: 'common.actions', defaultMessage: '操作' }),
        valueType: 'option',
        width: 180,
        render: (_, record) => [
          <Button key="edit" type="link" icon={<EditOutlined />} onClick={() => openEntryDrawer(record)}>
            {intl.formatMessage({ id: 'common.edit', defaultMessage: '编辑' })}
          </Button>,
          <Button
            key="copy"
            type="link"
            onClick={async () => {
              await copyTextToClipboard(record.messageKey);
              message.success(intl.formatMessage({ id: 'common.success', defaultMessage: '操作成功' }));
            }}
          >
            {intl.formatMessage({ id: 'page.localization.copyKey', defaultMessage: '复制键名' })}
          </Button>,
        ],
      },
    ],
    [intl, selectedLocale],
  );

  const localeOptions = languages.map((item) => ({
    label: `${item.languageName} (${item.localeCode})`,
    value: item.localeCode,
  }));

  const namespaceOptions = [{ label: intl.formatMessage({ id: 'page.localization.allNamespaces', defaultMessage: '全部命名空间' }), value: 'all' }].concat(
    namespaces.map((item) => ({
      label: `${item.namespaceName} (${item.namespaceCode})`,
      value: item.namespaceCode,
    })),
  );

  const selectedLanguageCoverage = resolveCoverageText(selectedLanguage?.coverageRate);

  return (
    <ManagementPage title={intl.formatMessage({ id: 'page.localization.title', defaultMessage: '本地化中心' })}>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          alignItems: 'stretch',
        }}
      >
        <Card
          title={intl.formatMessage({ id: 'page.localization.languageSwitch', defaultMessage: '语言切换' })}
          extra={
            <Button size="small" icon={<PlusOutlined />} onClick={() => openLanguageDrawer()}>
              {intl.formatMessage({ id: 'page.localization.addLanguage', defaultMessage: '新增语言' })}
            </Button>
          }
        >
          <Spin spinning={loadingMeta}>
            <List
              dataSource={languages}
              locale={{ emptyText: intl.formatMessage({ id: 'page.localization.noLanguages', defaultMessage: '暂无语言' }) }}
              renderItem={(item) => (
                <List.Item
                  onClick={() => setSelectedLocale(item.localeCode)}
                  style={{
                    cursor: 'pointer',
                    borderRadius: 8,
                    paddingInline: 12,
                    background: selectedLocale === item.localeCode ? 'rgba(24, 144, 255, 0.08)' : 'transparent',
                    marginBottom: 8,
                  }}
                >
                  <Space direction="vertical" size={2} style={{ width: '100%' }}>
                    <Space>
                      <Typography.Text strong>{item.languageName}</Typography.Text>
                      {item.defaultLanguage ? <Tag color="blue">{intl.formatMessage({ id: 'page.localization.default', defaultMessage: '默认' })}</Tag> : null}
                    </Space>
                    <Typography.Text type="secondary">{item.localeCode}</Typography.Text>
                    <Typography.Text type="secondary">
                      {intl.formatMessage({ id: 'page.localization.coverage', defaultMessage: '覆盖率：{rate}' }, { rate: resolveCoverageText(item.coverageRate) })}
                    </Typography.Text>
                  </Space>
                </List.Item>
              )}
            />
          </Spin>
        </Card>

        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card
            title={intl.formatMessage({ id: 'page.localization.currentStatus', defaultMessage: '当前状态' })}
          >
            <Space direction={responsive.isMobile ? 'vertical' : 'horizontal'} style={{ width: '100%', justifyContent: 'space-between' }} align={responsive.isMobile ? 'start' : 'center'}>
              <Space direction="vertical" size={2}>
                <Typography.Title level={3} style={{ margin: 0 }}>
                  {intl.formatMessage({ id: 'page.localization.title', defaultMessage: '本地化中心' })}
                </Typography.Title>
                <Typography.Text type="secondary">
                  {intl.formatMessage({ id: 'page.localization.currentLocale', defaultMessage: '当前语言：{locale}' }, { locale: selectedLanguage?.languageName || selectedLocale })}
                  {' · '}
                  {intl.formatMessage({ id: 'page.localization.currentNamespace', defaultMessage: '当前模块：{namespace}' }, { namespace: selectedNamespaceName })}
                  {' · '}
                  {intl.formatMessage({ id: 'page.localization.coverage', defaultMessage: '覆盖率：{rate}' }, { rate: selectedLanguageCoverage })}
                </Typography.Text>
              </Space>

              <Space wrap>
                <Button icon={<SyncOutlined />} loading={syncing} onClick={() => void handleSync()}>
                  {intl.formatMessage({ id: 'page.localization.sync', defaultMessage: '同步源码' })}
                </Button>
                <Button icon={<HistoryOutlined />} onClick={() => setHistoryDrawerOpen(true)}>
                  {intl.formatMessage({ id: 'page.localization.history', defaultMessage: '版本历史' })}
                </Button>
                <Button type="primary" icon={<SaveOutlined />} loading={publishing} onClick={() => void handlePublish()}>
                  {intl.formatMessage({ id: 'page.localization.publish', defaultMessage: '发布' })}
                </Button>
              </Space>
            </Space>
          </Card>

          <Card title={intl.formatMessage({ id: 'page.localization.searchFilters', defaultMessage: '搜索筛选' })}>
            <Space wrap>
              <Input
                allowClear
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder={intl.formatMessage({ id: 'page.localization.searchPlaceholder', defaultMessage: '搜索键名、原文或来源' })}
                style={{ width: responsive.isMobile ? '100%' : 280 }}
              />
              <Select
                value={selectedNamespace}
                onChange={setSelectedNamespace}
                options={namespaceOptions}
                style={{ width: responsive.isMobile ? '100%' : 220 }}
              />
              <Select
                value={statusFilter}
                onChange={setStatusFilter}
                options={STATUS_OPTIONS}
                style={{ width: responsive.isMobile ? '100%' : 140 }}
              />
            </Space>
          </Card>

          <ManagementTable<LocalizationEntry>
            actionRef={tableActionRef}
            rowKey="id"
            columns={columns}
            isMobile={responsive.isMobile}
            search={false}
            params={{
              localeCode: selectedLocale,
              namespaceCode: selectedNamespace === 'all' ? undefined : selectedNamespace,
              keyword: keyword || undefined,
              translationStatus: statusFilter === 'all' ? undefined : statusFilter,
            }}
            request={async (params, sorter) =>
              adaptPageResult(
                await localizationService.entries(
                {
                  localeCode: selectedLocale,
                  namespaceCode: selectedNamespace === 'all' ? undefined : selectedNamespace,
                  keyword: keyword || undefined,
                  translationStatus: statusFilter === 'all' ? undefined : statusFilter,
                  pageNo: Number(params.current) || 1,
                  pageSize: Number(params.pageSize) || 20,
                  sortField: Object.keys(sorter || {}).find((key) => ['ascend', 'descend'].includes(String((sorter as Record<string, unknown>)[key]))) || undefined,
                  sortOrder: Object.values(sorter || {}).find((value) => value === 'ascend' || value === 'descend') as string | undefined,
                },
                { autoRedirectOnUnauthorized: false },
                ),
              )
            }
            toolBarRender={() =>
              actionPermission.buildToolbarActions([
                {
                  permission: 'localization:create',
                  value: (
                    <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => openEntryDrawer()}>
                      {intl.formatMessage({ id: 'page.localization.addEntry', defaultMessage: '新增词条' })}
                    </Button>
                  ),
                },
              ])
            }
          />
        </Space>
      </div>

      <ManagementDrawer
        title={editingLanguage ? intl.formatMessage({ id: 'page.localization.editLanguage', defaultMessage: '编辑语言' }) : intl.formatMessage({ id: 'page.localization.addLanguage', defaultMessage: '新增语言' })}
        open={languageDrawerOpen}
        onClose={() => setLanguageDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: intl.formatMessage({ id: 'common.cancel', defaultMessage: '取消' }), onClick: () => setLanguageDrawerOpen(false) },
          { key: 'save', label: intl.formatMessage({ id: 'common.save', defaultMessage: '保存' }), type: 'primary', loading: languageSaving, onClick: () => void saveLanguage() },
        ]}
      >
        <Form form={languageForm} layout="vertical">
          <Form.Item name="localeCode" label={intl.formatMessage({ id: 'page.localization.localeCode', defaultMessage: '语言代码' })} rules={[{ required: true }]}>
            <Input placeholder="zh-CN" />
          </Form.Item>
          <Form.Item name="languageName" label={intl.formatMessage({ id: 'page.localization.languageName', defaultMessage: '语言名称' })} rules={[{ required: true }]}>
            <Input placeholder={intl.formatMessage({ id: 'page.localization.languageNamePlaceholder', defaultMessage: '例如：简体中文 / English' })} />
          </Form.Item>
          <Form.Item name="nativeName" label={intl.formatMessage({ id: 'page.localization.nativeName', defaultMessage: '本地名称' })}>
            <Input />
          </Form.Item>
          <Form.Item name="fallbackLocale" label={intl.formatMessage({ id: 'page.localization.fallbackLocale', defaultMessage: '回退语言' })}>
            <Select options={localeOptions} allowClear />
          </Form.Item>
          <Form.Item name="sortNo" label={intl.formatMessage({ id: 'page.localization.sortNo', defaultMessage: '排序' })}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="status" label={intl.formatMessage({ id: 'page.localization.status', defaultMessage: '状态' })}>
            <Select options={ENTRY_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="defaultLanguage" label={intl.formatMessage({ id: 'page.localization.defaultLanguage', defaultMessage: '默认语言' })} valuePropName="checked">
            <Checkbox />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={editingEntry ? intl.formatMessage({ id: 'page.localization.editEntry', defaultMessage: '编辑词条' }) : intl.formatMessage({ id: 'page.localization.addEntry', defaultMessage: '新增词条' })}
        open={entryDrawerOpen}
        onClose={() => setEntryDrawerOpen(false)}
        footerActions={[
          { key: 'cancel', label: intl.formatMessage({ id: 'common.cancel', defaultMessage: '取消' }), onClick: () => setEntryDrawerOpen(false) },
          { key: 'save', label: intl.formatMessage({ id: 'common.save', defaultMessage: '保存' }), type: 'primary', loading: entrySaving, onClick: () => void saveEntry() },
        ]}
      >
        <Form form={entryForm} layout="vertical">
          <Form.Item name="namespaceCode" label={intl.formatMessage({ id: 'page.localization.namespaceCode', defaultMessage: '命名空间' })} rules={[{ required: true }]}>
            <Select
              options={namespaceOptions.filter((item) => item.value !== 'all')}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="messageKey" label={intl.formatMessage({ id: 'page.localization.key', defaultMessage: '键名' })} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="defaultMessage" label={intl.formatMessage({ id: 'page.localization.defaultMessage', defaultMessage: '原文' })} rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="localeCode" label={intl.formatMessage({ id: 'page.localization.localeCode', defaultMessage: '语言代码' })}>
            <Select options={localeOptions} />
          </Form.Item>
          <Form.Item name="translatedMessage" label={intl.formatMessage({ id: 'page.localization.translation', defaultMessage: '译文' })}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="sourceLocale" label={intl.formatMessage({ id: 'page.localization.sourceLocale', defaultMessage: '源语言' })}>
            <Input />
          </Form.Item>
          <Form.Item name="sourceType" label={intl.formatMessage({ id: 'page.localization.sourceType', defaultMessage: '来源类型' })}>
            <Select options={[{ label: 'UI', value: 'UI' }, { label: 'ROUTE', value: 'ROUTE' }, { label: 'BACKEND', value: 'BACKEND' }, { label: 'TEMPLATE', value: 'TEMPLATE' }]} />
          </Form.Item>
          <Form.Item name="sourceRef" label={intl.formatMessage({ id: 'page.localization.sourceRef', defaultMessage: '来源' })}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label={intl.formatMessage({ id: 'page.localization.status', defaultMessage: '状态' })}>
            <Select options={ENTRY_STATUS_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={intl.formatMessage({ id: 'page.localization.history', defaultMessage: '版本历史' })}
        open={historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
      >
        <Spin spinning={loadingMeta}>
          <List
            dataSource={releases}
            locale={{ emptyText: intl.formatMessage({ id: 'page.localization.noReleases', defaultMessage: '暂无发布记录' }) }}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button
                    key="rollback"
                    type="link"
                    danger
                    icon={<RollbackOutlined />}
                    onClick={() =>
                      Modal.confirm({
                        title: intl.formatMessage({ id: 'page.localization.rollbackConfirm', defaultMessage: '确认回滚该版本吗？' }),
                        content: `${item.localeCode} · v${item.releaseVersion}`,
                        okText: intl.formatMessage({ id: 'common.confirm', defaultMessage: '确认' }),
                        cancelText: intl.formatMessage({ id: 'common.cancel', defaultMessage: '取消' }),
                        onOk: () => void handleRollback(item),
                      })
                    }
                  >
                    {intl.formatMessage({ id: 'page.localization.rollback', defaultMessage: '回滚' })}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <span>{`${item.localeCode} · v${item.releaseVersion}`}</span>
                      {item.active ? <Tag color="green">{intl.formatMessage({ id: 'page.localization.active', defaultMessage: '当前生效' })}</Tag> : null}
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={2}>
                      <Typography.Text type="secondary">{item.note || '-'}</Typography.Text>
                      <Typography.Text type="secondary">{item.publishedAt || '-'}</Typography.Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </Spin>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default LocalizationPage;
