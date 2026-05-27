import {
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  FileOutlined,
  InboxOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  Button,
  Card,
  Checkbox,
  Descriptions,
  Dropdown,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
  message,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { formatMessage, history, useLocation } from '@umijs/max';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { adaptPageResult } from '@/features/table/proTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import { fileService } from '@/services/file';
import type { FileObjectRecord, FileRenameStrategy, FileStorageProvider, FileStorageSpacePayload, FileStorageSpaceRecord } from '@/types/api';
import { copyTextToClipboard } from '@/utils/clipboard';
import { confirmAction } from '@/utils/confirm';
import {
  ALLOWED_UPLOAD_EXTENSIONS,
  buildPreviewAbsoluteUrl,
  FILE_ACCEPT,
  FILE_CATEGORY_OPTIONS,
  formatDateTime,
  formatFileSize,
  MAX_UPLOAD_FILE_COUNT,
  PREVIEW_MODE_LABELS,
  renderTags,
  resolveFileTypeLabel,
  resolvePreviewMode,
  resolveSortParams,
} from '@/pages/files/fileCenter.utils';

const STORAGE_PROVIDER_OPTIONS: Array<{ label: string; value: FileStorageProvider }> = [
  { label: '本地存储', value: 'LOCAL' },
  { label: '阿里云 OSS', value: 'ALIYUN_OSS' },
  { label: '腾讯云 COS', value: 'TENCENT_COS' },
];

const RENAME_STRATEGY_OPTIONS: Array<{ label: string; value: FileRenameStrategy }> = [
  { label: '追加随机 ID', value: 'APPEND_RANDOM_ID' },
  { label: '随机字符串', value: 'RANDOM_STRING' },
  { label: '保持原名（同名文件将被覆盖）', value: 'KEEP_ORIGINAL' },
];

const providerLabelMap: Record<FileStorageProvider, string> = {
  LOCAL: '本地存储',
  ALIYUN_OSS: '阿里云 OSS',
  TENCENT_COS: '腾讯云 COS',
};

const defaultStoragePayload = (provider: FileStorageProvider): FileStorageSpacePayload => ({
  title: providerLabelMap[provider],
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

const SystemFilesPage = () => {
  const { token } = theme.useToken();
  const location = useLocation();
  const actionRef = useRef<ActionType>(null);
  const storageActionRef = useRef<ActionType>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const isTenantScope = location.pathname === '/settings/files/all' || location.pathname === '/files/all' || location.pathname === '/system/files/all';
  const activeBucket = isTenantScope ? new URLSearchParams(location.search).get('bucket') || '' : '';
  const fileScope = isTenantScope ? 'tenant' : 'mine';
  const pageTitle = isTenantScope
    ? activeBucket
      ? `存储空间文件 / ${activeBucket}`
      : '文件管理器'
    : formatMessage({ id: 'system.files.title.my', defaultMessage: 'My Files' });
  const [storageDrawerOpen, setStorageDrawerOpen] = useState(false);
  const [storageDrawerMode, setStorageDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingStorageSpace, setEditingStorageSpace] = useState<FileStorageSpaceRecord | null>(null);
  const [storageSaving, setStorageSaving] = useState(false);
  const [storageForm] = Form.useForm<FileStorageSpacePayload>();
  const [uploadDrawerOpen, setUploadDrawerOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadForm] = Form.useForm<{
    category?: string | string[];
    tags?: string;
    remark?: string;
  }>();
  const [uploadFileList, setUploadFileList] = useState<UploadFile[]>([]);
  const [previewDrawerOpen, setPreviewDrawerOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewRecord, setPreviewRecord] = useState<FileObjectRecord | null>(null);
  const [previewTextLoading, setPreviewTextLoading] = useState(false);
  const [previewText, setPreviewText] = useState('');
  const [filePreviewLoading, setFilePreviewLoading] = useState(false);
  const [filePreviewUrl, setFilePreviewUrl] = useState('');

  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
    }),
    [],
  );
  const scopeParams = useMemo(() => ({ scope: fileScope as 'mine' | 'tenant' }), [fileScope]);
  const canManageStorage = actionPermission.can('system:file:manage');
  const canDeleteStorage = actionPermission.can('system:file:manage:delete');
  const canUploadFile = actionPermission.can('system:file:upload');
  const canUploadInCurrentScope = !isTenantScope && canUploadFile;

  const openStorageDrawer = (provider: FileStorageProvider, record?: FileStorageSpaceRecord) => {
    setStorageDrawerMode(record ? 'edit' : 'create');
    setEditingStorageSpace(record || null);
    storageForm.setFieldsValue(record ? {
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
    } : defaultStoragePayload(provider));
    setStorageDrawerOpen(true);
  };

  const closeStorageDrawer = () => {
    setStorageDrawerOpen(false);
    setEditingStorageSpace(null);
    setStorageSaving(false);
    storageForm.resetFields();
  };

  const handleSaveStorageSpace = async () => {
    const values = await storageForm.validateFields();
    setStorageSaving(true);
    try {
      if (storageDrawerMode === 'edit' && editingStorageSpace) {
        await fileService.updateStorageSpace(editingStorageSpace.id, values, requestOptions);
        message.success('存储空间已更新');
      } else {
        await fileService.createStorageSpace(values, requestOptions);
        message.success('存储空间已创建');
      }
      closeStorageDrawer();
      storageActionRef.current?.reload();
    } finally {
      setStorageSaving(false);
    }
  };

  const handleDeleteStorageSpace = (record: FileStorageSpaceRecord) => {
    confirmAction({
      title: '删除存储空间',
      content: `确认删除存储空间「${record.title}」吗？仅空存储空间可以删除。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await fileService.removeStorageSpace(record.id, requestOptions);
        message.success('存储空间已删除');
        storageActionRef.current?.reload();
      },
    });
  };

  const handleTestStorageSpace = async (record: FileStorageSpaceRecord) => {
    const result = await fileService.testStorageSpace(record.id, requestOptions);
    if (result.status === 'UP') {
      message.success(result.message || '存储空间连接正常');
      return;
    }
    message.warning(result.message || '存储空间连接异常');
  };

  const enterStorageSpace = (record: FileStorageSpaceRecord) => {
    history.push(`/settings/files/all?bucket=${encodeURIComponent(record.storageKey)}`);
  };

  const closeUploadDrawer = () => {
    setUploadDrawerOpen(false);
    setUploading(false);
    setUploadFileList([]);
    uploadForm.resetFields();
  };

  const openUploadDrawer = () => {
    if (!canUploadInCurrentScope) {
      message.warning(formatMessage({ id: 'system.files.bucketUploadDisabled', defaultMessage: 'Storage buckets do not support uploads from the admin console' }));
      return;
    }
    uploadForm.resetFields();
    setUploadFileList([]);
    setUploadDrawerOpen(true);
  };

  const openPreviewDrawer = async (record: FileObjectRecord) => {
    setPreviewDrawerOpen(true);
    setPreviewRecord(record);
    setPreviewText('');
    setFilePreviewUrl('');
    setPreviewLoading(true);
    try {
      const detail = await fileService.detail(record.id, scopeParams, requestOptions);
      setPreviewRecord(detail);
    } catch {
      message.warning(formatMessage({ id: 'system.files.detailLoadFailed', defaultMessage: 'File details failed to load, using the list data instead' }));
    } finally {
      setPreviewLoading(false);
    }
  };

  const closePreviewDrawer = () => {
    setPreviewDrawerOpen(false);
    setPreviewLoading(false);
    setPreviewTextLoading(false);
    setFilePreviewLoading(false);
    setPreviewText('');
    if (filePreviewUrl) {
      window.URL.revokeObjectURL(filePreviewUrl);
    }
    setFilePreviewUrl('');
    setPreviewRecord(null);
  };

  useEffect(() => {
    if (!previewDrawerOpen || !previewRecord) {
      return;
    }

    if (resolvePreviewMode(previewRecord) !== 'TEXT') {
      setPreviewText('');
      setPreviewTextLoading(false);
      return;
    }

    let active = true;
    setPreviewTextLoading(true);
    void fileService
      .preview(previewRecord.id, scopeParams, requestOptions)
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
  }, [previewDrawerOpen, previewRecord?.id, requestOptions, scopeParams]);

  useEffect(() => {
    if (!previewDrawerOpen || !previewRecord) {
      return;
    }

    const mode = resolvePreviewMode(previewRecord);
    if (mode !== 'PDF' && mode !== 'IMAGE') {
      setFilePreviewLoading(false);
      if (filePreviewUrl) {
        window.URL.revokeObjectURL(filePreviewUrl);
        setFilePreviewUrl('');
      }
      return;
    }

    let active = true;
    let objectUrl = '';
    setFilePreviewLoading(true);
    void fileService
      .preview(previewRecord.id, scopeParams, requestOptions)
      .then((blob) => {
        objectUrl = window.URL.createObjectURL(blob);
        if (active) {
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
      if (objectUrl) {
        window.URL.revokeObjectURL(objectUrl);
      }
    };
  }, [previewDrawerOpen, previewRecord?.id, requestOptions, scopeParams]);

  const handleUpload = async () => {
    if (!canUploadInCurrentScope) {
      message.warning(formatMessage({ id: 'system.files.bucketUploadDisabled', defaultMessage: 'Storage buckets do not support uploads from the admin console' }));
      return;
    }
    const values = await uploadForm.validateFields();
    const files = uploadFileList.map((item) => item.originFileObj).filter(Boolean) as File[];
    if (!files.length) {
      message.warning(formatMessage({ id: 'system.files.selectUploadFile', defaultMessage: 'Please select a file first' }));
      return;
    }
    if (files.length > MAX_UPLOAD_FILE_COUNT) {
      message.warning(formatMessage({ id: 'system.files.maxUploadCount', defaultMessage: 'At most {count} files can be uploaded at once' }, { count: MAX_UPLOAD_FILE_COUNT }));
      return;
    }
    const category = Array.isArray(values.category) ? values.category.filter(Boolean).join(',') : values.category;

    setUploading(true);
    try {
      let uploadedCount = 0;
      for (const file of files) {
        await fileService.upload(
          file,
          {
            category,
            tags: values.tags,
            remark: values.remark,
            bucket: activeBucket || undefined,
          },
          requestOptions,
        );
        uploadedCount += 1;
      }
      message.success(formatMessage({ id: 'system.files.uploadSuccess', defaultMessage: 'Uploaded {count} file(s)' }, { count: uploadedCount }));
      closeUploadDrawer();
      actionRef.current?.reload();
    } catch {
      message.error(formatMessage({ id: 'system.files.uploadFailed', defaultMessage: 'File upload failed, please try again later' }));
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (record: FileObjectRecord) => {
    try {
      const blob = await fileService.download(record.id, scopeParams, requestOptions);
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
  };

  const handleCopyLink = async (record: FileObjectRecord) => {
    const url = buildPreviewAbsoluteUrl(record);
    try {
      await copyTextToClipboard(url);
      message.success(formatMessage({ id: 'system.files.copySuccess', defaultMessage: 'Link copied' }));
    } catch {
      message.error(formatMessage({ id: 'system.files.copyFailed', defaultMessage: 'Copy failed, please copy the link manually' }));
    }
  };

  const handleDelete = (record: FileObjectRecord) => {
    confirmAction({
        title: formatMessage({ id: 'system.files.delete.title', defaultMessage: 'Delete file' }),
      content: isTenantScope
        ? formatMessage({ id: 'system.files.delete.confirmTenant', defaultMessage: 'Delete file "{name}"? This will remove the file and its record, and may affect avatars, logos, and other assets referencing it.' }, { name: record.originalFileName })
        : formatMessage({ id: 'system.files.delete.confirmMine', defaultMessage: 'Delete file "{name}"? This will remove the file and its record.' }, { name: record.originalFileName }),
      okText: formatMessage({ id: 'system.files.delete.okText', defaultMessage: 'Confirm delete' }),
      okButtonProps: { danger: true },
      onOk: async () => {
        await fileService.remove(record.id, scopeParams, requestOptions);
        message.success(formatMessage({ id: 'system.files.deleteSuccess', defaultMessage: 'File deleted' }));
        actionRef.current?.reload();
        if (previewRecord?.id === record.id) {
          closePreviewDrawer();
        }
      },
    });
  };

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
        render: (_, record) => <Tag>{providerLabelMap[record.provider] || record.provider}</Tag>,
      },
      {
        title: '默认存储空间',
        dataIndex: 'defaultStorage',
        width: 160,
        render: (_, record) => (record.defaultStorage ? <span style={{ color: '#52c41a', fontSize: 20 }}>✓</span> : '-'),
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
    [canDeleteStorage, canManageStorage, responsive.isMobile],
  );

  const columns = useMemo<ProColumns<FileObjectRecord>[]>(
    () => [
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
      {
        title: formatMessage({ id: 'system.files.field.fileName', defaultMessage: 'File name' }),
        dataIndex: 'originalFileName',
        width: 260,
        ellipsis: true,
        render: (_, record) => (
          <Space size={8} wrap={false}>
            <FileOutlined />
            <Typography.Link
              title={record.originalFileName}
              style={{ maxWidth: responsive.isMobile ? 180 : 300 }}
              ellipsis
              onClick={() => void openPreviewDrawer(record)}
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
        render: (_, record) => <Tag>{resolveFileTypeLabel(record.fileExtension)}</Tag>,
      },
      {
        title: formatMessage({ id: 'system.files.field.size', defaultMessage: 'Size' }),
        dataIndex: 'fileSizeBytes',
        width: 110,
        sorter: true,
        render: (_, record) => record.fileSizeLabel || formatFileSize(record.fileSizeBytes),
      },
      {
        title: formatMessage({ id: 'system.files.field.category', defaultMessage: 'Category' }),
        key: 'categoryColumn',
        dataIndex: 'category',
        width: 160,
        ...(responsive.isMobile ? { responsive: ['md', 'lg', 'xl', 'xxl'] as const } : {}),
        ellipsis: true,
        render: (_, record) => record.category ? <Tag color="blue">{record.category}</Tag> : '-',
      },
      {
        title: formatMessage({ id: 'system.files.field.tags', defaultMessage: 'Tags' }),
        dataIndex: 'tags',
        width: 180,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        ellipsis: true,
        render: (_, record) => renderTags(record.tags),
      },
      {
        title: formatMessage({ id: 'system.files.field.uploader', defaultMessage: 'Uploader' }),
        dataIndex: 'uploadedByName',
        width: 120,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        ellipsis: true,
        render: (_, record) => record.uploadedByName || '-',
      },
      {
        title: formatMessage({ id: 'system.files.field.uploadTime', defaultMessage: 'Upload time' }),
        dataIndex: 'createdAt',
        width: 170,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        sorter: true,
        render: (_, record) => formatDateTime(record.createdAt),
      },
      {
        title: formatMessage({ id: 'system.files.field.actions', defaultMessage: 'Actions' }),
        valueType: 'option',
        fixed: 'right',
        width: 220,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={[
              {
                key: 'download',
                label: formatMessage({ id: 'common.download', defaultMessage: 'Download' }),
                icon: <DownloadOutlined />,
                onClick: () => void handleDownload(record),
              },
              {
                key: 'copy',
                label: formatMessage({ id: 'common.copyLink', defaultMessage: 'Copy link' }),
                icon: <CopyOutlined />,
                onClick: () => void handleCopyLink(record),
              },
              ...(actionPermission.can(isTenantScope ? 'system:file:manage:delete' : 'system:file:delete')
                ? [
                    {
                      key: 'delete',
                      label: formatMessage({ id: 'common.delete', defaultMessage: 'Delete' }),
                      icon: <DeleteOutlined />,
                      danger: true,
                      onClick: () => handleDelete(record),
                    },
                  ]
                : []),
            ]}
          />
        ),
      },
    ],
    [actionPermission, isTenantScope, responsive.isMobile],
  );

  const uploadDraggerProps: UploadProps = {
    multiple: true,
    accept: FILE_ACCEPT,
    fileList: uploadFileList,
    beforeUpload: (file) => {
      const extension = file.name.split('.').pop()?.toLowerCase();
      if (!extension || !ALLOWED_UPLOAD_EXTENSIONS.includes(extension)) {
        message.error(formatMessage({ id: 'system.files.onlySupportDocument', defaultMessage: 'Only PDF, Word, Excel, and PPT files are allowed' }));
        return Upload.LIST_IGNORE;
      }
      return false;
    },
    onChange: (info) => {
      if (info.fileList.length > MAX_UPLOAD_FILE_COUNT) {
        message.warning(formatMessage({ id: 'system.files.maxUploadCount', defaultMessage: 'At most {count} files can be uploaded at once' }, { count: MAX_UPLOAD_FILE_COUNT }));
      }
      setUploadFileList(info.fileList.slice(-MAX_UPLOAD_FILE_COUNT));
    },
    onRemove: (file) => {
      setUploadFileList((prev) => prev.filter((item) => item.uid !== file.uid));
      return true;
    },
  };

  const addStorageItems: MenuProps['items'] = STORAGE_PROVIDER_OPTIONS.map((item) => ({
    key: item.value,
    label: item.label,
    onClick: () => openStorageDrawer(item.value),
  }));

  const storageToolbar = actionPermission.buildToolbarActions([
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
  ]);

  const actionToolbar = actionPermission.buildToolbarActions([
    {
      hidden: !canUploadInCurrentScope,
      value: (
        <Button key="upload" type="primary" icon={<UploadOutlined />} size={responsive.isMobile ? 'small' : 'middle'} disabled={!canUploadInCurrentScope} onClick={openUploadDrawer}>
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
  ]);

  const previewAbsoluteUrl = previewRecord ? buildPreviewAbsoluteUrl(previewRecord) : '';
  const previewMode = previewRecord ? resolvePreviewMode(previewRecord) : 'UNSUPPORTED';
  const previewMeta = previewRecord ? PREVIEW_MODE_LABELS[previewMode] : PREVIEW_MODE_LABELS.UNSUPPORTED;
  const storageProvider = Form.useWatch('provider', storageForm) as FileStorageProvider | undefined;
  const showRemoteStorageFields = storageProvider && storageProvider !== 'LOCAL';

  return (
    <ManagementPage
      title={pageTitle}
      ghost
    >
      {isTenantScope && !activeBucket ? (
        <ManagementTable<FileStorageSpaceRecord>
          actionRef={storageActionRef}
          rowKey="id"
          columns={storageColumns}
          isMobile={responsive.isMobile}
          search={false}
          request={async (params) => {
            const { current, pageSize } = params as Record<string, unknown>;
            const result = await fileService.storageSpaces(
              {
                pageNo: Number(current) || 1,
                pageSize: Number(pageSize) || 50,
              },
              requestOptions,
            );
            return adaptPageResult(result);
          }}
          toolBarRender={() => storageToolbar}
        />
      ) : (
        <ManagementTable<FileObjectRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          request={async (params, sorter) => {
            const { current, pageSize, keyword, category, previewMode: previewType } = params as Record<string, unknown>;
            const sortParams = resolveSortParams(sorter);
            const result = await fileService.list(
              {
                keyword: typeof keyword === 'string' ? keyword : undefined,
                category: typeof category === 'string' ? category : undefined,
                previewMode: typeof previewType === 'string' ? previewType : undefined,
                bucket: activeBucket || undefined,
                scope: fileScope,
                pageNo: Number(current) || 1,
                pageSize: Number(pageSize) || 20,
                ...sortParams,
              },
              requestOptions,
            );
            return adaptPageResult(result);
          }}
          toolBarRender={() => actionToolbar}
        />
      )}

      <ManagementDrawer
        title={storageDrawerMode === 'edit' ? `编辑 - ${editingStorageSpace?.title || '存储空间'}` : '新增存储空间'}
        open={storageDrawerOpen}
        onClose={closeStorageDrawer}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeStorageDrawer },
          { key: 'save', label: '保存', type: 'primary', loading: storageSaving, disabled: !canManageStorage, onClick: () => void handleSaveStorageSpace() },
        ]}
      >
        <Form form={storageForm} layout="vertical" initialValues={defaultStoragePayload('LOCAL')}>
          <Form.Item name="provider" label="存储类型" rules={[{ required: true, message: '请选择存储类型' }]}>
            <Select options={STORAGE_PROVIDER_OPTIONS} disabled={storageDrawerMode === 'edit'} />
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
            <Input placeholder="local" disabled={storageDrawerMode === 'edit'} />
          </Form.Item>
          {storageProvider === 'LOCAL' ? (
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
            <Radio.Group options={RENAME_STRATEGY_OPTIONS} />
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
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={formatMessage({ id: 'system.files.drawer.uploadTitle', defaultMessage: 'Upload document' })}
        open={uploadDrawerOpen}
        onClose={closeUploadDrawer}
        footerActions={[
          { key: 'cancel', label: formatMessage({ id: 'common.cancel', defaultMessage: 'Cancel' }), onClick: closeUploadDrawer },
          { key: 'upload', label: formatMessage({ id: 'system.files.drawer.startUpload', defaultMessage: 'Start upload' }), type: 'primary', loading: uploading, disabled: !canUploadInCurrentScope, onClick: () => void handleUpload() },
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Card title={formatMessage({ id: 'system.files.drawer.selectFiles', defaultMessage: 'Select files' })} bodyStyle={{ padding: 0 }} style={{ borderRadius: 8 }}>
            <Upload.Dragger {...uploadDraggerProps} style={{ borderRadius: 8 }}>
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{formatMessage({ id: 'system.files.drawer.draggerTitle', defaultMessage: 'Click or drag files here to upload' })}</p>
              <p className="ant-upload-hint">{formatMessage({ id: 'system.files.drawer.draggerHint', defaultMessage: 'Only PDF, Word, Excel, and PPT are allowed. Up to 5 files at a time.' })}</p>
            </Upload.Dragger>
          </Card>

          <Form form={uploadForm} layout="vertical">
            <Form.Item label={formatMessage({ id: 'system.files.drawer.categoryLabel', defaultMessage: 'Category' })} name="category">
              <Select
                allowClear
                mode="tags"
                tokenSeparators={[',', '，']}
                options={FILE_CATEGORY_OPTIONS}
                placeholder={formatMessage({ id: 'system.files.drawer.categoryPlaceholder', defaultMessage: 'e.g. policies, business materials, contracts' })}
              />
            </Form.Item>
            <Form.Item label={formatMessage({ id: 'system.files.drawer.tagsLabel', defaultMessage: 'Tags' })} name="tags" extra={formatMessage({ id: 'system.files.drawer.tagsExtra', defaultMessage: 'Separate multiple tags with commas' })}>
              <Input placeholder={formatMessage({ id: 'system.files.drawer.tagsPlaceholder', defaultMessage: 'e.g. ops,contract,archive' })} allowClear />
            </Form.Item>
            <Form.Item label={formatMessage({ id: 'system.files.drawer.remarkLabel', defaultMessage: 'Remark' })} name="remark">
              <Input.TextArea rows={4} placeholder={formatMessage({ id: 'system.files.drawer.remarkPlaceholder', defaultMessage: 'Optional, write a short note about the file' })} maxLength={512} showCount />
            </Form.Item>
          </Form>
        </Space>
      </ManagementDrawer>

      <ManagementDrawer
        title={previewRecord ? previewRecord.originalFileName : formatMessage({ id: 'system.files.preview.title', defaultMessage: 'File preview' })}
        open={previewDrawerOpen}
        onClose={closePreviewDrawer}
        footer={
          <div className="saas-drawer-footer">
            <Space wrap>
                <Button icon={<CopyOutlined />} onClick={() => previewRecord && void handleCopyLink(previewRecord)} disabled={!previewRecord}>
                {formatMessage({ id: 'common.copyLink', defaultMessage: 'Copy link' })}
              </Button>
              <Button icon={<DownloadOutlined />} onClick={() => previewRecord && void handleDownload(previewRecord)} disabled={!previewRecord}>
                {formatMessage({ id: 'common.download', defaultMessage: 'Download' })}
              </Button>
              <Button onClick={closePreviewDrawer}>{formatMessage({ id: 'system.files.preview.close', defaultMessage: 'Close' })}</Button>
            </Space>
          </div>
        }
      >
        {previewRecord ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions bordered column={responsive.isMobile ? 1 : 2} size="small">
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.fileName', defaultMessage: 'File name' })}>{previewRecord.originalFileName}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.type', defaultMessage: 'Type' })}>{resolveFileTypeLabel(previewRecord.fileExtension)}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.size', defaultMessage: 'Size' })}>{previewRecord.fileSizeLabel || formatFileSize(previewRecord.fileSizeBytes)}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.preview.downloadOnly', defaultMessage: 'Preview' })}>{<Tag color={previewMeta.color}>{previewMeta.text}</Tag>}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.category', defaultMessage: 'Category' })}>{previewRecord.category || '-'}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.uploader', defaultMessage: 'Uploader' })}>{previewRecord.uploadedByName || '-'}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.tags', defaultMessage: 'Tags' })} span={2}>
                {renderTags(previewRecord.tags)}
              </Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.drawer.remarkLabel', defaultMessage: 'Remark' })} span={2}>
                {previewRecord.remark || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.field.uploadTime', defaultMessage: 'Upload time' })}>{formatDateTime(previewRecord.createdAt)}</Descriptions.Item>
              <Descriptions.Item label={formatMessage({ id: 'system.files.preview.downloadLink', defaultMessage: 'Download link' })}>
                <Typography.Text copyable={{ text: previewAbsoluteUrl }}>{previewAbsoluteUrl || '-'}</Typography.Text>
              </Descriptions.Item>
            </Descriptions>

            <Card title={formatMessage({ id: 'system.files.preview.onlineTitle', defaultMessage: 'Online preview' })} bodyStyle={{ padding: 0 }} style={{ borderRadius: 8, overflow: 'hidden' }}>
              <Spin spinning={previewLoading || previewTextLoading || filePreviewLoading} tip={filePreviewLoading ? formatMessage({ id: 'system.files.preview.loadingPdf', defaultMessage: 'Loading file preview' }) : previewTextLoading ? formatMessage({ id: 'system.files.preview.loadingText', defaultMessage: 'Loading text content' }) : formatMessage({ id: 'system.files.preview.loadingDetails', defaultMessage: 'Loading file details' })}>
                <div style={{ minHeight: responsive.isMobile ? 240 : 520, padding: 16, background: token.colorFillQuaternary }}>
                  {previewMode === 'IMAGE' ? (
                    filePreviewUrl ? (
                      <Image
                        src={filePreviewUrl}
                        alt={previewRecord.originalFileName}
                        preview={false}
                        style={{ width: '100%', maxHeight: responsive.isMobile ? 360 : 560, objectFit: 'contain' }}
                      />
                    ) : null
                  ) : null}
                  {previewMode === 'PDF' ? (
                    filePreviewUrl ? (
                      <iframe
                        title={previewRecord.originalFileName}
                        src={`${filePreviewUrl}#view=FitH`}
                        style={{ width: '100%', height: responsive.isMobile ? 360 : 560, border: 0, background: token.colorBgContainer }}
                      />
                    ) : null
                  ) : null}
                  {previewMode === 'TEXT' ? (
                    <Typography.Paragraph
                      style={{
                        marginBottom: 0,
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        maxHeight: responsive.isMobile ? 360 : 560,
                        overflow: 'auto',
                      }}
                    >
                      {previewText || formatMessage({ id: 'system.files.preview.noText', defaultMessage: 'No text content yet' })}
                    </Typography.Paragraph>
                  ) : null}
                  {previewMode === 'UNSUPPORTED' ? (
                    <Empty
                      description={
                        <Space direction="vertical" size={8}>
                          <span>{formatMessage({ id: 'system.files.preview.unsupportedTitle', defaultMessage: 'This format is not supported for online preview yet' })}</span>
                          <span>{formatMessage({ id: 'system.files.preview.unsupportedHint', defaultMessage: 'You can download the file to view the full content' })}</span>
                        </Space>
                      }
                    />
                  ) : null}
                </div>
              </Spin>
            </Card>
          </Space>
        ) : (
          <Empty description={formatMessage({ id: 'system.files.preview.none', defaultMessage: 'No file details yet' })} />
        )}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default SystemFilesPage;
