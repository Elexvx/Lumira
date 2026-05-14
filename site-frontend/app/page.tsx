import { getContents, getPage, getRuntime, parseBlocks } from '@/lib/api';
import { Insights, renderBlock } from '@/lib/blocks';

export default async function HomePage() {
  const [runtime, page, contents] = await Promise.all([getRuntime(), getPage('/'), getContents()]);
  const blocks = parseBlocks(page);
  const hasContentList = blocks.some((block) => block.type === 'contentList');

  return (
    <main>
      <header className="site-header">
        <a className="brand" href="/">
          <span className="brand-mark" />
          {runtime.site.name || 'Legendary Invention'}
        </a>
        <nav>
          {runtime.navigation.map((item) => (
            <a key={item.id} href={item.linkTarget} target={item.openType === 'BLANK' ? '_blank' : undefined}>
              {item.title}
            </a>
          ))}
        </nav>
      </header>
      {blocks.map((block) => renderBlock(block, contents))}
      {!hasContentList && <Insights contents={contents} />}
      <footer>
        <span>{runtime.site.name || 'Legendary Invention'}</span>
        <span>Independent public site powered by CMS.</span>
      </footer>
    </main>
  );
}
