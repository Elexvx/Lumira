import { request, type RequestOptions } from '@/services/common/request';
import type {
  LocalizationEntry,
  LocalizationLanguage,
  LocalizationNamespace,
  LocalizationRelease,
  LocalizationRuntimeBundle,
  LocalizationSyncResult,
  PagedResult,
} from '@/types/api';

export interface LocalizationLanguagePayload {
  localeCode: string;
  languageName: string;
  nativeName?: string;
  fallbackLocale?: string;
  sortNo?: number;
  status?: string;
  defaultLanguage?: boolean;
}

export interface LocalizationNamespacePayload {
  namespaceCode: string;
  namespaceName: string;
  sourceType?: string;
  sourceRef?: string;
  sortNo?: number;
  status?: string;
}

export interface LocalizationEntryPayload {
  namespaceCode: string;
  messageKey: string;
  defaultMessage: string;
  sourceLocale: string;
  sourceType?: string;
  sourceRef?: string;
  status?: string;
  localeCode?: string;
  translatedMessage?: string;
  translations?: Record<string, string>;
}

export interface LocalizationSyncItem extends LocalizationEntryPayload {}

export interface LocalizationSyncPayload {
  sourceLocale: string;
  items: LocalizationSyncItem[];
}

export interface LocalizationPublishPayload {
  localeCode: string;
  note?: string;
}

export interface LocalizationRollbackPayload {
  releaseId: number;
}

export interface LocalizationEntryListQuery extends Record<string, unknown> {
  localeCode?: string;
  namespaceCode?: string;
  keyword?: string;
  status?: string;
  translationStatus?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface LocalizationNamespaceListQuery extends Record<string, unknown> {
  localeCode?: string;
}

export const localizationService = {
  languages: (options: RequestOptions = {}) =>
    request<LocalizationLanguage[]>('/v1/localization/languages', {
      method: 'GET',
      ...options,
    }),
  createLanguage: (payload: LocalizationLanguagePayload, options: RequestOptions = {}) =>
    request<LocalizationLanguage>('/v1/localization/languages', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateLanguage: (id: number, payload: LocalizationLanguagePayload, options: RequestOptions = {}) =>
    request<LocalizationLanguage>(`/v1/localization/languages/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteLanguage: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/localization/languages/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  namespaces: (params: LocalizationNamespaceListQuery = {}, options: RequestOptions = {}) =>
    request<LocalizationNamespace[]>('/v1/localization/namespaces', {
      method: 'GET',
      params,
      ...options,
    }),
  createNamespace: (payload: LocalizationNamespacePayload, options: RequestOptions = {}) =>
    request<LocalizationNamespace>('/v1/localization/namespaces', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateNamespace: (id: number, payload: LocalizationNamespacePayload, options: RequestOptions = {}) =>
    request<LocalizationNamespace>(`/v1/localization/namespaces/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteNamespace: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/localization/namespaces/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  entries: (params: LocalizationEntryListQuery = {}, options: RequestOptions = {}) =>
    request<PagedResult<LocalizationEntry>>('/v1/localization/entries', {
      method: 'GET',
      params,
      ...options,
    }),
  createEntry: (payload: LocalizationEntryPayload, options: RequestOptions = {}) =>
    request<LocalizationEntry>('/v1/localization/entries', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  updateEntry: (id: number, payload: LocalizationEntryPayload, options: RequestOptions = {}) =>
    request<LocalizationEntry>(`/v1/localization/entries/${id}`, {
      method: 'PUT',
      data: payload,
      ...options,
    }),
  deleteEntry: (id: number, options: RequestOptions = {}) =>
    request<boolean>(`/v1/localization/entries/${id}`, {
      method: 'DELETE',
      ...options,
    }),
  sync: (payload: LocalizationSyncPayload, options: RequestOptions = {}) =>
    request<LocalizationSyncResult>('/v1/localization/sync', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  releases: (localeCode: string, options: RequestOptions = {}) =>
    request<LocalizationRelease[]>('/v1/localization/releases', {
      method: 'GET',
      params: { localeCode },
      ...options,
    }),
  publish: (payload: LocalizationPublishPayload, options: RequestOptions = {}) =>
    request<LocalizationRelease>('/v1/localization/publish', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  rollback: (payload: LocalizationRollbackPayload, options: RequestOptions = {}) =>
    request<LocalizationRelease>('/v1/localization/rollback', {
      method: 'POST',
      data: payload,
      ...options,
    }),
  runtime: (localeCode: string, options: RequestOptions = {}) =>
    request<LocalizationRuntimeBundle>(`/v1/localization/runtime/${localeCode}`, {
      method: 'GET',
      skipAuth: true,
      silent: true,
      ...options,
    }),
};
