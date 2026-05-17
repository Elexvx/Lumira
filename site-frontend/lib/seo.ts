import type { Metadata } from 'next';
import type { ContentRecord, PublicPage, SiteSettings } from './api';
import { publicAssetUrl } from './api';

interface SeoConfig {
  title?: string;
  description?: string;
  keywords?: string[] | string;
  image?: string;
  canonical?: string;
}

const defaultDescription = '简洁大气的独立官网前端，由统一后台管理页面、内容与提交入口。';

export function siteUrl() {
  const raw =
    process.env.SITE_PUBLIC_URL ||
    process.env.NEXT_PUBLIC_SITE_PUBLIC_URL ||
    process.env.VERCEL_PROJECT_PRODUCTION_URL ||
    process.env.VERCEL_URL ||
    'https://legendary-invention-xrrq.vercel.app';
  const withProtocol = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
  return withProtocol.replace(/\/$/, '');
}

export function parseSeoJson(value?: string): SeoConfig {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function canonicalUrl(path: string) {
  if (path.startsWith('http')) return path;
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `${siteUrl()}${normalized === '/' ? '' : normalized}`;
}

export function metadataForPage(page: PublicPage | null, fallbackTitle = 'Legendary Invention'): Metadata {
  const site = page?.site || {};
  const siteSeo = parseSeoJson(site.seoJson);
  const pageSeo = parseSeoJson(page?.page?.seoJson);
  const title = pageSeo.title || page?.page?.title || siteSeo.title || site.name || fallbackTitle;
  const description = pageSeo.description || siteSeo.description || defaultDescription;
  const canonicalPath = pageSeo.canonical || page?.page?.slug || '/';
  const canonical = canonicalUrl(canonicalPath);
  const image = publicAssetUrl(pageSeo.image || siteSeo.image);

  return {
    title,
    description,
    keywords: pageSeo.keywords || siteSeo.keywords,
    alternates: { canonical },
    openGraph: {
      title,
      description,
      url: canonical,
      siteName: site.name || fallbackTitle,
      images: image ? [{ url: image }] : undefined,
      type: 'website',
      locale: 'zh_CN',
    },
    twitter: {
      card: image ? 'summary_large_image' : 'summary',
      title,
      description,
      images: image ? [image] : undefined,
    },
  };
}

export function metadataForContent(content: ContentRecord | null, site?: SiteSettings, fallbackTitle = 'Legendary Invention'): Metadata {
  const siteSeo = parseSeoJson(site?.seoJson);
  const contentSeo = parseSeoJson(content?.seoJson);
  const title = contentSeo.title || content?.title || siteSeo.title || site?.name || fallbackTitle;
  const description = contentSeo.description || content?.summary || siteSeo.description || defaultDescription;
  const canonicalPath = contentSeo.canonical || content?.slug || '/';
  const canonical = canonicalUrl(canonicalPath);
  const image = publicAssetUrl(contentSeo.image || content?.coverUrl || siteSeo.image);

  return {
    title,
    description,
    keywords: contentSeo.keywords || siteSeo.keywords,
    alternates: { canonical },
    openGraph: {
      title,
      description,
      url: canonical,
      siteName: site?.name || fallbackTitle,
      images: image ? [{ url: image }] : undefined,
      type: 'article',
      locale: 'zh_CN',
      publishedTime: content?.publishedAt,
    },
    twitter: {
      card: image ? 'summary_large_image' : 'summary',
      title,
      description,
      images: image ? [image] : undefined,
    },
  };
}

export function faviconFromSite(site?: SiteSettings) {
  const faviconUrl = publicAssetUrl(site?.faviconUrl);
  return faviconUrl ? [{ rel: 'icon', url: faviconUrl }] : undefined;
}
