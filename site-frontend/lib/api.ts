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
  return result?.records?.length ? result.records : defaultContents;
}

export async function getContent(slug: string) {
  const normalized = slug.startsWith('/') ? slug : `/${slug}`;
  const remote = await fetchJson<ContentRecord>(`/v1/public/site/contents/${encodeURIComponent(normalized)}`);
  return remote || defaultContents.find((item) => item.slug === normalized) || null;
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

export const defaultContents: ContentRecord[] = [
  {
    id: 1,
    title: '官网内容即将接入 CMS',
    slug: '/news/cms',
    summary: '后台发布后，官网自动读取已发布快照。',
    bodyText:
      '官网已经与统一后台的内容模型对齐。运营人员可以在管理端维护文章、公告与页面内容，发布后由公开站点读取已发布快照。\n\n第一阶段会保持内容结构简洁，优先覆盖品牌展示、资讯发布和申请入口，后续再逐步扩展为更细的专题页和活动页。',
    publishedAt: '2026-05-14T00:00:00+08:00',
  },
  {
    id: 2,
    title: '页面区块模型已准备',
    slug: '/news/pages',
    summary: '用更少的固定模板承载更多业务形态。',
    bodyText:
      '页面以区块方式组织，而不是写死成单一模板。这样既能保持官网视觉统一，也能让不同页面根据业务需要组合展示模块。\n\n目前已经准备了首页、能力展示、内容列表和联系入口等基础区块，适合先上线一个简洁大气的公开站点。',
    publishedAt: '2026-05-14T00:00:00+08:00',
  },
];
