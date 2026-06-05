import { Button, Card, Empty, Form, Image, Input, Space, Switch, Upload } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { FloatingWindowSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

type PersonalizationUploadTarget = 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr';

interface FloatingWindowTabProps {
  formProps: FormProps;
  preview: FloatingWindowSettings;
  uploadingTarget: PersonalizationUploadTarget | null;
  saving: boolean;
  canUpdate: boolean;
  onUpload: (target: PersonalizationUploadTarget, file: File) => Promise<void>;
  onClearQrImage: () => void;
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
  buttonLabel,
  clearLabel,
  emptyDescription = '未上传',
  onUpload,
  onClear,
  sectionGap,
  tagWrapGap,
  cardPadding,
}: {
  target: PersonalizationUploadTarget;
  previewSrc?: string | null;
  canUpdate: boolean;
  uploadingTarget: PersonalizationUploadTarget | null;
  cardWidth: number;
  cardHeight: number;
  imageWidth: number;
  imageHeight: number;
  buttonLabel: string;
  clearLabel: string;
  emptyDescription?: string;
  onUpload: (target: PersonalizationUploadTarget, file: File) => Promise<void>;
  onClear: () => void;
  sectionGap: number | number[];
  tagWrapGap: number | number[];
  cardPadding: number;
}) => (
  <Space align="start" size={sectionGap} wrap>
    <Card size="small" style={{ width: cardWidth }} bodyStyle={{ padding: cardPadding }}>
      <div style={{ width: '100%', height: cardHeight, display: 'grid', placeItems: 'center' }}>
        {previewSrc ? (
          <Image width={imageWidth} height={imageHeight} preview={false} src={normalizeUploadUrl(previewSrc)} style={{ objectFit: 'contain' }} />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} />
        )}
      </div>
    </Card>
    <Space direction="vertical" size={tagWrapGap}>
      <Upload
        accept="image/*"
        showUploadList={false}
        beforeUpload={async (file) => {
          await onUpload(target, file);
          return Upload.LIST_IGNORE;
        }}
        disabled={!canUpdate}
      >
        <Button icon={<UploadOutlined />} loading={uploadingTarget === target} disabled={!canUpdate}>
          {buttonLabel}
        </Button>
      </Upload>
      <Button icon={<DeleteOutlined />} onClick={onClear} disabled={!canUpdate || !previewSrc}>
        {clearLabel}
      </Button>
    </Space>
  </Space>
);

export const FloatingWindowTab = ({ formProps, preview, uploadingTarget, saving, canUpdate, onUpload, onClearQrImage, onSave }: FloatingWindowTabProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  const cardPadding = resolveResponsiveValue(
    { desktop: APP_SPACING.antdDesktopTokens.padding, mobile: APP_SPACING.antdMobileTokens.padding },
    isMobile,
  );

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="apiDocsQrEnabled" label="二维码" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="apiDocsQrTitle" label="弹窗标题" rules={[{ required: true, message: '请输入弹窗标题' }]}>
          <Input maxLength={30} placeholder="微信扫码联系我们" />
        </Form.Item>
        <Form.Item name="apiDocsQrImageUrl" hidden>
          <Input />
        </Form.Item>
        <Form.Item label="二维码图片" extra="用于悬浮窗按钮展开后的二维码弹窗。">
          {renderImageUploadPreviewField({
            target: 'floatingQr',
            previewSrc: preview.apiDocsQrImageUrl,
            canUpdate,
            uploadingTarget,
            cardWidth: 180,
            cardHeight: 132,
            imageWidth: 132,
            imageHeight: 132,
            buttonLabel: '上传二维码',
            clearLabel: '清除',
            onUpload,
            onClear: onClearQrImage,
            sectionGap,
            tagWrapGap,
            cardPadding,
          })}
        </Form.Item>
      </Form>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="primary" loading={saving} disabled={!canUpdate} onClick={onSave}>
          保存设置
        </Button>
      </div>
    </Space>
  );
};
