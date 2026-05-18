import { request, type RequestOptions } from '@/services/common/request';
import type { FileObjectRecord } from '@/types/api';

export interface PagedResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface SiteSettings {
  id?: number;
  code: string;
  name: string;
  primaryDomain?: string;
  loginRoute?: string;
  logoFileId?: number;
  logoUrl?: string;
  faviconFileId?: number;
  faviconUrl?: string;
  themeJson?: string;
  seoJson?: string;
  status?: string;
  updatedAt?: string;
}

export interface SiteNavigation {
  id: number;
  parentId?: number;
  title: string;
  linkType: string;
  linkTarget: string;
  openType: string;
  sortOrder: number;
  status: string;
}

export interface SiteCarousel {
  id: number;
  title: string;
  subtitle?: string;
  imageFileId?: number;
  imageUrl?: string;
  linkType: string;
  linkTarget?: string;
  openType: string;
  sortOrder: number;
  status: string;
  updatedAt?: string;
}

export interface SiteContent {
  id: number;
  categoryId?: number;
  title: string;
  slug: string;
  summary?: string;
  coverFileId?: number;
  coverUrl?: string;
  bodyType: string;
  bodyText?: string;
  bodyJson?: string;
  seoJson?: string;
  tagsJson?: string;
  status: string;
  publishedAt?: string;
  updatedAt?: string;
}

export interface SiteCategory {
  id: number;
  parentId?: number;
  code: string;
  name: string;
  sortOrder: number;
  status: string;
}

export interface SiteSubmission {
  id: number;
  formId: number;
  formName?: string;
  submitterUserId?: number;
  submitterIp?: string;
  dataJson: string;
  attachmentFileIdsJson?: string;
  status: string;
  reviewedBy?: number;
  reviewedAt?: string;
  reviewRemark?: string;
  createdAt?: string;
}

export const siteService = {
  settings: (options: RequestOptions = {}) => request<SiteSettings>('/v1/site/settings', { method: 'GET', ...options }),
  updateSettings: (payload: Partial<SiteSettings>, options: RequestOptions = {}) =>
    request<SiteSettings>('/v1/site/settings', { method: 'PUT', data: payload, ...options }),
  uploadImage: (file: File, usage: 'settings' | 'carousel', options: RequestOptions = {}) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('usage', usage);
    return request<FileObjectRecord>('/v1/site/uploads/image', {
      method: 'POST',
      headers: {},
      data: formData,
      ...options,
    });
  },
  navigation: (options: RequestOptions = {}) => request<SiteNavigation[]>('/v1/site/navigation', { method: 'GET', ...options }),
  createNavigation: (payload: Partial<SiteNavigation>, options: RequestOptions = {}) =>
    request<SiteNavigation>('/v1/site/navigation', { method: 'POST', data: payload, ...options }),
  updateNavigation: (id: number, payload: Partial<SiteNavigation>, options: RequestOptions = {}) =>
    request<SiteNavigation>(`/v1/site/navigation/${id}`, { method: 'PUT', data: payload, ...options }),
  deleteNavigation: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/site/navigation/${id}`, { method: 'DELETE', ...options }),
  carousels: (options: RequestOptions = {}) => request<SiteCarousel[]>('/v1/site/carousels', { method: 'GET', ...options }),
  createCarousel: (payload: Partial<SiteCarousel>, options: RequestOptions = {}) =>
    request<SiteCarousel>('/v1/site/carousels', { method: 'POST', data: payload, ...options }),
  updateCarousel: (id: number, payload: Partial<SiteCarousel>, options: RequestOptions = {}) =>
    request<SiteCarousel>(`/v1/site/carousels/${id}`, { method: 'PUT', data: payload, ...options }),
  deleteCarousel: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/site/carousels/${id}`, { method: 'DELETE', ...options }),
  contents: (params: Record<string, unknown> = {}, options: RequestOptions = {}) =>
    request<PagedResult<SiteContent>>('/v1/site/contents', { method: 'GET', params, ...options }),
  createContent: (payload: Partial<SiteContent>, options: RequestOptions = {}) =>
    request<SiteContent>('/v1/site/contents', { method: 'POST', data: payload, ...options }),
  updateContent: (id: number, payload: Partial<SiteContent>, options: RequestOptions = {}) =>
    request<SiteContent>(`/v1/site/contents/${id}`, { method: 'PUT', data: payload, ...options }),
  publishContent: (id: number, options: RequestOptions = {}) =>
    request<SiteContent>(`/v1/site/contents/${id}/publish`, { method: 'POST', ...options }),
  offlineContent: (id: number, options: RequestOptions = {}) =>
    request<SiteContent>(`/v1/site/contents/${id}/offline`, { method: 'POST', ...options }),
  deleteContent: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/site/contents/${id}`, { method: 'DELETE', ...options }),
  categories: (options: RequestOptions = {}) => request<SiteCategory[]>('/v1/site/categories', { method: 'GET', ...options }),
  createCategory: (payload: Partial<SiteCategory>, options: RequestOptions = {}) =>
    request<SiteCategory>('/v1/site/categories', { method: 'POST', data: payload, ...options }),
  submissions: (params: Record<string, unknown> = {}, options: RequestOptions = {}) =>
    request<PagedResult<SiteSubmission>>('/v1/site/submissions', { method: 'GET', params, ...options }),
  reviewSubmission: (id: number, payload: { status: string; reviewRemark?: string }, options: RequestOptions = {}) =>
    request<SiteSubmission>(`/v1/site/submissions/${id}/review`, { method: 'PUT', data: payload, ...options }),
};
