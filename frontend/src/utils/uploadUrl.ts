import { API_ORIGIN } from '@/constants/http';

const DEFAULT_UPLOAD_PUBLIC_PATH = '/api/uploads';

export const normalizeUploadUrl = (value?: string | null, publicPath = DEFAULT_UPLOAD_PUBLIC_PATH) => {
  const trimmed = value?.trim() || '';
  if (!trimmed) {
    return '';
  }

  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(trimmed)) {
    return trimmed;
  }

  const normalizedPublicPath = normalizePublicPath(publicPath);
  const publicPathWithoutLeadingSlash = normalizedPublicPath.slice(1);
  const uploadOrigin = normalizeUploadOrigin(API_ORIGIN);

  if (trimmed === normalizedPublicPath || trimmed.startsWith(`${normalizedPublicPath}/`)) {
    return uploadOrigin ? `${uploadOrigin}${trimmed}` : trimmed;
  }

  if (trimmed === publicPathWithoutLeadingSlash || trimmed.startsWith(`${publicPathWithoutLeadingSlash}/`)) {
    return uploadOrigin ? `${uploadOrigin}/${trimmed}` : `/${trimmed}`;
  }

  const normalizedPath = `${normalizedPublicPath}/${trimmed.replace(/^\/+/, '')}`;
  return uploadOrigin ? `${uploadOrigin}${normalizedPath}` : normalizedPath;
};

export const resolveAbsoluteUploadUrl = (value?: string | null, publicPath = DEFAULT_UPLOAD_PUBLIC_PATH) => {
  const normalized = normalizeUploadUrl(value, publicPath);
  if (!normalized) {
    return '';
  }

  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(normalized)) {
    return normalized;
  }

  if (typeof window === 'undefined' || !window.location?.origin) {
    return normalized;
  }

  return new URL(normalized, window.location.origin).toString();
};

const normalizePublicPath = (value: string) => {
  let normalized = value.trim();
  if (!normalized.startsWith('/')) {
    normalized = `/${normalized}`;
  }
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1);
  }
  return normalized;
};

const normalizeUploadOrigin = (value?: string | null) => value?.trim().replace(/\/+$/, '') || '';
