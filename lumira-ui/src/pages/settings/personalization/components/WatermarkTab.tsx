import { Button, Empty, Form, Image, Input, Popover, Segmented, Slider, Space, Switch, Typography, Upload } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useRef } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import type { WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
        aria-label={t('选择字体颜色', 'Select font color')}
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
          borderRadius: 6,
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
                borderRadius: 4,
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
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('点击或拖拽上传', 'Click or drag to upload')} />
          )}
          <Typography.Text type="secondary">
            {isUploading ? t('上传中...', 'Uploading...') : previewSrc ? t('点击或拖拽更换图片', 'Click or drag to replace the image') : t('点击或拖拽上传图片', 'Click or drag to upload an image')}
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
        <Form.Item name="enabled" label={t('启用水印', 'Enable watermark')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="mode" label={t('模式', 'Mode')}>
          <Segmented
            disabled={watermarkControlsDisabled}
            options={[{ label: t('文字', 'Text'), value: 'TEXT' }, { label: t('图片', 'Image'), value: 'IMAGE' }]}
          />
        </Form.Item>
        {!isImageMode ? (
          <Form.Item
            name="textLines"
            label={t('多行文字（每行一个）', 'Multiple lines of text (one per line)')}
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
              placeholder={t('每行输入一条水印文字', 'Enter one watermark line per row')}
            />
          </Form.Item>
        ) : null}

        <Form.Item name="imageUrl" hidden>
          <Input />
        </Form.Item>
        {isImageMode ? (
          <Form.Item label={t('水印图片', 'Watermark image')}>
            {renderImageUploadPreviewField({
              target: 'watermark',
              previewSrc: watermarkPreview.imageUrl,
              canUpdate: canUpdate && watermarkPreview.enabled,
              uploadingTarget,
              cardWidth: 200,
              cardHeight: 100,
              imageWidth: 180,
              imageHeight: 100,
              clearLabel: t('清除', 'Clear'),
              onUpload,
              onClear: onClearWatermarkImage,
              tagWrapGap,
            })}
          </Form.Item>
        ) : null}

        {!isImageMode ? (
          <>
            <Form.Item name="fontColor" label={t('字体颜色', 'Font color')}>
              <WatermarkColorControl disabled={watermarkControlsDisabled} />
            </Form.Item>
            <Form.Item name="fontSize" label={t('字号', 'Font size')}>
              <Slider min={10} max={48} disabled={watermarkControlsDisabled} marks={{ 10: '10', 48: '48' }} style={watermarkSliderStyle} />
            </Form.Item>
          </>
        ) : null}
        <Form.Item name="gapX" label={t('横向间距', 'Horizontal spacing')}>
          <Slider min={40} max={400} disabled={watermarkControlsDisabled} marks={{ 40: '40', 400: '400' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="gapY" label={t('纵向间距', 'Vertical spacing')}>
          <Slider min={40} max={400} disabled={watermarkControlsDisabled} marks={{ 40: '40', 400: '400' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="rotate" label={t('旋转', 'Rotation')}>
          <Slider min={-180} max={180} disabled={watermarkControlsDisabled} marks={{ '-180': '-180', 0: '0', 180: '180' }} style={watermarkSliderStyle} />
        </Form.Item>
        <Form.Item name="opacity" label={isImageMode ? t('图片透明度', 'Image opacity') : t('文字透明度', 'Text opacity')}>
          <Slider min={0.05} max={1} step={0.05} disabled={watermarkControlsDisabled} marks={{ 0.05: '0.05', 1: '1' }} style={watermarkSliderStyle} />
        </Form.Item>
      </Form>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={watermarkSaving} disabled={!canUpdate} onClick={onSave}>
          {t('保存设置', 'Save settings')}
        </Button>
      </div>
    </Space>
  );
};
