import { Carousel, Spin } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { publicSiteService, type PublicSiteRuntime, type SiteCarousel } from '@/services/site';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './Home.css';

const isVisible = (item: SiteCarousel) => item.status === 'VISIBLE';

const navigateTo = (item: SiteCarousel) => {
  if (!item.linkTarget || item.linkType === 'NONE') {
    return;
  }
  if (item.openType === 'BLANK' || item.linkType === 'EXTERNAL') {
    window.open(item.linkTarget, '_blank', 'noopener,noreferrer');
    return;
  }
  window.location.href = item.linkTarget;
};

const OfficialSiteHome = () => {
  const [runtime, setRuntime] = useState<PublicSiteRuntime | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    publicSiteService
      .runtime()
      .then((result) => {
        if (mounted) {
          setRuntime(result);
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

  const carousels = useMemo(() => (runtime?.carousels || []).filter(isVisible), [runtime?.carousels]);
  const siteName = runtime?.site?.name || '宏翔商道';
  const logoUrl = normalizeUploadUrl(runtime?.site?.logoUrl);

  return (
    <main className="official-site-home">
      <header className="official-site-header">
        <div className="official-site-brand">
          {logoUrl ? <img src={logoUrl} alt={siteName} /> : null}
          <span>{siteName}</span>
        </div>
        <nav className="official-site-nav">
          {(runtime?.navigation || []).slice(0, 5).map((item) => (
            <a key={item.id} href={item.linkTarget || '#'} target={item.openType === 'BLANK' ? '_blank' : undefined} rel="noreferrer">
              {item.title}
            </a>
          ))}
        </nav>
      </header>

      <section className="official-site-hero">
        {loading ? (
          <div className="official-site-loading">
            <Spin />
          </div>
        ) : carousels.length ? (
          <Carousel autoplay dots={{ className: 'official-site-carousel-dots' }}>
            {carousels.map((item) => {
              const imageUrl = normalizeUploadUrl(item.imageUrl);
              return (
                <div key={item.id}>
                  <article className="official-site-slide">
                    {imageUrl ? <img className="official-site-slide-image" src={imageUrl} alt={item.title} /> : null}
                    <div className="official-site-slide-shade" />
                    <div className="official-site-slide-content">
                      <p className="official-site-slide-kicker">ELEXVX OFFICIAL</p>
                      <h1>{item.title}</h1>
                      {item.subtitle ? <p className="official-site-slide-subtitle">{item.subtitle}</p> : null}
                      {item.linkType !== 'NONE' && item.linkTarget ? (
                        <button type="button" className="official-site-slide-action" onClick={() => navigateTo(item)}>
                          了解更多
                          <ArrowRightOutlined />
                        </button>
                      ) : null}
                    </div>
                  </article>
                </div>
              );
            })}
          </Carousel>
        ) : (
          <div className="official-site-empty">
            <p>官网轮播待发布</p>
            <span>在后台“官网管理 / 轮播管理”新增并显示后，这里会自动展示。</span>
          </div>
        )}
      </section>
    </main>
  );
};

export default OfficialSiteHome;
