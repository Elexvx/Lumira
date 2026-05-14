import type { Metadata } from 'next';
import { SiteFooter, SiteHeader } from '@/components/SiteChrome';
import { getContents, getPage, getRuntime, parseBlocks } from '@/lib/api';
import { Insights, renderBlock } from '@/lib/blocks';
import { metadataForPage } from '@/lib/seo';

export async function generateMetadata(): Promise<Metadata> {
  return metadataForPage(await getPage('/'));
}

export default async function HomePage() {
  const [runtime, page, contents] = await Promise.all([getRuntime(), getPage('/'), getContents()]);
  const blocks = parseBlocks(page);
  const hasContentList = blocks.some((block) => block.type === 'contentList');

  return (
    <main>
      <SiteHeader site={runtime.site} navigation={runtime.navigation} />
      {blocks.map((block) => renderBlock(block, contents))}
      {!hasContentList && <Insights contents={contents} />}
      <SiteFooter site={runtime.site} />
    </main>
  );
}
