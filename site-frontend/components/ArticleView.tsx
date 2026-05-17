import { ArrowLeft, CalendarDays, Clock3 } from 'lucide-react';
import { publicAssetUrl, type ContentRecord } from '@/lib/api';

function formatDate(value?: string) {
  if (!value) return '更新';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '更新';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
}

function textFromBlock(block: unknown) {
  if (typeof block === 'string') return block;
  if (!block || typeof block !== 'object') return '';
  const record = block as Record<string, unknown>;
  const candidates = [record.text, record.content, record.body, record.title];
  return candidates.find((item): item is string => typeof item === 'string' && item.trim().length > 0) || '';
}

function parseBodyBlocks(content: ContentRecord) {
  if (content.bodyJson) {
    try {
      const parsed = JSON.parse(content.bodyJson);
      const blocks = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.blocks) ? parsed.blocks : [];
      if (blocks.length) return blocks as Record<string, unknown>[];
    } catch {
      // Fall back to plain text below when the stored structured body cannot be parsed.
    }
  }

  const body = content.bodyText?.trim() || content.summary || '';
  return body
    .split(/\n{2,}/)
    .map((paragraph) => ({ type: 'paragraph', text: paragraph.trim() }))
    .filter((block) => block.text);
}

function estimateReadMinutes(content: ContentRecord) {
  const bodyText = parseBodyBlocks(content).map(textFromBlock).join('');
  const textLength = Math.max(bodyText.length, content.summary?.length || 0);
  return Math.max(1, Math.ceil(textLength / 480));
}

function renderBodyBlock(block: Record<string, unknown>, index: number) {
  const type = typeof block.type === 'string' ? block.type : 'paragraph';
  const content = textFromBlock(block);
  const key = `${type}-${index}-${content.slice(0, 16)}`;

  if (['heading', 'h2', 'title'].includes(type)) {
    return content ? <h2 key={key}>{content}</h2> : null;
  }

  if (['subheading', 'h3'].includes(type)) {
    return content ? <h3 key={key}>{content}</h3> : null;
  }

  if (['quote', 'blockquote'].includes(type)) {
    return content ? <blockquote key={key}>{content}</blockquote> : null;
  }

  if (type === 'image') {
    const src = publicAssetUrl(
      typeof block.url === 'string'
        ? block.url
        : typeof block.src === 'string'
          ? block.src
          : typeof block.imageUrl === 'string'
            ? block.imageUrl
            : '',
    );
    const caption = typeof block.caption === 'string' ? block.caption : '';
    return src ? (
      <figure key={key}>
        <img src={src} alt={caption || ''} />
        {caption ? <figcaption>{caption}</figcaption> : null}
      </figure>
    ) : null;
  }

  if (['list', 'unorderedList', 'orderedList'].includes(type)) {
    const items = Array.isArray(block.items) ? block.items.map(textFromBlock).filter(Boolean) : [];
    if (!items.length) return null;
    const ListTag = type === 'orderedList' ? 'ol' : 'ul';
    return (
      <ListTag key={key}>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ListTag>
    );
  }

  return content ? <p key={key}>{content}</p> : null;
}

export function ArticleView({ content }: { content: ContentRecord; relatedContents?: ContentRecord[] }) {
  const bodyBlocks = parseBodyBlocks(content);
  const coverUrl = publicAssetUrl(content.coverUrl);
  const readMinutes = estimateReadMinutes(content);

  return (
    <article className="article-page">
      <section className="article-hero">
        <a className="back-link" href="/#insights">
          <ArrowLeft size={18} />
          返回内容列表
        </a>
        <h1>{content.title}</h1>
        {content.summary ? <p>{content.summary}</p> : null}
        <div className="article-meta">
          <span><CalendarDays size={16} />{formatDate(content.publishedAt || content.updatedAt)}</span>
          <span><Clock3 size={16} />约 {readMinutes} 分钟阅读</span>
        </div>
      </section>

      {coverUrl ? (
        <figure className="article-cover">
          <img src={coverUrl} alt={content.title} />
        </figure>
      ) : null}

      <div className="article-body">
        {bodyBlocks.map(renderBodyBlock)}
        {!bodyBlocks.length ? <p>正文内容尚未发布。</p> : null}
      </div>
    </article>
  );
}
