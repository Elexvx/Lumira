import { notFound } from 'next/navigation';
import { getContents, getPage, getRuntime, parseBlocks } from '@/lib/api';
import { renderBlock } from '@/lib/blocks';

export default async function SlugPage({ params }: { params: Promise<{ slug: string[] }> }) {
  const resolved = await params;
  const slug = `/${resolved.slug.join('/')}`;
  const [runtime, page, contents] = await Promise.all([getRuntime(), getPage(slug), getContents()]);
  if (!page) notFound();
  const blocks = parseBlocks(page);

  return (
    <main>
      <header className="site-header">
        <a className="brand" href="/">
          <span className="brand-mark" />
          {runtime.site.name || 'Legendary Invention'}
        </a>
        <nav>
          {runtime.navigation.map((item) => (
            <a key={item.id} href={item.linkTarget}>{item.title}</a>
          ))}
        </nav>
      </header>
      {blocks.map((block) => renderBlock(block, contents))}
      <footer>
        <span>{runtime.site.name || 'Legendary Invention'}</span>
        <span>Independent public site powered by CMS.</span>
      </footer>
    </main>
  );
}
