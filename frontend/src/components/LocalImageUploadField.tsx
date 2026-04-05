import { UploadOutlined } from '@ant-design/icons';
import { Button, Space, Typography, Upload, message } from 'antd';
import { useState } from 'react';
import { systemService } from '@/services/system';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

export interface LocalImageUploadFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  buttonText: string;
  accept?: string;
  previewWidth?: number;
  previewHeight?: number;
  hint?: string;
  maxSizeBytes?: number;
}

const DEFAULT_MAX_SIZE_BYTES = 5 * 1024 * 1024;

export const LocalImageUploadField = ({
  value,
  onChange,
  buttonText,
  accept = 'image/*',
  previewWidth = 160,
  previewHeight = 72,
  hint = '仅支持本地图片文件上传，上传后会自动回填地址。',
  maxSizeBytes = DEFAULT_MAX_SIZE_BYTES,
}: LocalImageUploadFieldProps) => {
  const [uploading, setUploading] = useState(false);

  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      <Space align="start" size={16} wrap>
        <div
          style={{
            width: previewWidth,
            height: previewHeight,
            border: '1px dashed #d9d9d9',
            borderRadius: 8,
            background: '#fafafa',
            overflow: 'hidden',
            display: 'grid',
            placeItems: 'center',
            flexShrink: 0,
          }}
        >
          {value ? (
            <img
              src={normalizeUploadUrl(value)}
              alt=""
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'contain',
                display: 'block',
              }}
            />
          ) : (
            <Typography.Text type="secondary">未上传</Typography.Text>
          )}
        </div>
        <Space direction="vertical" size={4}>
          <Upload
            accept={accept}
            showUploadList={false}
            beforeUpload={async (file) => {
              const lowerName = file.name.toLowerCase();
              const looksLikeImage = file.type.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg|ico)$/i.test(lowerName);
              if (!looksLikeImage) {
                message.error('只支持图片文件');
                return Upload.LIST_IGNORE;
              }
              if (file.size > maxSizeBytes) {
                message.error(`图片不能超过 ${formatSize(maxSizeBytes)}`);
                return Upload.LIST_IGNORE;
              }

              setUploading(true);
              try {
                const uploadedUrl = await systemService.uploadImage(file, { autoRedirectOnUnauthorized: false });
                onChange?.(normalizeUploadUrl(uploadedUrl));
                message.success('图片已上传');
              } finally {
                setUploading(false);
              }
              return Upload.LIST_IGNORE;
            }}
          >
            <Button icon={<UploadOutlined />} loading={uploading}>
              {buttonText}
            </Button>
          </Upload>
          {value ? (
            <Button type="link" danger style={{ paddingInline: 0 }} onClick={() => onChange?.('')}>
              清除
            </Button>
          ) : null}
        </Space>
      </Space>
      <Typography.Text type="secondary">{hint}</Typography.Text>
    </Space>
  );
};

const formatSize = (bytes: number) => {
  if (bytes >= 1024 * 1024) {
    return `${Math.round(bytes / (1024 * 1024))}MB`;
  }
  if (bytes >= 1024) {
    return `${Math.round(bytes / 1024)}KB`;
  }
  return `${bytes}B`;
};
