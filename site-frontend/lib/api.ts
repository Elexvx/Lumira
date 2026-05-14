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
  publishedAt?: string;
}

export interface PagedResult<T> {
  records: T[];
  total: number;
}

const apiBase = process.env.SITE_API_BASE_URL || process.env.NEXT_PUBLIC_SITE_API_BASE_URL || 'http://localhost:8080/api';

const fallbackSite: SiteSettings = { name: 'Legendary Invention' };

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
  const runtime = await fetchJson<{ site: SiteSettings; navigation: NavigationItem[] }>('/v1/public/site/runtime');
  return runtime || { site: fallbackSite, navigation: defaultNavigation };
}

export async function getPage(slug = '/') {
  const encoded = encodeURIComponent(slug);
  return fetchJson<PublicPage>(`/v1/public/site/pages?slug=${encoded}`);
}

export async function getContents() {
  const result = await fetchJson<PagedResult<ContentRecord>>('/v1/public/site/contents?pageNo=1&pageSize=6');
  return result?.records || [];
}

export function parseBlocks(page: PublicPage | null): PageBlock[] {
  if (!page?.blocksJson) return defaultBlocks;
  try {
    const blocks = JSON.parse(page.blocksJson);
    return Array.isArray(blocks) ? blocks : defaultBlocks;
  } catch {
    return defaultBlocks;
  }
}

export const defaultNavigation: NavigationItem[] = [
  { id: 1, title: '首页', linkType: 'PAGE', linkTarget: '/', openType: 'SELF' },
  { id: 2, title: '能力', linkType: 'PAGE', linkTarget: '#capabilities', openType: 'SELF' },
  { id: 3, title: '内容', linkType: 'PAGE', linkTarget: '#insights', openType: 'SELF' },
  { id: 4, title: '联系', linkType: 'PAGE', linkTarget: '#contact', openType: 'SELF' },
];

export const defaultBlocks: PageBlock[] = [
  {
    id: 'hero',
    type: 'hero',
    props: {
      title: '面向长期运营的数字化平台',
      subtitle: '以清晰、稳定、可配置的方式承接品牌展示、内容发布与在线申请。',
    },
  },
  {
    id: 'capabilities',
    type: 'capabilities',
    props: {},
  },
  {
    id: 'contact',
    type: 'contact',
    props: {},
  },
];
