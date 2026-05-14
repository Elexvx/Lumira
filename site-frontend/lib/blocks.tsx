import { ArrowRight, FileText, Layers3, ShieldCheck } from 'lucide-react';
import type { ContentRecord, PageBlock } from './api';

const text = (value: unknown, fallback: string) => (typeof value === 'string' && value.trim() ? value : fallback);

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
