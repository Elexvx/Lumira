import type { MetadataRoute } from 'next';
import { getContents } from '@/lib/api';
import { siteUrl } from '@/lib/seo';

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const origin = siteUrl();
  const contents = await getContents();
  const now = new Date();

  return [
    {
      url: origin,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 1,
    },
    ...contents.map((item) => ({
      url: `${origin}${item.slug.startsWith('/') ? item.slug : `/${item.slug}`}`,
      lastModified: item.publishedAt ? new Date(item.publishedAt) : now,
      changeFrequency: 'monthly' as const,
      priority: 0.7,
    })),
  ];
}
