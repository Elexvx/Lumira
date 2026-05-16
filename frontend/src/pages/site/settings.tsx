import { Button, Form, Input, Select, message } from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { siteService, type SiteSettings } from '@/services/site';
import SiteAdminPage from './SiteAdminPage';
import './site.css';

const SiteSettingsPage = () => {
  const [form] = Form.useForm<SiteSettings>();
  const [loading, setLoading] = useState(false);
  const actionPermission = useActionPermission();
  const canUpdate = actionPermission.can('site:settings:update');

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

  return (
    <SiteAdminPage
      title="站点设置"
      description="管理官网基础信息、域名、品牌素材和 SEO 默认配置。"
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
          <Form.Item name="logoFileId" label="Logo 文件 ID">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="faviconFileId" label="Favicon 文件 ID">
            <Input type="number" />
          </Form.Item>
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
