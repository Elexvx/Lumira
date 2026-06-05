import { history, formatMessage, useLocation } from '@umijs/max';
import { Button, Card, Checkbox, Descriptions, Dropdown, Empty, Form, Image, Input, InputNumber, Radio, Select, Space, Spin, Tag, Typography, Upload, message, theme } from 'antd';
import { CopyOutlined, DeleteOutlined, DownloadOutlined, DownOutlined, FileOutlined, InboxOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { FormInstance, UploadFile, UploadProps } from 'antd';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { FILE_ACCEPT, FILE_CATEGORY_OPTIONS, ALLOWED_UPLOAD_EXTENSIONS, MAX_UPLOAD_FILE_COUNT, PREVIEW_MODE_LABELS, buildPreviewAbsoluteUrl, formatDateTime, formatFileSize, renderTags, resolveFileTypeLabel, resolvePreviewMode } from '@/pages/files/fileCenter.utils';
import type { FileRenameStrategy, FileStorageProvider, FileStorageSpacePayload, FileStorageSpaceRecord, FileObjectRecord, FileStorageSpaceTestResult, PagedResult } from '@/types/api';
import { adaptPageResult } from '@/features/table/proTableRequest';
import { request, requestFile, type RequestOptions } from '@/services/common/request';
import { resolveSortParams } from '@/pages/files/fileCenter.utils';
import { confirmAction } from '@/utils/confirm';
import { copyTextToClipboard } from '@/utils/clipboard';

type BuildFileListRequestParams = {
  fileScope: 'mine' | 'tenant';
  activeBucket: string;
  requestOptions: RequestOptions;
};

type BuildStorageSpaceRequestParams = {
  requestOptions: RequestOptions;
};

type BuildFileObjectColumnsParams = {
  isMobile: boolean;
  isTenantScope: boolean;
  actionPermissionCanDelete: (permission?: string | string[]) => boolean;
  onOpenPreviewDrawer: (record: FileObjectRecord) => void;
  onDownload: (record: FileObjectRecord) => void;
  onCopyLink: (record: FileObjectRecord) => void;
  onDelete: (record: FileObjectRecord) => void;
};

const buildFileListRequest = ({ fileScope, activeBucket, requestOptions }: BuildFileListRequestParams) =>
  async (params: Record<string, unknown>, sorter: Record<string, unknown>) => {
    const { current, pageSize, keyword, category, previewMode: previewType } = params;
    const sortParams = resolveSortParams(sorter);
    const result = await request<PagedResult<FileObjectRecord>>('/v1/files', {
      method: 'GET',
      params: {
        keyword: typeof keyword === 'string' ? keyword : undefined,
        category: typeof category === 'string' ? category : undefined,
        previewMode: typeof previewType === 'string' ? previewType : undefined,
        bucket: activeBucket || undefined,
        scope: fileScope,
        pageNo: Number(current) || 1,
        pageSize: Number(pageSize) || 20,
        ...sortParams,
      },
      ...requestOptions,
    });
    return adaptPageResult(result);
  };

const buildStorageSpaceRequest = ({ requestOptions }: BuildStorageSpaceRequestParams) =>
  async (params: Record<string, unknown>) => {
    const { current, pageSize } = params;
    const result = await request<PagedResult<FileStorageSpaceRecord>>('/v1/files/storage-spaces', {
      method: 'GET',
      params: {
        pageNo: Number(current) || 1,
        pageSize: Number(pageSize) || 50,
      },
      ...requestOptions,
    });
    return adaptPageResult(result);
  };

const STORAGE_PROVIDER_OPTIONS: Array<{ label: string; value: FileStorageProvider }> = [
  { label: '本地存储', value: 'LOCAL' },
  { label: '阿里云 OSS', value: 'ALIYUN_OSS' },
  { label: '腾讯云 COS', value: 'TENCENT_COS' },
];

const STORAGE_RENAME_STRATEGY_OPTIONS: Array<{ label: string; value: FileRenameStrategy }> = [
  { label: '追加随机 ID', value: 'APPEND_RANDOM_ID' },
  { label: '随机字符串', value: 'RANDOM_STRING' },
  { label: '保持原名（同名文件将被覆盖）', value: 'KEEP_ORIGINAL' },
];

const STORAGE_PROVIDER_LABELS: Record<FileStorageProvider, string> = {
  LOCAL: '本地存储',
  ALIYUN_OSS: '阿里云 OSS',
  TENCENT_COS: '腾讯云 COS',
};

const defaultStoragePayload = (provider: FileStorageProvider): FileStorageSpacePayload => ({
  title: STORAGE_PROVIDER_LABELS[provider],
  provider,
  storageKey: provider === 'LOCAL' ? 'local' : undefined,
  rootPath: provider === 'LOCAL' ? 'storage/uploads/' : '',
  bucketName: '',
  endpoint: '',
  region: '',
  accessKeyId: '',
  accessKeySecret: '',
  renameStrategy: 'APPEND_RANDOM_ID',
  maxFileSizeMb: 20,
  allowedMimeTypes: '*',
  defaultStorage: false,
  retainFileOnRecordDelete: false,
  status: 'ENABLED',
});

const buildFileObjectColumns = ({
  isMobile,
  isTenantScope,
  actionPermissionCanDelete,
  onOpenPreviewDrawer,
  onDownload,
  onCopyLink,
  onDelete,
}: BuildFileObjectColumnsParams): ProColumns<FileObjectRecord>[] => {
  const searchColumns = [
    {
      title: formatMessage({ id: 'system.files.search.keywordLabel', defaultMessage: 'Keyword' }),
      dataIndex: 'keyword',
      hideInTable: true,
      renderFormItem: () => <Input allowClear placeholder={formatMessage({ id: 'system.files.search.keyword', defaultMessage: 'File name, tags, remark' })} />,
    },
    {
      title: formatMessage({ id: 'system.files.search.categoryLabel', defaultMessage: 'Category' }),
      key: 'categorySearch',
      dataIndex: 'category',
      hideInTable: true,
      renderFormItem: () => <Input allowClear placeholder={formatMessage({ id: 'system.files.search.category', defaultMessage: 'Enter category name' })} />,
    },
  ] as unknown as ProColumns<FileObjectRecord>[];

  const dataColumns: ProColumns<FileObjectRecord>[] = [
    {
      title: formatMessage({ id: 'system.files.field.fileName', defaultMessage: 'File name' }),
      dataIndex: 'originalFileName',
      width: 260,
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => (
        <Space size={8} wrap={false}>
          <FileOutlined />
          <Typography.Link
            title={record.originalFileName}
            style={{ maxWidth: isMobile ? 180 : 300 }}
            ellipsis
            onClick={() => void onOpenPreviewDrawer(record)}
          >
            {record.originalFileName}
          </Typography.Link>
        </Space>
      ),
    },
    {
      title: formatMessage({ id: 'system.files.field.type', defaultMessage: 'Type' }),
      dataIndex: 'fileExtension',
      width: 100,
      render: (_: unknown, record: FileObjectRecord) => <Tag>{resolveFileTypeLabel(record.fileExtension)}</Tag>,
    },
    {
      title: formatMessage({ id: 'system.files.field.size', defaultMessage: 'Size' }),
      dataIndex: 'fileSizeBytes',
      width: 110,
      sorter: true,
      render: (_: unknown, record: FileObjectRecord) => record.fileSizeLabel || formatFileSize(record.fileSizeBytes),
    },
    {
      title: formatMessage({ id: 'system.files.field.category', defaultMessage: 'Category' }),
      key: 'categoryColumn',
      dataIndex: 'category',
      width: 160,
      ...(isMobile ? { responsive: ['md', 'lg', 'xl', 'xxl'] as const } : {}),
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => (record.category ? <Tag color="blue">{record.category}</Tag> : '-'),
    },
    {
      title: formatMessage({ id: 'system.files.field.tags', defaultMessage: 'Tags' }),
      dataIndex: 'tags',
      width: 180,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => renderTags(record.tags),
    },
    {
      title: formatMessage({ id: 'system.files.field.uploader', defaultMessage: 'Uploader' }),
      dataIndex: 'uploadedByName',
      width: 120,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => record.uploadedByName || '-',
    },
    {
      title: formatMessage({ id: 'system.files.field.uploadTime', defaultMessage: 'Upload time' }),
      dataIndex: 'createdAt',
      width: 170,
      responsive: ['md', 'lg', 'xl', 'xxl'],
      sorter: true,
      render: (_: unknown, record: FileObjectRecord) => formatDateTime(record.createdAt),
    },
  ];

  const actionColumn: ProColumns<FileObjectRecord> = {
    title: formatMessage({ id: 'system.files.field.actions', defaultMessage: 'Actions' }),
    valueType: 'option',
    fixed: 'right',
    width: 220,
    render: (_: unknown, record: FileObjectRecord) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          {
            key: 'download',
            label: formatMessage({ id: 'common.download', defaultMessage: 'Download' }),
            icon: <DownloadOutlined />,
            onClick: () => void onDownload(record),
          },
          {
            key: 'copy',
            label: formatMessage({ id: 'common.copyLink', defaultMessage: 'Copy link' }),
            icon: <CopyOutlined />,
            onClick: () => void onCopyLink(record),
          },
          ...(actionPermissionCanDelete(isTenantScope ? 'system:file:manage:delete' : 'system:file:delete')
            ? [
                {
                  key: 'delete',
                  label: formatMessage({ id: 'common.delete', defaultMessage: 'Delete' }),
                  icon: <DeleteOutlined />,
                  danger: true,
                  onClick: () => onDelete(record),
                },
              ]
            : []),
        ]}
      />
    ),
  };

  return [...searchColumns, ...dataColumns, actionColumn];
};

const FileStorageDrawer = ({
  open,
  mode,
  form,
  editingStorageSpace,
  canManageStorage,
  saving,
  provider,
  showRemoteStorageFields,
  providerOptions,
  renameStrategyOptions,
  onClose,
  onSave,
}: {
  open: boolean;
  mode: 'create' | 'edit';
  form: FormInstance<FileStorageSpacePayload>;
  editingStorageSpace: FileStorageSpaceRecord | null;
  canManageStorage: boolean;
  saving: boolean;
  provider: FileStorageProvider | undefined;
  showRemoteStorageFields: boolean;
  providerOptions: Array<{ label: string; value: FileStorageProvider }>;
  renameStrategyOptions: Array<{ label: string; value: FileRenameStrategy }>;
  onClose: () => void;
  onSave: () => void;
}) => (
  <ManagementDrawer
    title={mode === 'edit' ? `编辑 - ${editingStorageSpace?.title || '存储空间'}` : '新增存储空间'}
    open={open}
    onClose={onClose}
    footerActions={[
      { key: 'cancel', label: '取消', onClick: onClose },
      { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canManageStorage, onClick: onSave },
    ]}
  >
    <Form form={form} layout="vertical" initialValues={{ provider: 'LOCAL', renameStrategy: 'APPEND_RANDOM_ID' }}>
      <>
        <Form.Item name="provider" label="存储类型" rules={[{ required: true, message: '请选择存储类型' }]}>
          <Select options={providerOptions} disabled={mode === 'edit'} />
        </Form.Item>
        <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
          <Input placeholder="Local storage" />
        </Form.Item>
        <Form.Item
          name="storageKey"
          label="存储空间标识"
          rules={[
            { required: true, message: '请输入存储空间标识' },
            { pattern: /^[a-z][a-z0-9_]*$/, message: '必须以英文字母开头，仅支持英文、数字和下划线' },
          ]}
          extra="随机生成，可修改。支持英文、数字和下划线，必须以英文字母开头。"
        >
          <Input placeholder="local" disabled={mode === 'edit'} />
        </Form.Item>
        {provider === 'LOCAL' ? (
          <Form.Item name="rootPath" label="路径">
            <Input addonAfter="/" placeholder="storage/uploads" />
          </Form.Item>
        ) : null}
        {showRemoteStorageFields ? (
          <>
            <Form.Item name="bucketName" label="Bucket" rules={[{ required: true, message: '请输入 Bucket' }]}>
              <Input placeholder="对象存储 Bucket 名称" />
            </Form.Item>
            <Form.Item name="endpoint" label="Endpoint" rules={[{ required: true, message: '请输入 Endpoint' }]}>
              <Input placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
            </Form.Item>
            <Form.Item name="region" label="Region">
              <Input placeholder="cn-hangzhou / ap-guangzhou / us-east-1" />
            </Form.Item>
            <Form.Item name="accessKeyId" label="Access Key ID">
              <Input placeholder="对象存储访问密钥 ID" />
            </Form.Item>
            <Form.Item name="accessKeySecret" label="Access Key Secret" extra={editingStorageSpace?.secretConfigured ? '留空则保持现有密钥。' : undefined}>
              <Input.Password placeholder="留空则不修改已保存密钥" />
            </Form.Item>
          </>
        ) : null}
        <Form.Item name="renameStrategy" label="重命名" rules={[{ required: true, message: '请选择重命名策略' }]}>
          <Radio.Group options={renameStrategyOptions} />
        </Form.Item>
        <Form.Item name="maxFileSizeMb" label="文件大小限制" rules={[{ required: true, message: '请输入文件大小限制' }]}>
          <InputNumber min={1} addonAfter="MB" style={{ width: 220 }} />
        </Form.Item>
        <Form.Item name="allowedMimeTypes" label="允许的文件类型（MIME 格式）">
          <Input placeholder="*" />
        </Form.Item>
        <Form.Item name="defaultStorage" valuePropName="checked">
          <Checkbox disabled={Boolean(editingStorageSpace?.defaultStorage)}>默认存储空间</Checkbox>
        </Form.Item>
        <Form.Item name="retainFileOnRecordDelete" valuePropName="checked">
          <Checkbox>删除文件记录时保留文件</Checkbox>
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            options={[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
            ]}
          />
        </Form.Item>
      </>
    </Form>
  </ManagementDrawer>
);

const FileUploadDrawer = ({
  open,
  uploading,
  canUpload,
  onClose,
  onSubmit,
}: {
  open: boolean;
  uploading: boolean;
  canUpload: boolean;
  onClose: () => void;
  onSubmit: (payload: { files: File[]; values: { category?: string; tags?: string; remark?: string } }) => Promise<void>;
}) => {
  const [form] = Form.useForm<{
    category?: string | string[];
    tags?: string;
    remark?: string;
  }>();
  const [uploadFileList, setUploadFileList] = useState<UploadFile[]>([]);

  const uploadDraggerProps: UploadProps = useMemo(
    () => ({
      multiple: true,
      accept: FILE_ACCEPT,
      fileList: uploadFileList,
      beforeUpload: (file) => {
        const extension = file.name.split('.').pop()?.toLowerCase();
        if (!extension || !ALLOWED_UPLOAD_EXTENSIONS.includes(extension)) {
          message.error('仅支持 PDF、Word、Excel 和 PPT 文件');
          return Upload.LIST_IGNORE;
        }
        return false;
      },
      onChange: (info) => {
        if (info.fileList.length > MAX_UPLOAD_FILE_COUNT) {
          message.warning(`一次最多上传 ${MAX_UPLOAD_FILE_COUNT} 个文件`);
        }
        setUploadFileList(info.fileList.slice(-MAX_UPLOAD_FILE_COUNT));
      },
      onRemove: (file) => {
        setUploadFileList((prev) => prev.filter((item) => item.uid !== file.uid));
        return true;
      },
    }),
    [uploadFileList],
  );

  const handleClose = () => {
    setUploadFileList([]);
    form.resetFields();
    onClose();
  };

  const handleSubmit = async () => {
    if (!canUpload) {
      message.warning('存储空间不支持从后台直接上传');
      return;
    }
    const values = await form.validateFields();
    const files = uploadFileList.map((item) => item.originFileObj).filter(Boolean) as File[];
    if (!files.length) {
      message.warning('请先选择文件');
      return;
    }
    if (files.length > MAX_UPLOAD_FILE_COUNT) {
      message.warning(`一次最多上传 ${MAX_UPLOAD_FILE_COUNT} 个文件`);
      return;
    }
    const category = Array.isArray(values.category) ? values.category.filter(Boolean).join(',') : values.category;
    await onSubmit({
      files,
      values: {
        category,
        tags: values.tags,
        remark: values.remark,
      },
    });
    setUploadFileList([]);
    form.resetFields();
  };

  return (
    <ManagementDrawer
      title="Upload document"
      open={open}
      onClose={handleClose}
      footerActions={[
        { key: 'cancel', label: 'Cancel', onClick: handleClose },
        { key: 'upload', label: 'Start upload', type: 'primary', loading: uploading, disabled: !canUpload, onClick: () => void handleSubmit() },
      ]}
    >
      <Form form={form} layout="vertical">
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card title="Select files" bodyStyle={{ padding: 0 }} style={{ borderRadius: 8 }}>
            <Upload.Dragger {...uploadDraggerProps} style={{ borderRadius: 8 }}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">Click or drag files here to upload</p>
              <p className="ant-upload-hint">Only PDF, Word, Excel, and PPT are allowed. Up to 5 files at a time.</p>
            </Upload.Dragger>
          </Card>

          <Form.Item label="Category" name="category">
            <Select
              allowClear
              mode="tags"
              tokenSeparators={[',', '，']}
              options={FILE_CATEGORY_OPTIONS}
              placeholder="e.g. policies, business materials, contracts"
            />
          </Form.Item>
          <Form.Item label="Tags" name="tags" extra="Separate multiple tags with commas">
            <Input placeholder="e.g. ops,contract,archive" allowClear />
          </Form.Item>
          <Form.Item label="Remark" name="remark">
            <Input.TextArea rows={4} placeholder="Optional, write a short note about the file" maxLength={512} showCount />
          </Form.Item>
        </Space>
      </Form>
    </ManagementDrawer>
  );
};

const FilePreviewDrawer = ({
  open,
  record,
  previewMode,
  previewMeta,
  previewAbsoluteUrl,
  previewText,
  filePreviewUrl,
  loading,
  textLoading,
  fileLoading,
  isMobile,
  backgroundColor,
  containerBackgroundColor,
  onClose,
  onCopyLink,
  onDownload,
}: {
  open: boolean;
  record: FileObjectRecord | null;
  previewMode: 'IMAGE' | 'PDF' | 'TEXT' | 'UNSUPPORTED';
  previewMeta: { color: string; text: string };
  previewAbsoluteUrl: string;
  previewText: string;
  filePreviewUrl: string;
  loading: boolean;
  textLoading: boolean;
  fileLoading: boolean;
  isMobile: boolean;
  backgroundColor: string;
  containerBackgroundColor: string;
  onClose: () => void;
  onCopyLink: (record: FileObjectRecord) => void;
  onDownload: (record: FileObjectRecord) => void;
}) => (
  <ManagementDrawer
    title={record ? record.originalFileName : 'File preview'}
    open={open}
    onClose={onClose}
    footer={
      <div className="saas-drawer-footer">
        <Space wrap>
          <Button icon={<CopyOutlined />} onClick={() => record && void onCopyLink(record)} disabled={!record}>
            Copy link
          </Button>
          <Button icon={<DownloadOutlined />} onClick={() => record && void onDownload(record)} disabled={!record}>
            Download
          </Button>
          <Button onClick={onClose}>Close</Button>
        </Space>
      </div>
    }
  >
    {record ? (
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Descriptions bordered column={isMobile ? 1 : 2} size="small">
          <Descriptions.Item label="File name">{record.originalFileName}</Descriptions.Item>
          <Descriptions.Item label="Type">{resolveFileTypeLabel(record.fileExtension)}</Descriptions.Item>
          <Descriptions.Item label="Size">{record.fileSizeLabel || formatFileSize(record.fileSizeBytes)}</Descriptions.Item>
          <Descriptions.Item label="Preview">{<Tag color={previewMeta.color}>{previewMeta.text}</Tag>}</Descriptions.Item>
          <Descriptions.Item label="Category">{record.category || '-'}</Descriptions.Item>
          <Descriptions.Item label="Uploader">{record.uploadedByName || '-'}</Descriptions.Item>
          <Descriptions.Item label="Tags" span={2}>
            {renderTags(record.tags)}
          </Descriptions.Item>
          <Descriptions.Item label="Remark" span={2}>
            {record.remark || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Upload time">{formatDateTime(record.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Download link">
            <Typography.Text copyable={{ text: previewAbsoluteUrl }}>{previewAbsoluteUrl || '-'}</Typography.Text>
          </Descriptions.Item>
        </Descriptions>

        <Spin
          spinning={loading || textLoading || fileLoading}
          tip={fileLoading ? 'Loading file preview' : textLoading ? 'Loading text content' : 'Loading file details'}
        >
          <div style={{ minHeight: isMobile ? 240 : 520, padding: 16, background: backgroundColor }}>
            {previewMode === 'IMAGE' ? (
              filePreviewUrl ? (
                <Image src={filePreviewUrl} alt={record.originalFileName} preview={false} style={{ width: '100%', maxHeight: isMobile ? 360 : 560, objectFit: 'contain' }} />
              ) : null
            ) : null}
            {previewMode === 'PDF' ? (
              filePreviewUrl ? (
                <iframe
                  title={record.originalFileName}
                  src={`${filePreviewUrl}#view=FitH`}
                  style={{ width: '100%', height: isMobile ? 360 : 560, border: 0, background: containerBackgroundColor }}
                />
              ) : null
            ) : null}
            {previewMode === 'TEXT' ? (
              <Typography.Paragraph
                style={{
                  marginBottom: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: isMobile ? 360 : 560,
                  overflow: 'auto',
                }}
              >
                {previewText || 'No text content yet'}
              </Typography.Paragraph>
            ) : null}
            {previewMode === 'UNSUPPORTED' ? (
              <Empty
                description={
                  <Typography.Text>
                    This format is not supported for online preview yet
                  </Typography.Text>
                }
              />
            ) : null}
          </div>
        </Spin>
      </Space>
    ) : (
      <Empty description="No file details yet" />
    )}
  </ManagementDrawer>
);

const SystemFilesPage = () => {
  const { token } = theme.useToken();
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | null>(null);
  const storageActionRef = useRef<ActionType | null>(null);
  const [uploadDrawerOpen, setUploadDrawerOpen] = useState(false);
  const [uploading, setUploading] = useState(false);

  const isTenantScope = useMemo(
    () => location.pathname === '/settings/files/all' || location.pathname === '/files/all' || location.pathname === '/system/files/all',
    [location.pathname],
  );
  const activeBucket = useMemo(
    () => (isTenantScope ? new URLSearchParams(location.search).get('bucket') || '' : ''),
    [isTenantScope, location.search],
  );
  const fileScope: 'mine' | 'tenant' = isTenantScope ? 'tenant' : 'mine';
  const pageTitle = isTenantScope
    ? activeBucket
      ? `存储空间文件 / ${activeBucket}`
      : '文件管理器'
    : formatMessage({ id: 'system.files.title.my', defaultMessage: 'My Files' });
  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
    }),
    [],
  );
  const scopeParams = useMemo(() => ({ scope: fileScope }), [fileScope]);
  const canManageStorage = actionPermission.can('system:file:manage');
  const canDeleteStorage = actionPermission.can('system:file:manage:delete');
  const canUploadFile = actionPermission.can('system:file:upload');
  const canUploadInCurrentScope = !isTenantScope && canUploadFile;
  const buildToolbarActions = actionPermission.buildToolbarActions;
  const previewBackgroundColor = token.colorFillQuaternary;
  const previewContainerBackgroundColor = token.colorBgContainer;
  const canDeleteFile = actionPermission.can;
  const [storageDrawerOpen, setStorageDrawerOpen] = useState(false);
  const [storageDrawerMode, setStorageDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingStorageSpace, setEditingStorageSpace] = useState<FileStorageSpaceRecord | null>(null);
  const [storageSaving, setStorageSaving] = useState(false);
  const [storageForm] = Form.useForm<FileStorageSpacePayload>();
  const storageProvider = Form.useWatch('provider', storageForm) as FileStorageProvider | undefined;
  const showRemoteStorageFields = Boolean(storageProvider && storageProvider !== 'LOCAL');

  const openStorageDrawer = useCallback(
    (provider: FileStorageProvider, record?: FileStorageSpaceRecord) => {
      setStorageDrawerMode(record ? 'edit' : 'create');
      setEditingStorageSpace(record || null);
      storageForm.setFieldsValue(
        record
          ? {
              title: record.title,
              storageKey: record.storageKey,
              provider: record.provider,
              rootPath: record.rootPath || '',
              bucketName: record.bucketName || '',
              endpoint: record.endpoint || '',
              region: record.region || '',
              accessKeyId: record.accessKeyId || '',
              accessKeySecret: '',
              renameStrategy: record.renameStrategy,
              maxFileSizeMb: record.maxFileSizeMb,
              allowedMimeTypes: record.allowedMimeTypes || '*',
              defaultStorage: Boolean(record.defaultStorage),
              retainFileOnRecordDelete: Boolean(record.retainFileOnRecordDelete),
              status: record.status,
            }
          : defaultStoragePayload(provider),
      );
      setStorageDrawerOpen(true);
    },
    [storageForm],
  );
  const closeStorageDrawer = useCallback(() => {
    setStorageDrawerOpen(false);
    setEditingStorageSpace(null);
    setStorageSaving(false);
    storageForm.resetFields();
  }, [storageForm]);
  const handleSaveStorageSpace = useCallback(async () => {
      const values = (await storageForm.validateFields()) as FileStorageSpacePayload;
      setStorageSaving(true);
      try {
        if (storageDrawerMode === 'edit' && editingStorageSpace) {
          await request<FileStorageSpaceRecord>(`/v1/files/storage-spaces/${editingStorageSpace.id}`, {
            method: 'PUT',
            data: values,
            ...requestOptions,
          });
          message.success('存储空间已更新');
        } else {
          await request<FileStorageSpaceRecord>('/v1/files/storage-spaces', {
            method: 'POST',
            data: values,
            ...requestOptions,
          });
          message.success('存储空间已创建');
        }
      closeStorageDrawer();
      storageActionRef.current?.reload();
    } finally {
      setStorageSaving(false);
    }
  }, [closeStorageDrawer, editingStorageSpace, requestOptions, storageDrawerMode, storageForm]);
  const handleDeleteStorageSpace = useCallback(
    (record: FileStorageSpaceRecord) => {
      confirmAction({
        title: '删除存储空间',
        content: `确认删除存储空间「${record.title}」吗？仅空存储空间可以删除。`,
        okText: '确认删除',
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/files/storage-spaces/${record.id}`, {
            method: 'DELETE',
            ...requestOptions,
          });
          message.success('存储空间已删除');
          storageActionRef.current?.reload();
        },
      });
    },
    [requestOptions],
  );
  const handleTestStorageSpace = useCallback(
    async (record: FileStorageSpaceRecord) => {
      const result = await request<FileStorageSpaceTestResult>(`/v1/files/storage-spaces/${record.id}/test`, {
        method: 'POST',
        ...requestOptions,
      });
      if (result.status === 'UP') {
        message.success(result.message || '存储空间连接正常');
        return;
      }
      message.warning(result.message || '存储空间连接异常');
    },
    [requestOptions],
  );
  const enterStorageSpace = useCallback((record: FileStorageSpaceRecord) => {
    history.push(`/settings/files/all?bucket=${encodeURIComponent(record.storageKey)}`);
  }, []);
  const storageColumns = useMemo<ProColumns<FileStorageSpaceRecord>[]>(
    () => [
      {
        title: '标题',
        dataIndex: 'title',
        width: 260,
        render: (_, record) => (
          <Typography.Link onClick={() => enterStorageSpace(record)}>
            {record.title}
          </Typography.Link>
        ),
      },
      {
        title: '存储空间标识',
        dataIndex: 'storageKey',
        width: 220,
      },
      {
        title: '类型',
        dataIndex: 'provider',
        width: 160,
        render: (_, record) => <Tag>{STORAGE_PROVIDER_LABELS[record.provider] || record.provider}</Tag>,
      },
      {
        title: '默认存储空间',
        dataIndex: 'defaultStorage',
        width: 160,
        render: (_, record) => (record.defaultStorage ? <span style={{ color: token.colorSuccess, fontSize: 20 }}>✓</span> : '-'),
      },
      {
        title: '文件数',
        dataIndex: 'fileCount',
        width: 120,
        render: (_, record) => record.fileCount ?? 0,
      },
      {
        title: '容量',
        dataIndex: 'totalSizeLabel',
        width: 120,
        render: (_, record) => record.totalSizeLabel || formatFileSize(record.totalSizeBytes),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: 'right',
        width: 180,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              {
                key: 'edit',
                label: '编辑',
                disabled: !canManageStorage,
                onClick: () => openStorageDrawer(record.provider, record),
              },
              {
                key: 'test',
                label: '测试',
                disabled: !canManageStorage,
                onClick: () => void handleTestStorageSpace(record),
              },
              {
                key: 'delete',
                label: '删除',
                danger: true,
                disabled: !canDeleteStorage || Boolean(record.defaultStorage),
                onClick: () => handleDeleteStorageSpace(record),
              },
            ]}
          />
        ),
      },
    ],
    [canDeleteStorage, canManageStorage, enterStorageSpace, handleDeleteStorageSpace, handleTestStorageSpace, openStorageDrawer, responsive.isMobile, token.colorSuccess],
  );
  const addStorageItems = useMemo(
    () =>
      STORAGE_PROVIDER_OPTIONS.map((item) => ({
        key: item.value,
        label: item.label,
        onClick: () => openStorageDrawer(item.value),
      })),
    [openStorageDrawer],
  );
  const storageToolbar = useMemo(
    () =>
      buildToolbarActions([
        {
          value: (
            <Button key="delete" icon={<DeleteOutlined />} size={responsive.isMobile ? 'small' : 'middle'} disabled={!canDeleteStorage}>
              删除
            </Button>
          ),
        },
        {
          value: (
            <Dropdown key="add" menu={{ items: addStorageItems }} trigger={['click']}>
              <Button type="primary" icon={<PlusOutlined />} size={responsive.isMobile ? 'small' : 'middle'} disabled={!canManageStorage}>
                添加 <DownOutlined />
              </Button>
            </Dropdown>
          ),
        },
      ]),
    [addStorageItems, buildToolbarActions, canDeleteStorage, canManageStorage, responsive.isMobile],
  );
  const storageSpaceRequest = useMemo(
    () => buildStorageSpaceRequest({ requestOptions }),
    [requestOptions],
  );
  const storageProviderOptions = STORAGE_PROVIDER_OPTIONS;
  const storageRenameStrategyOptions = STORAGE_RENAME_STRATEGY_OPTIONS;
  const [previewDrawerOpen, setPreviewDrawerOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewRecord, setPreviewRecord] = useState<FileObjectRecord | null>(null);
  const [previewTextLoading, setPreviewTextLoading] = useState(false);
  const [previewText, setPreviewText] = useState('');
  const [filePreviewLoading, setFilePreviewLoading] = useState(false);
  const [filePreviewUrl, setFilePreviewUrl] = useState('');
  const filePreviewUrlRef = useRef('');
  const previewMode = previewRecord ? resolvePreviewMode(previewRecord) : 'UNSUPPORTED';
  const previewMeta = previewRecord ? PREVIEW_MODE_LABELS[previewMode] : PREVIEW_MODE_LABELS.UNSUPPORTED;
  const previewAbsoluteUrl = useMemo(() => (previewRecord ? buildPreviewAbsoluteUrl(previewRecord) : ''), [previewRecord]);

  const clearFilePreviewUrl = useCallback(() => {
    if (filePreviewUrlRef.current) {
      window.URL.revokeObjectURL(filePreviewUrlRef.current);
      filePreviewUrlRef.current = '';
    }
    setFilePreviewUrl('');
  }, []);

  useEffect(() => {
    if (!previewDrawerOpen || !previewRecord) {
      return;
    }

    if (previewMode !== 'TEXT') {
      setPreviewText('');
      setPreviewTextLoading(false);
      return;
    }

    let active = true;
    setPreviewTextLoading(true);
    void requestFile(`/v1/files/${previewRecord.id}/preview`, {
      method: 'GET',
      params: scopeParams,
      ...requestOptions,
    })
      .then((blob) => blob.text())
      .then((text) => {
        if (active) {
          setPreviewText(text);
        }
      })
      .catch(() => {
        if (active) {
          message.error(formatMessage({ id: 'system.files.textPreviewFailed', defaultMessage: 'Text preview failed to load' }));
        }
      })
      .finally(() => {
        if (active) {
          setPreviewTextLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [previewDrawerOpen, previewMode, previewRecord, requestOptions, scopeParams]);

  useEffect(() => {
    if (!previewDrawerOpen || !previewRecord) {
      return;
    }

    if (previewMode !== 'PDF' && previewMode !== 'IMAGE') {
      setFilePreviewLoading(false);
      clearFilePreviewUrl();
      return;
    }

    let active = true;
    setFilePreviewLoading(true);
    clearFilePreviewUrl();
    void requestFile(`/v1/files/${previewRecord.id}/preview`, {
      method: 'GET',
      params: scopeParams,
      ...requestOptions,
    })
      .then((blob) => {
        const objectUrl = window.URL.createObjectURL(blob);
        if (active) {
          if (filePreviewUrlRef.current) {
            window.URL.revokeObjectURL(filePreviewUrlRef.current);
          }
          filePreviewUrlRef.current = objectUrl;
          setFilePreviewUrl(objectUrl);
        } else {
          window.URL.revokeObjectURL(objectUrl);
        }
      })
      .catch(() => {
        if (active) {
          message.error(formatMessage({ id: 'system.files.pdfPreviewFailed', defaultMessage: 'File preview failed to load' }));
        }
      })
      .finally(() => {
        if (active) {
          setFilePreviewLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [clearFilePreviewUrl, previewDrawerOpen, previewMode, previewRecord, requestOptions, scopeParams]);

  const openPreviewDrawer = useCallback(
    async (record: FileObjectRecord) => {
      setPreviewDrawerOpen(true);
      setPreviewRecord(record);
      setPreviewText('');
      clearFilePreviewUrl();
      setPreviewLoading(true);
      try {
        const detail = await request<FileObjectRecord>(`/v1/files/${record.id}`, {
          method: 'GET',
          params: scopeParams,
          ...requestOptions,
        });
        setPreviewRecord(detail);
      } catch {
        // keep the list record when details fail
      } finally {
        setPreviewLoading(false);
      }
    },
    [clearFilePreviewUrl, requestOptions, scopeParams],
  );

  const closePreviewDrawer = useCallback(() => {
    setPreviewDrawerOpen(false);
    setPreviewLoading(false);
    setPreviewTextLoading(false);
    setFilePreviewLoading(false);
    setPreviewText('');
    clearFilePreviewUrl();
    setPreviewRecord(null);
  }, [clearFilePreviewUrl]);

  const handleDownload = useCallback(
    async (record: FileObjectRecord) => {
      try {
        const blob = await requestFile(`/v1/files/${record.id}/download`, {
          method: 'GET',
          params: scopeParams,
          ...requestOptions,
        });
        const downloadUrl = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = downloadUrl;
        anchor.download = record.originalFileName || `${record.id}.${record.fileExtension}`;
        anchor.rel = 'noreferrer';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.setTimeout(() => window.URL.revokeObjectURL(downloadUrl), 1500);
      } catch {
        message.error(formatMessage({ id: 'system.files.downloadFailed', defaultMessage: 'Download failed' }));
      }
    },
    [requestOptions, scopeParams],
  );
  const handleCopyLink = useCallback(async (record: FileObjectRecord) => {
    const url = buildPreviewAbsoluteUrl(record);
    try {
      await copyTextToClipboard(url);
      message.success(formatMessage({ id: 'system.files.copySuccess', defaultMessage: 'Link copied' }));
    } catch {
      message.error(formatMessage({ id: 'system.files.copyFailed', defaultMessage: 'Copy failed, please copy the link manually' }));
    }
  }, []);
  const handleDelete = useCallback(
    (record: FileObjectRecord) => {
      confirmAction({
        title: formatMessage({ id: 'system.files.delete.title', defaultMessage: 'Delete file' }),
        content: isTenantScope
          ? formatMessage(
              {
                id: 'system.files.delete.confirmTenant',
                defaultMessage: 'Delete file "{name}"? This will remove the file and its record, and may affect avatars, logos, and other assets referencing it.',
              },
              { name: record.originalFileName },
            )
          : formatMessage(
              {
                id: 'system.files.delete.confirmMine',
                defaultMessage: 'Delete file "{name}"? This will remove the file and its record.',
              },
              { name: record.originalFileName },
            ),
        okText: formatMessage({ id: 'system.files.delete.okText', defaultMessage: 'Confirm delete' }),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/files/${record.id}`, {
            method: 'DELETE',
            params: scopeParams,
            ...requestOptions,
          });
          message.success(formatMessage({ id: 'system.files.deleteSuccess', defaultMessage: 'File deleted' }));
          actionRef.current?.reload();
          if (previewRecord?.id === record.id) {
            closePreviewDrawer();
          }
        },
      });
    },
    [actionRef, closePreviewDrawer, isTenantScope, previewRecord, requestOptions, scopeParams],
  );

  const openUploadDrawer = useCallback(() => {
    if (!canUploadInCurrentScope) {
      message.warning(
        formatMessage({
          id: 'system.files.bucketUploadDisabled',
          defaultMessage: 'Storage buckets do not support uploads from the admin console',
        }),
      );
      return;
    }
    setUploadDrawerOpen(true);
  }, [canUploadInCurrentScope]);
  const closeUploadDrawer = useCallback(() => {
    setUploadDrawerOpen(false);
    setUploading(false);
  }, []);
  const handleUploadSubmit = useCallback(
    async ({ files, values }: { files: File[]; values: { category?: string; tags?: string; remark?: string } }) => {
      setUploading(true);
      try {
        let uploadedCount = 0;
        for (const file of files) {
          const formData = new FormData();
          formData.append('file', file);
          if (values.category) {
            formData.append('category', values.category);
          }
          if (values.tags) {
            formData.append('tags', values.tags);
          }
          if (values.remark) {
            formData.append('remark', values.remark);
          }
          if (activeBucket) {
            formData.append('bucket', activeBucket);
          }
          await request<FileObjectRecord>('/v1/files/upload', {
            method: 'POST',
            headers: {},
            data: formData,
            ...requestOptions,
          });
          uploadedCount += 1;
        }
        message.success(
          formatMessage({ id: 'system.files.uploadSuccess', defaultMessage: 'Uploaded {count} file(s)' }, { count: uploadedCount }),
        );
        closeUploadDrawer();
        actionRef.current?.reload();
      } catch {
        message.error(formatMessage({ id: 'system.files.uploadFailed', defaultMessage: 'File upload failed, please try again later' }));
      } finally {
        setUploading(false);
      }
    },
    [activeBucket, closeUploadDrawer, requestOptions],
  );

  const fileColumns = buildFileObjectColumns({
    isMobile: responsive.isMobile,
    isTenantScope,
    actionPermissionCanDelete: canDeleteFile,
    onOpenPreviewDrawer: openPreviewDrawer,
    onDownload: handleDownload,
    onCopyLink: handleCopyLink,
    onDelete: handleDelete,
  });

  const actionToolbar = useMemo(
    () =>
      buildToolbarActions([
        {
          hidden: !canUploadInCurrentScope,
          value: (
            <Button
              key="upload"
              type="primary"
              icon={<UploadOutlined />}
              size={responsive.isMobile ? 'small' : 'middle'}
              disabled={!canUploadInCurrentScope}
              onClick={openUploadDrawer}
            >
              {formatMessage({ id: 'common.uploadDocument', defaultMessage: 'Upload document' })}
            </Button>
          ),
        },
        {
          value: (
            <Button key="refresh" icon={<ReloadOutlined />} size={responsive.isMobile ? 'small' : 'middle'} onClick={() => actionRef.current?.reload()}>
              {formatMessage({ id: 'common.refresh', defaultMessage: 'Refresh' })}
            </Button>
          ),
        },
        {
          hidden: !activeBucket,
          value: (
            <Button key="back" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => history.push('/settings/files/all')}>
              返回存储空间
            </Button>
          ),
        },
      ]),
    [actionRef, activeBucket, buildToolbarActions, canUploadInCurrentScope, openUploadDrawer, responsive.isMobile],
  );
  const fileListRequest = useMemo(
    () =>
      buildFileListRequest({
        fileScope,
        activeBucket,
        requestOptions,
      }),
    [activeBucket, fileScope, requestOptions],
  );
  const previewDrawerProps = {
    open: previewDrawerOpen,
    record: previewRecord,
    previewMode,
    previewMeta,
    previewAbsoluteUrl,
    previewText,
    filePreviewUrl,
    loading: previewLoading,
    textLoading: previewTextLoading,
    fileLoading: filePreviewLoading,
    onClose: closePreviewDrawer,
    onCopyLink: handleCopyLink,
    onDownload: handleDownload,
  };
  const browserSectionProps = {
    title: pageTitle,
    isTenantScope,
    activeBucket,
    isMobile: responsive.isMobile,
    actionRef,
    storageActionRef,
    storageColumns,
    storageSpaceRequest,
    fileListRequest,
    storageToolbar,
    actionToolbar,
  };

  const drawerProps = {
    storageDrawerProps: {
      open: storageDrawerOpen,
      mode: storageDrawerMode,
      form: storageForm,
      editingStorageSpace,
      canManageStorage,
      saving: storageSaving,
      provider: storageProvider,
      showRemoteStorageFields,
      providerOptions: storageProviderOptions,
      renameStrategyOptions: storageRenameStrategyOptions,
      onClose: closeStorageDrawer,
      onSave: () => void handleSaveStorageSpace(),
    },
    uploadDrawerProps: {
      open: uploadDrawerOpen,
      uploading,
      canUpload: canUploadInCurrentScope,
      onClose: closeUploadDrawer,
      onSubmit: handleUploadSubmit,
    },
    previewDrawerProps: {
      ...previewDrawerProps,
      backgroundColor: previewBackgroundColor,
      containerBackgroundColor: previewContainerBackgroundColor,
    },
  };

  return (
    <ManagementPage title={browserSectionProps.title} ghost>
      {browserSectionProps.isTenantScope && !browserSectionProps.activeBucket ? (
        <ManagementTable
          actionRef={browserSectionProps.storageActionRef}
          rowKey="id"
          columns={browserSectionProps.storageColumns}
          isMobile={browserSectionProps.isMobile}
          search={false}
          request={browserSectionProps.storageSpaceRequest}
          toolBarRender={() => browserSectionProps.storageToolbar}
        />
      ) : (
        <ManagementTable
          actionRef={browserSectionProps.actionRef}
          rowKey="id"
          columns={fileColumns}
          isMobile={browserSectionProps.isMobile}
          search={{ labelWidth: 'auto', span: browserSectionProps.isMobile ? 24 : 8 }}
          request={browserSectionProps.fileListRequest}
          toolBarRender={() => browserSectionProps.actionToolbar}
        />
      )}
      <FileStorageDrawer {...drawerProps.storageDrawerProps} />
      <FileUploadDrawer {...drawerProps.uploadDrawerProps} />
      <FilePreviewDrawer {...drawerProps.previewDrawerProps} isMobile={browserSectionProps.isMobile} />
    </ManagementPage>
  );
};

export default SystemFilesPage;
