import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Image, Input, Space, Switch, Typography, Upload } from 'antd';
import type { FormProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { BrandingSettings } from '@/types/api';

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

export const BrandingTab = ({
  formProps,
  previewState,
  uploadingTarget,
  brandingSaving,
  canUpdate,
  onUpload,
  onClearField,
  onSave,
}: BrandingTabProps) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Form {...formProps} disabled={!canUpdate}>
      <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>

      <Form.Item name="websiteFaviconUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="网站 Icon（本地上传）">
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: 104, height: 104 }} bodyStyle={{ padding: 12, height: '100%' }}>
            <div style={{ width: '100%', height: '100%', display: 'grid', placeItems: 'center' }}>
              {previewState.websiteFaviconUrl ? (
                <Image width={72} height={72} preview={false} src={normalizeUploadUrl(previewState.websiteFaviconUrl)} style={{ objectFit: 'contain' }} />
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={false}
                  styles={{
                    image: { height: 28, marginBottom: 0 },
                  }}
                />
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
                disabled={!canUpdate}
              >
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'favicon'} disabled={!canUpdate}>
                  上传 Icon
                </Button>
              </Upload>
            </ImgCrop>
            <Button icon={<DeleteOutlined />} onClick={() => onClearField('websiteFaviconUrl', '网站 Icon')} disabled={!canUpdate || !previewState.websiteFaviconUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>

      <Form.Item name="websiteLogoUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="Logo（本地上传）">
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
                disabled={!canUpdate}
              >
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'logo'} disabled={!canUpdate}>
                  上传 Logo
                </Button>
              </Upload>
            </ImgCrop>
            <Button icon={<DeleteOutlined />} onClick={() => onClearField('websiteLogoUrl', 'Logo')} disabled={!canUpdate || !previewState.websiteLogoUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>

      <Form.Item name="loginBackgroundUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="登录页背景图（本地上传）">
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: 280 }} bodyStyle={{ padding: 12 }}>
            <div style={{ width: '100%', aspectRatio: '16 / 9', display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
              {previewState.loginBackgroundUrl ? (
                <Image
                  width="100%"
                  height="100%"
                  preview={false}
                  src={normalizeUploadUrl(previewState.loginBackgroundUrl)}
                  style={{ objectFit: 'cover' }}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未上传" />
              )}
            </div>
          </Card>
          <Space direction="vertical" size={8}>
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={async (file) => {
                await onUpload('loginBackground', file);
                return Upload.LIST_IGNORE;
              }}
              disabled={!canUpdate}
            >
              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'loginBackground'} disabled={!canUpdate}>
                上传背景图
              </Button>
            </Upload>
            <Button icon={<DeleteOutlined />} onClick={() => onClearField('loginBackgroundUrl', '登录页背景图')} disabled={!canUpdate || !previewState.loginBackgroundUrl}>
              清除
            </Button>
            <Typography.Text type="secondary">建议上传 16:9 或更宽的图片，登录页会自动铺满并裁切。</Typography.Text>
          </Space>
        </Space>
      </Form.Item>

      <Form.Item label="GitHub 链接">
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Form.Item name="githubLinkEnabled" valuePropName="checked" noStyle>
            <Switch checkedChildren="显示" unCheckedChildren="隐藏" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.githubLinkEnabled !== next.githubLinkEnabled}>
            {({ getFieldValue }) => (
              <Form.Item name="githubLinkUrl" noStyle>
                <Input allowClear disabled={!getFieldValue('githubLinkEnabled')} placeholder="https://github.com/your-org/your-repo" />
              </Form.Item>
            )}
          </Form.Item>
        </Space>
      </Form.Item>
      <Form.Item label="帮助链接">
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Form.Item name="helpLinkEnabled" valuePropName="checked" noStyle>
            <Switch checkedChildren="显示" unCheckedChildren="隐藏" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.helpLinkEnabled !== next.helpLinkEnabled}>
            {({ getFieldValue }) => (
              <Form.Item name="helpLinkUrl" noStyle>
                <Input allowClear disabled={!getFieldValue('helpLinkEnabled')} placeholder="https://docs.example.com/help" />
              </Form.Item>
            )}
          </Form.Item>
        </Space>
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
      <Button type="primary" loading={brandingSaving} disabled={!canUpdate} onClick={onSave}>
        保存设置
      </Button>
    </div>
  </Space>
);
