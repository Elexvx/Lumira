import { Upload } from 'antd';
import type { RcFile } from 'antd/es/upload';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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
    allowedTypeLabel?: string;
  } = {},
) => {
  const allowedExtensions = options.allowedExtensions || DOCUMENT_UPLOAD_EXTENSIONS;
  const allowedTypeLabel = options.allowedTypeLabel || t('ui.utils.uploadvalidation.pdfWordExcelPptMarkdownOrTxtFiles');
  const extension = getFileExtension(file.name);
  if (!extension) {
    return t('ui.utils.uploadvalidation.theFileIsMissingAnExtensionPleaseUpload', { allowedTypeLabel });
  }
  if (!allowedExtensions.includes(extension)) {
    return t('ui.utils.uploadvalidation.unsupportedFileTypePleaseUpload', { allowedTypeLabel });
  }

  const maxSizeMb = options.maxSizeMb || DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB;
  const maxSizeBytes = maxSizeMb * 1024 * 1024;
  if (file.size > maxSizeBytes) {
    return t('ui.utils.uploadvalidation.theFileIsTooLargeEachFileMust', { value1: formatUploadSize(maxSizeBytes) });
  }

  if (!isMimeAllowed(file, options.allowedMimeTypes)) {
    return t('ui.utils.uploadvalidation.thisStorageSpaceDoesNotAllowThisFile');
  }

  return undefined;
};

export const rejectAntUploadFile = (message: string, notify: (message: string) => void) => {
  notify(message);
  return Upload.LIST_IGNORE;
};

export type UploadBeforeResult = boolean | typeof Upload.LIST_IGNORE | Promise<boolean | typeof Upload.LIST_IGNORE>;

export type UploadFileLike = File | RcFile;
