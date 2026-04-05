import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { Watermark } from 'antd';
import { Alert, Button, Card, Form, Input, InputNumber, Segmented, Space, Switch, Typography, message } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { usePermission } from '@/hooks/usePermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import type { BrandingSettings, WatermarkSettings } from '@/types/api';

const defaultWatermark: WatermarkSettings = {
  enabled: false,
  mode: 'TEXT',
  textLines: ['宏翔商道', '后台管理系统'],
  imageUrl: '',
  fontColor: 'rgba(0,0,0,0.15)',
  fontSize: 14,
  fontWeight: 'normal',
  rotate: -22,
  gapX: 100,
  gapY: 100,
  offsetX: 0,
  offsetY: 0,
  zIndex: 9,
  opacity: 0.15,
};

const PersonalizationSettingsPage = () => {
  const [brandingForm] = Form.useForm<BrandingSettings>();
  const [watermarkForm] = Form.useForm<WatermarkSettings>();
  const { initialState, setInitialState } = useInitialStateModel();
  const { canAccess } = usePermission();
  const canUpdate = canAccess('system:config:update');
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);
  const [previewState, setPreviewState] = useState<BrandingSettings>(normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS));
  const [watermarkPreview, setWatermarkPreview] = useState<WatermarkSettings>(initialState?.watermarkSettings || defaultWatermark);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const [brandingResult, watermarkResult] = await Promise.all([
        systemService.brandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
        systemService.watermarkSettings({ autoRedirectOnUnauthorized: false, silent: true }),
      ]);
      const normalizedBranding = normalizeBrandingSettings(brandingResult);
      brandingForm.setFieldsValue(normalizedBranding);
      watermarkForm.setFieldsValue(watermarkResult);
      setPreviewState(normalizedBranding);
      setWatermarkPreview(watermarkResult);
      persistBrandingSettings(normalizedBranding);
      applyFavicon(normalizedBranding.websiteFaviconUrl);
      setInitialState((prev) => prev ? { ...prev, brandingSettings: normalizedBranding, watermarkSettings: watermarkResult } : prev);
    } finally {
      setLoading(false);
    }
  }, [brandingForm, watermarkForm, setInitialState]);

  useEffect(() => { void loadSettings(); }, [loadSettings, initialState?.currentTenant?.tenantId]);

  const handleSave = async () => {
    if (!canUpdate) return;
    setSaving(true);
    try {
      const [brandingValues, watermarkValues] = await Promise.all([brandingForm.validateFields(), watermarkForm.validateFields()]);
      const [updatedBranding, updatedWatermark] = await Promise.all([
        systemService.updateBrandingSettings(normalizeBrandingSettings(brandingValues), { autoRedirectOnUnauthorized: false }),
        systemService.updateWatermarkSettings({ ...defaultWatermark, ...watermarkValues }, { autoRedirectOnUnauthorized: false }),
      ]);
      setInitialState((prev) => prev ? { ...prev, brandingSettings: updatedBranding, watermarkSettings: updatedWatermark } : prev);
      message.success('品牌与水印设置已保存并即时生效');
    } finally { setSaving(false); }
  };

  const wm = useMemo(() => ({ ...defaultWatermark, ...watermarkPreview }), [watermarkPreview]);

  return (
    <PageContainer className="saas-management-page saas-crud-page" ghost style={{ height: '100%', minHeight: 0 }} content={null}>
      <div className="saas-management-page-body">
        <Alert showIcon type="info" message="保存后将同步更新全局品牌与水印" />
        <Card className="saas-action-bar">
          <Space><Button type="primary" loading={saving} onClick={handleSave}>保存设置</Button><Button onClick={() => loadSettings()}>重新拉取</Button></Space>
        </Card>
        <ProCard split="vertical" gutter={16}>
          <ProCard title="品牌设置" loading={loading}>
            <Form form={brandingForm} layout="vertical" onValuesChange={(_, v) => setPreviewState(normalizeBrandingSettings(v))}>
              <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="websiteFaviconUrl" label="网站 Icon 地址"><Input allowClear /></Form.Item>
              <Form.Item name="websiteLogoUrl" label="Logo 地址"><Input allowClear /></Form.Item>
              <Form.Item name="footerIcp" label="Footer ICP"><Input allowClear /></Form.Item>
              <Form.Item name="footerCopyright" label="Footer 版权声明"><Input.TextArea rows={3} /></Form.Item>
            </Form>
          </ProCard>
          <ProCard title="全局水印" loading={loading}>
            <Form form={watermarkForm} layout="vertical" onValuesChange={(_, v) => setWatermarkPreview({ ...defaultWatermark, ...v })} initialValues={wm}>
              <Form.Item name="enabled" label="启用水印" valuePropName="checked"><Switch /></Form.Item>
              <Form.Item name="mode" label="模式"><Segmented options={[{ label: '文字', value: 'TEXT' }, { label: '图片', value: 'IMAGE' }]} /></Form.Item>
              <Form.Item label="多行文字（每行一个）" name="textLines"><SelectTextLines /></Form.Item>
              <Form.Item name="imageUrl" label="图片 URL"><Input /></Form.Item>
              <Form.Item name="fontColor" label="字体颜色"><Input /></Form.Item>
              <Form.Item name="fontSize" label="字号"><InputNumber min={10} max={48} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="gapX" label="横向间距"><InputNumber min={40} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="gapY" label="纵向间距"><InputNumber min={40} style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="rotate" label="旋转"><InputNumber style={{ width: '100%' }} /></Form.Item>
              <Form.Item name="opacity" label="透明度"><InputNumber min={0.05} max={1} step={0.05} style={{ width: '100%' }} /></Form.Item>
            </Form>
          </ProCard>
        </ProCard>
        <Card title="预览">
          <Watermark content={wm.mode === 'TEXT' ? wm.textLines : undefined} image={wm.mode === 'IMAGE' ? wm.imageUrl : undefined}>
            <div style={{ height: 180, display: 'grid', placeItems: 'center', background: '#fafafa' }}><Typography.Text>{previewState.websiteName}</Typography.Text></div>
          </Watermark>
        </Card>
      </div>
    </PageContainer>
  );
};

const SelectTextLines = ({ value, onChange }: { value?: string[]; onChange?: (next: string[]) => void }) => (
  <Input.TextArea rows={3} value={(value || []).join('\n')} onChange={(e) => onChange?.(e.target.value.split('\n').map((item) => item.trim()).filter(Boolean))} />
);

export default PersonalizationSettingsPage;
