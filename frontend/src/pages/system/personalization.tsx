import { useCallback, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Form, Input, Space, Typography, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { usePermission } from '@/hooks/usePermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import type { BrandingSettings } from '@/types/api';

const PersonalizationSettingsPage = () => {
  const [form] = Form.useForm<BrandingSettings>();
  const { initialState, setInitialState } = useInitialStateModel();
  const { canAccess } = usePermission();
  const canUpdate = canAccess('system:config:update');
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);
  const [previewState, setPreviewState] = useState<BrandingSettings>(
    normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
  );

  const loadBrandingSettings = useCallback(async () => {
    setLoading(true);
    try {
      const result = normalizeBrandingSettings(
        await systemService.brandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
      );
      form.setFieldsValue(result);
      setPreviewState(result);
      persistBrandingSettings(result);
      applyFavicon(result.websiteFaviconUrl);
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              brandingSettings: result,
            }
          : prev,
      );
    } finally {
      setLoading(false);
    }
  }, [form, setInitialState]);

  useEffect(() => {
    void loadBrandingSettings();
  }, [loadBrandingSettings, initialState?.currentTenant?.tenantId]);

  const previewSettings = normalizeBrandingSettings(previewState || initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);

  const handleResetDefaults = () => {
    form.setFieldsValue(DEFAULT_BRANDING_SETTINGS);
    setPreviewState(DEFAULT_BRANDING_SETTINGS);
  };

  const handleSave = async () => {
    if (!canUpdate) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      const normalized = normalizeBrandingSettings(values);
      const updated = normalizeBrandingSettings(
        await systemService.updateBrandingSettings(normalized, { autoRedirectOnUnauthorized: false }),
      );
      form.setFieldsValue(updated);
      setPreviewState(updated);
      persistBrandingSettings(updated);
      applyFavicon(updated.websiteFaviconUrl);
      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              brandingSettings: updated,
            }
          : prev,
      );
      message.success('个性化设置已保存并立即生效');
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="个性化设置"
      subTitle="统一管理站点名称、浏览器 icon、左上角 logo 与页脚展示信息。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Alert
          showIcon
          type="info"
          message="保存后会立即影响当前控制台展示"
          description="建议使用可长期访问的 HTTPS 图片地址作为 icon 与 logo，避免浏览器缓存或跨域加载失败。"
        />

        <Card className="saas-action-bar">
          <Space wrap style={{ width: '100%', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Space>
              {canUpdate ? (
                <Button type="primary" loading={saving} onClick={handleSave}>
                  保存设置
                </Button>
              ) : null}
              <Button onClick={handleResetDefaults}>恢复默认</Button>
            </Space>
            <Button onClick={() => loadBrandingSettings()}>重新拉取</Button>
          </Space>
        </Card>

        <Card className="saas-crud-form-card" loading={loading}>
          <Form
            form={form}
            layout="vertical"
            initialValues={initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS}
            onValuesChange={(_, allValues) => {
              setPreviewState(normalizeBrandingSettings(allValues));
            }}
          >
            <Form.Item
              name="websiteName"
              label="网站名称"
              rules={[{ required: true, whitespace: true, message: '请输入网站名称' }]}
            >
              <Input maxLength={40} placeholder="例如：宏翔商道" />
            </Form.Item>
            <Form.Item
              name="websiteFaviconUrl"
              label="网站 Icon 地址"
              rules={[{ type: 'url', warningOnly: true, message: '建议填写有效的 URL 地址' }]}
            >
              <Input allowClear placeholder="例如：https://example.com/favicon.ico" />
            </Form.Item>
            <Form.Item
              name="websiteLogoUrl"
              label="左上角 Logo 地址"
              rules={[{ type: 'url', warningOnly: true, message: '建议填写有效的 URL 地址' }]}
            >
              <Input allowClear placeholder="例如：https://example.com/logo.png" />
            </Form.Item>
            <Form.Item name="footerIcp" label="Footer ICP 备案">
              <Input allowClear maxLength={120} placeholder="例如：粤ICP备12345678号-1" />
            </Form.Item>
            <Form.Item name="footerCopyright" label="Footer 版权声明">
              <Input.TextArea rows={3} maxLength={220} placeholder="例如：Copyright © 2026 宏翔商道 All Rights Reserved" />
            </Form.Item>
          </Form>
        </Card>

        <Card className="saas-crud-info-card" title="实时预览">
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              {previewSettings.websiteLogoUrl ? (
                <img
                  src={previewSettings.websiteLogoUrl}
                  alt={previewSettings.websiteName}
                  style={{ width: 32, height: 32, objectFit: 'contain', borderRadius: 6, border: '1px solid #e8ecf5', padding: 2 }}
                />
              ) : (
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: 6,
                    display: 'grid',
                    placeItems: 'center',
                    background: '#eaf2ff',
                    color: '#1d4ed8',
                    fontWeight: 600,
                  }}
                >
                  LOGO
                </div>
              )}
              <Typography.Title level={5} style={{ margin: 0 }}>
                {previewSettings.websiteName}
              </Typography.Title>
            </div>
            <div>
              <Typography.Text type="secondary">页脚备案：</Typography.Text>
              <Typography.Text>{previewSettings.footerIcp || '（未设置）'}</Typography.Text>
            </div>
            <div>
              <Typography.Text type="secondary">页脚版权：</Typography.Text>
              <Typography.Text>{previewSettings.footerCopyright || '（未设置）'}</Typography.Text>
            </div>
          </Space>
        </Card>
      </div>
    </PageContainer>
  );
};

export default PersonalizationSettingsPage;
