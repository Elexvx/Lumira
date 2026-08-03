import { Button, Card, Empty, Form, Image, Input, Space, Switch, Upload } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { FloatingWindowSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

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
  disabled,
  uploadingTarget,
  cardWidth,
  cardHeight,
  imageWidth,
  imageHeight,
  buttonLabel,
  clearLabel,
  emptyDescription = t('ui.settings.personalization.floatingwindow.notUploaded'),
  onUpload,
  onClear,
  sectionGap,
  tagWrapGap,
  cardPadding,
}: {
  target: PersonalizationUploadTarget;
  previewSrc?: string | null;
  canUpdate: boolean;
  disabled?: boolean;
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
  sectionGap: number | [number, number];
  tagWrapGap: number | [number, number];
  cardPadding: number;
}) => (
  <Space align="start" size={sectionGap} wrap>
    <Card size="small" style={{ width: cardWidth, opacity: disabled ? 0.45 : 1 }} bodyStyle={{ padding: cardPadding }}>
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
        disabled={!canUpdate || disabled}
      >
        <Button icon={<UploadOutlined />} loading={uploadingTarget === target} disabled={!canUpdate || disabled}>
          {buttonLabel}
        </Button>
      </Upload>
      <Button icon={<DeleteOutlined />} onClick={onClear} disabled={!canUpdate || disabled || !previewSrc}>
        {clearLabel}
      </Button>
    </Space>
  </Space>
);

export const FloatingWindowTab = ({ formProps, preview, uploadingTarget, saving, canUpdate, onUpload, onClearQrImage, onSave }: FloatingWindowTabProps) => {
  const { isMobile } = useResponsive();
  const apiDocsQrEnabled = Form.useWatch('apiDocsQrEnabled', formProps.form);
  const qrControlsDisabled = apiDocsQrEnabled === false;
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);
  const cardPadding = resolveResponsiveValue(
    { desktop: APP_SPACING.antdDesktopTokens.padding, mobile: APP_SPACING.antdMobileTokens.padding },
    isMobile,
  );

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="apiDocsQrEnabled" label={t('ui.settings.personalization.floatingwindow.qrCode')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <fieldset disabled={qrControlsDisabled} style={{ border: 0, margin: 0, padding: 0 }}>
          <Form.Item name="apiDocsQrTitle" label={t('ui.settings.personalization.floatingwindow.popupTitle')} rules={[{ required: !qrControlsDisabled, message: t('ui.settings.personalization.floatingwindow.pleaseEnterAPopupTitle') }]}>
            <Input maxLength={30} disabled={qrControlsDisabled} placeholder={t('ui.settings.personalization.floatingwindow.scanTheQrCodeOnWechatToContact')} />
          </Form.Item>
          <Form.Item name="apiDocsQrImageUrl" hidden>
            <Input />
          </Form.Item>
          <Form.Item label={t('ui.settings.personalization.floatingwindow.qrCodeImage')} extra={t('ui.settings.personalization.floatingwindow.usedInTheQrCodePopupOpenedFrom')}>
            {renderImageUploadPreviewField({
              target: 'floatingQr',
              previewSrc: preview.apiDocsQrImageUrl,
              canUpdate,
              disabled: qrControlsDisabled,
              uploadingTarget,
              cardWidth: 180,
              cardHeight: 132,
              imageWidth: 132,
              imageHeight: 132,
              buttonLabel: t('ui.settings.personalization.floatingwindow.uploadQrCode'),
              clearLabel: t('ui.settings.personalization.floatingwindow.clear'),
              onUpload,
              onClear: onClearQrImage,
              sectionGap,
              tagWrapGap,
              cardPadding,
            })}
          </Form.Item>
        </fieldset>
      </Form>
      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={saving} disabled={!canUpdate} onClick={onSave}>
          {t('ui.settings.personalization.floatingwindow.saveSettings')}
        </Button>
      </div>
    </Space>
  );
};
