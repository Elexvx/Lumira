import { Upload } from 'antd';
import type { RcFile } from 'antd/es/upload';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

export const DOCUMENT_UPLOAD_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt'] as const;
export const DOCUMENT_UPLOAD_ACCEPT = DOCUMENT_UPLOAD_EXTENSIONS.map((extension) => `.${extension}`).join(',');
export const DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB = 50;
export const DEFAULT_FILE_UPLOAD_MAX_COUNT = 5;

export const formatUploadSize = (bytes: number) => {
  if (bytes >= 1024 * 1024) {
    return `${Math.ceil(bytes / (1024 * 1024))}MB`;
  }
  if (bytes >= 1024) {
    return `${Math.ceil(bytes / 1024)}KB`;
  }
  return `${bytes}B`;
};

export const getFileExtension = (fileName?: string | null) => {
  const match = (fileName || '').trim().match(/\.([^.]+)$/);
  return match ? match[1].toLowerCase() : '';
};

export const parseAllowedMimeTypes = (allowedMimeTypes?: string | null) =>
  (allowedMimeTypes || '*')
    .split(/[,，\s]+/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

export const isMimeAllowed = (file: File, allowedMimeTypes?: string | null) => {
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

export const validateDocumentUploadFile = (
  file: File,
  options: {
    allowedExtensions?: readonly string[];
    allowedMimeTypes?: string | null;
    maxSizeMb?: number;
    allowedTypeLabelZh?: string;
    allowedTypeLabelEn?: string;
  } = {},
) => {
  const allowedExtensions = options.allowedExtensions || DOCUMENT_UPLOAD_EXTENSIONS;
  const allowedTypeLabel = t(
    options.allowedTypeLabelZh || 'PDF、Word、Excel、PPT、Markdown 或 TXT 文件',
    options.allowedTypeLabelEn || 'PDF, Word, Excel, PPT, Markdown, or TXT files',
  );
  const extension = getFileExtension(file.name);
  if (!extension) {
    return t(`文件缺少扩展名，请上传 ${allowedTypeLabel}`, `The file is missing an extension. Please upload ${allowedTypeLabel}.`);
  }
  if (!allowedExtensions.includes(extension)) {
    return t(`文件类型不支持，请上传 ${allowedTypeLabel}`, `Unsupported file type. Please upload ${allowedTypeLabel}.`);
  }

  const maxSizeMb = options.maxSizeMb || DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB;
  const maxSizeBytes = maxSizeMb * 1024 * 1024;
  if (file.size > maxSizeBytes) {
    return t(`文件过大，单个文件不能超过 ${formatUploadSize(maxSizeBytes)}`, `The file is too large. Each file must be ${formatUploadSize(maxSizeBytes)} or smaller.`);
  }

  if (!isMimeAllowed(file, options.allowedMimeTypes)) {
    return t('当前存储空间不允许上传该文件类型', 'This storage space does not allow this file type.');
  }

  return undefined;
};

export const rejectAntUploadFile = (message: string, notify: (message: string) => void) => {
  notify(message);
  return Upload.LIST_IGNORE;
};

export type UploadBeforeResult = boolean | typeof Upload.LIST_IGNORE | Promise<boolean | typeof Upload.LIST_IGNORE>;

export type UploadFileLike = File | RcFile;
