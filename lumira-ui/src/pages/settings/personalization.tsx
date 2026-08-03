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
import { applyBrandingRuntime, applyFavicon, DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings, persistBrandingSettings } from '@/branding/settings';
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
import { MaintenanceTab } from './personalization/components/MaintenanceTab';
import { WatermarkTab } from './personalization/components/WatermarkTab';
import { buildBrandingAssetSettings, isBrandingAssetTarget } from './personalization/brandingAssetSettings';
import { DEFAULT_AGREEMENT_SETTINGS } from '@/agreement/settings';
import type { AgreementSettings, BrandingSettings, FileStorageSpaceRecord, FloatingWindowSettings, PagedResult, WatermarkSettings } from '@/types/api';
import { useStandardFormProps } from '@/features/form/config';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

type PersonalizationTabKey = 'branding' | 'maintenance' | 'watermark' | 'floating' | 'agreement';

const normalizeTabKey = (value?: string | null): PersonalizationTabKey =>
  value === 'maintenance' || value === 'watermark' || value === 'floating' || value === 'agreement' ? value : 'branding';

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
  target === 'favicon' ? t('ui.settings.personalization.pleaseUploadAnImageOrIcoFile') : t('ui.settings.personalization.pleaseUploadAnImageFile');

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

const applyPersonalizationImagePreview = ({
  target,
  previewUrl,
  setPreviewState,
  setWatermarkPreview,
  setFloatingPreview,
}: {
  target: UploadTarget;
  previewUrl: string;
  setPreviewState: (updater: (prev: BrandingSettings) => BrandingSettings) => void;
  setWatermarkPreview: (updater: (prev: WatermarkSettings) => WatermarkSettings) => void;
  setFloatingPreview: (updater: (prev: FloatingWindowSettings) => FloatingWindowSettings) => void;
}) => {
  if (target === 'favicon') {
    setPreviewState((prev) => ({ ...prev, websiteFaviconUrl: previewUrl }));
    return;
  }

  if (target === 'logo') {
    setPreviewState((prev) => ({ ...prev, websiteLogoUrl: previewUrl }));
    return;
  }

  if (target === 'loginBackground') {
    setPreviewState((prev) => ({ ...prev, loginBackgroundUrl: previewUrl }));
    return;
  }

  if (target === 'watermark') {
    setWatermarkPreview((prev) => ({ ...prev, imageUrl: previewUrl }));
    return;
  }

  setFloatingPreview((prev) => ({ ...prev, apiDocsQrImageUrl: previewUrl }));
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
  const [maintenanceForm] = Form.useForm<BrandingSettings>();
  const [agreementSaving, setAgreementSaving] = useState(false);
  const [brandingSaving, setBrandingSaving] = useState(false);
  const [watermarkSaving, setWatermarkSaving] = useState(false);
  const [floatingSaving, setFloatingSaving] = useState(false);
  const [maintenanceSaving, setMaintenanceSaving] = useState(false);
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
    onValuesChange: (_, allValues) => setPreviewState((current) => normalizeBrandingSettings({ ...current, ...allValues })),
  });
  const maintenanceFormProps = useStandardFormProps({
    form: maintenanceForm,
    onValuesChange: (_, allValues) => setPreviewState((current) => normalizeBrandingSettings({ ...current, ...allValues })),
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

  const commitBrandingSettings = useCallback(
    async (brandingValues: BrandingSettings) => {
      const updatedBranding = await request<BrandingSettings>('/v1/system/branding-settings', {
        method: 'PUT',
        data: normalizeBrandingSettings(brandingValues),
        ...API_OPTS.NO_REDIRECT,
      });
      const normalizedBranding = normalizeBrandingSettings(updatedBranding);
      brandingForm.setFieldsValue(normalizedBranding);
      maintenanceForm.setFieldsValue(normalizedBranding);
      setInitialState((prev: AppInitialState | undefined) =>
        prev ? { ...prev, brandingSettings: normalizedBranding, brandingRevision: (prev.brandingRevision ?? 0) + 1 } : prev,
      );
      setPreviewState(normalizedBranding);
      persistBrandingSettings(normalizedBranding);
      applyBrandingRuntime(normalizedBranding);
      return normalizedBranding;
    },
    [brandingForm, maintenanceForm, setInitialState],
  );

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
          t('ui.settings.personalization.theImageIsTooLargePleaseUploadA', { imageUploadSizeMb: imageUploadSizeMb }),
        );
        return;
      }

      setUploadingTarget(target);
      const previousBranding = normalizeBrandingSettings({ ...previewState, ...brandingForm.getFieldsValue(true) });
      const previousWatermark = watermarkForm.getFieldsValue(true);
      const previousFloating = floatingForm.getFieldsValue(true);
      const localPreviewUrl = URL.createObjectURL(file);
      applyPersonalizationImagePreview({
        target,
        previewUrl: localPreviewUrl,
        setPreviewState,
        setWatermarkPreview,
        setFloatingPreview,
      });
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
        if (isBrandingAssetTarget(target)) {
          await commitBrandingSettings(buildBrandingAssetSettings(previousBranding, target, normalizedUrl));
        }
        message.success(
          isBrandingAssetTarget(target)
            ? t('ui.settings.personalization.imageUploadedAndAppliedImmediately')
            : t('ui.settings.personalization.imageUploaded'),
        );
      } catch (error) {
        brandingForm.setFieldsValue(previousBranding);
        watermarkForm.setFieldsValue(previousWatermark);
        floatingForm.setFieldsValue(previousFloating);
        setPreviewState(normalizeBrandingSettings(previousBranding));
        setWatermarkPreview((prev) => ({ ...prev, imageUrl: normalizeUploadUrl(previousWatermark.imageUrl) }));
        setFloatingPreview(normalizeFloatingWindowSettings(previousFloating));
        showErrorMessage(error, t('ui.settings.personalization.imageUploadFailedPleaseTryAgainLater'));
      } finally {
        window.setTimeout(() => URL.revokeObjectURL(localPreviewUrl), 30_000);
        setUploadingTarget(null);
      }
    },
    [
      brandingForm,
      canUpdate,
      commitBrandingSettings,
      floatingForm,
      imageUploadSizeMb,
      setFloatingPreview,
      setPreviewState,
      setWatermarkPreview,
      watermarkForm,
      previewState,
    ],
  );

  const handleClearBrandingField = useCallback(
    (field: 'websiteFaviconUrl' | 'websiteLogoUrl' | 'loginBackgroundUrl', label: string) => {
      confirmAction({
        title: t('ui.settings.personalization.clear').replace('{label}', label),
        content: t('ui.settings.personalization.clearThisWillRemoveItFromTheCurrent', { label: label }),
        okText: t('ui.settings.personalization.clear.6c1a5b98'),
        okButtonProps: { danger: true },
        onOk: async () => {
          const previousBranding = normalizeBrandingSettings({ ...previewState, ...brandingForm.getFieldsValue(true) });
          const nextBranding = normalizeBrandingSettings({ ...previousBranding, [field]: '' });
          brandingForm.setFieldValue(field, '');
          setPreviewState((prev) => ({ ...prev, [field]: '' }));
          try {
            await commitBrandingSettings(nextBranding);
            message.success(t('ui.settings.personalization.brandingImageClearedAndAppliedImmediately'));
          } catch (error) {
            brandingForm.setFieldsValue(previousBranding);
            setPreviewState(previousBranding);
            showErrorMessage(error, t('ui.settings.personalization.failedToClearTheBrandingImagePleaseTry'));
          }
        },
      });
    },
    [brandingForm, commitBrandingSettings, previewState],
  );
  const handleClearWatermarkImage = useCallback(() => {
    confirmAction({
      title: t('ui.settings.personalization.clearWatermarkImage'),
      content: t('ui.settings.personalization.clearTheWatermarkImageImageModeWillNo'),
      okText: t('ui.settings.personalization.clear.6c1a5b98'),
      okButtonProps: { danger: true },
      onOk: () => {
        watermarkForm.setFieldValue('imageUrl', '');
        setWatermarkPreview((prev) => ({ ...prev, imageUrl: '' }));
      },
    });
  }, [watermarkForm]);
  const handleClearFloatingQrImage = useCallback(() => {
    confirmAction({
      title: t('ui.settings.personalization.clearQrImage'),
      content: t('ui.settings.personalization.clearTheQrImageTheApiDocsFloating'),
      okText: t('ui.settings.personalization.clear.6c1a5b98'),
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
      await brandingForm.validateFields();
      const brandingValues = { ...previewState, ...brandingForm.getFieldsValue(true) };
      await commitBrandingSettings(normalizeBrandingSettings(brandingValues));
      message.success(t('ui.settings.personalization.brandingSettingsSavedAndAppliedImmediately'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.personalization.failedToSaveBrandingSettingsPleaseTryAgain'));
    } finally {
      setBrandingSaving(false);
    }
  }, [brandingForm, canUpdate, commitBrandingSettings, previewState]);
  const handleSaveMaintenance = useCallback(async () => {
    if (!canUpdate) return;
    setMaintenanceSaving(true);
    try {
      const maintenanceValues = await maintenanceForm.validateFields();
      const brandingValues = normalizeBrandingSettings({
        ...previewState,
        ...brandingForm.getFieldsValue(true),
        ...maintenanceValues,
      });
      await commitBrandingSettings(brandingValues);
      message.success(t('ui.settings.personalization.maintenanceSettingsSavedAndAppliedImmediately'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.personalization.failedToSaveMaintenanceSettingsPleaseTryAgain'));
    } finally {
      setMaintenanceSaving(false);
    }
  }, [brandingForm, canUpdate, commitBrandingSettings, maintenanceForm, previewState]);
  const handleSaveWatermark = useCallback(async () => {
    if (!canUpdate) return;
    setWatermarkSaving(true);
    try {
      const watermarkValues = await watermarkForm.validateFields();
      const resolvedMode = watermarkValues.mode === 'IMAGE' && !watermarkValues.imageUrl ? 'TEXT' : watermarkValues.mode;
      if (resolvedMode !== watermarkValues.mode) {
        message.warning(t('ui.settings.personalization.whenNoWatermarkImageIsUploadedTheSystem'));
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
      message.success(t('ui.settings.personalization.watermarkSettingsSavedAndAppliedImmediately'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.personalization.failedToSaveWatermarkSettingsPleaseTryAgain'));
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
      message.success(t('ui.settings.personalization.floatingWindowSettingsSavedAndAppliedImmediately'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.personalization.failedToSaveFloatingWindowSettingsPleaseTry'));
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
      const normalizedAgreement = normalizeAgreementSettings(updatedAgreement);
      agreementForm.setFieldsValue(normalizedAgreement);
      setInitialState((prev: AppInitialState | undefined) => (prev ? { ...prev, agreementSettings: normalizedAgreement } : prev));
      message.success(t('ui.settings.personalization.agreementSettingsSavedAndAppliedImmediately'));
    } catch (error) {
      showErrorMessage(error, t('ui.settings.personalization.failedToSaveAgreementSettingsPleaseTryAgain'));
    } finally {
      setAgreementSaving(false);
    }
  }, [agreementForm, canUpdate, setInitialState]);
  const handleClearAgreementField = useCallback(
    (field: keyof AgreementSettings) => {
      const fieldLabelMap: Record<keyof AgreementSettings, string> = {
        userAgreementMarkdown: t('ui.settings.personalization.userAgreement'),
        privacyAgreementMarkdown: t('ui.settings.personalization.privacyAgreement'),
      };

      confirmAction({
        title: t('ui.settings.personalization.clear.c4144ca6').replace('{label}', fieldLabelMap[field]),
        content: t('ui.settings.personalization.clearThisWillRemoveItFromTheCurrent.a5ff1ff7', { value1: fieldLabelMap[field] }),
        okText: t('ui.settings.personalization.clear.3e5155a1'),
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
      maintenanceForm.setFieldsValue(normalizedBranding);
      watermarkForm.setFieldsValue(normalizedWatermark);
      floatingForm.setFieldsValue(normalizedFloating);
      agreementForm.setFieldsValue(normalizedAgreement);
      setPreviewState(normalizedBranding);
      setWatermarkPreview(normalizedWatermark);
      setFloatingPreview(normalizedFloating);
      queryClient.setQueryData(FLOATING_WINDOW_SETTINGS_QUERY_KEY, normalizedFloating);
      persistWatermarkSettings(normalizedWatermark);
      persistBrandingSettings(normalizedBranding);
      applyBrandingRuntime(normalizedBranding);
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              brandingSettings: normalizedBranding,
              brandingRevision: (prev.brandingRevision ?? 0) + 1,
              watermarkSettings: normalizedWatermark,
              agreementSettings: normalizedAgreement,
            }
          : prev,
      );
    } catch (error) {
      if (isActive()) {
        showErrorMessage(error, t('ui.settings.personalization.failedToLoadBaseSettings'));
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
    maintenanceForm,
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
    <ManagementPage className="saas-crud-page" ghost title={t('ui.settings.personalization.personalizationSettings')} content={null}>
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
                label: t('ui.settings.personalization.branding'),
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
                key: 'maintenance',
                label: t('ui.settings.personalization.maintenance'),
                children: (
                  <MaintenanceTab
                    formProps={maintenanceFormProps}
                    preview={previewState}
                    saving={maintenanceSaving}
                    canUpdate={canUpdate}
                    onSave={() => void handleSaveMaintenance()}
                  />
                ),
              },
              {
                key: 'watermark',
                label: t('ui.settings.personalization.watermark'),
                children: (
                  <WatermarkTab
                    formProps={watermarkFormProps}
                    watermarkPreview={watermarkPreview}
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
                label: t('ui.settings.personalization.floatingWindow'),
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
                label: t('ui.settings.personalization.agreements'),
                children: (
                  <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                    <Form {...agreementFormProps} disabled={!canUpdate}>
                      <Form.Item name="userAgreementMarkdown" label={t('ui.settings.personalization.userAgreement')} getValueFromEvent={(value) => value ?? ''}>
                        <AgreementMarkdownEditor placeholder={t('ui.settings.personalization.enterTheUserAgreementMarkdownContent')} />
                      </Form.Item>
                      <Form.Item name="privacyAgreementMarkdown" label={t('ui.settings.personalization.privacyAgreement')} getValueFromEvent={(value) => value ?? ''}>
                        <AgreementMarkdownEditor placeholder={t('ui.settings.personalization.enterThePrivacyAgreementMarkdownContent')} />
                      </Form.Item>
                    </Form>

                    <Space wrap style={{ justifyContent: 'flex-start', width: '100%' }}>
                      <Button danger disabled={!canUpdate} onClick={() => handleClearAgreementField('userAgreementMarkdown')}>
                        {t('ui.settings.personalization.clearUserAgreement')}
                      </Button>
                      <Button danger disabled={!canUpdate} onClick={() => handleClearAgreementField('privacyAgreementMarkdown')}>
                        {t('ui.settings.personalization.clearPrivacyAgreement')}
                      </Button>
                      <Button type="primary" loading={agreementSaving} disabled={!canUpdate} onClick={() => void handleSaveAgreement()}>
                        {t('ui.settings.personalization.saveSettings')}
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
