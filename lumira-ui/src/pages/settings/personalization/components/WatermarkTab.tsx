import { Button, Empty, Form, Image, Input, Popover, Segmented, Slider, Space, Switch, Typography, Upload } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useRef } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import type { WatermarkSettings } from '@/types/api';
import { insertWatermarkTemplateToken, isValidWatermarkTemplateLines, normalizeWatermarkTextLines, WATERMARK_TEMPLATE_VARIABLES } from '@/watermark/template';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { resolveBuiltinMessage } from '@/i18n/messages';

const t = (id: string) => resolveBuiltinMessage(id);

type PersonalizationUploadTarget = 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr';

const watermarkSliderStyle = { width: '100%' };

type RgbaColor = {
  r: number;
  g: number;
  b: number;
  a: number;
};

type HsvColor = {
  h: number;
  s: number;
  v: number;
};

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const componentToHex = (value: number) => Math.round(clamp(value, 0, 255)).toString(16).padStart(2, '0').toUpperCase();

const rgbaToHex = ({ r, g, b }: RgbaColor) => `#${componentToHex(r)}${componentToHex(g)}${componentToHex(b)}`;

const formatWatermarkColor = (color: RgbaColor) => `${rgbaToHex(color)},${Math.round(clamp(color.a, 0, 1) * 100)}%`;
const formatWatermarkColorDisplay = (color: RgbaColor) => rgbaToHex(color);

const parseWatermarkColor = (value?: string): RgbaColor => {
  const raw = (value || '').trim();
  const rgbaMatch = raw.match(/^rgba?\(([^)]+)\)$/i);
  if (rgbaMatch) {
    const parts = rgbaMatch[1].split(',').map((item) => item.trim());
    const [r, g, b] = parts.slice(0, 3).map((item) => Number(item));
    const alpha = parts[3] === undefined ? 1 : Number(parts[3]);
    if ([r, g, b, alpha].every(Number.isFinite)) {
      return { r: clamp(r, 0, 255), g: clamp(g, 0, 255), b: clamp(b, 0, 255), a: clamp(alpha, 0, 1) };
    }
  }

  const hexMatch = raw.match(/^#?([0-9a-f]{3}|[0-9a-f]{6})(?:\s*,\s*(\d+(?:\.\d+)?)%)?$/i);
  if (hexMatch) {
    const hex = hexMatch[1].length === 3
      ? hexMatch[1].split('').map((item) => `${item}${item}`).join('')
      : hexMatch[1];
    return {
      r: parseInt(hex.slice(0, 2), 16),
      g: parseInt(hex.slice(2, 4), 16),
      b: parseInt(hex.slice(4, 6), 16),
      a: hexMatch[2] === undefined ? 1 : clamp(Number(hexMatch[2]) / 100, 0, 1),
    };
  }

  return { r: 25, g: 19, b: 19, a: 0.15 };
};

const rgbaToHsv = ({ r, g, b }: RgbaColor): HsvColor => {
  const red = r / 255;
  const green = g / 255;
  const blue = b / 255;
  const max = Math.max(red, green, blue);
  const min = Math.min(red, green, blue);
  const delta = max - min;
  let hue = 0;

  if (delta) {
    if (max === red) {
      hue = ((green - blue) / delta) % 6;
    } else if (max === green) {
      hue = (blue - red) / delta + 2;
    } else {
      hue = (red - green) / delta + 4;
    }
    hue = Math.round(hue * 60);
    if (hue < 0) {
      hue += 360;
    }
  }

  return {
    h: hue,
    s: max === 0 ? 0 : delta / max,
    v: max,
  };
};

const hsvToRgba = ({ h, s, v }: HsvColor, alpha: number): RgbaColor => {
  const chroma = v * s;
  const x = chroma * (1 - Math.abs(((h / 60) % 2) - 1));
  const match = v - chroma;
  const sector = Math.floor(h / 60) % 6;
  const [red, green, blue] = [
    [chroma, x, 0],
    [x, chroma, 0],
    [0, chroma, x],
    [0, x, chroma],
    [x, 0, chroma],
    [chroma, 0, x],
  ][sector] || [chroma, 0, x];

  return {
    r: (red + match) * 255,
    g: (green + match) * 255,
    b: (blue + match) * 255,
    a: alpha,
  };
};

const WatermarkColorControl = ({
  value,
  onChange,
  disabled,
}: {
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
}) => {
  const popupHostRef = useRef<HTMLDivElement>(null);
  const rgba = parseWatermarkColor(value);
  const hsv = rgbaToHsv(rgba);
  const displayText = formatWatermarkColorDisplay(rgba);
  const paletteBackground = {
    backgroundColor: `hsl(${hsv.h}, 100%, 50%)`,
    backgroundImage: 'linear-gradient(0deg, #000, transparent), linear-gradient(90deg, #fff, hsla(0, 0%, 100%, 0))',
  };
  const updateFromPaletteEvent = (event: React.MouseEvent<HTMLDivElement>) => {
    const x = clamp(event.nativeEvent.offsetX, 0, event.currentTarget.clientWidth);
    const y = clamp(event.nativeEvent.offsetY, 0, event.currentTarget.clientHeight);
    onChange?.(formatWatermarkColor(hsvToRgba({
      h: hsv.h,
      s: x / event.currentTarget.clientWidth,
      v: 1 - y / event.currentTarget.clientHeight,
    }, rgba.a)));
  };
  const updateHue = (nextHue: number) => {
    onChange?.(formatWatermarkColor(hsvToRgba({ ...hsv, h: nextHue }, rgba.a)));
  };
  const updateAlpha = (nextAlpha: number) => {
    onChange?.(formatWatermarkColor({ ...rgba, a: nextAlpha }));
  };
  const colorPanel = (
    <div style={{ width: 260 }}>
      <div
        role="slider"
        aria-label={t('ui.settings.personalization.watermark.selectFontColor')}
        aria-valuetext={displayText}
        onMouseDown={updateFromPaletteEvent}
        onMouseMove={(event) => {
          if (event.buttons === 1) {
            updateFromPaletteEvent(event);
          }
        }}
        style={{
          ...paletteBackground,
          position: 'relative',
          height: 160,
          borderRadius: 'var(--ant-border-radius)',
          cursor: disabled ? 'not-allowed' : 'crosshair',
          pointerEvents: disabled ? 'none' : undefined,
        }}
      >
        <span
          style={{
            position: 'absolute',
            left: `${hsv.s * 100}%`,
            top: `${(1 - hsv.v) * 100}%`,
            width: 16,
            height: 16,
            border: '2px solid #fff',
            borderRadius: '50%',
            boxShadow: '0 0 0 1px rgba(0,0,0,0.35)',
            transform: 'translate(-50%, -50%)',
            pointerEvents: 'none',
          }}
        />
      </div>
      <Slider min={0} max={360} disabled={disabled} value={hsv.h} onChange={updateHue} tooltip={{ formatter: null }} />
      <Slider min={0} max={1} step={0.01} disabled={disabled} value={rgba.a} onChange={updateAlpha} tooltip={{ formatter: (next) => `${Math.round(Number(next) * 100)}%` }} />
    </div>
  );

  return (
    <div ref={popupHostRef} data-watermark-color-popup-host="true" style={{ position: 'relative', width: '100%' }}>
      <Space.Compact>
        <Popover
          content={colorPanel}
          trigger="click"
          placement="bottomLeft"
          autoAdjustOverflow={false}
          getPopupContainer={() => popupHostRef.current || document.body}
        >
          <Button disabled={disabled} style={{ minWidth: 168, justifyContent: 'flex-start' }}>
            <span
              style={{
                display: 'inline-block',
                width: 22,
                height: 22,
                marginInlineEnd: 8,
                borderRadius: 'var(--ant-border-radius-sm)',
                border: '1px solid var(--ant-color-border)',
                background: rgbaToHex(rgba),
              }}
            />
            {displayText}
          </Button>
        </Popover>
      </Space.Compact>
    </div>
  );
};

type WatermarkTemplateInputProps = {
  value?: string[];
  onChange?: (value: string[]) => void;
  disabled?: boolean;
};

const WatermarkTemplateInput = ({ value, onChange, disabled }: WatermarkTemplateInputProps) => {
  const inputRef = useRef<{ resizableTextArea?: { textArea?: HTMLTextAreaElement } } | null>(null);
  const textValue = normalizeWatermarkTextLines(value).join('\n');

  const insertToken = (token: string) => {
    const textarea = inputRef.current?.resizableTextArea?.textArea;
    const start = textarea?.selectionStart ?? textValue.length;
    const end = textarea?.selectionEnd ?? textValue.length;
    const inserted = insertWatermarkTemplateToken(textValue, token, start, end);
    onChange?.(inserted.lines);
    const nextCursor = inserted.cursor;
    const restoreCursor = () => {
      textarea?.focus();
      textarea?.setSelectionRange(nextCursor, nextCursor);
    };
    if (typeof window !== 'undefined') {
      window.requestAnimationFrame(restoreCursor);
    }
  };

  return (
    <Space direction="vertical" size={8} style={{ width: '100%', minWidth: 0 }}>
      <Input.TextArea
        ref={(instance) => {
          inputRef.current = instance;
        }}
        data-watermark-personalized-text-lines="true"
        rows={4}
        value={textValue}
        disabled={disabled}
        onChange={(event) => onChange?.(normalizeWatermarkTextLines(event.target.value))}
        placeholder={t('ui.settings.personalization.watermark.enterPersonalizedWatermarkTemplate')}
      />
      <Space wrap size={[8, 8]} style={{ width: '100%', maxWidth: '100%', minWidth: 0 }}>
        {WATERMARK_TEMPLATE_VARIABLES.map(({ name, token }) => (
          <Button
            key={name}
            size="small"
            disabled={disabled}
            title={token}
            aria-label={`${t(`ui.settings.personalization.watermark.${name}`)} ${token}`}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => insertToken(token)}
          >
            {t(`ui.settings.personalization.watermark.${name}`)} {token}
          </Button>
        ))}
      </Space>
    </Space>
  );
};

interface WatermarkTabProps {
  formProps: FormProps;
  watermarkPreview: WatermarkSettings;
  uploadingTarget: PersonalizationUploadTarget | null;
  watermarkSaving: boolean;
  canUpdate: boolean;
  onUpload: (target: PersonalizationUploadTarget, file: File) => Promise<void>;
  onClearWatermarkImage: () => void;
  onSave: () => void;
}

const renderImageUploadPreviewField = ({
  target,
  previewSrc,
  canUpdate,
  uploadingTarget,
  cardWidth,
  cardHeight,
  imageWidth,
  imageHeight,
  clearLabel,
  onUpload,
  onClear,
  tagWrapGap,
}: {
  target: PersonalizationUploadTarget;
  previewSrc?: string | null;
  canUpdate: boolean;
  uploadingTarget: PersonalizationUploadTarget | null;
  cardWidth: number;
  cardHeight: number;
  imageWidth: number;
  imageHeight: number;
  clearLabel: string;
  onUpload: (target: PersonalizationUploadTarget, file: File) => Promise<void>;
  onClear: () => void;
  tagWrapGap: number | [number, number];
}) => {
  const isUploading = uploadingTarget === target;

  return (
    <Space direction="vertical" size={tagWrapGap}>
      <Upload.Dragger
        accept="image/*"
        showUploadList={false}
        beforeUpload={async (file) => {
          await onUpload(target, file);
          return Upload.LIST_IGNORE;
        }}
        disabled={!canUpdate || isUploading}
        style={{
          width: cardWidth,
          minHeight: cardHeight,
          padding: 0,
          borderRadius: 'var(--saas-card-radius)',
          border: '1px dashed var(--ant-color-border)',
          background: 'var(--ant-color-fill-quaternary)',
          cursor: canUpdate && !isUploading ? 'pointer' : 'not-allowed',
          overflow: 'hidden',
          opacity: isUploading ? 0.72 : 1,
        }}
      >
        <div
          style={{
            width: '100%',
            minHeight: cardHeight,
            display: 'grid',
            placeItems: 'center',
            gap: 12,
            padding: 16,
            color: 'var(--ant-color-text-secondary)',
          }}
        >
          {previewSrc ? (
            <Image
              width={imageWidth}
              height={imageHeight}
              preview={false}
              src={normalizeUploadUrl(previewSrc)}
              style={{ objectFit: 'contain' }}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ui.settings.personalization.watermark.clickOrDragToUpload')} />
          )}
          <Typography.Text type="secondary">
            {isUploading ? t('ui.settings.personalization.watermark.uploading') : previewSrc ? t('ui.settings.personalization.watermark.clickOrDragToReplaceTheImage') : t('ui.settings.personalization.watermark.clickOrDragToUploadAnImage')}
          </Typography.Text>
        </div>
      </Upload.Dragger>
      <Button icon={<DeleteOutlined />} onClick={onClear} disabled={!canUpdate || !previewSrc}>
        {clearLabel}
      </Button>
    </Space>
  );
};

export const WatermarkTab = ({
  formProps,
  watermarkPreview,
  uploadingTarget,
  watermarkSaving,
  canUpdate,
  onUpload,
  onClearWatermarkImage,
  onSave,
}: WatermarkTabProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  const watermarkControlsDisabled = !canUpdate || !watermarkPreview.enabled;
  const isImageMode = watermarkPreview.mode === 'IMAGE';

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="enabled" label={t('ui.settings.personalization.watermark.enableWatermark')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="mode" label={t('ui.settings.personalization.watermark.mode')}>
          <Segmented
            disabled={watermarkControlsDisabled}
            options={[{ label: t('ui.settings.personalization.watermark.text'), value: 'TEXT' }, { label: t('ui.settings.personalization.watermark.image'), value: 'IMAGE' }]}
          />
        </Form.Item>
        {!isImageMode ? (
          <Form.Item
            name="personalizedTextLines"
            label={t('ui.settings.personalization.watermark.personalizedWatermarkTemplate')}
            extra={t('ui.settings.personalization.watermark.personalizedWatermarkTemplateHint')}
            rules={[{
              validator: async (_, value) => {
                if (isValidWatermarkTemplateLines(value)) {
                  return;
                }
                throw new Error(t('ui.settings.personalization.watermark.invalidPersonalizedWatermarkTemplate'));
              },
            }]}
          >
            <WatermarkTemplateInput disabled={watermarkControlsDisabled} />
          </Form.Item>
        ) : null}
        {!isImageMode ? (
          <Form.Item
            name="textLines"
            label={t('ui.settings.personalization.watermark.fixedVisitorWatermarkText')}
            getValueProps={(value?: string[]) => ({ value: (value || []).join('\n') })}
            getValueFromEvent={(event: { target: { value: string } }) =>
              event.target.value
                .split('\n')
                .map((item: string) => item.trim())
                .filter(Boolean)
            }
          >
            <Input.TextArea
              rows={4}
              disabled={watermarkControlsDisabled}
              placeholder={t('ui.settings.personalization.watermark.enterOneWatermarkLinePerRow')}
            />
          </Form.Item>
        ) : null}

        <Form.Item name="imageUrl" hidden>
          <Input />
        </Form.Item>
        {isImageMode ? (
          <Form.Item label={t('ui.settings.personalization.watermark.watermarkImage')}>
            {renderImageUploadPreviewField({
              target: 'watermark',
              previewSrc: watermarkPreview.imageUrl,
              canUpdate: canUpdate && watermarkPreview.enabled,
              uploadingTarget,
              cardWidth: 200,
              cardHeight: 100,
              imageWidth: 180,
              imageHeight: 100,
              clearLabel: t('ui.settings.personalization.watermark.clear'),
              onUpload,
              onClear: onClearWatermarkImage,
              tagWrapGap,
            })}
          </Form.Item>
        ) : null}

        {!isImageMode ? (
          <>
            <Form.Item name="fontColor" label={t('ui.settings.personalization.watermark.fontColor')}>
              <WatermarkColorControl disabled={watermarkControlsDisabled} />
            </Form.Item>
            <Form.Item name="fontSize" label={t('ui.settings.personalization.watermark.fontSize')}>
              <Slider min={10} max={48} disabled={watermarkControlsDisabled} marks={{ 10: '10', 48: '48' }} style={watermarkSliderStyle} />
            </Form.Item>
          </>
        ) : null}
        <Form.Item name="gapX" label={t('ui.settings.personalization.watermark.horizontalSpacing')}>
          <Slider min={40} max={400} disabled={watermarkControlsDisabled} marks={{ 40: '40', 400: '400' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="gapY" label={t('ui.settings.personalization.watermark.verticalSpacing')}>
          <Slider min={40} max={400} disabled={watermarkControlsDisabled} marks={{ 40: '40', 400: '400' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="rotate" label={t('ui.settings.personalization.watermark.rotation')}>
          <Slider min={-180} max={180} disabled={watermarkControlsDisabled} marks={{ '-180': '-180', 0: '0', 180: '180' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="opacity" label={isImageMode ? t('ui.settings.personalization.watermark.imageOpacity') : t('ui.settings.personalization.watermark.textOpacity')}>
          <Slider min={0.05} max={1} step={0.05} disabled={watermarkControlsDisabled} marks={{ 0.05: '0.05', 1: '1' }} style={watermarkSliderStyle} />
        </Form.Item>
      </Form>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={watermarkSaving} disabled={!canUpdate} onClick={onSave}>
          {t('ui.settings.personalization.watermark.saveSettings')}
        </Button>
      </div>
    </Space>
  );
};
