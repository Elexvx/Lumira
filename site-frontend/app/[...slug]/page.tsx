import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { ArticleView } from '@/components/ArticleView';
import { SiteFooter, SiteHeader } from '@/components/SiteChrome';
import { getContent, getContents, getPage, getRuntime, parseBlocks } from '@/lib/api';
import { renderBlock } from '@/lib/blocks';
import { metadataForContent, metadataForPage } from '@/lib/seo';

interface SlugPageProps {
  params: Promise<{ slug: string[] }>;
}

async function resolveSlug(params: SlugPageProps['params']) {
  const resolved = await params;
  return `/${resolved.slug.join('/')}`;
}

export async function generateMetadata({ params }: SlugPageProps): Promise<Metadata> {
  const slug = await resolveSlug(params);
  const page = await getPage(slug);
  if (page) return metadataForPage(page);
  const [runtime, content] = await Promise.all([getRuntime(), getContent(slug)]);
  return metadataForContent(content, runtime.site);
}

export default async function SlugPage({ params }: SlugPageProps) {
  const slug = await resolveSlug(params);
  const [runtime, page, content, contents] = await Promise.all([getRuntime(), getPage(slug), getContent(slug), getContents()]);
  if (!page && !content) notFound();
  const blocks = parseBlocks(page);

  return (
    <main>
      <SiteHeader site={runtime.site} navigation={runtime.navigation} />
      {page ? blocks.map((block) => renderBlock(block, contents)) : <ArticleView content={content!} />}
      <SiteFooter site={runtime.site} />
    </main>
  );
}
