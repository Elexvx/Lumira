export interface ApiResponse<T> {
  code: string;
  data: T;
}

export interface SiteSettings {
  id?: number;
  code?: string;
  name?: string;
  primaryDomain?: string;
  loginRoute?: string;
  logoUrl?: string;
  faviconUrl?: string;
  themeJson?: string;
  seoJson?: string;
  status?: string;
}

export interface NavigationItem {
  id: number;
  parentId?: number | null;
  title: string;
  linkType: string;
  linkTarget?: string | null;
  openType?: string | null;
  children?: NavigationItem[] | null;
}

export interface SiteCarousel {
  id: number;
  imageFileId?: number | null;
  imageUrl?: string | null;
  title?: string | null;
  subtitle?: string | null;
  linkType?: string | null;
  linkTarget?: string | null;
  openType?: string | null;
  sortOrder?: number | null;
  status?: string | null;
  updatedAt?: string | null;
}

export interface PageBlock {
  id: string;
  type: string;
  props: Record<string, unknown>;
}

export interface PublicPage {
  site?: SiteSettings;
  page?: {
    id?: number;
    title?: string;
    slug?: string;
    pageType?: string;
    seoJson?: string;
    status?: string;
    publishedAt?: string;
    updatedAt?: string;
  };
  blocksJson?: string;
}

export interface ContentRecord {
  id: number;
  title: string;
  slug: string;
  summary?: string;
  coverUrl?: string;
  bodyText?: string;
  bodyJson?: string;
  seoJson?: string;
  tagsJson?: string;
  publishedAt?: string;
  updatedAt?: string;
}

export interface PublicFormField {
  name: string;
  label?: string;
  type?: string;
  required?: boolean;
  placeholder?: string;
  options?: Array<{ label: string; value: string } | string>;
}

export interface PublicForm {
  id: number;
  code: string;
  name: string;
  submitPolicy?: string;
  schemaJson?: string;
  status?: string;
}

export interface PublicSubmission {
  id: number;
  formId: number;
  status: string;
  createdAt?: string;
}

export interface PagedResult<T> {
  records: T[];
  total: number;
}

const apiBase = process.env.SITE_API_BASE_URL || process.env.NEXT_PUBLIC_SITE_API_BASE_URL || 'http://localhost:8080/api';

interface FetchJsonOptions {
  cache?: RequestCache;
  revalidate?: number | false;
}

interface SiteApiErrorLog {
  path: string;
  url: string;
  kind: 'http' | 'network' | 'json';
  status?: number;
  code?: string;
  message?: string;
}

const unavailableSite: SiteSettings = { name: 'Legendary Invention' };

export function publicAssetUrl(value?: string | null) {
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;
  const normalized = value.startsWith('/') ? value : `/${value}`;
  try {
    return `${new URL(apiBase).origin}${normalized}`;
  } catch {
    return normalized;
  }
}

export function siteApiUrl(path: string) {
  return `${apiBase}${path}`;
}

export async function fetchJson<T>(path: string, options: FetchJsonOptions = {}): Promise<T | null> {
  const url = `${apiBase}${path}`;
  try {
    const init = options.cache
      ? { cache: options.cache }
      : { next: { revalidate: options.revalidate ?? 120 } };
    const response = await fetch(url, init);
    if (!response.ok) {
      logSiteApiError({
        path,
        url,
        kind: 'http',
        status: response.status,
        message: `Site API responded with HTTP ${response.status}`,
      });
      return null;
    }

    let payload: ApiResponse<T> & { message?: string; userMessage?: string };
    try {
      payload = (await response.json()) as ApiResponse<T> & { message?: string; userMessage?: string };
    } catch (error) {
      logSiteApiError({
        path,
        url,
        kind: 'json',
        status: response.status,
        message: error instanceof Error ? error.message : 'Failed to parse Site API JSON response',
      });
      return null;
    }

    if (payload?.code && payload.code !== '0') {
      logSiteApiError({
        path,
        url,
        kind: 'http',
        status: response.status,
        code: payload.code,
        message: payload.userMessage || payload.message || `Site API returned code ${payload.code}`,
      });
      return null;
    }
    return payload?.data ?? null;
  } catch (error) {
    logSiteApiError({
      path,
      url,
      kind: 'network',
      message: error instanceof Error ? error.message : 'Site API request failed',
    });
    return null;
  }
}

function logSiteApiError(error: SiteApiErrorLog) {
  const level = error.kind === 'http' && error.status === 404 ? 'warn' : 'error';
  console[level]('[site-api]', error);
}

export async function getRuntime() {
  const runtime = await fetchJson<{ site: SiteSettings; navigation: NavigationItem[]; carousels?: SiteCarousel[] }>(
    '/v1/public/site/runtime',
    { cache: 'no-store' },
  );
  return runtime || { site: unavailableSite, navigation: [], carousels: [] };
}

export async function getPage(slug = '/') {
  const encoded = encodeURIComponent(slug);
  return fetchJson<PublicPage>(`/v1/public/site/pages?slug=${encoded}`);
}

export async function getContents() {
  const result = await fetchJson<PagedResult<ContentRecord>>('/v1/public/site/contents?pageNo=1&pageSize=6');
  return result?.records || [];
}

async function findContentBySlug(normalized: string) {
  const legacySlug = normalized.substring(1);
  const pageSize = 50;
  const maxPages = 10;
  for (let pageNo = 1; pageNo <= maxPages; pageNo += 1) {
    const result = await fetchJson<PagedResult<ContentRecord>>(`/v1/public/site/contents?pageNo=${pageNo}&pageSize=${pageSize}`);
    const matched = result?.records?.find((item) => item.slug === normalized || item.slug === legacySlug);
    if (matched || !result?.records?.length || result.records.length < pageSize) return matched || null;
  }
  return null;
}

export async function getContent(slug: string) {
  const normalized = slug.startsWith('/') ? slug : `/${slug}`;
  const content = await fetchJson<ContentRecord>(`/v1/public/site/contents/detail?slug=${encodeURIComponent(normalized)}`);
  if (content || normalized === '/') return content;
  const legacySlug = normalized.substring(1);
  const legacyContent = await fetchJson<ContentRecord>(`/v1/public/site/contents/detail?slug=${encodeURIComponent(legacySlug)}`);
  if (legacyContent) return legacyContent;
  return findContentBySlug(normalized);
}

export async function getForm(code: string) {
  return fetchJson<PublicForm>(`/v1/public/site/forms/${encodeURIComponent(code)}`);
}

export async function submitForm(code: string, data: Record<string, unknown>) {
  const response = await fetch(siteApiUrl(`/v1/public/site/forms/${encodeURIComponent(code)}/submissions`), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ dataJson: JSON.stringify(data) }),
  });
  const payload = (await response.json()) as ApiResponse<PublicSubmission> & { message?: string; userMessage?: string };
  if (!response.ok || payload.code !== '0') {
    throw new Error(payload.userMessage || payload.message || '提交失败，请稍后重试');
  }
  return payload.data;
}

export function parseBlocks(page: PublicPage | null): PageBlock[] {
  if (!page?.blocksJson) return [];
  try {
    const blocks = JSON.parse(page.blocksJson);
    return Array.isArray(blocks) ? blocks : [];
  } catch {
    return [];
  }
}
