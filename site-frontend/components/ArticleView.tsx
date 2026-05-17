import { ArrowLeft } from 'lucide-react';
import { publicAssetUrl, type ContentRecord } from '@/lib/api';

function formatDate(value?: string) {
  if (!value) return '更新';
  return new Date(value).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
}

function plainTextBody(content: ContentRecord) {
  if (content.bodyText?.trim()) return content.bodyText;
  if (!content.bodyJson) return content.summary || '';

  try {
    const parsed = JSON.parse(content.bodyJson);
    if (Array.isArray(parsed)) {
      return parsed
        .map((block) => (typeof block?.text === 'string' ? block.text : typeof block?.content === 'string' ? block.content : ''))
        .filter(Boolean)
        .join('\n\n');
    }
  } catch {
    return content.summary || '';
  }

  return content.summary || '';
}

export function ArticleView({ content }: { content: ContentRecord }) {
  const body = plainTextBody(content);
  const coverUrl = publicAssetUrl(content.coverUrl);

  return (
    <article className="article-shell">
      <a className="back-link" href="/#insights">
        <ArrowLeft size={18} />
        返回内容列表
      </a>
      <header className="article-header">
        <span>{formatDate(content.publishedAt)}</span>
        <h1>{content.title}</h1>
        {content.summary ? <p>{content.summary}</p> : null}
      </header>
      {coverUrl ? <img className="article-cover" src={coverUrl} alt="" /> : null}
      <div className="article-body">
        {body
          .split(/\n{2,}/)
          .map((paragraph) => paragraph.trim())
          .filter(Boolean)
          .map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
      </div>
    </article>
  );
}
