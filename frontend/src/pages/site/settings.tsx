import { Button, Card, Empty, Form, Image, Input, Select, Space, Upload, message } from 'antd';
import { DeleteOutlined, SaveOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteSettings } from '@/services/site';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import SiteAdminPage from './SiteAdminPage';
import './site.css';

type SiteImageTarget = 'logo' | 'favicon';

const SITE_IMAGE_LIMIT = 5 * 1024 * 1024;

const isImageFile = (file: File) => file.type.startsWith('image/') || /\.(ico|png|jpe?g|svg|webp)$/i.test(file.name);

const SiteSettingsPage = () => {
  const [form] = Form.useForm<SiteSettings>();
  const [loading, setLoading] = useState(false);
  const [uploadingTarget, setUploadingTarget] = useState<SiteImageTarget | null>(null);
  const actionPermission = useActionPermission();
  const canUpdate = actionPermission.can(['site:settings:update', 'site:settings']);
  const logoUrl = Form.useWatch('logoUrl', form);
  const faviconUrl = Form.useWatch('faviconUrl', form);

  const load = async () => {
    setLoading(true);
    try {
      form.setFieldsValue(await siteService.settings());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const save = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      form.setFieldsValue(await siteService.updateSettings(values));
      message.success('站点设置已保存');
    } finally {
      setLoading(false);
    }
  };

  const uploadSiteImage = async (target: SiteImageTarget, file: File) => {
    if (!isImageFile(file)) {
      message.error('请上传图片文件');
      return;
    }
    if (file.size > SITE_IMAGE_LIMIT) {
      message.error('图片过大，请上传不超过 5MB 的文件');
      return;
    }

    setUploadingTarget(target);
    try {
      const uploaded = await siteService.uploadImage(file, 'settings');
      form.setFieldsValue(
        target === 'logo'
          ? { logoFileId: uploaded.id, logoUrl: normalizeUploadUrl(uploaded.publicUrl) }
          : { faviconFileId: uploaded.id, faviconUrl: normalizeUploadUrl(uploaded.publicUrl) },
      );
      message.success(target === 'logo' ? 'Logo 已上传' : 'Favicon 已上传');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '图片上传失败，请稍后重试');
    } finally {
      setUploadingTarget(null);
    }
  };

  const buildUploadProps = (target: SiteImageTarget): UploadProps => ({
    accept: target === 'favicon' ? 'image/*,.ico' : 'image/*',
    showUploadList: false,
    beforeUpload: async (file) => {
      await uploadSiteImage(target, file);
      return Upload.LIST_IGNORE;
    },
  });

  const clearSiteImage = (target: SiteImageTarget) => {
    form.setFieldsValue(
      target === 'logo'
        ? { logoFileId: undefined, logoUrl: undefined }
        : { faviconFileId: undefined, faviconUrl: undefined },
    );
  };

  const renderImageUpload = (
    target: SiteImageTarget,
    label: string,
    imageUrl: string | undefined,
  ) => {
    const isLogo = target === 'logo';
    const previewClassName = isLogo ? 'site-admin-image-card site-admin-image-card--logo' : 'site-admin-image-card site-admin-image-card--favicon';
    const imageWidth = isLogo ? 180 : 72;
    const imageHeight = isLogo ? 72 : 72;

    return (
      <Form.Item label={label}>
        <Space align="start" size={16} wrap>
          <Card size="small" className={previewClassName} bodyStyle={{ padding: 12, height: '100%' }}>
            <div className="site-admin-image-card__body">
              {imageUrl ? (
                <Image
                  width={imageWidth}
                  height={imageHeight}
                  preview={false}
                  src={normalizeUploadUrl(imageUrl)}
                  style={{ objectFit: 'contain' }}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未上传" />
              )}
            </div>
          </Card>
          <Space direction="vertical" size={8}>
            <Upload {...buildUploadProps(target)} disabled={loading || !canUpdate || uploadingTarget !== null}>
              <Button icon={<UploadOutlined />} loading={uploadingTarget === target}>
                {isLogo ? '上传 Logo' : '上传 Favicon'}
              </Button>
            </Upload>
            <Button
              icon={<DeleteOutlined />}
              onClick={() => clearSiteImage(target)}
              disabled={!imageUrl || loading || !canUpdate || uploadingTarget !== null}
            >
              清除
            </Button>
          </Space>
        </Space>
      </Form.Item>
    );
  };

  return (
    <SiteAdminPage
      title="站点设置"
      extra={canUpdate ? <Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={save}>
          保存
        </Button> : null}
    >
      <div className="site-admin-card">
        <Form form={form} layout="vertical" disabled={loading || !canUpdate} initialValues={{ code: 'main', name: '官网', status: 'ENABLED' }}>
          <Form.Item name="code" label="站点编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="站点名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="primaryDomain" label="主域名">
            <Input placeholder="www.example.com" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
          </Form.Item>
          <Form.Item name="logoFileId" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="logoUrl" hidden>
            <Input />
          </Form.Item>
          {renderImageUpload('logo', 'Logo（本地上传）', logoUrl)}
          <Form.Item name="faviconFileId" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="faviconUrl" hidden>
            <Input />
          </Form.Item>
          {renderImageUpload('favicon', 'Favicon（本地上传）', faviconUrl)}
          <Form.Item name="themeJson" label="主题配置 JSON">
            <Input.TextArea className="site-admin-json" rows={6} placeholder='{"primaryColor":"#111827"}' />
          </Form.Item>
          <Form.Item name="seoJson" label="SEO 默认配置 JSON">
            <Input.TextArea className="site-admin-json" rows={6} placeholder='{"title":"官网","description":"品牌官网"}' />
          </Form.Item>
        </Form>
      </div>
    </SiteAdminPage>
  );
};

export default SiteSettingsPage;
