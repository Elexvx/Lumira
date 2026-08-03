import { Button, Form, Image, Input, Space, Switch, Typography, Upload } from 'antd';
import { DeleteOutlined, InboxOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { BrandingSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

type BrandingAssetField = 'websiteFaviconUrl' | 'websiteLogoUrl' | 'loginBackgroundUrl';
type BrandingAssetTarget = 'favicon' | 'logo' | 'loginBackground';

type BrandingAssetItemConfig = {
  field: BrandingAssetField;
  label: string;
  clearLabel: string;
  target: BrandingAssetTarget;
  previewKey: keyof Pick<BrandingSettings, 'websiteFaviconUrl' | 'websiteLogoUrl' | 'loginBackgroundUrl'>;
  cardWidth: number;
  cardHeight?: number;
  imageWidth?: number | string;
  imageHeight?: number | string;
  cropModalTitle?: string;
  cropAspect?: number;
  accept: string;
  note?: string;
  useCrop?: boolean;
  beforeCrop?: (file: File) => boolean | Promise<boolean>;
};

const BRANDING_ASSET_ITEM_CONFIGS = [
  {
    field: 'websiteFaviconUrl',
    label: t('ui.settings.personalization.branding.websiteIcon'),
    clearLabel: t('ui.settings.personalization.branding.websiteIcon'),
    target: 'favicon',
    previewKey: 'websiteFaviconUrl',
    cardWidth: 128,
    cardHeight: 128,
    imageWidth: 72,
    imageHeight: 72,
    cropModalTitle: t('ui.settings.personalization.branding.cropWebsiteIcon'),
    cropAspect: 1,
    accept: 'image/*,.ico',
    useCrop: true,
    beforeCrop: (file: File) => {
      const lowerName = file.name.toLowerCase();
      if (lowerName.endsWith('.ico') || file.type === 'image/x-icon' || file.type === 'image/vnd.microsoft.icon') {
        return false;
      }
      return true;
    },
  },
  {
    field: 'websiteLogoUrl',
    label: t('ui.settings.personalization.branding.logo'),
    clearLabel: t('ui.settings.personalization.branding.logo'),
    target: 'logo',
    previewKey: 'websiteLogoUrl',
    cardWidth: 240,
    cardHeight: 128,
    imageWidth: 200,
    imageHeight: 72,
    cropModalTitle: t('ui.settings.personalization.branding.cropLogo'),
    cropAspect: 25 / 9,
    accept: 'image/*',
  },
  {
    field: 'loginBackgroundUrl',
    label: t('ui.settings.personalization.branding.loginBackground'),
    clearLabel: t('ui.settings.personalization.branding.loginBackground'),
    target: 'loginBackground',
    previewKey: 'loginBackgroundUrl',
    cardWidth: 320,
    cardHeight: 180,
    accept: 'image/*',
    note: t('ui.settings.personalization.branding.useA169ImageOrWiderThe'),
  },
] as const satisfies readonly BrandingAssetItemConfig[];

interface BrandingTabProps {
  formProps: FormProps;
  previewState: BrandingSettings;
  uploadingTarget: 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr' | null;
  brandingSaving: boolean;
  canUpdate: boolean;
  onUpload: (target: 'favicon' | 'logo' | 'loginBackground', file: File) => Promise<void>;
  onClearField: (field: 'websiteFaviconUrl' | 'websiteLogoUrl' | 'loginBackgroundUrl', label: string) => void;
  onSave: () => void;
}

const renderBrandingUploadField = ({
  target,
  previewSrc,
  canUpdate,
  uploadingTarget,
  cardWidth,
  cardHeight,
  imageWidth,
  imageHeight,
  cropModalTitle,
  cropAspect,
  accept,
  clearLabel,
  note,
  useCrop,
  beforeCrop,
  onUpload,
  onClear,
  tagWrapGap,
}: {
  target: BrandingAssetTarget;
  previewSrc?: string | null;
  canUpdate: boolean;
  uploadingTarget: BrandingTabProps['uploadingTarget'];
  cardWidth: number;
  cardHeight?: number;
  imageWidth?: number | string;
  imageHeight?: number | string;
  cropModalTitle?: string;
  cropAspect?: number;
  accept: string;
  clearLabel: string;
  note?: string;
  useCrop?: boolean;
  beforeCrop?: (file: File) => boolean | Promise<boolean>;
  onUpload: BrandingTabProps['onUpload'];
  onClear: () => void;
  tagWrapGap: number | [number, number];
}) => {
  const previewHeight = cardHeight ?? Math.max(Math.round(cardWidth * 0.5625), typeof imageHeight === 'number' ? imageHeight + 48 : 0, 140);
  const isUploading = uploadingTarget === target;
  const uploadArea = (
    <Upload
      accept={accept}
      showUploadList={false}
      beforeUpload={async (file) => {
        await onUpload(target, file);
        return Upload.LIST_IGNORE;
      }}
      disabled={!canUpdate || isUploading}
    >
      <div
        role="button"
        aria-disabled={!canUpdate || isUploading}
        tabIndex={canUpdate && !isUploading ? 0 : -1}
        onKeyDown={(event) => {
          if (!canUpdate || isUploading) {
            return;
          }
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            event.currentTarget.click();
          }
        }}
        style={{
          width: cardWidth,
          minHeight: previewHeight,
          padding: 16,
          borderRadius: 'var(--saas-card-radius)',
          border: '1px dashed var(--ant-color-border)',
          background: 'var(--ant-color-fill-quaternary)',
          cursor: canUpdate && !isUploading ? 'pointer' : 'not-allowed',
          overflow: 'hidden',
          opacity: isUploading ? 0.72 : 1,
          display: 'grid',
          placeItems: 'center',
          gap: 12,
          color: 'var(--ant-color-text-secondary)',
          textAlign: 'center',
        }}
      >
        {previewSrc ? (
          <Image
            width={imageWidth ?? '100%'}
            height={imageHeight}
            preview={false}
            src={normalizeUploadUrl(previewSrc)}
            style={{ objectFit: target === 'loginBackground' ? 'cover' : 'contain' }}
          />
        ) : (
          <Space direction="vertical" size={8} align="center">
            <InboxOutlined style={{ fontSize: 24, color: 'var(--ant-color-text-quaternary)' }} />
            <Typography.Text type="secondary">{isUploading ? t('ui.settings.personalization.branding.uploading') : t('ui.settings.personalization.branding.clickToChooseAnImage')}</Typography.Text>
          </Space>
        )}
      </div>
    </Upload>
  );

  return (
    <Space direction="vertical" size={tagWrapGap}>
      {useCrop ? (
        <ImgCrop modalTitle={cropModalTitle} rotationSlider aspect={cropAspect} beforeCrop={beforeCrop}>
          {uploadArea}
        </ImgCrop>
      ) : (
        uploadArea
      )}
      <Button icon={<DeleteOutlined />} onClick={onClear} disabled={!canUpdate || !previewSrc}>
        {clearLabel}
      </Button>
      {note ? <span style={{ color: 'var(--ant-color-text-secondary)' }}>{note}</span> : null}
    </Space>
  );
};

export const BrandingTab = ({
  formProps,
  previewState,
  uploadingTarget,
  brandingSaving,
  canUpdate,
  onUpload,
  onClearField,
  onSave,
}: BrandingTabProps) => {
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const tagWrapGap = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile);

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="websiteName" label={t('ui.settings.personalization.branding.websiteName')} rules={[{ required: true }]}>
          <Input />
        </Form.Item>

        {BRANDING_ASSET_ITEM_CONFIGS.map((config) => (
          <Form.Item key={config.field} name={config.field} hidden>
            <Input />
          </Form.Item>
        ))}

        {BRANDING_ASSET_ITEM_CONFIGS.map((config) => {
          const assetConfig = config as BrandingAssetItemConfig;

          return (
            <Form.Item key={assetConfig.field} label={assetConfig.label}>
              {renderBrandingUploadField({
                target: assetConfig.target,
                previewSrc: previewState[assetConfig.previewKey],
                canUpdate,
                uploadingTarget,
                cardWidth: assetConfig.cardWidth,
                cardHeight: assetConfig.cardHeight,
                imageWidth: assetConfig.imageWidth,
                imageHeight: assetConfig.imageHeight,
                cropModalTitle: assetConfig.cropModalTitle,
                cropAspect: assetConfig.cropAspect,
                accept: assetConfig.accept,
                clearLabel: t('ui.settings.personalization.branding.clear'),
                note: assetConfig.note,
                useCrop: assetConfig.useCrop,
                beforeCrop: assetConfig.beforeCrop,
                onUpload,
                onClear: () => onClearField(assetConfig.field, assetConfig.clearLabel),
                tagWrapGap,
              })}
            </Form.Item>
          );
        })}
        <Form.Item label={t('ui.settings.personalization.branding.githubLink')}>
          <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
            <Form.Item name="githubLinkEnabled" valuePropName="checked" noStyle>
              <Switch />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, next) => prev.githubLinkEnabled !== next.githubLinkEnabled}>
              {({ getFieldValue }) => (
                <Form.Item name="githubLinkUrl" noStyle>
                  <Input disabled={!getFieldValue('githubLinkEnabled')} placeholder="https://github.com/your-org/your-repo" />
                </Form.Item>
              )}
            </Form.Item>
          </Space>
        </Form.Item>
        <Form.Item label={t('ui.settings.personalization.branding.helpLink')}>
          <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
            <Form.Item name="helpLinkEnabled" valuePropName="checked" noStyle>
              <Switch />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, next) => prev.helpLinkEnabled !== next.helpLinkEnabled}>
              {({ getFieldValue }) => (
                <Form.Item name="helpLinkUrl" noStyle>
                  <Input disabled={!getFieldValue('helpLinkEnabled')} placeholder="https://docs.example.com/help" />
                </Form.Item>
              )}
            </Form.Item>
          </Space>
        </Form.Item>
        <Form.Item name="footerIcp" label={t('ui.settings.personalization.branding.icpFilingNumber')}>
          <Input />
        </Form.Item>
        <Form.Item name="footerPoliceBeian" label={t('ui.settings.personalization.branding.publicSecurityFilingNumber')}>
          <Input />
        </Form.Item>
        <Form.Item name="footerCopyright" label={t('ui.settings.personalization.branding.copyrightNotice')}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>

      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
        <Button type="primary" loading={brandingSaving} disabled={!canUpdate} onClick={onSave}>
          {t('ui.settings.personalization.branding.saveSettings')}
        </Button>
      </div>
    </Space>
  );
};
