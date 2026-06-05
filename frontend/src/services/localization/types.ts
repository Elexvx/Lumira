import type {
  LocalizationEntry,
  LocalizationLanguage,
  LocalizationNamespace,
  LocalizationRelease,
  LocalizationRuntimeBundle,
  LocalizationSyncResult,
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

export type {
  LocalizationEntry,
  LocalizationLanguage,
  LocalizationNamespace,
  LocalizationRelease,
  LocalizationRuntimeBundle,
  LocalizationSyncResult,
};
