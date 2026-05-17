'use client';

import { useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { publicAssetUrl, type SiteCarousel } from '@/lib/api';

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

  useEffect(() => {
    setActive((current) => (visibleItems.length ? Math.min(current, visibleItems.length - 1) : 0));
  }, [visibleItems.length]);

  if (!visibleItems.length) {
    return null;
  }

  const hasMultipleItems = visibleItems.length > 1;
  const goToPrevious = () => {
    setActive((current) => (current - 1 + visibleItems.length) % visibleItems.length);
  };
  const goToNext = () => {
    setActive((current) => (current + 1) % visibleItems.length);
  };

  return (
    <section className="site-carousel-hero" aria-label="官网轮播">
      {visibleItems.map((item, index) => (
        <article className={`site-carousel-slide ${index === active ? 'is-active' : ''}`} key={item.id}>
          <img src={publicAssetUrl(item.imageUrl)} alt={item.title || '官网轮播'} />
        </article>
      ))}
      {hasMultipleItems ? (
        <>
          <button
            type="button"
            className="site-carousel-arrow site-carousel-arrow-left"
            aria-label="上一张轮播"
            onClick={goToPrevious}
          >
            <ChevronLeft aria-hidden="true" size={30} strokeWidth={2.4} />
          </button>
          <button
            type="button"
            className="site-carousel-arrow site-carousel-arrow-right"
            aria-label="下一张轮播"
            onClick={goToNext}
          >
            <ChevronRight aria-hidden="true" size={30} strokeWidth={2.4} />
          </button>
        </>
      ) : null}
      {hasMultipleItems ? (
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
