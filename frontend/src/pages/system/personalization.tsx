import { history, useLocation } from '@umijs/max';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Form, Tabs, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, applyFavicon, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import { confirmAction } from '@/utils/confirm';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { DEFAULT_WATERMARK_SETTINGS, persistWatermarkSettings } from '@/watermark/settings';
import { AgreementTab } from '@/pages/system/personalization/components/AgreementTab';
import { BrandingTab } from '@/pages/system/personalization/components/BrandingTab';
import { WatermarkTab } from '@/pages/system/personalization/components/WatermarkTab';
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
  const actionPermission = useActionPermission();
  const canUpdate = actionPermission.can('system:config:update');
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
          setPreviewState((prev) => ({ ...prev, websiteFaviconUrl: normalizedUrl }));
        } else if (target === 'logo') {
          brandingForm.setFieldValue('websiteLogoUrl', normalizedUrl);
          setPreviewState((prev) => ({ ...prev, websiteLogoUrl: normalizedUrl }));
        } else {
          watermarkForm.setFieldValue('imageUrl', normalizedUrl);
          setWatermarkPreview((prev) => ({ ...prev, imageUrl: normalizedUrl }));
        }
        message.success('图片已上传');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '图片上传失败，请稍后重试');
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

  const brandingFormProps = useStandardFormProps({
    form: brandingForm,
    onValuesChange: (_, allValues) => setPreviewState(normalizeBrandingSettings(allValues)),
  });
  const watermarkFormProps = useStandardFormProps({
    form: watermarkForm,
    onValuesChange: (_, allValues) =>
      setWatermarkPreview({
        ...DEFAULT_WATERMARK_SETTINGS,
        ...allValues,
        imageUrl: normalizeUploadUrl(allValues.imageUrl),
      }),
  });
  const agreementFormProps = useStandardFormProps({
    form: agreementForm,
    initialValues: DEFAULT_AGREEMENT_SETTINGS,
  });

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
                  <BrandingTab
                    formProps={brandingFormProps}
                    previewState={previewState}
                    uploadingTarget={uploadingTarget}
                    brandingSaving={brandingSaving}
                    onUpload={(target, file) => handleUpload(target, file)}
                    onClearField={handleClearBrandingField}
                    onSave={() => void handleSaveBranding()}
                  />
                ),
              },
              {
                key: 'watermark',
                label: '全局水印',
                children: (
                  <WatermarkTab
                    formProps={watermarkFormProps}
                    watermarkPreview={watermarkPreview}
                    previewState={previewState}
                    uploadingTarget={uploadingTarget}
                    watermarkSaving={watermarkSaving}
                    onUpload={(target, file) => handleUpload(target, file)}
                    onClearWatermarkImage={handleClearWatermarkImage}
                    onSave={() => void handleSaveWatermark()}
                  />
                ),
              },
              {
                key: 'agreement',
                label: '协议设置',
                children: (
                  <AgreementTab
                    formProps={agreementFormProps}
                    agreementSaving={agreementSaving}
                    onClearUserAgreement={() => handleClearAgreementField('userAgreementMarkdown')}
                    onClearPrivacyAgreement={() => handleClearAgreementField('privacyAgreementMarkdown')}
                    onSave={() => void handleSaveAgreement()}
                  />
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
