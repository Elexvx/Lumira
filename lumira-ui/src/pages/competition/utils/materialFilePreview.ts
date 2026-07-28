import type { FileObjectRecord } from '@/types/api';

export type MaterialFilePreviewKind = 'IMAGE' | 'PDF' | 'OFFICE_HTML' | 'EXTRACTED_TEXT' | 'UNSUPPORTED';

const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']);
const OFFICE_PREVIEW_EXTENSIONS = new Set(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']);
const TEXT_PREVIEW_EXTENSIONS = new Set([
  'md',
  'markdown',
  'txt',
  'csv',
  'json',
  'log',
]);

const normalizeExtension = (record: Pick<FileObjectRecord, 'fileExtension' | 'originalFileName'>) => {
  const configured = record.fileExtension?.trim().toLowerCase().replace(/^\./, '');
  if (configured) {
    return configured;
  }
  const matched = record.originalFileName?.trim().toLowerCase().match(/\.([^.]+)$/);
  return matched?.[1] || '';
};

export const resolveMaterialFilePreviewKind = (
  record: Pick<FileObjectRecord, 'fileExtension' | 'originalFileName' | 'mimeType' | 'previewMode'>,
): MaterialFilePreviewKind => {
  const extension = normalizeExtension(record);
  const mimeType = record.mimeType?.trim().toLowerCase() || '';

  if (extension === 'pdf' || record.previewMode === 'PDF' || mimeType === 'application/pdf') {
    return 'PDF';
  }
  if (IMAGE_EXTENSIONS.has(extension) || record.previewMode === 'IMAGE' || mimeType.startsWith('image/')) {
    return 'IMAGE';
  }
  if (
    OFFICE_PREVIEW_EXTENSIONS.has(extension)
    || mimeType.includes('word')
    || mimeType.includes('excel')
    || mimeType.includes('powerpoint')
  ) {
    return 'OFFICE_HTML';
  }
  if (
    TEXT_PREVIEW_EXTENSIONS.has(extension)
    || record.previewMode === 'TEXT'
    || mimeType.startsWith('text/')
  ) {
    return 'EXTRACTED_TEXT';
  }
  return 'UNSUPPORTED';
};

export const supportsExtractedTextPreview = (kind: MaterialFilePreviewKind) =>
  kind === 'EXTRACTED_TEXT';
