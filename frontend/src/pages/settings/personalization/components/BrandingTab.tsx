import { Button, Card, Empty, Form, Image, Input, Space, Switch, Typography, Upload } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';
import type { BrandingSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';

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
  buttonLabel: string;
  emptyDescription?: string | false;
  note?: string;
  useCrop?: boolean;
  beforeCrop?: (file: File) => boolean | Promise<boolean>;
};

const BRANDING_ASSET_ITEM_CONFIGS = [
  {
    field: 'websiteFaviconUrl',
    label: '网站 Icon（本地上传）',
    clearLabel: '网站 Icon',
    target: 'favicon',
    previewKey: 'websiteFaviconUrl',
    cardWidth: 104,
    cardHeight: 104,
    imageWidth: 72,
    imageHeight: 72,
    cropModalTitle: '裁切网站 Icon',
    cropAspect: 1,
    accept: 'image/*,.ico',
    buttonLabel: '上传 Icon',
    emptyDescription: false,
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
    label: 'Logo（本地上传）',
    clearLabel: 'Logo',
    target: 'logo',
    previewKey: 'websiteLogoUrl',
    cardWidth: 200,
    imageWidth: 180,
    imageHeight: 72,
    cropModalTitle: '裁切 Logo',
    cropAspect: 25 / 9,
    accept: 'image/*',
    buttonLabel: '上传 Logo',
  },
  {
    field: 'loginBackgroundUrl',
    label: '登录页背景图（本地上传）',
    clearLabel: '登录页背景图',
    target: 'loginBackground',
    previewKey: 'loginBackgroundUrl',
    cardWidth: 280,
    accept: 'image/*',
    buttonLabel: '上传背景图',
    note: '建议上传 16:9 或更宽的图片，登录页会自动铺满并裁切。',
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
  buttonLabel,
  clearLabel,
  emptyDescription,
  note,
  useCrop,
  beforeCrop,
  onUpload,
  onClear,
  tagWrapGap,
  cardPadding,
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
  buttonLabel: string;
  clearLabel: string;
  emptyDescription?: string | false;
  note?: string;
  useCrop?: boolean;
  beforeCrop?: (file: File) => boolean | Promise<boolean>;
  onUpload: BrandingTabProps['onUpload'];
  onClear: () => void;
  tagWrapGap: number | [number, number];
  cardPadding: number;
}) => {
  const uploadButton = (
    <Upload
      accept={accept}
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
  );

  return (
    <Space direction="vertical" size={tagWrapGap}>
      {useCrop ? (
        <ImgCrop modalTitle={cropModalTitle} rotationSlider aspect={cropAspect} beforeCrop={beforeCrop}>
          {uploadButton}
        </ImgCrop>
      ) : (
        uploadButton
      )}
      <Button icon={<DeleteOutlined />} onClick={onClear} disabled={!canUpdate || !previewSrc}>
        {clearLabel}
      </Button>
      {note ? <span style={{ color: 'var(--ant-color-text-secondary)' }}>{note}</span> : null}
      <Card size="small" style={{ width: cardWidth, height: cardHeight }} bodyStyle={{ padding: cardPadding, height: '100%' }}>
        <div
          style={{
            width: '100%',
            height: '100%',
            display: 'grid',
            placeItems: 'center',
            background: 'var(--ant-color-fill-quaternary)',
            color: 'var(--ant-color-text-secondary)',
          }}
        >
          {previewSrc ? (
            <Image
              width={imageWidth ?? '100%'}
              height={imageHeight ?? '100%'}
              preview={false}
              src={normalizeUploadUrl(previewSrc)}
              style={{ objectFit: target === 'loginBackground' ? 'cover' : 'contain' }}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription ?? '未上传'} />
          )}
        </div>
      </Card>
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
  const cardPadding = resolveResponsiveValue(
    { desktop: APP_SPACING.antdDesktopTokens.padding, mobile: APP_SPACING.antdMobileTokens.padding },
    isMobile,
  );

  return (
    <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}>
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
                buttonLabel: assetConfig.buttonLabel,
                clearLabel: '清除',
                emptyDescription: assetConfig.emptyDescription,
                note: assetConfig.note,
                useCrop: assetConfig.useCrop,
                beforeCrop: assetConfig.beforeCrop,
                onUpload,
                onClear: () => onClearField(assetConfig.field, assetConfig.clearLabel),
                tagWrapGap,
                cardPadding,
              })}
            </Form.Item>
          );
        })}
        <Form.Item label="GitHub 链接">
          <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
            <Form.Item name="githubLinkEnabled" valuePropName="checked" noStyle>
              <Switch checkedChildren="显示" unCheckedChildren="隐藏" />
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
        <Form.Item label="帮助链接">
          <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
            <Form.Item name="helpLinkEnabled" valuePropName="checked" noStyle>
              <Switch checkedChildren="显示" unCheckedChildren="隐藏" />
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
        <Form.Item name="footerIcp" label="Footer ICP">
          <Input />
        </Form.Item>
        <Form.Item name="footerCopyright" label="Footer 版权声明">
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>

      <Card title="预览">
        <Space direction="vertical" size={tagWrapGap} style={{ width: '100%' }}>
          <Typography.Title level={4} style={{ marginBottom: 0 }}>
            {previewState.websiteName}
          </Typography.Title>
          <Typography.Text type="secondary">{previewState.footerCopyright || '版权信息会显示在页面底部'}</Typography.Text>
        </Space>
      </Card>

      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="primary" loading={brandingSaving} disabled={!canUpdate} onClick={onSave}>
          保存设置
        </Button>
      </div>
    </Space>
  );
};
