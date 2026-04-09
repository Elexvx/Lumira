import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Watermark } from 'antd';
import { Button, Card, Form, Input, InputNumber, Segmented, Switch, Tabs, Typography, message } from 'antd';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { MarkdownEditor } from '@/components/MarkdownEditor';
import { LocalImageUploadField } from '@/components/LocalImageUploadField';
import { usePermission } from '@/hooks/usePermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import type { AgreementSettings, BrandingSettings, WatermarkSettings } from '@/types/api';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { history, useLocation } from 'umi';

type PersonalizationTabKey = 'branding' | 'watermark' | 'agreement';

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

const normalizeTabKey = (value?: string | null): PersonalizationTabKey =>
  value === 'watermark' ? 'watermark' : value === 'agreement' ? 'agreement' : 'branding';

const PersonalizationSettingsPage = () => {
  const [brandingForm] = Form.useForm<BrandingSettings>();
  const [watermarkForm] = Form.useForm<WatermarkSettings>();
  const [agreementForm] = Form.useForm<AgreementSettings>();
  const location = useLocation();
  const { initialState, setInitialState } = useInitialStateModel();
  const { canAccess } = usePermission();
  const canUpdate = canAccess('system:config:update');
  const [activeTab, setActiveTab] = useState<PersonalizationTabKey>(() => normalizeTabKey(new URLSearchParams(location.search).get('tab')));
  const [brandingSaving, setBrandingSaving] = useState(false);
  const [watermarkSaving, setWatermarkSaving] = useState(false);
  const [agreementSaving, setAgreementSaving] = useState(false);
  const [loading, setLoading] = useState(false);
  const [previewState, setPreviewState] = useState<BrandingSettings>(normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS));
  const [watermarkPreview, setWatermarkPreview] = useState<WatermarkSettings>(() => ({
    ...defaultWatermark,
    ...(initialState?.watermarkSettings || defaultWatermark),
    imageUrl: normalizeUploadUrl(initialState?.watermarkSettings?.imageUrl),
  }));
  const [agreementPreview, setAgreementPreview] = useState<AgreementSettings>(DEFAULT_AGREEMENT_SETTINGS);

  const updateTabInUrl = useCallback(
    (nextTab: PersonalizationTabKey) => {
      const searchParams = new URLSearchParams(location.search);
      searchParams.set('tab', nextTab);
      history.replace({
        pathname: location.pathname,
        search: `?${searchParams.toString()}`,
      });
    },
    [location.pathname, location.search],
  );

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const normalizedTab = normalizeTabKey(searchParams.get('tab'));
    setActiveTab(normalizedTab);
    if (searchParams.get('tab') !== normalizedTab) {
      updateTabInUrl(normalizedTab);
    }
  }, [location.search, updateTabInUrl]);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const [brandingResult, watermarkResult, agreementResult] = await Promise.all([
        systemService.brandingSettings({ autoRedirectOnUnauthorized: false, silent: true }),
        systemService.watermarkSettings({ autoRedirectOnUnauthorized: false, silent: true }),
        systemService.agreementSettings({ autoRedirectOnUnauthorized: false, silent: true }),
      ]);
      const normalizedBranding = normalizeBrandingSettings(brandingResult);
      const normalizedWatermark = {
        ...defaultWatermark,
        ...watermarkResult,
        imageUrl: normalizeUploadUrl(watermarkResult.imageUrl),
      };
      const normalizedAgreement = normalizeAgreementSettings(agreementResult);
      brandingForm.setFieldsValue(normalizedBranding);
      watermarkForm.setFieldsValue(normalizedWatermark);
      agreementForm.setFieldsValue(normalizedAgreement);
      setPreviewState(normalizedBranding);
      setWatermarkPreview(normalizedWatermark);
      setAgreementPreview(normalizedAgreement);
      persistBrandingSettings(normalizedBranding);
      setInitialState((prev) => (prev ? { ...prev, brandingSettings: normalizedBranding, watermarkSettings: normalizedWatermark } : prev));
    } finally {
      setLoading(false);
    }
  }, [agreementForm, brandingForm, setInitialState, watermarkForm]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings, initialState?.currentTenant?.tenantId]);

  const handleSaveBranding = async () => {
    if (!canUpdate) return;
    setBrandingSaving(true);
    try {
      const brandingValues = await brandingForm.validateFields();
      const updatedBranding = await systemService.updateBrandingSettings(normalizeBrandingSettings(brandingValues), { autoRedirectOnUnauthorized: false });
      brandingForm.setFieldsValue(updatedBranding);
      setInitialState((prev) => (prev ? { ...prev, brandingSettings: updatedBranding } : prev));
      setPreviewState(updatedBranding);
      persistBrandingSettings(updatedBranding);
      message.success('品牌设置已保存并即时生效');
    } finally {
      setBrandingSaving(false);
    }
  };

  const handleSaveWatermark = async () => {
    if (!canUpdate) return;
    setWatermarkSaving(true);
    try {
      const watermarkValues = await watermarkForm.validateFields();
      const updatedWatermark = await systemService.updateWatermarkSettings(
        {
          ...defaultWatermark,
          ...watermarkValues,
          imageUrl: normalizeUploadUrl(watermarkValues.imageUrl),
        },
        { autoRedirectOnUnauthorized: false },
      );
      watermarkForm.setFieldsValue(updatedWatermark);
      setInitialState((prev) => (prev ? { ...prev, watermarkSettings: updatedWatermark } : prev));
      setWatermarkPreview(updatedWatermark);
      message.success('水印设置已保存并即时生效');
    } finally {
      setWatermarkSaving(false);
    }
  };

  const handleSaveAgreement = async () => {
    if (!canUpdate) return;
    setAgreementSaving(true);
    try {
      const agreementValues = await agreementForm.validateFields();
      const updatedAgreement = await systemService.updateAgreementSettings(normalizeAgreementSettings(agreementValues), {
        autoRedirectOnUnauthorized: false,
      });
      agreementForm.setFieldsValue(updatedAgreement);
      setAgreementPreview(updatedAgreement);
      message.success('协议设置已保存并即时生效');
    } finally {
      setAgreementSaving(false);
    }
  };

  const handleClearAgreementField = (field: keyof AgreementSettings) => {
    const nextAgreement = {
      ...agreementPreview,
      [field]: '',
    };
    agreementForm.setFieldsValue(nextAgreement);
    setAgreementPreview(nextAgreement);
  };

  const wm = useMemo(() => ({ ...defaultWatermark, ...watermarkPreview }), [watermarkPreview]);

  useEffect(() => {
    applyFavicon(previewState.websiteFaviconUrl);
  }, [previewState.websiteFaviconUrl]);

  return (
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      title="个性化设置"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card loading={loading} bodyStyle={{ paddingTop: 8 }}>
          <Tabs
            activeKey={activeTab}
            destroyInactiveTabPane={false}
            onChange={(key) => {
              const nextTab = normalizeTabKey(key);
              setActiveTab(nextTab);
              updateTabInUrl(nextTab);
            }}
            items={[
              {
                key: 'branding',
                label: '品牌设置',
                children: (
                  <>
                    <Form form={brandingForm} layout="vertical" onValuesChange={(_, v) => setPreviewState(normalizeBrandingSettings(v))}>
                      <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                      <Form.Item name="websiteFaviconUrl" label="网站 Icon（本地上传）">
                        <LocalImageUploadField buttonText="上传 Icon" previewWidth={72} previewHeight={72} accept="image/*,.ico" />
                      </Form.Item>
                      <Form.Item name="websiteLogoUrl" label="Logo（本地上传）">
                        <LocalImageUploadField buttonText="上传 Logo" previewWidth={180} previewHeight={72} />
                      </Form.Item>
                      <Form.Item
                        name="githubLinkUrl"
                        label="GitHub 链接"
                        extra="顶部 GitHub 图标点击后会跳转到这里，支持完整网址或以 / 开头的站内路径。"
                      >
                        <Input allowClear placeholder="https://github.com/your-org/your-repo" />
                      </Form.Item>
                      <Form.Item
                        name="helpLinkUrl"
                        label="帮助链接"
                        extra="顶部帮助图标点击后会跳转到这里，支持完整网址或以 / 开头的站内路径。"
                      >
                        <Input allowClear placeholder="https://docs.example.com/help" />
                      </Form.Item>
                      <Form.Item name="footerIcp" label="Footer ICP">
                        <Input allowClear />
                      </Form.Item>
                      <Form.Item name="footerCopyright" label="Footer 版权声明">
                        <Input.TextArea rows={3} />
                      </Form.Item>
                    </Form>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 24 }}>
                      <Button type="primary" loading={brandingSaving} onClick={() => void handleSaveBranding()}>
                        保存设置
                      </Button>
                    </div>
                  </>
                ),
              },
              {
                key: 'watermark',
                label: '全局水印',
                children: (
                  <>
                    <Form form={watermarkForm} layout="vertical" onValuesChange={(_, v) => setWatermarkPreview({ ...defaultWatermark, ...v })} initialValues={wm}>
                      <Form.Item name="enabled" label="启用水印" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                      <Form.Item name="mode" label="模式">
                        <Segmented options={[{ label: '文字', value: 'TEXT' }, { label: '图片', value: 'IMAGE' }]} />
                      </Form.Item>
                      <Form.Item label="多行文字（每行一个）" name="textLines">
                        <SelectTextLines />
                      </Form.Item>
                      <Form.Item name="imageUrl" label="水印图片（本地上传）">
                        <LocalImageUploadField buttonText="上传水印图片" previewWidth={180} previewHeight={100} />
                      </Form.Item>
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
                    <Card title="预览" style={{ marginTop: 24 }}>
                      <Watermark content={wm.mode === 'TEXT' ? wm.textLines : undefined} image={wm.mode === 'IMAGE' ? normalizeUploadUrl(wm.imageUrl) : undefined}>
                        <div style={{ height: 180, display: 'grid', placeItems: 'center', background: '#fafafa' }}>
                          <Typography.Text>{previewState.websiteName}</Typography.Text>
                        </div>
                      </Watermark>
                    </Card>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 24 }}>
                      <Button type="primary" loading={watermarkSaving} onClick={() => void handleSaveWatermark()}>
                        保存设置
                      </Button>
                    </div>
                  </>
                ),
              },
              {
                key: 'agreement',
                label: '协议设置',
                children: (
                  <>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                      支持 Markdown 语法，登录页点击协议链接后会直接展示这里保存的内容。
                    </Typography.Paragraph>
                    <Form
                      form={agreementForm}
                      layout="vertical"
                      initialValues={DEFAULT_AGREEMENT_SETTINGS}
                      onValuesChange={(_, values) => setAgreementPreview(normalizeAgreementSettings(values))}
                    >
                      <Form.Item name="userAgreementMarkdown" label="用户协议">
                        <MarkdownEditor placeholder="请输入用户协议 Markdown 内容" height={360} style={{ marginBottom: 24 }} />
                      </Form.Item>
                      <Form.Item name="privacyAgreementMarkdown" label="隐私协议">
                        <MarkdownEditor placeholder="请输入隐私协议 Markdown 内容" height={360} />
                      </Form.Item>
                    </Form>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 24, flexWrap: 'wrap' }}>
                      <Button danger onClick={() => handleClearAgreementField('userAgreementMarkdown')}>
                        清空用户协议
                      </Button>
                      <Button danger onClick={() => handleClearAgreementField('privacyAgreementMarkdown')}>
                        清空隐私协议
                      </Button>
                      <Button type="primary" loading={agreementSaving} onClick={() => void handleSaveAgreement()}>
                        保存设置
                      </Button>
                    </div>
                  </>
                ),
              },
            ]}
          />
        </Card>
      </div>
    </PageContainer>
  );
};

const SelectTextLines = ({ value, onChange }: { value?: string[]; onChange?: (next: string[]) => void }) => (
  <Input.TextArea
    rows={3}
    value={(value || []).join('\n')}
    onChange={(e) => onChange?.(e.target.value.split('\n').map((item) => item.trim()).filter(Boolean))}
  />
);

export default PersonalizationSettingsPage;
