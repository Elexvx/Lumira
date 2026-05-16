import { Button, Form, Image, Input, InputNumber, Select, Space, Upload, message } from 'antd';
import { SaveOutlined, UploadOutlined } from '@ant-design/icons';
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
  const canUpdate = actionPermission.can('site:settings:update');
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
          <Form.Item label="Logo 文件 ID">
            <Space.Compact block>
              <Form.Item name="logoFileId" noStyle>
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Upload {...buildUploadProps('logo')} disabled={loading || !canUpdate || uploadingTarget !== null}>
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'logo'}>
                  上传 Logo
                </Button>
              </Upload>
            </Space.Compact>
          </Form.Item>
          <Form.Item name="logoUrl" hidden>
            <Input />
          </Form.Item>
          {logoUrl ? (
            <div className="site-admin-image-preview">
              <Image width={160} height={64} preview={false} src={normalizeUploadUrl(logoUrl)} style={{ objectFit: 'contain' }} />
            </div>
          ) : null}
          <Form.Item label="Favicon 文件 ID">
            <Space.Compact block>
              <Form.Item name="faviconFileId" noStyle>
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Upload {...buildUploadProps('favicon')} disabled={loading || !canUpdate || uploadingTarget !== null}>
                <Button icon={<UploadOutlined />} loading={uploadingTarget === 'favicon'}>
                  上传 Favicon
                </Button>
              </Upload>
            </Space.Compact>
          </Form.Item>
          <Form.Item name="faviconUrl" hidden>
            <Input />
          </Form.Item>
          {faviconUrl ? (
            <div className="site-admin-image-preview">
              <Image width={48} height={48} preview={false} src={normalizeUploadUrl(faviconUrl)} style={{ objectFit: 'contain' }} />
            </div>
          ) : null}
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
