import { getLocale, history, formatMessage, useLocation } from '@umijs/max';
import { Button, Card, Checkbox, Descriptions, Dropdown, Empty, Form, Image, Input, InputNumber, Radio, Select, Space, Spin, Tag, Typography, Upload, theme } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { CopyOutlined, DeleteOutlined, DownloadOutlined, DownOutlined, FileOutlined, InboxOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { FormInstance, UploadFile, UploadProps } from 'antd';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { FILE_ACCEPT, FILE_CATEGORY_OPTIONS, FILE_STORAGE_PROVIDER_OPTIONS, FILE_STORAGE_RENAME_STRATEGY_OPTIONS, ALLOWED_UPLOAD_EXTENSIONS, MAX_UPLOAD_FILE_COUNT, PREVIEW_MODE_LABELS, buildPreviewAbsoluteUrl, formatDateTime, formatFileSize, renderTags, resolveFileTypeLabel, resolvePreviewMode } from '@/pages/files/fileCenter.utils';
import type { FileRenameStrategy, FileStorageProvider, FileStorageSpacePayload, FileStorageSpaceRecord, FileObjectRecord, FileStorageSpaceTestResult, PagedResult } from '@/types/api';
import { adaptPageResult, DEFAULT_TABLE_PAGE_SIZE } from '@/features/table/proTableRequest';
import { request, requestFile, type RequestOptions } from '@/services/common/request';
import { resolveSortParams } from '@/pages/files/fileCenter.utils';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { confirmAction } from '@/utils/confirm';
import { copyTextToClipboard } from '@/utils/clipboard';
import { showErrorMessage } from '@/utils/errorMessage';
import { rejectAntUploadFile, validateDocumentUploadFile } from '@/utils/uploadValidation';
import { normalizeLocale } from '@/i18n/locale';

type BuildFileListRequestParams = {
  fileScope: 'mine' | 'tenant' | 'download-center';
  activeBucket: string;
  requestOptions: RequestOptions;
};

type BuildStorageSpaceRequestParams = {
  requestOptions: RequestOptions;
};

type BuildFileObjectColumnsParams = {
  isMobile: boolean;
  isTenantScope: boolean;
  readOnlyCenter: boolean;
  deletePermission?: string | string[];
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
        pageSize: Number(pageSize) || DEFAULT_TABLE_PAGE_SIZE,
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
        pageSize: Number(pageSize) || DEFAULT_TABLE_PAGE_SIZE,
      },
      ...requestOptions,
    });
    return adaptPageResult(result);
  };

const STORAGE_PROVIDER_OPTIONS: Array<{ label: string; value: FileStorageProvider }> = FILE_STORAGE_PROVIDER_OPTIONS as unknown as Array<{ label: string; value: FileStorageProvider }>;
const STORAGE_RENAME_STRATEGY_OPTIONS: Array<{ label: string; value: FileRenameStrategy }> = FILE_STORAGE_RENAME_STRATEGY_OPTIONS as unknown as Array<{ label: string; value: FileRenameStrategy }>;

const STORAGE_PROVIDER_LABELS: Record<FileStorageProvider, string> = {
  LOCAL: FILE_STORAGE_PROVIDER_OPTIONS[0].label,
  ALIYUN_OSS: FILE_STORAGE_PROVIDER_OPTIONS[1].label,
  TENCENT_COS: FILE_STORAGE_PROVIDER_OPTIONS[2].label,
};

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const DEFAULT_STORAGE_MAX_FILE_SIZE_MB = 20;

const parseAllowedMimeTypes = (allowedMimeTypes?: string) =>
  (allowedMimeTypes || '*')
    .split(/[,，;\s]+/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

const isMimeAllowedByStorage = (file: File, allowedMimeTypes?: string) => {
  const rules = parseAllowedMimeTypes(allowedMimeTypes);
  if (!rules.length || rules.includes('*')) {
    return true;
  }
  const contentType = (file.type || '').toLowerCase();
  if (!contentType) {
    return false;
  }
  return rules.some((rule) => contentType === rule || (rule.endsWith('/*') && contentType.startsWith(rule.slice(0, -1))));
};

const storageUploadHint = (storageSpace?: FileStorageSpaceRecord | null) => {
  const maxFileSizeMb = storageSpace?.maxFileSizeMb || DEFAULT_STORAGE_MAX_FILE_SIZE_MB;
  const allowedMimeTypes = storageSpace?.allowedMimeTypes || '*';
  if (allowedMimeTypes.trim() === '*') {
    return t(`按存储空间配置允许的文件类型上传，单个文件不超过 ${maxFileSizeMb}MB，单次最多 5 个。`, `Upload file types allowed by the storage space. Each file must be under ${maxFileSizeMb}MB. Up to 5 files at a time.`);
  }
  return t(`仅允许 ${allowedMimeTypes}，单个文件不超过 ${maxFileSizeMb}MB，单次最多 5 个。`, `Only ${allowedMimeTypes} is allowed. Each file must be under ${maxFileSizeMb}MB. Up to 5 files at a time.`);
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
  anonymousAccessAllowed: false,
  status: 'ENABLED',
});

const buildFileObjectColumns = ({
  isMobile,
  isTenantScope,
  readOnlyCenter,
  deletePermission,
  actionPermissionCanDelete,
  onOpenPreviewDrawer,
  onDownload,
  onCopyLink,
  onDelete,
}: BuildFileObjectColumnsParams): ProColumns<FileObjectRecord>[] => {
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile) as [number, number];
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
      width: 'var(--saas-spacing-260)',
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => (
        <Space size={tagWrapGap} wrap={false}>
          <FileOutlined />
          <Typography.Link
            title={record.originalFileName}
            style={{ maxWidth: isMobile ? 'var(--saas-spacing-180)' : 'var(--saas-spacing-300)' }}
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
      width: 'var(--saas-spacing-100)',
      render: (_: unknown, record: FileObjectRecord) => <Tag>{resolveFileTypeLabel(record.fileExtension)}</Tag>,
    },
    {
      title: formatMessage({ id: 'system.files.field.size', defaultMessage: 'Size' }),
      dataIndex: 'fileSizeBytes',
      width: 'var(--saas-spacing-110)',
      sorter: true,
      render: (_: unknown, record: FileObjectRecord) => record.fileSizeLabel || formatFileSize(record.fileSizeBytes),
    },
    {
      title: formatMessage({ id: 'system.files.field.category', defaultMessage: 'Category' }),
      key: 'categoryColumn',
      dataIndex: 'category',
      width: 'var(--saas-spacing-160)',
      ...(isMobile ? { responsive: ['md', 'lg', 'xl', 'xxl'] as const } : {}),
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => (record.category ? <Tag color="blue">{record.category}</Tag> : '-'),
    },
    {
      title: formatMessage({ id: 'system.files.field.tags', defaultMessage: 'Tags' }),
      dataIndex: 'tags',
      width: 'var(--saas-spacing-180)',
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => renderTags(record.tags),
    },
    {
      title: formatMessage({ id: 'system.files.field.uploader', defaultMessage: 'Uploader' }),
      dataIndex: 'uploadedByName',
      width: 'var(--saas-spacing-120)',
      responsive: ['md', 'lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_: unknown, record: FileObjectRecord) => record.uploadedByName || '-',
    },
    {
      title: formatMessage({ id: 'system.files.field.uploadTime', defaultMessage: 'Upload time' }),
      dataIndex: 'createdAt',
      width: 'var(--saas-spacing-170)',
      responsive: ['md', 'lg', 'xl', 'xxl'],
      sorter: true,
      render: (_: unknown, record: FileObjectRecord) => formatDateTime(record.createdAt),
    },
  ];

  const actionColumn: ProColumns<FileObjectRecord> = {
    title: formatMessage({ id: 'system.files.field.actions', defaultMessage: 'Actions' }),
    valueType: 'option',
    fixed: 'right',
    width: isMobile ? 64 : 'var(--saas-spacing-220)',
    render: (_: unknown, record: FileObjectRecord) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          {
            key: 'preview',
            label: t('预览', 'Preview'),
            icon: <FileOutlined />,
            onClick: () => void onOpenPreviewDrawer(record),
          },
          {
            key: 'download',
            label: formatMessage({ id: 'common.download', defaultMessage: 'Download' }),
            icon: <DownloadOutlined />,
            onClick: () => void onDownload(record),
          },
          ...(readOnlyCenter
            ? []
            : [
                {
                  key: 'copy',
                  label: formatMessage({ id: 'common.copyLink', defaultMessage: 'Copy link' }),
                  icon: <CopyOutlined />,
                  onClick: () => void onCopyLink(record),
                },
              ]),
          ...(actionPermissionCanDelete(deletePermission ?? (isTenantScope ? 'system:file:manage:delete' : 'system:file:delete'))
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

function FileStorageDrawer({
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
}) {
  return (
    <ManagementDrawer
      title={mode === 'edit' ? t(`编辑 - ${editingStorageSpace?.title || '存储空间'}`, `Edit - ${editingStorageSpace?.title || 'Storage space'}`) : t('新增存储空间', 'Create storage space')}
      open={open}
      onClose={onClose}
      footerActions={[
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: onClose },
        { key: 'save', label: t('保存', 'Save'), type: 'primary', loading: saving, disabled: !canManageStorage, onClick: onSave },
      ]}
    >
      <Form form={form} layout="vertical" initialValues={{ provider: 'LOCAL', renameStrategy: 'APPEND_RANDOM_ID' }}>
        <>
          <Form.Item name="provider" label={t('存储类型', 'Storage type')} rules={[{ required: true, message: t('请选择存储类型', 'Please select a storage type') }]}>
            <Select options={providerOptions} disabled={mode === 'edit'} />
          </Form.Item>
          <Form.Item name="title" label={t('标题', 'Title')} rules={[{ required: true, message: t('请输入标题', 'Please enter a title') }]}>
            <Input placeholder={t('本地存储', 'Local storage')} />
          </Form.Item>
          <Form.Item
            name="storageKey"
            label={t('存储空间标识', 'Storage key')}
            rules={[
              { required: true, message: t('请输入存储空间标识', 'Please enter the storage key') },
              { pattern: /^[a-z][a-z0-9_]*$/, message: t('必须以英文字母开头，仅支持英文、数字和下划线', 'Must start with a letter and contain only letters, numbers, and underscores') },
            ]}
            extra={t('随机生成，可修改。支持英文、数字和下划线，必须以英文字母开头。', 'Generated randomly and can be edited. It must start with a letter and may contain letters, numbers, and underscores.')}
          >
            <Input placeholder={t('local', 'local')} disabled={mode === 'edit'} />
          </Form.Item>
          {provider === 'LOCAL' ? (
            <Form.Item name="rootPath" label={t('路径', 'Path')}>
              <Input addonAfter="/" placeholder={t('storage/uploads', 'storage/uploads')} />
            </Form.Item>
          ) : null}
          {showRemoteStorageFields ? (
            <>
              <Form.Item name="bucketName" label={t('存储桶', 'Bucket')} rules={[{ required: true, message: t('请输入 Bucket', 'Please enter a Bucket') }]}>
                <Input placeholder={t('对象存储 Bucket 名称', 'Object storage Bucket name')} />
              </Form.Item>
              <Form.Item name="endpoint" label={t('访问端点', 'Endpoint')} rules={[{ required: true, message: t('请输入 Endpoint', 'Please enter an Endpoint') }]}>
                <Input placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
              </Form.Item>
              <Form.Item name="region" label={t('地域', 'Region')}>
                <Input placeholder="cn-hangzhou / ap-guangzhou / us-east-1" />
              </Form.Item>
              <Form.Item name="accessKeyId" label={t('访问密钥 ID', 'Access Key ID')}>
                <Input placeholder={t('对象存储访问密钥 ID', 'Object storage access key ID')} />
              </Form.Item>
              <Form.Item name="accessKeySecret" label={t('访问密钥 Secret', 'Access Key Secret')} extra={editingStorageSpace?.secretConfigured ? t('留空则保持现有密钥。', 'Leave blank to keep the existing secret.') : undefined}>
                <Input.Password placeholder={t('留空则不修改已保存密钥', 'Leave blank to keep the saved secret')} />
              </Form.Item>
            </>
          ) : null}
          <Form.Item name="renameStrategy" label={t('重命名', 'Rename strategy')} rules={[{ required: true, message: t('请选择重命名策略', 'Please select a rename strategy') }]}>
            <Radio.Group options={renameStrategyOptions} />
          </Form.Item>
          <Form.Item name="maxFileSizeMb" label={t('文件大小限制', 'Max file size')} rules={[{ required: true, message: t('请输入文件大小限制', 'Please enter the max file size') }]}>
            <InputNumber min={1} addonAfter="MB" style={{ width: 'var(--saas-spacing-220)' }} />
          </Form.Item>
          <Form.Item name="allowedMimeTypes" label={t('允许的文件类型（MIME 格式）', 'Allowed file types (MIME)')}>
            <Input placeholder="*" />
          </Form.Item>
          <Form.Item name="defaultStorage" valuePropName="checked">
            <Checkbox disabled={Boolean(editingStorageSpace?.defaultStorage)}>{t('默认存储空间', 'Default storage space')}</Checkbox>
          </Form.Item>
          <Form.Item name="retainFileOnRecordDelete" valuePropName="checked">
            <Checkbox>{t('删除文件记录时保留文件', 'Keep the file when its record is deleted')}</Checkbox>
          </Form.Item>
          <Form.Item
            name="anonymousAccessAllowed"
            valuePropName="checked"
            extra={t('关闭后，该存储空间下的文件即使标记为公开，也不能被未登录用户通过直链访问。', 'When disabled, files in this storage space cannot be accessed by anonymous users through direct links, even if they are marked public.')}
          >
            <Checkbox>{t('允许未登录访问公开文件', 'Allow anonymous access to public files')}</Checkbox>
          </Form.Item>
          <Form.Item name="status" label={t('状态', 'Status')}>
            <Select
              options={[
                { label: t('启用', 'Enabled'), value: 'ENABLED' },
                { label: t('停用', 'Disabled'), value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </>
      </Form>
    </ManagementDrawer>
  );
}

function FileUploadDrawer({
  open,
  uploading,
  canUpload,
  sectionGap,
  storageSpace,
  onClose,
  onSubmit,
}: {
  open: boolean;
  uploading: boolean;
  canUpload: boolean;
  sectionGap: number;
  storageSpace?: FileStorageSpaceRecord | null;
  onClose: () => void;
  onSubmit: (payload: { files: File[]; values: { category?: string; tags?: string; remark?: string } }) => Promise<void>;
}) {
  const [form] = Form.useForm<{
    category?: string | string[];
    tags?: string;
    remark?: string;
  }>();
  const [uploadFileList, setUploadFileList] = useState<UploadFile[]>([]);
  const maxFileSizeMb = storageSpace?.maxFileSizeMb || DEFAULT_STORAGE_MAX_FILE_SIZE_MB;
  const maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
  const allowedMimeTypes = storageSpace?.allowedMimeTypes || '*';
  const uploadHint = storageUploadHint(storageSpace);

  const uploadDraggerProps: UploadProps = useMemo(
    () => ({
      multiple: true,
      accept: allowedMimeTypes.trim() === '*' ? FILE_ACCEPT : allowedMimeTypes,
      fileList: uploadFileList,
      beforeUpload: (file) => {
        const validationMessage = validateDocumentUploadFile(file as File, {
          allowedExtensions: ALLOWED_UPLOAD_EXTENSIONS,
          allowedMimeTypes,
          maxSizeMb: maxFileSizeMb,
        });
        if (validationMessage) {
          return rejectAntUploadFile(validationMessage, message.error);
        }
        const extension = file.name.split('.').pop()?.toLowerCase();
        if (!extension || !ALLOWED_UPLOAD_EXTENSIONS.includes(extension)) {
          message.error(t('当前文件格式暂不支持安全上传', 'This file format is not supported for secure upload yet'));
          return Upload.LIST_IGNORE;
        }
        if (file.size > maxFileSizeBytes) {
          message.error(t(`文件大小不能超过 ${maxFileSizeMb}MB`, `The file size cannot exceed ${maxFileSizeMb}MB`));
          return Upload.LIST_IGNORE;
        }
        if (!isMimeAllowedByStorage(file as File, allowedMimeTypes)) {
          message.error(t('当前存储空间不允许上传该文件类型', 'This storage space does not allow this file type'));
          return Upload.LIST_IGNORE;
        }
        return false;
      },
      onChange: (info) => {
        if (info.fileList.length > MAX_UPLOAD_FILE_COUNT) {
          message.warning(t(`一次最多上传 ${MAX_UPLOAD_FILE_COUNT} 个文件`, `You can upload at most ${MAX_UPLOAD_FILE_COUNT} files at a time`));
        }
        setUploadFileList(info.fileList.slice(-MAX_UPLOAD_FILE_COUNT));
      },
      onRemove: (file) => {
        setUploadFileList((prev) => prev.filter((item) => item.uid !== file.uid));
        return true;
      },
    }),
    [allowedMimeTypes, maxFileSizeBytes, maxFileSizeMb, uploadFileList],
  );

  const handleClose = () => {
    setUploadFileList([]);
    form.resetFields();
    onClose();
  };

  const handleSubmit = async () => {
    if (!canUpload) {
      message.warning(t('存储空间不支持从后台直接上传', 'This storage space does not support direct upload from the backend'));
      return;
    }
    const values = await form.validateFields();
    const files = uploadFileList.map((item) => item.originFileObj).filter(Boolean) as File[];
    if (!files.length) {
      message.warning(t('请先选择文件', 'Please select files first'));
      return;
    }
    if (files.length > MAX_UPLOAD_FILE_COUNT) {
      message.warning(t(`一次最多上传 ${MAX_UPLOAD_FILE_COUNT} 个文件`, `You can upload at most ${MAX_UPLOAD_FILE_COUNT} files at a time`));
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
      title={t('上传文件', 'Upload files')}
      open={open}
      onClose={handleClose}
      footerActions={[
        { key: 'cancel', label: t('取消', 'Cancel'), onClick: handleClose },
        { key: 'upload', label: t('开始上传', 'Start upload'), type: 'primary', loading: uploading, disabled: !canUpload, onClick: () => void handleSubmit() },
      ]}
    >
      <Form form={form} layout="vertical">
        <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
          <Card title={t('选择文件', 'Select files')} bodyStyle={{ padding: 0 }} style={{ borderRadius: 'var(--saas-card-radius)' }}>
            <Upload.Dragger {...uploadDraggerProps} style={{ borderRadius: 'var(--saas-card-radius)' }}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{t('点击或拖拽文件到此处上传', 'Click or drag files here to upload')}</p>
              <p className="ant-upload-hint">{uploadHint}</p>
            </Upload.Dragger>
          </Card>

          <Form.Item label={t('分类', 'Category')} name="category">
            <Select
              allowClear
              mode="tags"
              tokenSeparators={[',', '，']}
              options={FILE_CATEGORY_OPTIONS}
              placeholder={t('例如：制度文档、业务资料、合同协议', 'e.g. policies, business materials, contracts')}
            />
          </Form.Item>
          <Form.Item label={t('标签', 'Tags')} name="tags" extra={t('多个标签请用逗号分隔', 'Separate multiple tags with commas')}>
            <Input placeholder={t('例如：ops,contract,archive', 'e.g. ops,contract,archive')} allowClear />
          </Form.Item>
          <Form.Item label={t('备注', 'Remark')} name="remark">
            <Input.TextArea rows={4} placeholder={t('选填，写一段简短备注', 'Optional, write a short note about the file')} maxLength={512} showCount />
          </Form.Item>
        </Space>
      </Form>
    </ManagementDrawer>
  );
}

function FilePreviewDrawer({
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
  sectionGap,
  backgroundColor,
  containerBackgroundColor,
  onClose,
  onCopyLink,
  onDownload,
  allowCopyLink,
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
  sectionGap: number;
  backgroundColor: string;
  containerBackgroundColor: string;
  onClose: () => void;
  onCopyLink: (record: FileObjectRecord) => void;
  onDownload: (record: FileObjectRecord) => void;
  allowCopyLink: boolean;
}) {
  return (
    <ManagementDrawer
      title={record ? record.originalFileName : t('文件预览', 'File preview')}
      open={open}
      onClose={onClose}
      footer={
        <div className="saas-drawer-footer">
          <Space wrap>
            {allowCopyLink ? (
              <Button icon={<CopyOutlined />} onClick={() => record && void onCopyLink(record)} disabled={!record}>
                {t('复制链接', 'Copy link')}
              </Button>
            ) : null}
            <Button icon={<DownloadOutlined />} onClick={() => record && void onDownload(record)} disabled={!record}>
              {t('下载', 'Download')}
            </Button>
            <Button onClick={onClose}>{t('关闭', 'Close')}</Button>
          </Space>
        </div>
      }
    >
      {record ? (
        <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('文件名', 'File name')}>{record.originalFileName}</Descriptions.Item>
            <Descriptions.Item label={t('类型', 'Type')}>{resolveFileTypeLabel(record.fileExtension)}</Descriptions.Item>
            <Descriptions.Item label={t('大小', 'Size')}>{record.fileSizeLabel || formatFileSize(record.fileSizeBytes)}</Descriptions.Item>
            <Descriptions.Item label={t('预览', 'Preview')}>{<Tag color={previewMeta.color}>{previewMeta.text}</Tag>}</Descriptions.Item>
            <Descriptions.Item label={t('分类', 'Category')}>{record.category || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('上传人', 'Uploader')}>{record.uploadedByName || '-'}</Descriptions.Item>
            <Descriptions.Item label={t('标签', 'Tags')}>
              {renderTags(record.tags)}
            </Descriptions.Item>
            <Descriptions.Item label={t('备注', 'Remark')}>
              {record.remark || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('上传时间', 'Upload time')}>{formatDateTime(record.createdAt)}</Descriptions.Item>
            <Descriptions.Item label={t('下载链接', 'Download link')}>
              <Typography.Text copyable={{ text: previewAbsoluteUrl }} style={{ wordBreak: 'break-all' }}>
                {previewAbsoluteUrl || '-'}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>

          <Spin
            spinning={loading || textLoading || fileLoading}
            tip={fileLoading ? t('文件预览加载中', 'Loading file preview') : textLoading ? t('文本内容加载中', 'Loading text content') : t('文件详情加载中', 'Loading file details')}
          >
            <div
              style={{
                minHeight: isMobile ? 'var(--saas-spacing-240)' : 'var(--saas-spacing-520)',
                padding: sectionGap,
                background: backgroundColor,
              }}
            >
              {previewMode === 'IMAGE' ? (
                filePreviewUrl ? (
                  <Image
                    src={filePreviewUrl}
                    alt={record.originalFileName}
                    preview={false}
                    style={{ width: '100%', maxHeight: isMobile ? 'var(--saas-spacing-360)' : 'var(--saas-spacing-560)', objectFit: 'contain' }}
                  />
                ) : null
              ) : null}
              {previewMode === 'PDF' ? (
                filePreviewUrl ? (
                  <iframe
                    title={record.originalFileName}
                    src={`${filePreviewUrl}#view=FitH`}
                    style={{ width: '100%', height: isMobile ? 'var(--saas-spacing-360)' : 'var(--saas-spacing-560)', border: 0, background: containerBackgroundColor }}
                  />
                ) : null
              ) : null}
              {previewMode === 'TEXT' ? (
                <Typography.Paragraph
                  style={{
                    marginBottom: 0,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    maxHeight: isMobile ? 'var(--saas-spacing-360)' : 'var(--saas-spacing-560)',
                    overflow: 'auto',
                  }}
                >
                  {previewText || t('暂无文本内容', 'No text content yet')}
                </Typography.Paragraph>
              ) : null}
              {previewMode === 'UNSUPPORTED' ? (
                <Empty
                  description={
                    <Typography.Text>
                      {t('当前格式暂不支持在线预览', 'This format is not supported for online preview yet')}
                    </Typography.Text>
                  }
                />
              ) : null}
            </div>
          </Spin>
        </Space>
      ) : (
        <Empty description={t('暂无文件详情', 'No file details yet')} />
      )}
    </ManagementDrawer>
  );
}

function SystemFilesPage({ variant = 'file-center' }: { variant?: 'file-center' | 'download-center' }) {
  const { token } = theme.useToken();
  const location = useLocation();
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const actionRef = useRef<ActionType | null>(null);
  const storageActionRef = useRef<ActionType | null>(null);
  const [uploadDrawerOpen, setUploadDrawerOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [defaultStorageSpace, setDefaultStorageSpace] = useState<FileStorageSpaceRecord | null>(null);

  const readOnlyCenter = variant === 'download-center';
  const isTenantScope = useMemo(
    () => readOnlyCenter || location.pathname === '/settings/files/all' || location.pathname === '/files/all' || location.pathname === '/system/files/all',
    [location.pathname, readOnlyCenter],
  );
  const activeBucket = useMemo(
    () => (isTenantScope ? new URLSearchParams(location.search).get('bucket') || '' : ''),
    [isTenantScope, location.search],
  );
  const fileScope: 'mine' | 'tenant' | 'download-center' = readOnlyCenter ? 'download-center' : isTenantScope ? 'tenant' : 'mine';
  const pageTitle = isTenantScope
    ? readOnlyCenter
      ? formatMessage({ id: 'system.files.title.downloadCenter', defaultMessage: 'Download Center' })
      : activeBucket
      ? t(`存储空间文件 / ${activeBucket}`, `Storage files / ${activeBucket}`)
      : t('文件管理器', 'File Manager')
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
  const uploadPermission = readOnlyCenter ? 'download:center:create' : 'system:file:upload';
  const deletePermission = readOnlyCenter ? 'download:center:delete' : isTenantScope ? 'system:file:manage:delete' : 'system:file:delete';
  const canUploadFile = actionPermission.can(uploadPermission);
  const canUploadInCurrentScope = readOnlyCenter ? canUploadFile : !isTenantScope && canUploadFile;
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

  useEffect(() => {
    let active = true;
    void request<PagedResult<FileStorageSpaceRecord>>('/v1/files/storage-spaces', {
      method: 'GET',
      params: {
        pageNo: 1,
        pageSize: 100,
      },
      ...requestOptions,
    })
      .then((result) => {
        if (!active) {
          return;
        }
        const records = result.records || [];
        setDefaultStorageSpace(records.find((record) => record.defaultStorage) || records[0] || null);
      })
      .catch(() => {
        if (active) {
          setDefaultStorageSpace(null);
        }
      });
    return () => {
      active = false;
    };
  }, [requestOptions]);

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
              anonymousAccessAllowed: Boolean(record.anonymousAccessAllowed),
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
      let savedStorageSpace: FileStorageSpaceRecord;
      if (storageDrawerMode === 'edit' && editingStorageSpace) {
        savedStorageSpace = await request<FileStorageSpaceRecord>(`/v1/files/storage-spaces/${editingStorageSpace.id}`, {
          method: 'PUT',
          data: values,
          ...requestOptions,
        });
        message.success(t('存储空间已更新', 'Storage space updated'));
      } else {
        savedStorageSpace = await request<FileStorageSpaceRecord>('/v1/files/storage-spaces', {
          method: 'POST',
          data: values,
          ...requestOptions,
        });
        message.success(t('存储空间已创建', 'Storage space created'));
      }
      if (savedStorageSpace.defaultStorage || defaultStorageSpace?.id === savedStorageSpace.id) {
        setDefaultStorageSpace(savedStorageSpace);
      }
      closeStorageDrawer();
      storageActionRef.current?.reload();
    } finally {
      setStorageSaving(false);
    }
  }, [closeStorageDrawer, defaultStorageSpace?.id, editingStorageSpace, requestOptions, storageDrawerMode, storageForm]);
  const handleDeleteStorageSpace = useCallback(
    (record: FileStorageSpaceRecord) => {
      if (readOnlyCenter) {
        return;
      }
      confirmAction({
        title: t('删除存储空间', 'Delete storage space'),
        content: t(`确认删除存储空间「${record.title}」吗？仅空存储空间可以删除。`, `Delete storage space "${record.title}"? Only empty storage spaces can be deleted.`),
        okText: t('确认删除', 'Confirm delete'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/files/storage-spaces/${record.id}`, {
            method: 'DELETE',
            ...requestOptions,
          });
          message.success(t('存储空间已删除', 'Storage space deleted'));
          storageActionRef.current?.reload();
        },
      });
    },
    [readOnlyCenter, requestOptions],
  );
  const handleTestStorageSpace = useCallback(
    async (record: FileStorageSpaceRecord) => {
      const result = await request<FileStorageSpaceTestResult>(`/v1/files/storage-spaces/${record.id}/test`, {
        method: 'POST',
        ...requestOptions,
      });
      if (result.status === 'UP') {
        message.success(result.message || t('存储空间连接正常', 'Storage connection is healthy'));
        return;
      }
      message.warning(result.message || t('存储空间连接异常', 'Storage connection is unhealthy'));
    },
    [requestOptions],
  );
  const enterStorageSpace = useCallback((record: FileStorageSpaceRecord) => {
    history.push(`/settings/files/all?bucket=${encodeURIComponent(record.storageKey)}`);
  }, []);
  const storageColumns = useMemo<ProColumns<FileStorageSpaceRecord>[]>(
    () => [
      {
        title: t('标题', 'Title'),
        dataIndex: 'title',
        width: 'var(--saas-spacing-260)',
        render: (_, record) => (
          <Typography.Link onClick={() => enterStorageSpace(record)}>
            {record.title}
          </Typography.Link>
        ),
      },
      {
        title: t('存储空间标识', 'Storage key'),
        dataIndex: 'storageKey',
        width: 'var(--saas-spacing-220)',
      },
      {
        title: t('类型', 'Type'),
        dataIndex: 'provider',
        width: 'var(--saas-spacing-160)',
        render: (_, record) => <Tag>{STORAGE_PROVIDER_LABELS[record.provider] || record.provider}</Tag>,
      },
      {
        title: t('默认存储空间', 'Default storage space'),
        dataIndex: 'defaultStorage',
        width: 'var(--saas-spacing-160)',
        render: (_, record) => (record.defaultStorage ? <span style={{ color: token.colorSuccess, fontSize: 20 }}>✓</span> : '-'),
      },
      {
        title: t('匿名访问', 'Anonymous access'),
        dataIndex: 'anonymousAccessAllowed',
        width: 'var(--saas-spacing-140)',
        render: (_, record) => (record.anonymousAccessAllowed ? <Tag color="green">{t('允许', 'Allowed')}</Tag> : <Tag>{t('关闭', 'Off')}</Tag>),
      },
      {
        title: t('文件数', 'File count'),
        dataIndex: 'fileCount',
        width: 'var(--saas-spacing-120)',
        render: (_, record) => record.fileCount ?? 0,
      },
      {
        title: t('容量', 'Size'),
        dataIndex: 'totalSizeLabel',
        width: 'var(--saas-spacing-120)',
        render: (_, record) => record.totalSizeLabel || formatFileSize(record.totalSizeBytes),
      },
      {
        title: t('操作', 'Actions'),
        valueType: 'option',
        fixed: 'right',
        width: 'var(--saas-spacing-180)',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              {
                key: 'edit',
                label: t('编辑', 'Edit'),
                disabled: !canManageStorage,
                onClick: () => openStorageDrawer(record.provider, record),
              },
              {
                key: 'test',
                label: t('测试', 'Test'),
                disabled: !canManageStorage,
                onClick: () => void handleTestStorageSpace(record),
              },
              {
                key: 'delete',
                label: t('删除', 'Delete'),
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
              {t('删除', 'Delete')}
            </Button>
          ),
        },
        {
          value: (
            <Dropdown key="add" menu={{ items: addStorageItems }} trigger={['click']}>
              <Button type="primary" icon={<PlusOutlined />} size={responsive.isMobile ? 'small' : 'middle'} disabled={!canManageStorage}>
                {t('添加', 'Add')} <DownOutlined />
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
          defaultMessage: readOnlyCenter ? 'You do not have permission to upload files to the download center' : 'Storage buckets do not support uploads from the admin console',
        }),
      );
      return;
    }
    setUploadDrawerOpen(true);
  }, [canUploadInCurrentScope, readOnlyCenter]);
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
          formData.append('scope', fileScope);
          await request<FileObjectRecord>('/v1/files/upload', {
            method: 'POST',
            headers: {},
            data: formData,
            ...requestOptions,
            silent: true,
          });
          uploadedCount += 1;
        }
        message.success(
          formatMessage({ id: 'system.files.uploadSuccess', defaultMessage: 'Uploaded {count} file(s)' }, { count: uploadedCount }),
        );
        closeUploadDrawer();
        actionRef.current?.reload();
      } catch (error) {
        showErrorMessage(error, formatMessage({ id: 'system.files.uploadFailed', defaultMessage: 'File upload failed, please try again later' }));
      } finally {
        setUploading(false);
      }
    },
    [activeBucket, closeUploadDrawer, fileScope, requestOptions],
  );

  const fileColumns = buildFileObjectColumns({
    isMobile: responsive.isMobile,
    isTenantScope,
    readOnlyCenter,
    deletePermission,
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
          hidden: !activeBucket || readOnlyCenter,
          value: (
            <Button key="back" size={responsive.isMobile ? 'small' : 'middle'} onClick={() => history.push('/settings/files/all')}>
              {t('返回存储空间', 'Back to storage spaces')}
            </Button>
          ),
        },
      ]),
    [actionRef, activeBucket, buildToolbarActions, canUploadInCurrentScope, openUploadDrawer, readOnlyCenter, responsive.isMobile],
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
    allowCopyLink: !readOnlyCenter,
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
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);

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
      isMobile: responsive.isMobile,
      sectionGap,
      onClose: closeStorageDrawer,
      onSave: () => void handleSaveStorageSpace(),
    },
    uploadDrawerProps: {
      open: uploadDrawerOpen,
      uploading,
      canUpload: canUploadInCurrentScope,
      isMobile: responsive.isMobile,
      sectionGap,
      storageSpace: defaultStorageSpace,
      onClose: closeUploadDrawer,
      onSubmit: handleUploadSubmit,
    },
    previewDrawerProps: {
      ...previewDrawerProps,
      sectionGap,
      backgroundColor: previewBackgroundColor,
      containerBackgroundColor: previewContainerBackgroundColor,
    },
  };

  return (
    <ManagementPage title={browserSectionProps.title} ghost>
      <ManagementPageBody>
        {browserSectionProps.isTenantScope && !browserSectionProps.activeBucket && !readOnlyCenter ? (
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
      </ManagementPageBody>
      <FileStorageDrawer {...drawerProps.storageDrawerProps} />
      <FileUploadDrawer {...drawerProps.uploadDrawerProps} />
      <FilePreviewDrawer {...drawerProps.previewDrawerProps} isMobile={browserSectionProps.isMobile} />
    </ManagementPage>
  );
}

export default SystemFilesPage;
