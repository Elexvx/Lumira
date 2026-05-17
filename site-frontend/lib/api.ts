export interface ApiResponse<T> {
  code: string;
  data: T;
}

export interface SiteSettings {
  name?: string;
  logoUrl?: string;
  faviconUrl?: string;
  seoJson?: string;
}

export interface NavigationItem {
  id: number;
  title: string;
  linkType: string;
  linkTarget: string;
  openType: string;
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
    title?: string;
    slug?: string;
    seoJson?: string;
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
}

export interface PagedResult<T> {
  records: T[];
  total: number;
}

const apiBase = process.env.SITE_API_BASE_URL || process.env.NEXT_PUBLIC_SITE_API_BASE_URL || 'http://localhost:8080/api';

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

export async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${apiBase}${path}`, { next: { revalidate: 120 } });
    if (!response.ok) return null;
    const payload = (await response.json()) as ApiResponse<T>;
    return payload?.data ?? null;
  } catch {
    return null;
  }
}

export async function getRuntime() {
  const runtime = await fetchJson<{ site: SiteSettings; navigation: NavigationItem[]; carousels?: SiteCarousel[] }>('/v1/public/site/runtime');
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

export async function getContent(slug: string) {
  const normalized = slug.startsWith('/') ? slug : `/${slug}`;
  return fetchJson<ContentRecord>(`/v1/public/site/contents/${encodeURIComponent(normalized)}`);
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
