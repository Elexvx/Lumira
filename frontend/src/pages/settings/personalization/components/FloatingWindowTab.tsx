import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Image, Input, Space, Switch, Typography, Upload } from 'antd';
import type { FormProps } from 'antd';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { FloatingWindowSettings } from '@/types/api';

interface FloatingWindowTabProps {
  formProps: FormProps;
  preview: FloatingWindowSettings;
  uploadingTarget: 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr' | null;
  saving: boolean;
  onUpload: (target: 'floatingQr', file: File) => Promise<void>;
  onClearQrImage: () => void;
  onSave: () => void;
}

export const FloatingWindowTab = ({ formProps, preview, uploadingTarget, saving, onUpload, onClearQrImage, onSave }: FloatingWindowTabProps) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Form {...formProps}>
      <Form.Item name="apiDocsQrEnabled" label="接口文档二维码" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="apiDocsQrTitle" label="弹窗标题" rules={[{ required: true, message: '请输入弹窗标题' }]}>
        <Input maxLength={30} placeholder="微信扫码联系我们" />
      </Form.Item>
      <Form.Item name="apiDocsQrImageUrl" hidden>
        <Input />
      </Form.Item>
      <Form.Item label="二维码图片" extra="用于接口文档悬浮按钮展开后的二维码弹窗。">
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: 220 }} bodyStyle={{ padding: 12 }}>
            <div style={{ width: '100%', height: 180, display: 'grid', placeItems: 'center' }}>
              {preview.apiDocsQrImageUrl ? (
                <Image width={180} height={180} preview={false} src={normalizeUploadUrl(preview.apiDocsQrImageUrl)} style={{ objectFit: 'contain' }} />
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
                await onUpload('floatingQr', file);
                return Upload.LIST_IGNORE;
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'floatingQr'}>
                上传二维码
              </Button>
            </Upload>
            <Button icon={<DeleteOutlined />} onClick={onClearQrImage} disabled={!preview.apiDocsQrImageUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>
    </Form>

    <Card title="悬浮窗预览">
      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <div
          style={{
            width: 280,
            border: '1px solid rgba(0,0,0,0.08)',
            borderRadius: 16,
            padding: 16,
            textAlign: 'center',
            boxShadow: '0 20px 45px rgba(15,23,42,0.12)',
          }}
        >
          <Typography.Text type="secondary">{preview.apiDocsQrTitle || '微信扫码联系我们'}</Typography.Text>
          <div style={{ height: 220, marginTop: 12, display: 'grid', placeItems: 'center' }}>
            {preview.apiDocsQrImageUrl ? (
              <Image width={220} height={220} preview={false} src={normalizeUploadUrl(preview.apiDocsQrImageUrl)} style={{ objectFit: 'contain' }} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未上传二维码" />
            )}
          </div>
        </div>
      </div>
    </Card>

    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Button type="primary" loading={saving} onClick={onSave}>
        保存设置
      </Button>
    </div>
  </Space>
);
