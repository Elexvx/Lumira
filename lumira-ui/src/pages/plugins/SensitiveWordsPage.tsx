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

type SensitiveWordFormValues = {
  word: string;
  category?: string;
  action?: string;
};

type SensitiveWordsPageProps = {
  embedded?: boolean;
};

const ACTION_OPTIONS = [
  { label: '拦截', value: 'BLOCK' },
  { label: '仅记录', value: 'LOG_ONLY' },
];

const CATEGORY_OPTIONS = [
  { label: '默认', value: 'DEFAULT' },
  { label: '导入', value: 'IMPORTED' },
  { label: '自定义', value: 'CUSTOM' },
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
        message.success('敏感词已更新');
      } else {
        await request<SensitiveWordRecord>('/v1/sensitive-words', {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('敏感词已新增');
      }
      crud.reloadAndCloseEditor();
    } finally {
      setSaving(false);
    }
  };

  const deleteWord = useCallback(async (record: SensitiveWordRecord) => {
    confirmAction({
      title: '删除敏感词',
      content: `确定删除敏感词“${record.word}”吗？`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await request<boolean>(`/v1/sensitive-words/${record.id}`, {
          method: 'DELETE',
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('敏感词已删除');
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
      message.warning('请先选择导入文件');
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
        label: '编辑',
        disabled: !actionPermission.can('plugin:sensitive-words:manage'),
        onClick: () => openEdit(record),
      },
      {
        key: 'delete',
        label: '删除',
        danger: true,
        disabled: !actionPermission.can('plugin:sensitive-words:manage'),
        onClick: () => void deleteWord(record),
      },
    ];

    return [
      { title: '敏感词', dataIndex: 'word' },
      { title: '分类', dataIndex: 'category', search: false },
      {
        title: '执行操作',
        dataIndex: 'action',
        valueType: 'select',
        valueEnum: {
          BLOCK: { text: '拦截' },
          LOG_ONLY: { text: '仅记录' },
        },
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        search: false,
        width: 180,
      },
      {
        title: '操作',
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
            description={<Alert type="warning" showIcon message="敏感词拦截插件当前未启用" />}
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
              { key: 'import', label: <><UploadOutlined /> 批量导入</>, onClick: () => setImportOpen(true), permission: 'plugin:sensitive-words:import' },
              { key: 'create', label: <><PlusOutlined /> 新增敏感词</>, onClick: openCreate, permission: 'plugin:sensitive-words:manage', type: 'primary' },
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
        title={crud.drawer.editingId ? '编辑敏感词' : '新增敏感词'}
        open={crud.drawer.open}
        onClose={crud.drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: crud.drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canCreate, onClick: () => void saveWord() },
        ]}
      >
        <Form form={form} layout="vertical" initialValues={{ action: 'BLOCK', category: 'CUSTOM' }}>
          <Form.Item name="word" label="敏感词" rules={[{ required: true, message: '请输入敏感词' }, { max: 128, message: '长度不能超过 128 个字符' }]}>
            <Input maxLength={128} showCount />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Select options={CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item name="action" label="执行操作">
            <Select options={ACTION_OPTIONS} />
          </Form.Item>
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title="批量导入敏感词"
        open={importOpen}
        onClose={() => {
          setImportOpen(false);
          setFileList([]);
        }}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: () => setImportOpen(false) },
          { key: 'submit', label: '开始导入', type: 'primary', loading: importing, disabled: !canImport, onClick: () => void submitImport() },
        ]}
      >
        <Upload.Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">拖拽文件到这里，或点击选择文件</p>
          <Typography.Text type="secondary">支持 txt、md、docx、xls、xlsx</Typography.Text>
        </Upload.Dragger>
      </ManagementDrawer>
    </>
  );

  if (embedded) {
    return content;
  }

  return (
    <ManagementPage title="敏感词拦截">
      {content}
    </ManagementPage>
  );
};

export default SensitiveWordsPage;
