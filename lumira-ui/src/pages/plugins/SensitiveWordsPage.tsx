import { InboxOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Empty, Form, Input, Select, Typography, Upload } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import { buildTableRequest, type PagedResponse } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import { message } from '@/theme/antdFeedbackBridge';
import type { SensitiveWordImportResult, SensitiveWordRecord } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

type SensitiveWordFormValues = {
  word: string;
  category?: string;
  action?: string;
};

type SensitiveWordsPageProps = {
  embedded?: boolean;
};

const ACTION_OPTIONS = [
  { label: t('plugin.sensitiveWords.action.block'), value: 'BLOCK' },
  { label: t('plugin.sensitiveWords.action.logOnly'), value: 'LOG_ONLY' },
];

const CATEGORY_OPTIONS = [
  { label: t('plugin.sensitiveWords.category.default'), value: 'DEFAULT' },
  { label: t('plugin.sensitiveWords.category.imported'), value: 'IMPORTED' },
  { label: t('plugin.sensitiveWords.category.custom'), value: 'CUSTOM' },
];

const SensitiveWordsPage = ({ embedded = false }: SensitiveWordsPageProps) => {
  const { initialState } = useInitialStateModel();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const crud = useCrudPageState<SensitiveWordRecord>();
  const [form] = Form.useForm<SensitiveWordFormValues>();
  const [saving, setSaving] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const canCreate = actionPermission.can('plugin:sensitive-words:manage');
  const canImport = actionPermission.can('plugin:sensitive-words:import');
  const pluginEnabled = Boolean(initialState?.availablePlugins?.some((item) => item.pluginCode === 'sensitive-words'));

  const openCreate = useCallback(() => {
    crud.drawer.openCreate();
    form.resetFields();
    form.setFieldsValue({ action: 'BLOCK', category: 'CUSTOM' });
  }, [crud.drawer, form]);

  const openEdit = useCallback((record: SensitiveWordRecord) => {
    crud.drawer.openEdit(record, record.id);
    form.setFieldsValue({
      word: record.word,
      category: record.category ?? 'CUSTOM',
      action: record.action ?? 'BLOCK',
    });
  }, [crud.drawer, form]);

  const saveWord = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      if (crud.drawer.editingId) {
        await request<SensitiveWordRecord>(`/v1/sensitive-words/${crud.drawer.editingId}`, {
          method: 'PUT',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('plugin.sensitiveWords.message.updated'));
      } else {
        await request<SensitiveWordRecord>('/v1/sensitive-words', {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('plugin.sensitiveWords.message.created'));
      }
      crud.reloadAndCloseEditor();
    } finally {
      setSaving(false);
    }
  };

  const deleteWord = useCallback(async (record: SensitiveWordRecord) => {
    confirmAction({
      title: t('plugin.sensitiveWords.delete.title'),
      content: `确定删除敏感词“${record.word}”吗？`,
      okText: t('plugin.sensitiveWords.common.delete'),
      okButtonProps: { danger: true },
      onOk: async () => {
        await request<boolean>(`/v1/sensitive-words/${record.id}`, {
          method: 'DELETE',
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('plugin.sensitiveWords.message.deleted'));
        crud.reloadTable();
      },
    });
  }, [crud]);

  const uploadProps: UploadProps = {
    multiple: false,
    accept: '.txt,.md,.docx,.xls,.xlsx',
    beforeUpload: (file) => {
      setFileList([file]);
      return false;
    },
    onRemove: () => {
      setFileList([]);
    },
    fileList,
  };

  const submitImport = async () => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.warning(t('plugin.sensitiveWords.import.selectFileFirst'));
      return;
    }
    setImporting(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const result = await request<SensitiveWordImportResult>('/v1/sensitive-words/import', {
        method: 'POST',
        data: formData,
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(`导入完成：新增 ${result.imported}，重复 ${result.duplicated}，无效 ${result.invalid}`);
      setImportOpen(false);
      setFileList([]);
      crud.reloadTable();
    } finally {
      setImporting(false);
    }
  };

  const columns = useMemo<ProColumns<SensitiveWordRecord>[]>(() => {
    const actionItems = (record: SensitiveWordRecord): TableActionItem[] => [
      {
        key: 'edit',
        label: t('plugin.sensitiveWords.common.edit'),
        disabled: !actionPermission.can('plugin:sensitive-words:manage'),
        onClick: () => openEdit(record),
      },
      {
        key: 'delete',
        label: t('plugin.sensitiveWords.common.delete'),
        danger: true,
        disabled: !actionPermission.can('plugin:sensitive-words:manage'),
        onClick: () => void deleteWord(record),
      },
    ];

    return [
      { title: t('plugin.sensitiveWords.field.word'), dataIndex: 'word' },
      { title: t('plugin.sensitiveWords.field.category'), dataIndex: 'category', search: false },
      {
        title: t('plugin.sensitiveWords.field.action'),
        dataIndex: 'action',
        valueType: 'select',
        valueEnum: {
          BLOCK: { text: t('plugin.sensitiveWords.action.block') },
          LOG_ONLY: { text: t('plugin.sensitiveWords.action.logOnly') },
        },
      },
      {
        title: t('plugin.sensitiveWords.field.updatedAt'),
        dataIndex: 'updatedAt',
        search: false,
        width: 180,
      },
      {
        title: t('plugin.sensitiveWords.field.actions'),
        valueType: 'option',
        fixed: responsive.isMobile ? undefined : 'right',
        render: (_, record) => <TableActionBar isMobile={responsive.isMobile} items={actionItems(record)} />,
      },
    ];
  }, [actionPermission, deleteWord, openEdit, responsive.isMobile]);

  const content = (
    <>
      <ManagementPageBody>
        {!pluginEnabled ? (
          <Empty
            description={<Alert type="warning" showIcon message={t('plugin.sensitiveWords.disabled')} />}
          />
        ) : (
        <ManagementTable<SensitiveWordRecord, { keyword?: string }>
          rowKey="id"
          isMobile={responsive.isMobile}
          actionRef={crud.actionRef}
          columns={columns}
          search={searchConfig}
          options={{ reload: false }}
          toolBarRender={() =>
            buildToolbarButtons([
              { key: 'import', label: <><UploadOutlined /> {t('plugin.sensitiveWords.import.action')}</>, onClick: () => setImportOpen(true), permission: 'plugin:sensitive-words:import' },
              { key: 'create', label: <><PlusOutlined /> {t('plugin.sensitiveWords.create.title')}</>, onClick: openCreate, permission: 'plugin:sensitive-words:manage', type: 'primary' },
            ])
          }
          request={buildTableRequest(async (params) => {
            const result = await request<PagedResponse<SensitiveWordRecord>>('/v1/sensitive-words', {
              method: 'GET',
              params,
              ...API_OPTS.NO_REDIRECT,
            });
            return result;
          })}
        />
        )}
      </ManagementPageBody>

      <ManagementDrawer
        title={crud.drawer.editingId ? t('plugin.sensitiveWords.edit.title') : t('plugin.sensitiveWords.create.title')}
        open={crud.drawer.open}
        onClose={crud.drawer.close}
        footerActions={[
          { key: 'cancel', label: t('plugin.sensitiveWords.common.cancel'), onClick: crud.drawer.close },
          { key: 'save', label: t('plugin.sensitiveWords.common.save'), type: 'primary', loading: saving, disabled: !canCreate, onClick: () => void saveWord() },
        ]}
      >
        <Form form={form} layout="vertical" initialValues={{ action: 'BLOCK', category: 'CUSTOM' }}>
          <Form.Item name="word" label={t('plugin.sensitiveWords.field.word')} rules={[{ required: true, message: t('plugin.sensitiveWords.validation.wordRequired') }, { max: 128, message: t('plugin.sensitiveWords.validation.wordMaxLength') }]}>
            <Input maxLength={128} showCount />
          </Form.Item>
          <Form.Item name="category" label={t('plugin.sensitiveWords.field.category')}>
            <Select options={CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item name="action" label={t('plugin.sensitiveWords.field.action')}>
            <Select options={ACTION_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={t('plugin.sensitiveWords.import.title')}
        open={importOpen}
        onClose={() => {
          setImportOpen(false);
          setFileList([]);
        }}
        footerActions={[
          { key: 'cancel', label: t('plugin.sensitiveWords.common.cancel'), onClick: () => setImportOpen(false) },
          { key: 'submit', label: t('plugin.sensitiveWords.import.submit'), type: 'primary', loading: importing, disabled: !canImport, onClick: () => void submitImport() },
        ]}
      >
        <Upload.Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">{t('plugin.sensitiveWords.import.dropHint')}</p>
          <Typography.Text type="secondary">{t('plugin.sensitiveWords.import.formats')}</Typography.Text>
        </Upload.Dragger>
      </ManagementDrawer>
    </>
  );

  if (embedded) {
    return content;
  }

  return (
    <ManagementPage title={t('plugin.sensitiveWords.page.title')}>
      {content}
    </ManagementPage>
  );
};

export default SensitiveWordsPage;
