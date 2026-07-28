import { describe, expect, it } from 'vitest';
import type { FilePreviewMode } from '@/types/api';
import { resolveMaterialFilePreviewKind } from './materialFilePreview';

const record = (
  fileExtension: string,
  previewMode: FilePreviewMode = 'UNSUPPORTED',
  mimeType?: string,
) => ({
  fileExtension,
  originalFileName: `material.${fileExtension}`,
  mimeType,
  previewMode,
});

describe('material file preview strategy', () => {
  it.each(['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt'])(
    'uses extracted text preview for %s',
    (extension) => {
      expect(resolveMaterialFilePreviewKind(record(extension))).toBe('EXTRACTED_TEXT');
    },
  );

  it('keeps native PDF and image previews', () => {
    expect(resolveMaterialFilePreviewKind(record('pdf', 'PDF'))).toBe('PDF');
    expect(resolveMaterialFilePreviewKind(record('png', 'IMAGE'))).toBe('IMAGE');
  });

  it('falls back to the MIME type when extension metadata is incomplete', () => {
    expect(resolveMaterialFilePreviewKind(record('', 'UNSUPPORTED', 'application/msword')))
      .toBe('EXTRACTED_TEXT');
  });

  it('does not attempt to preview archives', () => {
    expect(resolveMaterialFilePreviewKind(record('zip'))).toBe('UNSUPPORTED');
  });
});
