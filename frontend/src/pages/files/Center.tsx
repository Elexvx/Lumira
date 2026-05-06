import {
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileOutlined,
  InboxOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { type ActionType, type ProColumns } from '@ant-design/pro-components';
import dayjs from 'dayjs';
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Image,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { formatMessage, useLocation } from '@umijs/max';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { adaptPageResult } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { fileService } from '@/services/file';
import type { FileObjectRecord, FilePreviewMode } from '@/types/api';
import { copyTextToClipboard } from '@/utils/clipboard';
import { confirmAction } from '@/utils/confirm';
import { normalizeUploadUrl, resolveAbsoluteUploadUrl } from '@/utils/uploadUrl';

const FILE_EXTENSION_LABELS: Record<string, string> = {
  pdf: 'PDF',
  doc: 'Word',
  docx: 'Word',
  xls: 'Excel',
  xlsx: 'Excel',
  ppt: 'PPT',
  pptx: 'PPT',
};

const PREVIEW_MODE_LABELS: Record<FilePreviewMode, { text: string; color: string }> = {
  IMAGE: { text: formatMessage({ id: 'system.files.preview.image', defaultMessage: 'Image preview' }), color: 'green' },
  PDF: { text: formatMessage({ id: 'system.files.preview.pdf', defaultMessage: 'PDF preview' }), color: 'blue' },
  TEXT: { text: formatMessage({ id: 'system.files.preview.text', defaultMessage: 'Text preview' }), color: 'gold' },
  UNSUPPORTED: { text: formatMessage({ id: 'system.files.preview.downloadOnly', defaultMessage: 'Download only' }), color: 'default' },
};

const FILE_CATEGORY_OPTIONS = [
  { label: formatMessage({ id: 'system.files.category.rules', defaultMessage: 'Policies' }), value: '制度文档' },
  { label: formatMessage({ id: 'system.files.category.contract', defaultMessage: 'Contracts' }), value: '合同协议' },
  { label: formatMessage({ id: 'system.files.category.business', defaultMessage: 'Business materials' }), value: '业务资料' },
  { label: formatMessage({ id: 'system.files.category.image', defaultMessage: 'Images' }), value: '图片素材' },
  { label: formatMessage({ id: 'system.files.category.other', defaultMessage: 'Other' }), value: '其他' },
];

const ALLOWED_UPLOAD_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'];
const FILE_ACCEPT = ALLOWED_UPLOAD_EXTENSIONS.map((extension) => `.${extension}`).join(',');
const MAX_UPLOAD_FILE_COUNT = 5;

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
};

const formatFileSize = (value?: number | null) => {
  const size = value ?? 0;
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
};

const splitTags = (value?: string | null) =>
  (value || '')
    .split(/[,\n;]/)
    .map((item) => item.trim())
    .filter(Boolean);

const renderTags = (value?: string | null) => {
  const tags = splitTags(value);
  if (!tags.length) {
    return '-';
  }
  return (
    <Space wrap size={[6, 6]}>
      {tags.map((tag) => (
        <Tag key={tag}>{tag}</Tag>
      ))}
    </Space>
  );
};

const resolveFileTypeLabel = (extension?: string | null) => {
  if (!extension) {
    return '-';
  }
  return FILE_EXTENSION_LABELS[extension.toLowerCase()] || extension.toUpperCase();
};

const resolvePreviewMode = (record: FileObjectRecord) => record.previewMode || 'UNSUPPORTED';

const resolveSortParams = (sorter?: Record<string, unknown>) => {
  if (!sorter) {
    return {};
  }

  const entry = Object.entries(sorter).find(([, order]) => order === 'ascend' || order === 'descend');
  if (!entry) {
    return {};
  }

  const [sortField, sortOrder] = entry;
  return {
    sortField,
    sortOrder: sortOrder === 'ascend' ? 'ascend' : 'descend',
  };
};

const buildPreviewUrl = (record: FileObjectRecord) => normalizeUploadUrl(record.previewUrl || record.publicUrl);
const buildPreviewAbsoluteUrl = (record: FileObjectRecord) => resolveAbsoluteUploadUrl(record.previewUrl || record.publicUrl);

const SystemFilesPage = () => {
  const location = useLocation();
  const actionRef = useRef<ActionType>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const isTenantScope = location.pathname === '/settings/files/all' || location.pathname === '/files/all' || location.pathname === '/system/files/all';
  const fileScope = isTenantScope ? 'tenant' : 'mine';
  const pageTitle = isTenantScope
    ? formatMessage({ id: 'system.files.title.all', defaultMessage: 'Global File Management' })
    : formatMessage({ id: 'system.files.title.my', defaultMessage: 'My Files' });
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
  const [pdfPreviewLoading, setPdfPreviewLoading] = useState(false);
  const [pdfPreviewUrl, setPdfPreviewUrl] = useState('');

  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
    }),
    [],
  );
  const scopeParams = useMemo(() => ({ scope: fileScope as 'mine' | 'tenant' }), [fileScope]);

  const closeUploadDrawer = () => {
    setUploadDrawerOpen(false);
    setUploading(false);
    setUploadFileList([]);
    uploadForm.resetFields();
  };

  const openUploadDrawer = () => {
    uploadForm.resetFields();
    setUploadFileList([]);
    setUploadDrawerOpen(true);
  };

  const openPreviewDrawer = async (record: FileObjectRecord) => {
    setPreviewDrawerOpen(true);
    setPreviewRecord(record);
    setPreviewText('');
    setPdfPreviewUrl('');
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
    setPdfPreviewLoading(false);
    setPreviewText('');
    if (pdfPreviewUrl) {
      window.URL.revokeObjectURL(pdfPreviewUrl);
    }
    setPdfPreviewUrl('');
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
    void fetch(buildPreviewUrl(previewRecord))
      .then((response) => response.text())
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
  }, [previewDrawerOpen, previewRecord?.id]);

  useEffect(() => {
    if (!previewDrawerOpen || !previewRecord) {
      return;
    }

    if (resolvePreviewMode(previewRecord) !== 'PDF') {
      setPdfPreviewLoading(false);
      if (pdfPreviewUrl) {
        window.URL.revokeObjectURL(pdfPreviewUrl);
        setPdfPreviewUrl('');
      }
      return;
    }

    let active = true;
    let objectUrl = '';
    setPdfPreviewLoading(true);
    void fileService
      .download(previewRecord.id, scopeParams, requestOptions)
      .then((blob) => {
        objectUrl = window.URL.createObjectURL(blob);
        if (active) {
          setPdfPreviewUrl(objectUrl);
        } else {
          window.URL.revokeObjectURL(objectUrl);
        }
      })
      .catch(() => {
        if (active) {
          message.error(formatMessage({ id: 'system.files.pdfPreviewFailed', defaultMessage: 'PDF preview failed to load' }));
        }
      })
      .finally(() => {
        if (active) {
          setPdfPreviewLoading(false);
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
        ellipsis: true,
        render: (_, record) => record.category ? <Tag color="blue">{record.category}</Tag> : '-',
      },
      {
        title: formatMessage({ id: 'system.files.field.tags', defaultMessage: 'Tags' }),
        dataIndex: 'tags',
        width: 180,
        ellipsis: true,
        render: (_, record) => renderTags(record.tags),
      },
      {
        title: formatMessage({ id: 'system.files.field.uploader', defaultMessage: 'Uploader' }),
        dataIndex: 'uploadedByName',
        width: 120,
        ellipsis: true,
        render: (_, record) => record.uploadedByName || '-',
      },
      {
        title: formatMessage({ id: 'system.files.field.uploadTime', defaultMessage: 'Upload time' }),
        dataIndex: 'createdAt',
        width: 170,
        sorter: true,
        render: (_, record) => formatDateTime(record.createdAt),
      },
      {
        title: formatMessage({ id: 'system.files.field.actions', defaultMessage: 'Actions' }),
        valueType: 'option',
        width: 220,
        render: (_, record) => (
          <Space size={4} wrap={false}>
            <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => void handleDownload(record)}>
              {formatMessage({ id: 'common.download', defaultMessage: 'Download' })}
            </Button>
            <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => void handleCopyLink(record)}>
              {formatMessage({ id: 'common.copyLink', defaultMessage: 'Copy link' })}
            </Button>
            {actionPermission.can(isTenantScope ? 'system:file:manage:delete' : 'system:file:delete') ? (
              <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
                {formatMessage({ id: 'common.delete', defaultMessage: 'Delete' })}
              </Button>
            ) : null}
          </Space>
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

  const actionToolbar = actionPermission.buildToolbarActions([
    {
      permission: 'system:file:upload',
      hidden: isTenantScope,
      value: (
        <Button key="upload" type="primary" icon={<UploadOutlined />} size={responsive.isMobile ? 'small' : 'middle'} onClick={openUploadDrawer}>
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
  ]);

  const previewUrl = previewRecord ? buildPreviewUrl(previewRecord) : '';
  const previewAbsoluteUrl = previewRecord ? buildPreviewAbsoluteUrl(previewRecord) : '';
  const previewMode = previewRecord ? resolvePreviewMode(previewRecord) : 'UNSUPPORTED';
  const previewMeta = previewRecord ? PREVIEW_MODE_LABELS[previewMode] : PREVIEW_MODE_LABELS.UNSUPPORTED;

  return (
    <ManagementPage
      title={pageTitle}
      ghost
    >
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

      <ManagementDrawer
        title={formatMessage({ id: 'system.files.drawer.uploadTitle', defaultMessage: 'Upload document' })}
        open={uploadDrawerOpen}
        onClose={closeUploadDrawer}
        footerActions={[
          { key: 'cancel', label: formatMessage({ id: 'common.cancel', defaultMessage: 'Cancel' }), onClick: closeUploadDrawer },
          { key: 'upload', label: formatMessage({ id: 'system.files.drawer.startUpload', defaultMessage: 'Start upload' }), type: 'primary', loading: uploading, onClick: () => void handleUpload() },
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
              <Spin spinning={previewLoading || previewTextLoading || pdfPreviewLoading} tip={pdfPreviewLoading ? formatMessage({ id: 'system.files.preview.loadingPdf', defaultMessage: 'Loading PDF preview' }) : previewTextLoading ? formatMessage({ id: 'system.files.preview.loadingText', defaultMessage: 'Loading text content' }) : formatMessage({ id: 'system.files.preview.loadingDetails', defaultMessage: 'Loading file details' })}>
                <div style={{ minHeight: responsive.isMobile ? 240 : 520, padding: 16, background: 'rgba(0,0,0,0.02)' }}>
                  {previewMode === 'IMAGE' ? (
                    <Image
                      src={previewUrl}
                      alt={previewRecord.originalFileName}
                      preview={false}
                      style={{ width: '100%', maxHeight: responsive.isMobile ? 360 : 560, objectFit: 'contain' }}
                    />
                  ) : null}
                  {previewMode === 'PDF' ? (
                    pdfPreviewUrl ? (
                      <iframe
                        title={previewRecord.originalFileName}
                        src={`${pdfPreviewUrl}#view=FitH`}
                        style={{ width: '100%', height: responsive.isMobile ? 360 : 560, border: 0, background: '#fff' }}
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
