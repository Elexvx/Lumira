import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Image, Input, Space, Switch, Upload } from 'antd';
import type { FormProps } from 'antd';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { FloatingWindowSettings } from '@/types/api';

const FLOATING_QR_PREVIEW_CARD_WIDTH = 180;
const FLOATING_QR_PREVIEW_CONTENT_SIZE = 132;

interface FloatingWindowTabProps {
  formProps: FormProps;
  preview: FloatingWindowSettings;
  uploadingTarget: 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr' | null;
  saving: boolean;
  canUpdate: boolean;
  onUpload: (target: 'floatingQr', file: File) => Promise<void>;
  onClearQrImage: () => void;
  onSave: () => void;
}

export const FloatingWindowTab = ({ formProps, preview, uploadingTarget, saving, canUpdate, onUpload, onClearQrImage, onSave }: FloatingWindowTabProps) => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
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
        <Space align="start" size={16} wrap>
          <Card size="small" style={{ width: FLOATING_QR_PREVIEW_CARD_WIDTH }} bodyStyle={{ padding: 12 }}>
            <div style={{ width: '100%', height: FLOATING_QR_PREVIEW_CONTENT_SIZE, display: 'grid', placeItems: 'center' }}>
              {preview.apiDocsQrImageUrl ? (
                <Image
                  width={FLOATING_QR_PREVIEW_CONTENT_SIZE}
                  height={FLOATING_QR_PREVIEW_CONTENT_SIZE}
                  preview={false}
                  src={normalizeUploadUrl(preview.apiDocsQrImageUrl)}
                  style={{ objectFit: 'contain' }}
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
                await onUpload('floatingQr', file);
                return Upload.LIST_IGNORE;
              }}
              disabled={!canUpdate}
            >
              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'floatingQr'} disabled={!canUpdate}>
                上传二维码
              </Button>
            </Upload>
            <Button icon={<DeleteOutlined />} onClick={onClearQrImage} disabled={!canUpdate || !preview.apiDocsQrImageUrl}>
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>
    </Form>

    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Button type="primary" loading={saving} disabled={!canUpdate} onClick={onSave}>
        保存设置
      </Button>
    </div>
  </Space>
);
