import { Button, Card, Empty, Form, Image, Input, InputNumber, Segmented, Space, Switch, Typography, Watermark, Upload, theme } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { BrandingSettings, WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type PersonalizationUploadTarget = 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr';

interface WatermarkTabProps {
  formProps: FormProps;
  watermarkPreview: WatermarkSettings;
  previewState: BrandingSettings;
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
  previewState,
  uploadingTarget,
  watermarkSaving,
  canUpdate,
  onUpload,
  onClearWatermarkImage,
  onSave,
}: WatermarkTabProps) => {
  const { token } = theme.useToken();
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="enabled" label={t('启用水印', 'Enable watermark')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="mode" label={t('模式', 'Mode')}>
          <Segmented options={[{ label: t('文字', 'Text'), value: 'TEXT' }, { label: t('图片', 'Image'), value: 'IMAGE' }]} />
        </Form.Item>
        {watermarkPreview.mode !== 'IMAGE' ? (
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
            <Input.TextArea rows={4} placeholder={t('每行输入一条水印文字', 'Enter one watermark line per row')} />
          </Form.Item>
        ) : null}

        <Form.Item name="imageUrl" hidden>
          <Input />
        </Form.Item>
        {watermarkPreview.mode === 'IMAGE' ? (
          <Form.Item label={t('水印图片', 'Watermark image')}>
            {renderImageUploadPreviewField({
              target: 'watermark',
              previewSrc: watermarkPreview.imageUrl,
              canUpdate,
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

        <Form.Item name="fontColor" label={t('字体颜色', 'Font color')}>
          <Input />
        </Form.Item>
        <Form.Item name="fontSize" label={t('字号', 'Font size')}>
          <InputNumber min={10} max={48} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="gapX" label={t('横向间距', 'Horizontal spacing')}>
          <InputNumber min={40} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="gapY" label={t('纵向间距', 'Vertical spacing')}>
          <InputNumber min={40} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="rotate" label={t('旋转', 'Rotation')}>
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="opacity" label={t('透明度', 'Opacity')}>
          <InputNumber min={0.05} max={1} step={0.05} style={{ width: '100%' }} />
        </Form.Item>
      </Form>

      <Card title={t('预览', 'Preview')}>
        <Watermark
          content={watermarkPreview.mode === 'TEXT' ? watermarkPreview.textLines : undefined}
          image={watermarkPreview.mode === 'IMAGE' ? normalizeUploadUrl(watermarkPreview.imageUrl) : undefined}
        >
          <div
            style={{
              height: 'var(--saas-spacing-180)',
              display: 'grid',
              placeItems: 'center',
              background: token.colorFillAlter,
            }}
          >
            <Typography.Text>{previewState.websiteName}</Typography.Text>
          </div>
        </Watermark>
      </Card>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={watermarkSaving} disabled={!canUpdate} onClick={onSave}>
          {t('保存设置', 'Save settings')}
        </Button>
      </div>
    </Space>
  );
};
