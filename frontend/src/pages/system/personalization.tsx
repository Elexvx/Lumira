import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Watermark, Button, Card, Form, Image, Input, InputNumber, Segmented, Space, Switch, Tabs, Typography, Upload, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import MDEditor from '@uiw/react-md-editor';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { usePermission } from '@/hooks/usePermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import { confirmAction } from '@/utils/confirm';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import type { AgreementSettings, BrandingSettings, WatermarkSettings } from '@/types/api';

type PersonalizationTabKey = 'branding' | 'watermark' | 'agreement';
type UploadTarget = 'favicon' | 'logo' | 'watermark';
type BrandingClearField = 'websiteFaviconUrl' | 'websiteLogoUrl';

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
    ...DEFAULT_WATERMARK_SETTINGS,
    ...(initialState?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS),
    imageUrl: normalizeUploadUrl(initialState?.watermarkSettings?.imageUrl),
  }));
  const [uploadingTarget, setUploadingTarget] = useState<UploadTarget | null>(null);

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
        ...DEFAULT_WATERMARK_SETTINGS,
        ...watermarkResult,
        imageUrl: normalizeUploadUrl(watermarkResult.imageUrl),
      };
      const normalizedAgreement = normalizeAgreementSettings(agreementResult);
      brandingForm.setFieldsValue(normalizedBranding);
      watermarkForm.setFieldsValue(normalizedWatermark);
      agreementForm.setFieldsValue(normalizedAgreement);
      setPreviewState(normalizedBranding);
      setWatermarkPreview(normalizedWatermark);
      persistBrandingSettings(normalizedBranding);
      persistWatermarkSettings(normalizedWatermark);
      setInitialState((prev) => (prev ? { ...prev, brandingSettings: normalizedBranding, watermarkSettings: normalizedWatermark } : prev));
    } finally {
      setLoading(false);
    }
  }, [agreementForm, brandingForm, setInitialState, watermarkForm]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings, initialState?.currentTenant?.tenantId]);

  const handleUpload = useCallback(
    async (target: UploadTarget, file: File) => {
      setUploadingTarget(target);
      try {
        const uploadedUrl = await systemService.uploadImage(file, { autoRedirectOnUnauthorized: false });
        const normalizedUrl = normalizeUploadUrl(uploadedUrl);
        if (target === 'favicon') {
          brandingForm.setFieldValue('websiteFaviconUrl', normalizedUrl);
        } else if (target === 'logo') {
          brandingForm.setFieldValue('websiteLogoUrl', normalizedUrl);
        } else {
          watermarkForm.setFieldValue('imageUrl', normalizedUrl);
        }
        message.success('图片已上传');
      } finally {
        setUploadingTarget(null);
      }
    },
    [brandingForm, watermarkForm],
  );

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
      applyFavicon(updatedBranding.websiteFaviconUrl);
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
          ...DEFAULT_WATERMARK_SETTINGS,
          ...watermarkValues,
          imageUrl: normalizeUploadUrl(watermarkValues.imageUrl),
        },
        { autoRedirectOnUnauthorized: false },
      );
      watermarkForm.setFieldsValue(updatedWatermark);
      setInitialState((prev) => (prev ? { ...prev, watermarkSettings: updatedWatermark } : prev));
      setWatermarkPreview(updatedWatermark);
      persistWatermarkSettings(updatedWatermark);
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
      message.success('协议设置已保存并即时生效');
    } finally {
      setAgreementSaving(false);
    }
  };

  const handleClearBrandingField = (field: BrandingClearField, label: string) => {
    confirmAction({
      title: `清除${label}`,
      content: `确认清除${label}吗？清除后该内容会立即从当前设置中移除。`,
      okText: '确认清除',
      okButtonProps: { danger: true },
      onOk: () => {
        brandingForm.setFieldValue(field, '');
        setPreviewState((prev) => ({ ...prev, [field]: '' }));
      },
    });
  };

  const handleClearWatermarkImage = () => {
    confirmAction({
      title: '清除水印图片',
      content: '确认清除水印图片吗？清除后图片模式将不再使用当前图片。',
      okText: '确认清除',
      okButtonProps: { danger: true },
      onOk: () => {
        watermarkForm.setFieldValue('imageUrl', '');
        setWatermarkPreview((prev) => ({ ...prev, imageUrl: '' }));
      },
    });
  };

  const handleClearAgreementField = (field: keyof AgreementSettings) => {
    const fieldLabelMap: Record<keyof AgreementSettings, string> = {
      userAgreementMarkdown: '用户协议',
      privacyAgreementMarkdown: '隐私协议',
    };

    confirmAction({
      title: `清空${fieldLabelMap[field]}`,
      content: `确认清空${fieldLabelMap[field]}吗？清空后该内容会立即从当前设置中移除。`,
      okText: '确认清空',
      okButtonProps: { danger: true },
      onOk: () => {
        agreementForm.setFieldValue(field, '');
      },
    });
  };

  const wm = useMemo(() => ({ ...DEFAULT_WATERMARK_SETTINGS, ...watermarkPreview }), [watermarkPreview]);

  useEffect(() => {
    applyFavicon(previewState.websiteFaviconUrl);
  }, [previewState.websiteFaviconUrl]);

  return (
    <PageContainer className="saas-management-page saas-crud-page" ghost title="个性化设置" style={{ height: '100%', minHeight: 0 }} content={null}>
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
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Form
                      form={brandingForm}
                      layout="vertical"
                      onValuesChange={(_, allValues) => setPreviewState(normalizeBrandingSettings(allValues))}
                    >
                      <Form.Item name="websiteName" label="网站名称" rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>

                      <Form.Item name="websiteFaviconUrl" hidden>
                        <Input />
                      </Form.Item>
                      <Form.Item label="网站 Icon（本地上传）" extra="使用 antd Upload 上传后回填地址。">
                        <Space align="start" size={16} wrap>
                          <Card size="small" style={{ width: 104 }} bodyStyle={{ padding: 12 }}>
                            <div style={{ width: '100%', height: 72, display: 'grid', placeItems: 'center' }}>
                              {previewState.websiteFaviconUrl ? (
                                <Image
                                  width={72}
                                  height={72}
                                  preview={false}
                                  src={normalizeUploadUrl(previewState.websiteFaviconUrl)}
                                  style={{ objectFit: 'contain' }}
                                />
                              ) : (
                                <Typography.Text type="secondary">未上传</Typography.Text>
                              )}
                            </div>
                          </Card>
                          <Space direction="vertical" size={8}>
                            <Upload
                              accept="image/*,.ico"
                              showUploadList={false}
                              beforeUpload={async (file) => {
                                await handleUpload('favicon', file);
                                return Upload.LIST_IGNORE;
                              }}
                            >
                              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'favicon'}>
                                上传 Icon
                              </Button>
                            </Upload>
                            <Button
                              icon={<DeleteOutlined />}
                              onClick={() => handleClearBrandingField('websiteFaviconUrl', '网站 Icon')}
                              disabled={!previewState.websiteFaviconUrl}
                            >
                              清除
                            </Button>
                          </Space>
                        </Space>
                      </Form.Item>

                      <Form.Item name="websiteLogoUrl" hidden>
                        <Input />
                      </Form.Item>
                      <Form.Item label="Logo（本地上传）" extra="Logo 会显示在顶部导航和登录页。">
                        <Space align="start" size={16} wrap>
                          <Card size="small" style={{ width: 200 }} bodyStyle={{ padding: 12 }}>
                            <div style={{ width: '100%', height: 72, display: 'grid', placeItems: 'center' }}>
                              {previewState.websiteLogoUrl ? (
                                <Image
                                  width={180}
                                  height={72}
                                  preview={false}
                                  src={normalizeUploadUrl(previewState.websiteLogoUrl)}
                                  style={{ objectFit: 'contain' }}
                                />
                              ) : (
                                <Typography.Text type="secondary">未上传</Typography.Text>
                              )}
                            </div>
                          </Card>
                          <Space direction="vertical" size={8}>
                            <Upload
                              accept="image/*"
                              showUploadList={false}
                              beforeUpload={async (file) => {
                                await handleUpload('logo', file);
                                return Upload.LIST_IGNORE;
                              }}
                            >
                              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'logo'}>
                                上传 Logo
                              </Button>
                            </Upload>
                            <Button
                              icon={<DeleteOutlined />}
                              onClick={() => handleClearBrandingField('websiteLogoUrl', 'Logo')}
                              disabled={!previewState.websiteLogoUrl}
                            >
                              清除
                            </Button>
                          </Space>
                        </Space>
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

                    <Card title="预览">
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        <Typography.Title level={4} style={{ marginBottom: 0 }}>
                          {previewState.websiteName}
                        </Typography.Title>
                        <Typography.Text type="secondary">{previewState.footerCopyright || '版权信息会显示在页面底部'}</Typography.Text>
                      </Space>
                    </Card>

                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <Button type="primary" loading={brandingSaving} onClick={() => void handleSaveBranding()}>
                        保存设置
                      </Button>
                    </div>
                  </Space>
                ),
              },
              {
                key: 'watermark',
                label: '全局水印',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Form
                      form={watermarkForm}
                      layout="vertical"
                      onValuesChange={(_, allValues) =>
                        setWatermarkPreview({
                          ...DEFAULT_WATERMARK_SETTINGS,
                          ...allValues,
                          imageUrl: normalizeUploadUrl(allValues.imageUrl),
                        })
                      }
                    >
                      <Form.Item name="enabled" label="启用水印" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                      <Form.Item name="mode" label="模式">
                        <Segmented options={[{ label: '文字', value: 'TEXT' }, { label: '图片', value: 'IMAGE' }]} />
                      </Form.Item>
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

                      <Form.Item name="imageUrl" hidden>
                        <Input />
                      </Form.Item>
                      <Form.Item label="水印图片（本地上传）" extra="仅在图片模式下生效。">
                        <Space align="start" size={16} wrap>
                          <Card size="small" style={{ width: 200 }} bodyStyle={{ padding: 12 }}>
                            <div style={{ width: '100%', height: 100, display: 'grid', placeItems: 'center' }}>
                              {watermarkPreview.imageUrl ? (
                                <Image
                                  width={180}
                                  height={100}
                                  preview={false}
                                  src={normalizeUploadUrl(watermarkPreview.imageUrl)}
                                  style={{ objectFit: 'contain' }}
                                />
                              ) : (
                                <Typography.Text type="secondary">未上传</Typography.Text>
                              )}
                            </div>
                          </Card>
                          <Space direction="vertical" size={8}>
                            <Upload
                              accept="image/*"
                              showUploadList={false}
                              beforeUpload={async (file) => {
                                await handleUpload('watermark', file);
                                return Upload.LIST_IGNORE;
                              }}
                            >
                              <Button icon={<UploadOutlined />} loading={uploadingTarget === 'watermark'}>
                                上传水印图片
                              </Button>
                            </Upload>
                            <Button
                              icon={<DeleteOutlined />}
                              onClick={handleClearWatermarkImage}
                              disabled={!watermarkPreview.imageUrl}
                            >
                              清除
                            </Button>
                          </Space>
                        </Space>
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

                    <Card title="预览">
                      <Watermark
                        content={wm.mode === 'TEXT' ? wm.textLines : undefined}
                        image={wm.mode === 'IMAGE' ? normalizeUploadUrl(wm.imageUrl) : undefined}
                      >
                        <div style={{ height: 180, display: 'grid', placeItems: 'center', background: '#fafafa' }}>
                          <Typography.Text>{previewState.websiteName}</Typography.Text>
                        </div>
                      </Watermark>
                    </Card>

                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <Button type="primary" loading={watermarkSaving} onClick={() => void handleSaveWatermark()}>
                        保存设置
                      </Button>
                    </div>
                  </Space>
                ),
              },
              {
                key: 'agreement',
                label: '协议设置',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      使用 Markdown 编辑器编写协议内容，保存后会同步到登录页。
                    </Typography.Paragraph>
                    <Form
                      form={agreementForm}
                      layout="vertical"
                      initialValues={DEFAULT_AGREEMENT_SETTINGS}
                    >
                      <Form.Item name="userAgreementMarkdown" label="用户协议" getValueFromEvent={(value) => value ?? ''}>
                        <MDEditor
                          preview="edit"
                          height={320}
                          style={{ width: '100%' }}
                          textareaProps={{ placeholder: '请输入用户协议 Markdown 内容' }}
                          data-color-mode="light"
                        />
                      </Form.Item>
                      <Form.Item name="privacyAgreementMarkdown" label="隐私协议" getValueFromEvent={(value) => value ?? ''}>
                        <MDEditor
                          preview="edit"
                          height={320}
                          style={{ width: '100%' }}
                          textareaProps={{ placeholder: '请输入隐私协议 Markdown 内容' }}
                          data-color-mode="light"
                        />
                      </Form.Item>
                    </Form>

                    <Space wrap style={{ justifyContent: 'flex-end', width: '100%' }}>
                      <Button danger onClick={() => handleClearAgreementField('userAgreementMarkdown')}>
                        清空用户协议
                      </Button>
                      <Button danger onClick={() => handleClearAgreementField('privacyAgreementMarkdown')}>
                        清空隐私协议
                      </Button>
                      <Button type="primary" loading={agreementSaving} onClick={() => void handleSaveAgreement()}>
                        保存设置
                      </Button>
                    </Space>
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      </div>
    </PageContainer>
  );
};

export default PersonalizationSettingsPage;
