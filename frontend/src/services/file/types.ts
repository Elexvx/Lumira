import type { FileObjectRecord, FileStorageSpacePayload, FileStorageSpaceRecord, FileStorageSpaceTestResult, PagedResult } from '@/types/api';

export interface FileListQuery extends Record<string, unknown> {
  keyword?: string;
  category?: string;
  fileExtension?: string;
  previewMode?: string;
  bucket?: string;
  scope?: 'mine' | 'tenant';
  sortField?: string;
  sortOrder?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface FileUploadPayload {
  category?: string;
  tags?: string;
  remark?: string;
  bucket?: string;
}

export interface StorageSpaceListQuery extends Record<string, unknown> {
  pageNo?: number;
  pageSize?: number;
}

export type {
  FileObjectRecord,
  FileStorageSpacePayload,
  FileStorageSpaceRecord,
  FileStorageSpaceTestResult,
  PagedResult,
};
