import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Image, Input, InputNumber, Segmented, Space, Switch, Typography, Upload, Watermark, theme } from 'antd';
import type { FormProps } from 'antd';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import type { BrandingSettings, WatermarkSettings } from '@/types/api';

interface WatermarkTabProps {
  formProps: FormProps;
  watermarkPreview: WatermarkSettings;
  previewState: BrandingSettings;
  uploadingTarget: 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr' | null;
  watermarkSaving: boolean;
  canUpdate: boolean;
  onUpload: (target: 'watermark', file: File) => Promise<void>;
  onClearWatermarkImage: () => void;
  onSave: () => void;
}

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
  const wm = watermarkPreview;
  const isImageMode = wm.mode === 'IMAGE';
  const { token } = theme.useToken();

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...formProps} disabled={!canUpdate}>
        <Form.Item name="enabled" label="启用水印" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="mode" label="模式">
          <Segmented options={[{ label: '文字', value: 'TEXT' }, { label: '图片', value: 'IMAGE' }]} />
        </Form.Item>
        {!isImageMode ? (
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
        {isImageMode ? (
          <Form.Item label="水印图片（本地上传）">
            <Space align="start" size={16} wrap>
              <Card size="small" style={{ width: 200 }} bodyStyle={{ padding: 12 }}>
                <div style={{ width: '100%', height: 100, display: 'grid', placeItems: 'center' }}>
                  {watermarkPreview.imageUrl ? (
                    <Image width={180} height={100} preview={false} src={normalizeUploadUrl(watermarkPreview.imageUrl)} style={{ objectFit: 'contain' }} />
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
                    await onUpload('watermark', file);
                    return Upload.LIST_IGNORE;
                  }}
                  disabled={!canUpdate}
                >
                  <Button icon={<UploadOutlined />} loading={uploadingTarget === 'watermark'} disabled={!canUpdate}>
                    上传水印图片
                  </Button>
                </Upload>
                <Button icon={<DeleteOutlined />} onClick={onClearWatermarkImage} disabled={!canUpdate || !watermarkPreview.imageUrl}>
                  清除
                </Button>
              </Space>
            </Space>
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
        <Watermark content={wm.mode === 'TEXT' ? wm.textLines : undefined} image={wm.mode === 'IMAGE' ? normalizeUploadUrl(wm.imageUrl) : undefined}>
          <div
            style={{
              height: 180,
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
