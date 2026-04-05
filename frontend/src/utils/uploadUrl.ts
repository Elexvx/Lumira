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

  if (trimmed === normalizedPublicPath || trimmed.startsWith(`${normalizedPublicPath}/`)) {
    return trimmed;
  }

  if (trimmed === publicPathWithoutLeadingSlash || trimmed.startsWith(`${publicPathWithoutLeadingSlash}/`)) {
    return `/${trimmed}`;
  }

  return `${normalizedPublicPath}/${trimmed.replace(/^\/+/, '')}`;
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
