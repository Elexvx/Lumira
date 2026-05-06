import { request, requestFile, type RequestOptions } from '@/services/common/request';
import type { FileObjectRecord, PagedResult } from '@/types/api';

export interface FileListQuery extends Record<string, unknown> {
  keyword?: string;
  category?: string;
  fileExtension?: string;
  previewMode?: string;
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
};
