import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Image, Input, Space, Typography, Upload } from 'antd';
import type { FormProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { BrandingSettings } from '@/types/api';

interface BrandingTabProps {
  formProps: FormProps;
  previewState: BrandingSettings;
  uploadingTarget: 'favicon' | 'logo' | 'watermark' | null;
  brandingSaving: boolean;
  onUpload: (target: 'favicon' | 'logo', file: File) => Promise<void>;
  onClearField: (field: 'websiteFaviconUrl' | 'websiteLogoUrl', label: string) => void;
  onSave: () => void;
}

export const BrandingTab = ({
  formProps,
  previewState,
  uploadingTarget,
  brandingSaving,
  onUpload,
  onClearField,
  onSave,
}: BrandingTabProps) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Form {...formProps}>
      <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>

      <Form.Item name="websiteFaviconUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="网站 Icon（本地上传）" extra="使用 antd Upload 上传后回填地址。">
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: 104 }} bodyStyle={{ padding: 12 }}>
            <div style={{ width: '100%', height: 72, display: 'grid', placeItems: 'center' }}>
              {previewState.websiteFaviconUrl ? (
                <Image width={72} height={72} preview={false} src={normalizeUploadUrl(previewState.websiteFaviconUrl)} style={{ objectFit: 'contain' }} />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未上传" />
              )}
            </div>
          </Card>
          <Space direction="vertical" size={8}>
            <ImgCrop
              modalTitle="裁切网站 Icon"
              rotationSlider
              aspect={1}
              beforeCrop={(file) => {
                const lowerName = file.name.toLowerCase();
                if (lowerName.endsWith('.ico') || file.type === 'image/x-icon' || file.type === 'image/vnd.microsoft.icon') {
                  return false;
                }
                return true;
              }}
            >
              <Upload
                accept="image/*,.ico"
                showUploadList={false}
                beforeUpload={async (file) => {
                  await onUpload('favicon', file);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'favicon'}>
                  上传 Icon
                </Button>
              </Upload>
            </ImgCrop>
            <Button icon={<DeleteOutlined />} onClick={() => onClearField('websiteFaviconUrl', '网站 Icon')} disabled={!previewState.websiteFaviconUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>

      <Form.Item name="websiteLogoUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="Logo（本地上传）" extra="Logo 会显示在顶部导航和登录页。">
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: 200 }} bodyStyle={{ padding: 12 }}>
            <div style={{ width: '100%', height: 72, display: 'grid', placeItems: 'center' }}>
              {previewState.websiteLogoUrl ? (
                <Image
                  width={180}
                  height={72}
                  preview={false}
                  src={normalizeUploadUrl(previewState.websiteLogoUrl)}
                  style={{ objectFit: 'contain' }}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未上传" />
              )}
            </div>
          </Card>
          <Space direction="vertical" size={8}>
            <ImgCrop modalTitle="裁切 Logo" rotationSlider aspect={25 / 9}>
              <Upload
                accept="image/*"
                showUploadList={false}
                beforeUpload={async (file) => {
                  await onUpload('logo', file);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'logo'}>
                  上传 Logo
                </Button>
              </Upload>
            </ImgCrop>
            <Button icon={<DeleteOutlined />} onClick={() => onClearField('websiteLogoUrl', 'Logo')} disabled={!previewState.websiteLogoUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>

      <Form.Item
        name="githubLinkUrl"
        label="GitHub 链接"
        extra="顶部 GitHub 图标点击后会跳转到这里，支持完整网址或以 / 开头的站内路径。"
      >
        <Input allowClear placeholder="https://github.com/your-org/your-repo" />
      </Form.Item>
      <Form.Item
        name="helpLinkUrl"
        label="帮助链接"
        extra="顶部帮助图标点击后会跳转到这里，支持完整网址或以 / 开头的站内路径。"
      >
        <Input allowClear placeholder="https://docs.example.com/help" />
      </Form.Item>
      <Form.Item name="footerIcp" label="Footer ICP">
        <Input allowClear />
      </Form.Item>
      <Form.Item name="footerCopyright" label="Footer 版权声明">
        <Input.TextArea rows={3} />
      </Form.Item>
    </Form>

    <Card title="预览">
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <Typography.Title level={4} style={{ marginBottom: 0 }}>
          {previewState.websiteName}
        </Typography.Title>
        <Typography.Text type="secondary">{previewState.footerCopyright || '版权信息会显示在页面底部'}</Typography.Text>
      </Space>
    </Card>

    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Button type="primary" loading={brandingSaving} onClick={onSave}>
        保存设置
      </Button>
    </div>
  </Space>
);
