import { InboxOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Empty, Form, Input, Select, Switch, Tag, Typography, Upload } from 'antd';
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
  severity?: string;
  enabled?: boolean;
};

const SEVERITY_OPTIONS = [
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
];

const CATEGORY_OPTIONS = [
  { label: '默认', value: 'DEFAULT' },
  { label: '导入', value: 'IMPORTED' },
  { label: '自定义', value: 'CUSTOM' },
];

const renderEnabled = (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '停用'}</Tag>;

const SensitiveWordsPage = () => {
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
    form.setFieldsValue({ enabled: true, severity: 'MEDIUM', category: 'CUSTOM' });
  }, [crud.drawer, form]);

  const openEdit = useCallback((record: SensitiveWordRecord) => {
    crud.drawer.openEdit(record, record.id);
    form.setFieldsValue({
      word: record.word,
      category: record.category ?? 'CUSTOM',
      severity: record.severity ?? 'MEDIUM',
      enabled: record.enabled,
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

  const toggleEnabled = useCallback(async (record: SensitiveWordRecord) => {
    await request<boolean>(`/v1/sensitive-words/${record.id}/status`, {
      method: 'PATCH',
      data: { enabled: !record.enabled },
      ...API_OPTS.NO_REDIRECT,
    });
    message.success(record.enabled ? '敏感词已停用' : '敏感词已启用');
    crud.reloadTable();
  }, [crud]);

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
        key: 'toggle',
        label: record.enabled ? '停用' : '启用',
        disabled: !actionPermission.can('plugin:sensitive-words:manage'),
        onClick: () => void toggleEnabled(record),
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
        title: '等级',
        dataIndex: 'severity',
        valueType: 'select',
        valueEnum: {
          HIGH: { text: '高' },
          MEDIUM: { text: '中' },
          LOW: { text: '低' },
        },
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        valueType: 'select',
        valueEnum: {
          true: { text: '启用' },
          false: { text: '停用' },
        },
        render: (_, record) => renderEnabled(record.enabled),
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
  }, [actionPermission, deleteWord, openEdit, responsive.isMobile, toggleEnabled]);

  return (
    <ManagementPage title="敏感词拦截">
      <ManagementPageBody>
        {!pluginEnabled ? (
          <Empty
            description={<Alert type="warning" showIcon message="敏感词拦截插件当前未启用" />}
          />
        ) : (
        <ManagementTable<SensitiveWordRecord, { keyword?: string; enabled?: boolean }>
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
        <Form form={form} layout="vertical" initialValues={{ enabled: true, severity: 'MEDIUM', category: 'CUSTOM' }}>
          <Form.Item name="word" label="敏感词" rules={[{ required: true, message: '请输入敏感词' }, { max: 128, message: '长度不能超过 128 个字符' }]}>
            <Input maxLength={128} showCount />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Select options={CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item name="severity" label="等级">
            <Select options={SEVERITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
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
    </ManagementPage>
  );
};

export default SensitiveWordsPage;
