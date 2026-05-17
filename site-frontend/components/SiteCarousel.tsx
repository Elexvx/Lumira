'use client';

import { useEffect, useMemo, useState } from 'react';
import { publicAssetUrl, type SiteCarousel } from '@/lib/api';

function targetFor(item: SiteCarousel) {
  if (!item.linkTarget || item.linkType === 'NONE') return null;
  return item.linkTarget;
}

export function SiteCarouselHero({ items }: { items: SiteCarousel[] }) {
  const visibleItems = useMemo(
    () => items.filter((item) => item.status === 'VISIBLE' && item.imageUrl),
    [items],
  );
  const [active, setActive] = useState(0);

  useEffect(() => {
    if (visibleItems.length <= 1) return undefined;
    const timer = window.setInterval(() => {
      setActive((current) => (current + 1) % visibleItems.length);
    }, 5200);
    return () => window.clearInterval(timer);
  }, [visibleItems.length]);

  if (!visibleItems.length) {
    return null;
  }

  const activeItem = visibleItems[Math.min(active, visibleItems.length - 1)];
  const href = targetFor(activeItem);

  return (
    <section className="site-carousel-hero" aria-label="官网轮播">
      {visibleItems.map((item, index) => (
        <article className={`site-carousel-slide ${index === active ? 'is-active' : ''}`} key={item.id}>
          <img src={publicAssetUrl(item.imageUrl)} alt={item.title || '官网轮播'} />
        </article>
      ))}
      <div className="site-carousel-shade" />
      <div className="site-carousel-copy">
        <span>Official Site</span>
        <h1>{activeItem.title || '欢迎访问'}</h1>
        {activeItem.subtitle ? <p>{activeItem.subtitle}</p> : null}
        {href ? (
          <a className="primary-link dark" href={href} target={activeItem.openType === 'BLANK' ? '_blank' : undefined} rel="noreferrer">
            了解更多
          </a>
        ) : null}
      </div>
      {visibleItems.length > 1 ? (
        <div className="site-carousel-controls" aria-label="轮播切换">
          {visibleItems.map((item, index) => (
            <button
              type="button"
              aria-label={`切换到第 ${index + 1} 张轮播`}
              aria-pressed={index === active}
              className={index === active ? 'is-active' : ''}
              key={item.id}
              onClick={() => setActive(index)}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}
