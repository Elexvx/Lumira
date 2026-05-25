import { request, requestFile, type RequestOptions } from '@/services/common/request';
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

export const fileService = {
  list: (params: FileListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<FileObjectRecord>>('/v1/files', {
      method: 'GET',
      params,
      ...options,
    }),
  detail: (id: number, params: Pick<FileListQuery, 'scope'> = {}, options: RequestOptions = {}) =>
    request<FileObjectRecord>(`/v1/files/${id}`, {
      method: 'GET',
      params,
      ...options,
    }),
  upload: (file: File, payload: FileUploadPayload = {}, options: RequestOptions = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    if (payload.category) {
      formData.append('category', payload.category);
    }
    if (payload.tags) {
      formData.append('tags', payload.tags);
    }
    if (payload.remark) {
      formData.append('remark', payload.remark);
    }
    if (payload.bucket) {
      formData.append('bucket', payload.bucket);
    }

    return request<FileObjectRecord>('/v1/files/upload', {
      method: 'POST',
      headers: {},
      data: formData,
      ...options,
    });
  },
  remove: (id: number, params: Pick<FileListQuery, 'scope'> = {}, options: RequestOptions = {}) =>
    request<boolean>(`/v1/files/${id}`, {
      method: 'DELETE',
      params,
      ...options,
    }),
  download: (id: number, params: Pick<FileListQuery, 'scope'> = {}, options: RequestOptions = {}) =>
    requestFile(`/v1/files/${id}/download`, {
      method: 'GET',
      params,
      ...options,
    }),
  preview: (id: number, params: Pick<FileListQuery, 'scope'> = {}, options: RequestOptions = {}) =>
    requestFile(`/v1/files/${id}/preview`, {
      method: 'GET',
      params,
      ...options,
    }),
  storageSpaces: (params: { pageNo?: number; pageSize?: number } = {}, options: RequestOptions = {}) =>
    request<PagedResult<FileStorageSpaceRecord>>('/v1/files/storage-spaces', {
      method: 'GET',
      params,
      ...options,
    }),
  storageSpace: (storageKey: string, options: RequestOptions = {}) =>
    request<FileStorageSpaceRecord>(`/v1/files/storage-spaces/${storageKey}`, {
      method: 'GET',
      ...options,
    }),
  createStorageSpace: (payload: FileStorageSpacePayload, options: RequestOptions = {}) =>
    request<FileStorageSpaceRecord>('/v1/files/storage-spaces', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateStorageSpace: (id: number, payload: FileStorageSpacePayload, options: RequestOptions = {}) =>
    request<FileStorageSpaceRecord>(`/v1/files/storage-spaces/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  removeStorageSpace: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/files/storage-spaces/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  testStorageSpace: (id: number, options: RequestOptions = {}) =>
    request<FileStorageSpaceTestResult>(`/v1/files/storage-spaces/${id}/test`, {
      method: 'POST',
      ...options,
    }),
};
