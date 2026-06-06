import { Button, Card, Empty, Form, Image, Input, InputNumber, Segmented, Space, Switch, Typography, Watermark, Upload, theme } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { BrandingSettings, WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

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
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="点击或拖拽上传" />
          )}
          <Typography.Text type="secondary">{isUploading ? '上传中...' : previewSrc ? '点击或拖拽更换图片' : '点击或拖拽上传图片'}</Typography.Text>
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
        <Form.Item name="enabled" label="启用水印" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="mode" label="模式">
          <Segmented options={[{ label: '文字', value: 'TEXT' }, { label: '图片', value: 'IMAGE' }]} />
        </Form.Item>
        {watermarkPreview.mode !== 'IMAGE' ? (
          <Form.Item
            name="textLines"
            label="多行文字（每行一个）"
            getValueProps={(value?: string[]) => ({ value: (value || []).join('\n') })}
            getValueFromEvent={(event: { target: { value: string } }) =>
              event.target.value
                .split('\n')
                .map((item: string) => item.trim())
                .filter(Boolean)
            }
          >
            <Input.TextArea rows={4} placeholder="每行输入一条水印文字" />
          </Form.Item>
        ) : null}

        <Form.Item name="imageUrl" hidden>
          <Input />
        </Form.Item>
        {watermarkPreview.mode === 'IMAGE' ? (
          <Form.Item label="水印图片">
            {renderImageUploadPreviewField({
              target: 'watermark',
              previewSrc: watermarkPreview.imageUrl,
              canUpdate,
              uploadingTarget,
              cardWidth: 200,
              cardHeight: 100,
              imageWidth: 180,
              imageHeight: 100,
              clearLabel: '清除',
              onUpload,
              onClear: onClearWatermarkImage,
              tagWrapGap,
            })}
          </Form.Item>
        ) : null}

        <Form.Item name="fontColor" label="字体颜色">
          <Input />
        </Form.Item>
        <Form.Item name="fontSize" label="字号">
          <InputNumber min={10} max={48} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="gapX" label="横向间距">
          <InputNumber min={40} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="gapY" label="纵向间距">
          <InputNumber min={40} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="rotate" label="旋转">
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="opacity" label="透明度">
          <InputNumber min={0.05} max={1} step={0.05} style={{ width: '100%' }} />
        </Form.Item>
      </Form>

      <Card title="预览">
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

      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="primary" loading={watermarkSaving} disabled={!canUpdate} onClick={onSave}>
          保存设置
        </Button>
      </div>
    </Space>
  );
};
