import { type ReactNode } from 'react';
import dayjs from 'dayjs';
import { formatMessage } from '@umijs/max';
import type { FileObjectRecord, FilePreviewMode } from '@/types/api';
import { normalizeUploadUrl, resolveAbsoluteUploadUrl } from '@/utils/uploadUrl';
import { Space, Tag } from 'antd';

export const FILE_EXTENSION_LABELS: Record<string, string> = {
  png: 'PNG',
  jpg: 'JPG',
  jpeg: 'JPG',
  gif: 'GIF',
  bmp: 'BMP',
  pdf: 'PDF',
  doc: 'Word',
  docx: 'Word',
  xls: 'Excel',
  xlsx: 'Excel',
  ppt: 'PPT',
  pptx: 'PPT',
  md: 'Markdown',
  txt: 'Text',
};

export const PREVIEW_MODE_LABELS: Record<FilePreviewMode, { text: string; color: string }> = {
  IMAGE: { text: formatMessage({ id: 'system.files.preview.image', defaultMessage: 'Image preview' }), color: 'green' },
  PDF: { text: formatMessage({ id: 'system.files.preview.pdf', defaultMessage: 'PDF preview' }), color: 'blue' },
  TEXT: { text: formatMessage({ id: 'system.files.preview.text', defaultMessage: 'Text preview' }), color: 'gold' },
  UNSUPPORTED: { text: formatMessage({ id: 'system.files.preview.downloadOnly', defaultMessage: 'Download only' }), color: 'default' },
};

export const FILE_CATEGORY_OPTIONS = [
  { label: formatMessage({ id: 'system.files.category.rules', defaultMessage: 'Policies' }), value: '制度文档' },
  { label: formatMessage({ id: 'system.files.category.contract', defaultMessage: 'Contracts' }), value: '合同协议' },
  { label: formatMessage({ id: 'system.files.category.business', defaultMessage: 'Business materials' }), value: '业务资料' },
  { label: formatMessage({ id: 'system.files.category.image', defaultMessage: 'Images' }), value: '图片素材' },
  { label: formatMessage({ id: 'system.files.category.other', defaultMessage: 'Other' }), value: '其他' },
];

export const FILE_STORAGE_PROVIDER_OPTIONS = [
  { label: formatMessage({ id: 'system.files.storage.provider.local', defaultMessage: '本地存储' }), value: 'LOCAL' },
  { label: formatMessage({ id: 'system.files.storage.provider.aliyunOss', defaultMessage: '阿里云 OSS' }), value: 'ALIYUN_OSS' },
  { label: formatMessage({ id: 'system.files.storage.provider.tencentCos', defaultMessage: '腾讯云 COS' }), value: 'TENCENT_COS' },
] as const;

export const FILE_STORAGE_RENAME_STRATEGY_OPTIONS = [
  { label: formatMessage({ id: 'system.files.storage.rename.appendRandomId', defaultMessage: '追加随机 ID' }), value: 'APPEND_RANDOM_ID' },
  { label: formatMessage({ id: 'system.files.storage.rename.randomString', defaultMessage: '随机字符串' }), value: 'RANDOM_STRING' },
  { label: formatMessage({ id: 'system.files.storage.rename.keepOriginal', defaultMessage: '保持原名（同名文件将被覆盖）' }), value: 'KEEP_ORIGINAL' },
] as const;

export const ALLOWED_UPLOAD_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt'];
export const FILE_ACCEPT = ALLOWED_UPLOAD_EXTENSIONS.map((extension) => `.${extension}`).join(',');
export const MAX_UPLOAD_FILE_COUNT = 5;

export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
};

export const formatFileSize = (value?: number | null) => {
  const size = value ?? 0;
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
};

export const splitTags = (value?: string | null) =>
  (value || '')
    .split(/[,\n;]/)
    .map((item) => item.trim())
    .filter(Boolean);

export const renderTags = (value?: string | null): ReactNode => {
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

export const resolveFileTypeLabel = (extension?: string | null) => {
  if (!extension) {
    return '-';
  }
  return FILE_EXTENSION_LABELS[extension.toLowerCase()] || extension.toUpperCase();
};

export const resolvePreviewMode = (record: FileObjectRecord) => record.previewMode || 'UNSUPPORTED';

export const resolveSortParams = (sorter?: Record<string, unknown>) => {
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

export const buildPreviewUrl = (record: FileObjectRecord) => normalizeUploadUrl(record.previewUrl || record.publicUrl);
export const buildPreviewAbsoluteUrl = (record: FileObjectRecord) => resolveAbsoluteUploadUrl(record.previewUrl || record.publicUrl);
