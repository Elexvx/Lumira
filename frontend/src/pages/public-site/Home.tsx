import { Carousel, Spin } from 'antd';
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  LeftOutlined,
  LoginOutlined,
  ReadOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { CarouselRef } from 'antd/es/carousel';
import { publicSiteService, type PublicSiteRuntime, type SiteCarousel, type SiteContent } from '@/services/site';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './Home.css';

const isVisible = (item: SiteCarousel) => item.status === 'VISIBLE';
const formatDate = (value?: string) => {
  if (!value) return '更新';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '更新';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
};
const normalizeArticlePath = (slug?: string) => (slug ? (slug.startsWith('/') ? slug : `/${slug}`) : '');

const parseTags = (value?: string) => {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => (typeof item === 'string' ? item : typeof item?.label === 'string' ? item.label : typeof item?.name === 'string' ? item.name : ''))
        .map((item) => item.trim())
        .filter(Boolean);
    }
  } catch {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
};

const textFromBlock = (block: unknown) => {
  if (typeof block === 'string') return block;
  if (!block || typeof block !== 'object') return '';
  const record = block as Record<string, unknown>;
  return [record.text, record.content, record.body, record.title].find((item): item is string => typeof item === 'string' && item.trim().length > 0) || '';
};

const parseBodyBlocks = (content: SiteContent | null) => {
  if (!content) return [];
  if (content.bodyJson) {
    try {
      const parsed = JSON.parse(content.bodyJson);
      const blocks = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.blocks) ? parsed.blocks : [];
      if (blocks.length) return blocks as Record<string, unknown>[];
    } catch {
      // Fall back to plain text when saved structured content is invalid.
    }
  }
  const body = content.bodyText?.trim() || content.summary || '';
  return body
    .split(/\n{2,}/)
    .map((paragraph) => ({ type: 'paragraph', text: paragraph.trim() }))
    .filter((block) => block.text);
};

const estimateReadMinutes = (content: SiteContent | null) => {
  const text = parseBodyBlocks(content).map(textFromBlock).join('');
  return Math.max(1, Math.ceil(Math.max(text.length, content?.summary?.length || 0) / 480));
};

const renderArticleBodyBlock = (block: Record<string, unknown>, index: number) => {
  const type = typeof block.type === 'string' ? block.type : 'paragraph';
  const content = textFromBlock(block);
  const key = `${type}-${index}-${content.slice(0, 16)}`;

  if (['heading', 'h2', 'title'].includes(type)) return content ? <h2 key={key}>{content}</h2> : null;
  if (['subheading', 'h3'].includes(type)) return content ? <h3 key={key}>{content}</h3> : null;
  if (['quote', 'blockquote'].includes(type)) return content ? <blockquote key={key}>{content}</blockquote> : null;
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

  if (type === 'image') {
    const src = normalizeUploadUrl(
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

  return content ? <p key={key}>{content}</p> : null;
};

const OfficialSiteHome = () => {
  const [runtime, setRuntime] = useState<PublicSiteRuntime | null>(null);
  const [contents, setContents] = useState<SiteContent[]>([]);
  const [article, setArticle] = useState<SiteContent | null>(null);
  const [loading, setLoading] = useState(true);
  const [articleLoading, setArticleLoading] = useState(false);
  const carouselRef = useRef<CarouselRef>(null);
  const location = useLocation();
  const articleSlug = useMemo(() => new URLSearchParams(location.search).get('article') || '', [location.search]);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      publicSiteService.runtime(),
      publicSiteService.contents({ pageNo: 1, pageSize: 6 }),
    ])
      .then(([runtimeResult, contentResult]) => {
        if (mounted) {
          setRuntime(runtimeResult);
          setContents(contentResult?.records || []);
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!articleSlug) {
      setArticle(null);
      return;
    }
    let mounted = true;
    setArticleLoading(true);
    publicSiteService
      .content(articleSlug)
      .then((result) => {
        if (mounted) setArticle(result);
      })
      .finally(() => {
        if (mounted) setArticleLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [articleSlug]);

  const openArticle = (item: SiteContent) => {
    const slug = normalizeArticlePath(item.slug);
    if (!slug) return;
    history.push(`/official?article=${encodeURIComponent(slug)}`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const carousels = useMemo(() => (runtime?.carousels || []).filter(isVisible), [runtime?.carousels]);
  const siteName = runtime?.site?.name || '宏翔商道';
  const logoUrl = normalizeUploadUrl(runtime?.site?.logoUrl);
  const articleCoverUrl = normalizeUploadUrl(article?.coverUrl);
  const articleBodyBlocks = parseBodyBlocks(article);
  const relatedContents = contents.filter((item) => item.id !== article?.id).slice(0, 3);

  return (
    <main className={`official-site-home${articleSlug ? ' is-article' : ''}`}>
      <header className="official-site-header">
        <div className="official-site-header__brand">
          <div className="official-site-brand">
            {logoUrl ? <img src={logoUrl} alt={siteName} /> : null}
            <span>{siteName}</span>
          </div>
        </div>
        <nav className="official-site-nav">
          {(runtime?.navigation || []).slice(0, 5).map((item) => (
            <a key={item.id} href={item.linkTarget || '#'} target={item.openType === 'BLANK' ? '_blank' : undefined} rel="noreferrer">
              {item.title}
            </a>
          ))}
        </nav>
        <div className="official-site-header__actions">
          <button type="button" className="official-site-login-button" onClick={() => history.push('/user/login')}>
            <LoginOutlined />
            登录
          </button>
        </div>
      </header>

      {articleSlug ? (
        <section className="official-site-article">
          {articleLoading ? (
            <div className="official-site-loading official-site-article-loading">
              <Spin />
            </div>
          ) : article ? (
            <>
              <button type="button" className="official-site-back" onClick={() => history.push('/official')}>
                <ArrowLeftOutlined />
                返回官网首页
              </button>
              <div className="official-site-article-hero">
                <div className="official-site-article-kicker">
                  <ReadOutlined />
                  文章详情
                </div>
                <h1>{article.title}</h1>
                {article.summary ? <p>{article.summary}</p> : null}
                <div className="official-site-article-meta">
                  <span><CalendarOutlined />{formatDate(article.publishedAt || article.updatedAt)}</span>
                  <span><ClockCircleOutlined />约 {estimateReadMinutes(article)} 分钟阅读</span>
                </div>
                {parseTags(article.tagsJson).length ? (
                  <div className="official-site-article-tags">
                    {parseTags(article.tagsJson).map((tag) => (
                      <span key={tag}>{tag}</span>
                    ))}
                  </div>
                ) : null}
              </div>
              {articleCoverUrl ? <img className="official-site-article-cover" src={articleCoverUrl} alt={article.title} /> : null}
              <div className="official-site-article-layout">
                <aside>
                  <span>Published</span>
                  <strong>{formatDate(article.publishedAt || article.updatedAt)}</strong>
                </aside>
                <article className="official-site-article-body">
                  {articleBodyBlocks.map(renderArticleBodyBlock)}
                  {!articleBodyBlocks.length ? <p>正文内容尚未发布。</p> : null}
                </article>
              </div>
              {relatedContents.length ? (
                <section className="official-site-related">
                  <div className="official-site-section-heading">
                    <h2>继续阅读</h2>
                    <p>更多后台发布的内容会在这里延展。</p>
                  </div>
                  <div className="official-site-content-list">
                    {relatedContents.map((item) => (
                      <button type="button" key={item.id} onClick={() => openArticle(item)}>
                        <span>{formatDate(item.publishedAt || item.updatedAt)}</span>
                        <strong>{item.title}</strong>
                        {item.summary ? <p>{item.summary}</p> : null}
                        <em>查看详情 <ArrowRightOutlined /></em>
                      </button>
                    ))}
                  </div>
                </section>
              ) : null}
            </>
          ) : (
            <div className="official-site-empty official-site-article-empty">
              <p>文章未发布或不存在</p>
              <span>请返回官网首页查看已发布内容。</span>
              <button type="button" onClick={() => history.push('/official')}>返回官网首页</button>
            </div>
          )}
        </section>
      ) : (
        <>
      <section className="official-site-hero">
        {loading ? (
          <div className="official-site-loading">
            <Spin />
          </div>
        ) : carousels.length ? (
          <>
            <Carousel ref={carouselRef} autoplay dots={{ className: 'official-site-carousel-dots' }}>
              {carousels.map((item) => {
                const imageUrl = normalizeUploadUrl(item.imageUrl);
                return (
                  <div key={item.id}>
                    <article className="official-site-slide">
                      {imageUrl ? <img className="official-site-slide-image" src={imageUrl} alt={item.title} /> : null}
                      <div className="official-site-slide-shade" />
                    </article>
                  </div>
                );
              })}
            </Carousel>
            {carousels.length > 1 ? (
              <>
                <button
                  type="button"
                  className="official-site-carousel-arrow official-site-carousel-arrow-left"
                  aria-label="上一张轮播"
                  onClick={() => carouselRef.current?.prev()}
                >
                  <LeftOutlined />
                </button>
                <button
                  type="button"
                  className="official-site-carousel-arrow official-site-carousel-arrow-right"
                  aria-label="下一张轮播"
                  onClick={() => carouselRef.current?.next()}
                >
                  <RightOutlined />
                </button>
              </>
            ) : null}
          </>
        ) : (
          <div className="official-site-empty">
            <p>官网轮播待发布</p>
            <span>在后台“官网管理 / 轮播管理”新增并显示后，这里会自动展示。</span>
          </div>
        )}
      </section>

      <section className="official-site-content">
        <div className="official-site-section-heading">
          <h2>最新内容</h2>
          <p>后台发布的文章、公告和动态会进入官网列表，并可打开详情页阅读。</p>
        </div>
        {loading ? (
          <div className="official-site-loading official-site-content-loading">
            <Spin />
          </div>
        ) : contents.length ? (
          <div className="official-site-content-list">
            {contents.map((item) => (
              <button type="button" key={item.id} onClick={() => openArticle(item)}>
                <span>{formatDate(item.publishedAt || item.updatedAt)}</span>
                <strong>{item.title}</strong>
                {item.summary ? <p>{item.summary}</p> : null}
                <em>查看详情 <ArrowRightOutlined /></em>
              </button>
            ))}
          </div>
        ) : (
          <div className="official-site-empty official-site-content-empty">
            <p>暂无已发布内容</p>
            <span>在后台“官网管理 / 内容管理”发布后，这里会自动展示。</span>
          </div>
        )}
      </section>
        </>
      )}
    </main>
  );
};

export default OfficialSiteHome;
