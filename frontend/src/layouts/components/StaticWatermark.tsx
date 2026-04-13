import { memo, type CSSProperties, type ReactNode } from 'react';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { WatermarkSettings } from '@/types/api';

interface StaticWatermarkProps {
  settings: WatermarkSettings;
  children: ReactNode;
}

const DEFAULT_GAP_X = 100;
const DEFAULT_GAP_Y = 100;

const escapeXml = (value: string) =>
  value.replace(/[<>&"']/g, (character) => {
    switch (character) {
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '&':
        return '&amp;';
      case '"':
        return '&quot;';
      case "'":
        return '&apos;';
      default:
        return character;
    }
  });

const toDataUrl = (svg: string) => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;

const buildWatermarkImage = (settings: WatermarkSettings) => {
  const gapX = Number.isFinite(settings.gapX) && settings.gapX > 0 ? settings.gapX : DEFAULT_GAP_X;
  const gapY = Number.isFinite(settings.gapY) && settings.gapY > 0 ? settings.gapY : DEFAULT_GAP_Y;
  const rotate = Number.isFinite(settings.rotate) ? settings.rotate : -22;
  const fontSize = Number.isFinite(settings.fontSize) && settings.fontSize > 0 ? settings.fontSize : 14;
  const width = Math.max(1, Math.round(gapX));
  const height = Math.max(1, Math.round(gapY));
  const centerX = width / 2;
  const centerY = height / 2;
  const imageUrl = settings.mode === 'IMAGE' ? normalizeUploadUrl(settings.imageUrl) : '';

  if (settings.mode === 'IMAGE' && imageUrl) {
    const imageWidth = Math.max(24, Math.round(width * 0.72));
    const imageHeight = Math.max(24, Math.round(height * 0.72));
    const x = (width - imageWidth) / 2;
    const y = (height - imageHeight) / 2;

    return toDataUrl(
      `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
        <g transform="rotate(${rotate}, ${centerX}, ${centerY})">
          <image href="${escapeXml(imageUrl)}" x="${x}" y="${y}" width="${imageWidth}" height="${imageHeight}" preserveAspectRatio="xMidYMid meet" />
        </g>
      </svg>`,
    );
  }

  const lines = settings.textLines?.length ? settings.textLines : [''];
  const lineHeight = Math.max(14, Math.round(fontSize * 1.35));
  const totalHeight = lineHeight * lines.length;
  const startY = centerY - totalHeight / 2 + lineHeight / 2;
  const fill = settings.fontColor || 'rgba(0, 0, 0, 0.15)';
  const fontWeight = settings.fontWeight || 'normal';

  return toDataUrl(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <g transform="rotate(${rotate}, ${centerX}, ${centerY})">
        ${lines
          .map(
            (line, index) =>
              `<text x="${centerX}" y="${startY + index * lineHeight}" text-anchor="middle" dominant-baseline="middle" fill="${escapeXml(fill)}" font-size="${fontSize}" font-weight="${escapeXml(
                fontWeight,
              )}" font-family="sans-serif">${escapeXml(line)}</text>`,
          )
          .join('')}
      </g>
    </svg>`,
  );
};

const StaticWatermark = memo(({ settings, children }: StaticWatermarkProps) => {
  if (!settings.enabled) {
    return <>{children}</>;
  }

  const gapX = Number.isFinite(settings.gapX) && settings.gapX > 0 ? settings.gapX : DEFAULT_GAP_X;
  const gapY = Number.isFinite(settings.gapY) && settings.gapY > 0 ? settings.gapY : DEFAULT_GAP_Y;
  const offsetX = Number.isFinite(settings.offsetX) ? settings.offsetX : 0;
  const offsetY = Number.isFinite(settings.offsetY) ? settings.offsetY : 0;
  const backgroundImage = buildWatermarkImage(settings);

  const overlayStyle: CSSProperties = {
    position: 'fixed',
    inset: 0,
    zIndex: settings.zIndex,
    pointerEvents: 'none',
    backgroundImage: `url("${backgroundImage}")`,
    backgroundRepeat: 'repeat',
    backgroundSize: `${gapX}px ${gapY}px`,
    backgroundPosition: `${offsetX - gapX / 2}px ${offsetY - gapY / 2}px`,
  };

  return (
    <>
      {children}
      <div aria-hidden="true" className="saas-static-watermark" style={overlayStyle} />
    </>
  );
});

export default StaticWatermark;
