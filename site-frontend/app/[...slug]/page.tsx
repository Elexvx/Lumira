import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { SiteFooter, SiteHeader } from '@/components/SiteChrome';
import { getContents, getPage, getRuntime, parseBlocks } from '@/lib/api';
import { renderBlock } from '@/lib/blocks';
import { metadataForPage } from '@/lib/seo';

interface SlugPageProps {
  params: Promise<{ slug: string[] }>;
}

async function resolveSlug(params: SlugPageProps['params']) {
  const resolved = await params;
  return `/${resolved.slug.join('/')}`;
}

export async function generateMetadata({ params }: SlugPageProps): Promise<Metadata> {
  const slug = await resolveSlug(params);
  return metadataForPage(await getPage(slug));
}

export default async function SlugPage({ params }: SlugPageProps) {
  const slug = await resolveSlug(params);
  const [runtime, page, contents] = await Promise.all([getRuntime(), getPage(slug), getContents()]);
  if (!page) notFound();
  const blocks = parseBlocks(page);

  return (
    <main>
      <SiteHeader site={runtime.site} navigation={runtime.navigation} />
      {blocks.map((block) => renderBlock(block, contents))}
      <SiteFooter site={runtime.site} />
    </main>
  );
}
