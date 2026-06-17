import { history, useLocation } from '@umijs/max';
import { Button, Card, Form, Space, Tabs } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { useCallback, useEffect, useState } from 'react';
import type { AppInitialState } from '@/app.types';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { applyFavicon, DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
import { normalizeAgreementSettings } from '@/agreement/settings';
import { normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import { queryClient } from '@/query/queryClient';
import { request } from '@/services/common/request';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import { DEFAULT_FLOATING_WINDOW_SETTINGS } from '@/floatingWindow/settings';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { persistWatermarkSettings } from '@/watermark/settingsStorage';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { AgreementMarkdownEditor } from './personalization/components/AgreementMarkdownEditor';
import { BrandingTab } from './personalization/components/BrandingTab';
import { FloatingWindowTab } from './personalization/components/FloatingWindowTab';
import { WatermarkTab } from './personalization/components/WatermarkTab';
import { DEFAULT_AGREEMENT_SETTINGS } from '@/agreement/settings';
import type { AgreementSettings, BrandingSettings, FileStorageSpaceRecord, FloatingWindowSettings, PagedResult, WatermarkSettings } from '@/types/api';
import { useStandardFormProps } from '@/features/form/config';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type PersonalizationTabKey = 'branding' | 'watermark' | 'floating' | 'agreement';

const normalizeTabKey = (value?: string | null): PersonalizationTabKey =>
  value === 'watermark' || value === 'floating' || value === 'agreement' ? value : 'branding';

type UploadTarget = 'favicon' | 'logo' | 'loginBackground' | 'watermark' | 'floatingQr';

const DEFAULT_IMAGE_UPLOAD_SIZE_MB = 20;
const FLOATING_WINDOW_SETTINGS_QUERY_KEY = ['floating-window-settings'] as const;

type RuntimeAppearanceSettingsResponse = {
  brandingSettings?: BrandingSettings;
  watermarkSettings?: WatermarkSettings;
  floatingWindowSettings?: FloatingWindowSettings;
};

const isAllowedImageFile = (target: UploadTarget, file: File) => {
  const lowerName = file.name.toLowerCase();
  const isIcoFile = lowerName.endsWith('.ico') || file.type === 'image/x-icon' || file.type === 'image/vnd.microsoft.icon';
  const isImageFile = file.type.startsWith('image/');

  if (target === 'favicon') {
    return isIcoFile || isImageFile;
  }

  return isImageFile;
};

const getPersonalizationImageUploadErrorMessage = (target: UploadTarget) =>
  target === 'favicon' ? t('请上传图片或 .ico 文件', 'Please upload an image or .ico file') : t('请上传图片文件', 'Please upload an image file');

const applyPersonalizationImageUpload = ({
  target,
  normalizedUrl,
  brandingForm,
  watermarkForm,
  floatingForm,
  setPreviewState,
  setWatermarkPreview,
  setFloatingPreview,
}: {
  target: UploadTarget;
  normalizedUrl: string;
  brandingForm: {
    setFieldValue: (field: keyof BrandingSettings, value: unknown) => void;
  };
  watermarkForm: {
    setFieldValue: (field: keyof WatermarkSettings, value: unknown) => void;
  };
  floatingForm: {
    setFieldValue: (field: keyof FloatingWindowSettings, value: unknown) => void;
  };
  setPreviewState: (updater: (prev: BrandingSettings) => BrandingSettings) => void;
  setWatermarkPreview: (updater: (prev: WatermarkSettings) => WatermarkSettings) => void;
  setFloatingPreview: (updater: (prev: FloatingWindowSettings) => FloatingWindowSettings) => void;
}) => {
  if (target === 'favicon') {
    brandingForm.setFieldValue('websiteFaviconUrl', normalizedUrl);
    setPreviewState((prev) => ({ ...prev, websiteFaviconUrl: normalizedUrl }));
    return;
  }

  if (target === 'logo') {
    brandingForm.setFieldValue('websiteLogoUrl', normalizedUrl);
    setPreviewState((prev) => ({ ...prev, websiteLogoUrl: normalizedUrl }));
    return;
  }

  if (target === 'loginBackground') {
    brandingForm.setFieldValue('loginBackgroundUrl', normalizedUrl);
    setPreviewState((prev) => ({ ...prev, loginBackgroundUrl: normalizedUrl }));
    return;
  }

  if (target === 'watermark') {
    watermarkForm.setFieldValue('imageUrl', normalizedUrl);
    setWatermarkPreview((prev) => ({ ...prev, imageUrl: normalizedUrl }));
    return;
  }

  floatingForm.setFieldValue('apiDocsQrImageUrl', normalizedUrl);
  setFloatingPreview((prev) => ({ ...prev, apiDocsQrImageUrl: normalizedUrl }));
};

const PersonalizationSettingsPage = () => {
  const location = useLocation();
  const { initialState, setInitialState } = useInitialStateModel();
  const actionPermission = useActionPermission();
  const { isMobile } = useResponsive();
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);
  const cardPaddingTop = resolveResponsiveValue(APP_SPACING.tagWrapGap, isMobile)[0];
  const [loading, setLoading] = useState(false);
  const [brandingForm] = Form.useForm<BrandingSettings>();
  const [watermarkForm] = Form.useForm<WatermarkSettings>();
  const [floatingForm] = Form.useForm<FloatingWindowSettings>();
  const [agreementForm] = Form.useForm<AgreementSettings>();
  const [agreementSaving, setAgreementSaving] = useState(false);
  const [brandingSaving, setBrandingSaving] = useState(false);
  const [watermarkSaving, setWatermarkSaving] = useState(false);
  const [floatingSaving, setFloatingSaving] = useState(false);
  const [activeTab, setActiveTab] = useState<PersonalizationTabKey>(() => normalizeTabKey(new URLSearchParams(location.search).get('tab')));
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
  const canUpdate = actionPermission.can('system:config:update');
  const agreementFormProps = useStandardFormProps({
    form: agreementForm,
    initialValues: DEFAULT_AGREEMENT_SETTINGS,
  });
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
  const floatingFormProps = useStandardFormProps({
    form: floatingForm,
    initialValues: DEFAULT_FLOATING_WINDOW_SETTINGS,
    onValuesChange: (_, allValues) => setFloatingPreview(normalizeFloatingWindowSettings(allValues)),
  });
  const [previewState, setPreviewState] = useState<BrandingSettings>(
    normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
  );
  const [watermarkPreview, setWatermarkPreview] = useState<WatermarkSettings>(() => ({
    ...DEFAULT_WATERMARK_SETTINGS,
    ...(initialState?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS),
    imageUrl: normalizeUploadUrl(initialState?.watermarkSettings?.imageUrl),
  }));
  const [floatingPreview, setFloatingPreview] = useState<FloatingWindowSettings>(DEFAULT_FLOATING_WINDOW_SETTINGS);
  const [uploadingTarget, setUploadingTarget] = useState<UploadTarget | null>(null);
  const [imageUploadSizeMb, setImageUploadSizeMb] = useState(DEFAULT_IMAGE_UPLOAD_SIZE_MB);

  useEffect(() => {
    applyFavicon(previewState.websiteFaviconUrl);
  }, [previewState.websiteFaviconUrl]);

  useEffect(() => {
    let ignore = false;
    request<PagedResult<FileStorageSpaceRecord>>('/v1/files/storage-spaces', {
      params: { pageNo: 1, pageSize: 100 },
      ...API_OPTS.NO_REDIRECT,
    })
      .then((result) => {
        if (ignore) {
          return;
        }
        const defaultStorage = result.records.find((record) => record.defaultStorage) || result.records[0];
        setImageUploadSizeMb(defaultStorage?.maxFileSizeMb || DEFAULT_IMAGE_UPLOAD_SIZE_MB);
      })
      .catch(() => {
        if (!ignore) {
          setImageUploadSizeMb(DEFAULT_IMAGE_UPLOAD_SIZE_MB);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const handleUpload = useCallback(
    async (target: UploadTarget, file: File) => {
      if (!canUpdate) {
        return;
      }
      if (!isAllowedImageFile(target, file)) {
        message.error(getPersonalizationImageUploadErrorMessage(target));
        return;
      }
      const imageUploadSizeBytes = imageUploadSizeMb * 1024 * 1024;
      if (file.size > imageUploadSizeBytes) {
        message.error(
          t(
            `图片过大，请上传不超过 ${imageUploadSizeMb}MB 的文件`,
            `The image is too large. Please upload a file under ${imageUploadSizeMb}MB.`,
          ),
        );
        return;
      }

      setUploadingTarget(target);
      try {
        const formData = new FormData();
        formData.append('file', file);
        const uploadedUrl = await request<string>('/v1/system/uploads/image', {
          method: 'POST',
          headers: {},
          data: formData,
          ...API_OPTS.NO_REDIRECT,
        });
        const normalizedUrl = normalizeUploadUrl(uploadedUrl);
        applyPersonalizationImageUpload({
          target,
          normalizedUrl,
          brandingForm,
          watermarkForm,
          floatingForm,
          setPreviewState,
          setWatermarkPreview,
          setFloatingPreview,
        });
        message.success(t('图片已上传', 'Image uploaded'));
      } catch (error) {
        showErrorMessage(error, t('图片上传失败，请稍后重试', 'Image upload failed. Please try again later.'));
      } finally {
        setUploadingTarget(null);
      }
    },
    [brandingForm, canUpdate, floatingForm, imageUploadSizeMb, setFloatingPreview, setPreviewState, setWatermarkPreview, watermarkForm],
  );

  const handleClearBrandingField = useCallback(
    (field: 'websiteFaviconUrl' | 'websiteLogoUrl' | 'loginBackgroundUrl', label: string) => {
      confirmAction({
        title: t('清除{label}', 'Clear {label}').replace('{label}', label),
        content: t(`确认清除${label}吗？清除后该内容会立即从当前设置中移除。`, `Clear ${label}? This will remove it from the current settings immediately.`),
        okText: t('确认清除', 'Clear'),
        okButtonProps: { danger: true },
        onOk: () => {
          brandingForm.setFieldValue(field, '');
          setPreviewState((prev) => ({ ...prev, [field]: '' }));
        },
      });
    },
    [brandingForm],
  );
  const handleClearWatermarkImage = useCallback(() => {
    confirmAction({
      title: t('清除水印图片', 'Clear watermark image'),
      content: t('确认清除水印图片吗？清除后图片模式将不再使用当前图片。', 'Clear the watermark image? Image mode will no longer use the current image.'),
      okText: t('确认清除', 'Clear'),
      okButtonProps: { danger: true },
      onOk: () => {
        watermarkForm.setFieldValue('imageUrl', '');
        setWatermarkPreview((prev) => ({ ...prev, imageUrl: '' }));
      },
    });
  }, [watermarkForm]);
  const handleClearFloatingQrImage = useCallback(() => {
    confirmAction({
      title: t('清除二维码图片', 'Clear QR image'),
      content: t('确认清除二维码图片吗？清除后接口文档悬浮按钮将展示未配置提示。', 'Clear the QR image? The API docs floating button will then show the unconfigured hint.'),
      okText: t('确认清除', 'Clear'),
      okButtonProps: { danger: true },
      onOk: () => {
        floatingForm.setFieldValue('apiDocsQrImageUrl', '');
        setFloatingPreview((prev) => ({ ...prev, apiDocsQrImageUrl: '' }));
      },
    });
  }, [floatingForm]);
  const handleSaveBranding = useCallback(async () => {
    if (!canUpdate) return;
    setBrandingSaving(true);
    try {
      const brandingValues = await brandingForm.validateFields();
      const updatedBranding = await request<BrandingSettings>('/v1/system/branding-settings', {
        method: 'PUT',
        data: normalizeBrandingSettings(brandingValues),
        ...API_OPTS.NO_REDIRECT,
      });
      brandingForm.setFieldsValue(updatedBranding);
      setInitialState((prev: AppInitialState | undefined) => (prev ? { ...prev, brandingSettings: updatedBranding } : prev));
      setPreviewState(updatedBranding);
      persistBrandingSettings(updatedBranding);
      message.success(t('品牌设置已保存并即时生效', 'Branding settings saved and applied immediately'));
    } catch (error) {
      showErrorMessage(error, t('品牌设置保存失败，请稍后重试', 'Failed to save branding settings. Please try again later.'));
    } finally {
      setBrandingSaving(false);
    }
  }, [brandingForm, canUpdate, setInitialState, setPreviewState]);
  const handleSaveWatermark = useCallback(async () => {
    if (!canUpdate) return;
    setWatermarkSaving(true);
    try {
      const watermarkValues = await watermarkForm.validateFields();
      const resolvedMode = watermarkValues.mode === 'IMAGE' && !watermarkValues.imageUrl ? 'TEXT' : watermarkValues.mode;
      if (resolvedMode !== watermarkValues.mode) {
        message.warning(t('未上传水印图片时，将自动回退为文字水印', 'When no watermark image is uploaded, the system will fall back to a text watermark.'));
      }
      const updatedWatermark = await request<WatermarkSettings>('/v1/system/watermark-settings', {
        method: 'PUT',
        data: {
          ...DEFAULT_WATERMARK_SETTINGS,
          ...watermarkValues,
          mode: resolvedMode,
          imageUrl: normalizeUploadUrl(watermarkValues.imageUrl),
        },
        ...API_OPTS.NO_REDIRECT,
      });
      watermarkForm.setFieldsValue(updatedWatermark);
      setInitialState((prev: AppInitialState | undefined) => (prev ? { ...prev, watermarkSettings: updatedWatermark } : prev));
      setWatermarkPreview(updatedWatermark);
      persistWatermarkSettings(updatedWatermark);
      message.success(t('水印设置已保存并即时生效', 'Watermark settings saved and applied immediately'));
    } catch (error) {
      showErrorMessage(error, t('水印设置保存失败，请稍后重试', 'Failed to save watermark settings. Please try again later.'));
    } finally {
      setWatermarkSaving(false);
    }
  }, [canUpdate, setInitialState, setWatermarkPreview, watermarkForm]);
  const handleSaveFloating = useCallback(async () => {
    if (!canUpdate) return;
    setFloatingSaving(true);
    try {
      const floatingValues = await floatingForm.validateFields();
      const updatedFloating = normalizeFloatingWindowSettings(
        await request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
          method: 'PUT',
          data: normalizeFloatingWindowSettings(floatingValues),
          autoRedirectOnUnauthorized: false,
        }),
      );
      floatingForm.setFieldsValue(updatedFloating);
      setFloatingPreview(updatedFloating);
      queryClient.setQueryData(FLOATING_WINDOW_SETTINGS_QUERY_KEY, updatedFloating);
      void queryClient.invalidateQueries({ queryKey: FLOATING_WINDOW_SETTINGS_QUERY_KEY });
      message.success(t('悬浮窗设置已保存并即时生效', 'Floating window settings saved and applied immediately'));
    } catch (error) {
      showErrorMessage(error, t('悬浮窗设置保存失败，请稍后重试', 'Failed to save floating window settings. Please try again later.'));
    } finally {
      setFloatingSaving(false);
    }
  }, [canUpdate, floatingForm, setFloatingPreview]);
  const handleSaveAgreement = useCallback(async () => {
    if (!canUpdate) return;
    setAgreementSaving(true);
    try {
      const agreementValues = await agreementForm.validateFields();
      const updatedAgreement = await request<AgreementSettings>('/v1/system/agreement-settings', {
        method: 'PUT',
        data: normalizeAgreementSettings(agreementValues),
        autoRedirectOnUnauthorized: false,
      });
      agreementForm.setFieldsValue(updatedAgreement);
      message.success(t('协议设置已保存并即时生效', 'Agreement settings saved and applied immediately'));
    } catch (error) {
      showErrorMessage(error, t('协议设置保存失败，请稍后重试', 'Failed to save agreement settings. Please try again later.'));
    } finally {
      setAgreementSaving(false);
    }
  }, [agreementForm, canUpdate]);
  const handleClearAgreementField = useCallback(
    (field: keyof AgreementSettings) => {
      const fieldLabelMap: Record<keyof AgreementSettings, string> = {
        userAgreementMarkdown: t('用户协议', 'User agreement'),
        privacyAgreementMarkdown: t('隐私协议', 'Privacy agreement'),
      };

      confirmAction({
        title: t('清空{label}', 'Clear {label}').replace('{label}', fieldLabelMap[field]),
        content: t(`确认清空${fieldLabelMap[field]}吗？清空后该内容会立即从当前设置中移除。`, `Clear ${fieldLabelMap[field]}? This will remove it from the current settings immediately.`),
        okText: t('确认清空', 'Clear'),
        okButtonProps: { danger: true },
        onOk: () => {
          agreementForm.setFieldValue(field, '');
        },
      });
    },
    [agreementForm],
  );

  const loadSettings = useCallback(async (isActive: () => boolean) => {
      setLoading(true);
    try {
      const [appearanceResult, agreementResult] = await Promise.all([
        request<RuntimeAppearanceSettingsResponse>('/v1/system/runtime-appearance-settings', {
          method: 'GET',
          ...API_OPTS.SILENT_NO_REDIRECT,
        }).catch(async () => {
          const [brandingSettings, watermarkSettings, floatingWindowSettings] = await Promise.all([
            request<BrandingSettings>('/v1/system/branding-settings', {
              method: 'GET',
              ...API_OPTS.SILENT_NO_REDIRECT,
            }),
            request<WatermarkSettings>('/v1/system/watermark-settings', {
              method: 'GET',
              ...API_OPTS.SILENT_NO_REDIRECT,
            }),
            request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
              method: 'GET',
              ...API_OPTS.SILENT_NO_REDIRECT,
            }),
          ]);
          return { brandingSettings, watermarkSettings, floatingWindowSettings };
        }),
        request<AgreementSettings>('/v1/system/agreement-settings', {
          method: 'GET',
          ...API_OPTS.SILENT_NO_REDIRECT,
        }),
      ]);

      const normalizedBranding = normalizeBrandingSettings(appearanceResult.brandingSettings || DEFAULT_BRANDING_SETTINGS);
      const watermarkResult = appearanceResult.watermarkSettings || DEFAULT_WATERMARK_SETTINGS;
      const normalizedWatermark = {
        ...DEFAULT_WATERMARK_SETTINGS,
        ...watermarkResult,
        imageUrl: normalizeUploadUrl(watermarkResult.imageUrl),
      };
      const normalizedFloating = normalizeFloatingWindowSettings(appearanceResult.floatingWindowSettings || DEFAULT_FLOATING_WINDOW_SETTINGS);
      const normalizedAgreement = normalizeAgreementSettings(agreementResult);

      if (!isActive()) {
        return;
      }

      brandingForm.setFieldsValue(normalizedBranding);
      watermarkForm.setFieldsValue(normalizedWatermark);
      floatingForm.setFieldsValue(normalizedFloating);
      agreementForm.setFieldsValue(normalizedAgreement);
      setPreviewState(normalizedBranding);
      setWatermarkPreview(normalizedWatermark);
      setFloatingPreview(normalizedFloating);
      queryClient.setQueryData(FLOATING_WINDOW_SETTINGS_QUERY_KEY, normalizedFloating);
      persistWatermarkSettings(normalizedWatermark);
      setInitialState((prev: AppInitialState | undefined) =>
        prev ? { ...prev, brandingSettings: normalizedBranding, watermarkSettings: normalizedWatermark } : prev,
      );
    } catch (error) {
      if (isActive()) {
        showErrorMessage(error, t('基础设置加载失败', 'Failed to load base settings'));
      }
    } finally {
      if (isActive()) {
        setLoading(false);
      }
    }
  }, [
    agreementForm,
    brandingForm,
    floatingForm,
    setFloatingPreview,
    setInitialState,
    setPreviewState,
    setWatermarkPreview,
    watermarkForm,
  ]);

  useEffect(() => {
    let active = true;
    void loadSettings(() => active);
    return () => {
      active = false;
    };
  }, [loadSettings]);

  return (
    <ManagementPage className="saas-crud-page" ghost title={t('个性化设置', 'Personalization settings')} content={null}>
      <ManagementPageBody>
        <Card loading={loading} bodyStyle={{ paddingTop: cardPaddingTop }}>
          <Tabs
            activeKey={activeTab}
            destroyInactiveTabPane={false}
            onChange={(key) => {
              setActiveTab(key as typeof activeTab);
              updateTabInUrl(key as typeof activeTab);
            }}
            items={[
              {
                key: 'branding',
                label: t('品牌设置', 'Branding'),
                children: (
                  <BrandingTab
                    formProps={brandingFormProps}
                    previewState={previewState}
                    uploadingTarget={uploadingTarget}
                    brandingSaving={brandingSaving}
                    canUpdate={canUpdate}
                    onUpload={(target, file) => handleUpload(target, file)}
                    onClearField={handleClearBrandingField}
                    onSave={() => void handleSaveBranding()}
                  />
                ),
              },
              {
                key: 'watermark',
                label: t('全局水印', 'Watermark'),
                children: (
                  <WatermarkTab
                    formProps={watermarkFormProps}
                    watermarkPreview={watermarkPreview}
                    previewState={previewState}
                    uploadingTarget={uploadingTarget}
                    watermarkSaving={watermarkSaving}
                    canUpdate={canUpdate}
                    onUpload={(target, file) => handleUpload(target, file)}
                    onClearWatermarkImage={handleClearWatermarkImage}
                    onSave={() => void handleSaveWatermark()}
                  />
                ),
              },
              {
                key: 'floating',
                label: t('悬浮窗设置', 'Floating window'),
                children: (
                  <FloatingWindowTab
                    formProps={floatingFormProps}
                    preview={floatingPreview}
                    uploadingTarget={uploadingTarget}
                    saving={floatingSaving}
                    canUpdate={canUpdate}
                    onUpload={(target, file) => handleUpload(target, file)}
                    onClearQrImage={handleClearFloatingQrImage}
                    onSave={() => void handleSaveFloating()}
                  />
                ),
              },
              {
                key: 'agreement',
                label: t('协议设置', 'Agreements'),
                children: (
                  <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                    <Form {...agreementFormProps} disabled={!canUpdate}>
                      <Form.Item name="userAgreementMarkdown" label={t('用户协议', 'User agreement')} getValueFromEvent={(value) => value ?? ''}>
                        <AgreementMarkdownEditor placeholder={t('请输入用户协议 Markdown 内容', 'Enter the user agreement Markdown content')} />
                      </Form.Item>
                      <Form.Item name="privacyAgreementMarkdown" label={t('隐私协议', 'Privacy agreement')} getValueFromEvent={(value) => value ?? ''}>
                        <AgreementMarkdownEditor placeholder={t('请输入隐私协议 Markdown 内容', 'Enter the privacy agreement Markdown content')} />
                      </Form.Item>
                    </Form>

                    <Space wrap style={{ justifyContent: 'flex-start', width: '100%' }}>
                      <Button danger disabled={!canUpdate} onClick={() => handleClearAgreementField('userAgreementMarkdown')}>
                        {t('清空用户协议', 'Clear user agreement')}
                      </Button>
                      <Button danger disabled={!canUpdate} onClick={() => handleClearAgreementField('privacyAgreementMarkdown')}>
                        {t('清空隐私协议', 'Clear privacy agreement')}
                      </Button>
                      <Button type="primary" loading={agreementSaving} disabled={!canUpdate} onClick={() => void handleSaveAgreement()}>
                        {t('保存设置', 'Save settings')}
                      </Button>
                    </Space>
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default PersonalizationSettingsPage;
