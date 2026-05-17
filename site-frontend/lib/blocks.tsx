import { ArrowRight, FileText, Layers3, ShieldCheck } from 'lucide-react';
import { PublicForm } from '@/components/PublicForm';
import { publicAssetUrl, type ContentRecord, type PageBlock } from './api';

const text = (value: unknown, fallback: string) => (typeof value === 'string' && value.trim() ? value : fallback);
const optionalText = (value: unknown) => (typeof value === 'string' && value.trim() ? value : '');
const arrayValue = (value: unknown) => (Array.isArray(value) ? value : []);

export function renderBlock(block: PageBlock, contents: ContentRecord[]) {
  if (block.type === 'hero') {
    return (
      <section className="hero" key={block.id}>
        <div className="hero-copy">
          <h1>{text(block.props.title, '简洁大气的品牌官网')}</h1>
          <p>{text(block.props.subtitle, '用统一后台管理官网内容、页面与提交入口。')}</p>
          <div className="hero-actions">
            <a className="primary-link" href="#contact">开始咨询 <ArrowRight size={18} /></a>
            <a className="secondary-link" href="#insights">查看动态</a>
          </div>
        </div>
        <div className="hero-panel">
          <div className="panel-line" />
          <div className="panel-title">Site Control</div>
          <div className="panel-row"><span>页面发布</span><strong>Ready</strong></div>
          <div className="panel-row"><span>内容管理</span><strong>CMS</strong></div>
          <div className="panel-row"><span>表单提交</span><strong>Open</strong></div>
        </div>
      </section>
    );
  }

  if (block.type === 'capabilities') {
    return (
      <section className="section" id="capabilities" key={block.id}>
        <div className="section-heading">
          <h2>为官网运营保留足够自由度</h2>
          <p>从展示、内容到申请收集，都由后台统一管理。</p>
        </div>
        <div className="capability-list">
          <article><Layers3 /><h3>区块式页面</h3><p>页面由可发布区块组成，适合首页、专题页和自定义页面。</p></article>
          <article><FileText /><h3>内容资讯</h3><p>文章、公告和动态内容独立维护，便于持续运营。</p></article>
          <article><ShieldCheck /><h3>可控提交</h3><p>公开或登录后提交都可配置，数据沉淀到管理后台。</p></article>
        </div>
      </section>
    );
  }

  if (block.type === 'contentList') {
    return <Insights key={block.id} contents={contents} />;
  }

  if (block.type === 'richText') {
    const body = text(block.props.body || block.props.content, '');
    return (
      <section className="section rich-text-section" key={block.id}>
        {optionalText(block.props.title) ? <h2>{optionalText(block.props.title)}</h2> : null}
        <div className="rich-text-body">
          {body
            .split(/\n{2,}/)
            .map((paragraph) => paragraph.trim())
            .filter(Boolean)
            .map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
        </div>
      </section>
    );
  }

  if (block.type === 'imageText') {
    const imageUrl = publicAssetUrl(optionalText(block.props.imageUrl));
    return (
      <section className="section image-text-section" key={block.id}>
        <div>
          <h2>{text(block.props.title, '图文内容')}</h2>
          <p>{text(block.props.description || block.props.subtitle, '这里展示后台配置的图文内容。')}</p>
        </div>
        {imageUrl ? <img src={imageUrl} alt={optionalText(block.props.title)} /> : null}
      </section>
    );
  }

  if (block.type === 'cta') {
    const href = optionalText(block.props.href || block.props.linkTarget) || '#contact';
    return (
      <section className="contact" id={optionalText(block.props.id) || undefined} key={block.id}>
        <div>
          <h2>{text(block.props.title, '准备开始？')}</h2>
          <p>{text(block.props.description || block.props.subtitle, '通过后台配置行动入口，让官网和业务流程连接起来。')}</p>
        </div>
        <a className="primary-link dark" href={href}>{text(block.props.buttonText, '立即了解')} <ArrowRight size={18} /></a>
      </section>
    );
  }

  if (block.type === 'form') {
    return (
      <section className="section form-section" id={optionalText(block.props.id) || 'form'} key={block.id}>
        <div className="section-heading">
          <h2>{text(block.props.title, '在线提交')}</h2>
          <p>{text(block.props.description || block.props.subtitle, '提交内容会进入后台官网管理的提交记录。')}</p>
        </div>
        <PublicForm code={optionalText(block.props.code) || 'contact'} title={optionalText(block.props.formTitle)} />
      </section>
    );
  }

  if (block.type === 'downloadList') {
    const items = arrayValue(block.props.items);
    return (
      <section className="section" key={block.id}>
        <div className="section-heading">
          <h2>{text(block.props.title, '资料下载')}</h2>
          <p>{text(block.props.description, '后台配置的公开资料会显示在这里。')}</p>
        </div>
        <div className="download-list">
          {items.map((item, index) => {
            const record = item && typeof item === 'object' ? item as Record<string, unknown> : {};
            const href = publicAssetUrl(optionalText(record.url || record.href));
            return (
              <a href={href || '#'} key={`${href}-${index}`}>
                <strong>{text(record.title, `资料 ${index + 1}`)}</strong>
                <span>{text(record.description, '查看或下载')}</span>
              </a>
            );
          })}
        </div>
      </section>
    );
  }

  if (block.type === 'contact') {
    return (
      <section className="contact" id="contact" key={block.id}>
        <div>
          <h2>让官网成为系统的一部分</h2>
          <p>独立呈现，统一管理。适合企业展示、信息发布、申请入口和长期内容运营。</p>
        </div>
        <a className="primary-link dark" href="mailto:hello@example.com">联系负责人 <ArrowRight size={18} /></a>
      </section>
    );
  }

  return null;
}

export function Insights({ contents }: { contents: ContentRecord[] }) {
  return (
    <section className="section" id="insights">
      <div className="section-heading">
        <h2>最新内容</h2>
        <p>以清晰列表承载资讯、公告和申请说明。</p>
      </div>
      {contents.length ? (
        <div className="insight-list">
          {contents.map((item) => (
            <a href={item.slug} key={item.id}>
              <span>{item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('zh-CN') : '更新'}</span>
              <strong>{item.title}</strong>
              <p>{item.summary}</p>
            </a>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <strong>暂无已发布内容</strong>
          <p>这里会显示后台发布后的文章、公告和动态。</p>
        </div>
      )}
    </section>
  );
}
