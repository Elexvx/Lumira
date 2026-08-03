import { Checkbox, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { message } from '@/theme/antdFeedbackBridge';
import type { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import type { PaymentProviderSettings, PaymentProviderTestResult } from '@/types/api';

import { buildSystemPaymentWebhookUrl } from '../paymentWebhookUrl';
import { requestPaymentApi } from '../paymentAuthenticatedRequest';
import { localizePaymentMessage } from '../paymentMessage';
import { normalizePaymentEnvironment, paymentEnvironmentDisplayName, paymentProviderDisplayName } from '../paymentDisplay';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

export type PaymentProviderCode = 'alipay' | 'wechat_pay' | 'stripe' | 'paypal';

type PaymentFieldName = keyof PaymentProviderSettings;

type PaymentFieldConfig = {
  name: PaymentFieldName;
  label: string;
  placeholder?: string;
  required?: boolean;
  password?: boolean;
  inputMode?: 'text' | 'number' | 'textarea';
  systemManaged?: boolean;
};

const MASKED_SECRET = '********';

const paymentEnvironmentOptions = () => ['SANDBOX', 'PRODUCTION'].map((value) => ({
  value,
  label: paymentEnvironmentDisplayName(value),
}));

const PAYMENT_PROVIDER_FIELD_SCHEMAS: Record<PaymentProviderCode, PaymentFieldConfig[]> = {
  alipay: [
    { name: 'appId', label: t('ui.settings.payment.usepaymentconfig.appId'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.alipayAppId') },
    { name: 'publicKey', label: t('ui.settings.payment.usepaymentconfig.alipayPublicKey'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.alipayPublicKey') },
    { name: 'privateKey', label: t('ui.settings.payment.usepaymentconfig.applicationPrivateKey'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: t('ui.settings.payment.usepaymentconfig.asyncNotificationUrl'), required: true, systemManaged: true },
    { name: 'returnUrl', label: t('ui.settings.payment.usepaymentconfig.returnUrl'), placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: t('ui.settings.payment.usepaymentconfig.apiBaseUrl'), placeholder: t('ui.settings.payment.usepaymentconfig.eGHttpsOpenapiAlipayCom') },
  ],
  wechat_pay: [
    { name: 'appId', label: t('ui.settings.payment.usepaymentconfig.appId'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.wechatPayAppId') },
    { name: 'merchantId', label: t('ui.settings.payment.usepaymentconfig.merchantId'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.wechatPayMerchantId') },
    { name: 'merchantSerialNo', label: t('ui.settings.payment.usepaymentconfig.merchantCertificateSerialNumber'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.merchantPlatformCertificateSerialNumber') },
    { name: 'apiV3Key', label: 'APIv3 Key', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true },
    { name: 'platformCertSerialNo', label: t('ui.settings.payment.usepaymentconfig.wechatPayPublicKeyIdPlatformCertificateSerial'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.publicKeyIdOrPlatformCertificateSerialNumber') },
    { name: 'publicKey', label: t('ui.settings.payment.usepaymentconfig.wechatPayPlatformPublicKeyOrCertificate'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.pasteTheWechatPayPlatformPublicKeyOr'), inputMode: 'textarea' },
    { name: 'privateKey', label: t('ui.settings.payment.usepaymentconfig.privateKey'), required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: t('ui.settings.payment.usepaymentconfig.asyncNotificationUrl'), required: true, systemManaged: true },
    { name: 'refundNotifyUrl', label: t('ui.settings.payment.usepaymentconfig.refundNotificationUrl'), placeholder: 'https://example.com/payment/wechat/refund-notify' },
    { name: 'apiBaseUrl', label: t('ui.settings.payment.usepaymentconfig.apiBaseUrl'), placeholder: t('ui.settings.payment.usepaymentconfig.eGHttpsApiMchWeixinQqCom') },
  ],
  stripe: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.stripeClientId') },
    { name: 'publishableKey', label: 'Publishable Key', placeholder: t('ui.settings.payment.usepaymentconfig.stripePublishableKey') },
    { name: 'secretKey', label: 'Secret Key', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true },
    { name: 'successUrl', label: t('ui.settings.payment.usepaymentconfig.successUrl'), placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: t('ui.settings.payment.usepaymentconfig.cancelUrl'), placeholder: 'https://example.com/payment/cancel' },
    { name: 'apiBaseUrl', label: t('ui.settings.payment.usepaymentconfig.apiBaseUrl'), placeholder: t('ui.settings.payment.usepaymentconfig.eGHttpsApiStripeCom') },
    { name: 'currency', label: t('ui.settings.payment.usepaymentconfig.currency'), placeholder: t('ui.settings.payment.usepaymentconfig.eGUsd') },
  ],
  paypal: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.paypalClientId') },
    { name: 'clientSecret', label: 'Client Secret', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true },
    { name: 'webhookId', label: 'Webhook ID', required: true, placeholder: 'PayPal Webhook ID' },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey'), password: true },
    { name: 'successUrl', label: t('ui.settings.payment.usepaymentconfig.successUrl'), placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: t('ui.settings.payment.usepaymentconfig.cancelUrl'), placeholder: 'https://example.com/payment/cancel' },
    { name: 'returnUrl', label: t('ui.settings.payment.usepaymentconfig.returnUrl'), placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: t('ui.settings.payment.usepaymentconfig.apiBaseUrl'), placeholder: t('ui.settings.payment.usepaymentconfig.eGHttpsApiMPaypalCom') },
    { name: 'currency', label: t('ui.settings.payment.usepaymentconfig.currency'), placeholder: t('ui.settings.payment.usepaymentconfig.eGUsd') },
  ],
};

const PAYMENT_SCENE_LABELS: Record<string, string> = {
  NATIVE: t('ui.settings.payment.usepaymentconfig.desktopQrPayment'),
  H5: t('ui.settings.payment.usepaymentconfig.mobileBrowserPayment'),
  JSAPI: t('ui.settings.payment.usepaymentconfig.wechatInAppBrowserPayment'),
  PC_WEB: t('ui.settings.payment.usepaymentconfig.desktopWebPayment'),
  WAP: t('ui.settings.payment.usepaymentconfig.mobileWebPayment'),
  QR_CODE: t('ui.settings.payment.usepaymentconfig.qrCodePayment'),
  CHECKOUT: t('ui.settings.payment.usepaymentconfig.hostedCheckout'),
};

const PAYMENT_PROVIDER_SCENES: Record<PaymentProviderCode, string[]> = {
  alipay: ['PC_WEB', 'WAP', 'QR_CODE'],
  wechat_pay: ['NATIVE', 'H5', 'JSAPI'],
  stripe: ['CHECKOUT'],
  paypal: ['CHECKOUT'],
};

const resolveSupportedScenes = (
  providerCode: PaymentProviderCode,
  settings?: PaymentProviderSettings,
) => settings?.supportedScenes?.length
  ? settings.supportedScenes
  : PAYMENT_PROVIDER_SCENES[providerCode];

type PaymentFormValidationError = {
  errorFields?: Array<{
    name?: (string | number)[];
    errors?: string[];
  }>;
};

const isFormValidationError = (error: unknown): error is PaymentFormValidationError =>
  Boolean(error && typeof error === 'object' && 'errorFields' in error);

const resolveSandboxEnabled = (environment?: string | null) => normalizePaymentEnvironment(environment) === 'SANDBOX';

const buildFormValues = (settings: PaymentProviderSettings): PaymentProviderSettings => ({
  ...settings,
  environment: normalizePaymentEnvironment(settings.environment),
  appId: settings.appId ?? '',
  merchantId: settings.merchantId ?? '',
  merchantSerialNo: settings.merchantSerialNo ?? '',
  platformCertSerialNo: settings.platformCertSerialNo ?? '',
  apiV3Key: settings.configuredFields?.includes('apiV3Key') ? MASKED_SECRET : '',
  clientId: settings.clientId ?? '',
  clientSecret: settings.configuredFields?.includes('clientSecret') ? MASKED_SECRET : '',
  publishableKey: settings.publishableKey ?? '',
  secretKey: settings.configuredFields?.includes('secretKey') ? MASKED_SECRET : '',
  privateKey: settings.configuredFields?.includes('privateKey') ? MASKED_SECRET : '',
  publicKey: settings.publicKey ?? '',
  apiBaseUrl: settings.apiBaseUrl ?? '',
  notifyUrl: PAYMENT_PROVIDER_FIELD_SCHEMAS[settings.providerCode as PaymentProviderCode]?.some((field) => field.name === 'notifyUrl' && field.systemManaged)
    ? buildSystemPaymentWebhookUrl(settings.providerCode)
    : settings.notifyUrl ?? '',
  returnUrl: settings.returnUrl ?? '',
  refundNotifyUrl: settings.refundNotifyUrl ?? '',
  successUrl: settings.successUrl ?? '',
  cancelUrl: settings.cancelUrl ?? '',
  webhookSecret: settings.configuredFields?.includes('webhookSecret') ? MASKED_SECRET : '',
  webhookId: settings.webhookId ?? '',
  currency: settings.currency ?? '',
  extraConfig: settings.extraConfig ?? '',
  displayName: settings.displayName || settings.providerName,
  sortOrder: settings.sortOrder ?? 100,
  enabledScenes: settings.enabledScenes?.length ? settings.enabledScenes : settings.supportedScenes || [],
  sandboxEnabled: settings.sandboxEnabled ?? false,
});

const renderFieldControl = (field: PaymentFieldConfig, canManageSettings: boolean) => {
  if (field.systemManaged) {
    return <Input readOnly placeholder={field.placeholder} />;
  }
  if (field.name === 'extraConfig') {
    return <Input.TextArea disabled={!canManageSettings} autoSize={{ minRows: 3, maxRows: 8 }} placeholder={field.placeholder} />;
  }
  if (field.name === 'currency') {
    return <Input disabled={!canManageSettings} placeholder={field.placeholder} maxLength={16} />;
  }
  if (field.inputMode === 'number') {
    return <InputNumber disabled={!canManageSettings} style={{ width: '100%' }} placeholder={field.placeholder} />;
  }
  if (field.inputMode === 'textarea') {
    return <Input.TextArea disabled={!canManageSettings} autoSize={{ minRows: 3, maxRows: 8 }} placeholder={field.placeholder} />;
  }
  if (field.password) {
    return <Input.Password disabled={!canManageSettings} placeholder={field.placeholder} />;
  }
  return <Input disabled={!canManageSettings} placeholder={field.placeholder} />;
};

export type UsePaymentConfigDrawerParams = {
  canUpdateSettings: boolean;
  canTestSettings: boolean;
  paymentSettingsData?: PaymentProviderSettings[];
  onRefetch: () => Promise<unknown> | void;
};

export const usePaymentConfigDrawer = ({ canUpdateSettings, canTestSettings, paymentSettingsData, onRefetch }: UsePaymentConfigDrawerParams) => {
  const [form] = Form.useForm<PaymentProviderSettings>();
  const [editingProviderCode, setEditingProviderCode] = useState<PaymentProviderCode | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  const currentProviderSettings = useMemo(
    () => paymentSettingsData?.find((item) => item.providerCode === editingProviderCode),
    [editingProviderCode, paymentSettingsData],
  );

  const openConfigDrawer = useCallback(
    (providerCode: PaymentProviderCode) => {
      const provider = paymentSettingsData?.find((item) => item.providerCode === providerCode);
      const supportedScenes = resolveSupportedScenes(providerCode, provider);
      setEditingProviderCode(providerCode);
      form.setFieldsValue(buildFormValues(provider || {
        providerCode,
        providerName: paymentProviderDisplayName(providerCode, providerCode),
        supportedScenes,
        enabledScenes: supportedScenes,
        enabled: true,
        configured: false,
        environment: 'SANDBOX',
        configuredFields: [],
      } as PaymentProviderSettings));
    },
    [form, paymentSettingsData],
  );

  const closeConfigDrawer = useCallback(() => {
    setEditingProviderCode(null);
    form.resetFields();
  }, [form]);

  const handleSaveProviderSettings = useCallback(async () => {
    if (!editingProviderCode) {
      return;
    }

    try {
      setSaving(true);
      const values = await form.validateFields();
      const providerFields = PAYMENT_PROVIDER_FIELD_SCHEMAS[editingProviderCode];
      const systemManagedNotifyUrl = providerFields.some((field) => field.name === 'notifyUrl' && field.systemManaged)
        ? buildSystemPaymentWebhookUrl(editingProviderCode)
        : values.notifyUrl;
      const payload = {
        ...values,
        enabled: true,
        notifyUrl: systemManagedNotifyUrl,
        sandboxEnabled: resolveSandboxEnabled(values.environment),
      };
      await requestPaymentApi<PaymentProviderSettings>(`/v1/payment/providers/${editingProviderCode}`, {
        method: 'PUT',
        data: payload,
      });
      message.success(t('ui.settings.payment.usepaymentconfig.paymentConfigurationSaved'));
      await onRefetch();
      closeConfigDrawer();
    } catch (error) {
      if (isFormValidationError(error)) {
        const firstError = error.errorFields?.[0];
        if (firstError?.name?.length) {
          form.scrollToField(firstError.name, { block: 'center' });
        }
        message.warning(firstError?.errors?.[0] || t('ui.settings.payment.usepaymentconfig.pleaseCompleteTheRequiredPaymentConfigurationFieldsFirst'));
      }
    } finally {
      setSaving(false);
    }
  }, [closeConfigDrawer, editingProviderCode, form, onRefetch]);

  const handleTestProvider = useCallback(
    async (providerCode?: PaymentProviderCode | null) => {
      const code = providerCode || editingProviderCode;
      if (!code) {
        return;
      }

      try {
        setTesting(true);
        const result = await requestPaymentApi<PaymentProviderTestResult>(`/v1/payment/providers/${code}/test`, {
          method: 'POST',
        });
        const feedback = localizePaymentMessage(result.message)
          || t('ui.settings.payment.usepaymentconfig.paymentConnectivityTestCompleted');
        if (result.success) {
          message.success(feedback);
        } else {
          message.warning(feedback);
        }
        await onRefetch();
      } finally {
        setTesting(false);
      }
    },
    [editingProviderCode, onRefetch],
  );

  const renderConfigDrawerContent = useCallback(() => {
    if (!editingProviderCode) {
      return (
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          {t('ui.settings.payment.usepaymentconfig.pleaseSelectAPaymentPlatformToConfigure')}
        </Typography.Paragraph>
      );
    }

    const providerFields = PAYMENT_PROVIDER_FIELD_SCHEMAS[editingProviderCode];
    const configuredFields = new Set(currentProviderSettings?.configuredFields || []);
    const supportedScenes = resolveSupportedScenes(editingProviderCode, currentProviderSettings);

    return (
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <Form form={form} layout="vertical" disabled={!canUpdateSettings}>
          <Form.Item name="providerCode" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="providerName" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="environment"
            label={t('ui.settings.payment.usepaymentconfig.environment')}
            rules={[{ required: true, message: t('ui.settings.payment.usepaymentconfig.pleaseSelectAnEnvironment') }]}
            extra={t('ui.settings.payment.usepaymentconfig.testEnvironmentUsesTestConfigurationAndDoesNot')}
          >
            <Select options={paymentEnvironmentOptions()} disabled={!canUpdateSettings} />
          </Form.Item>
          <Form.Item name="displayName" label={t('ui.settings.payment.usepaymentconfig.checkoutDisplayName')} rules={[{ required: true, message: t('ui.settings.payment.usepaymentconfig.pleaseEnterTheCheckoutDisplayName') }]}>
            <Input disabled={!canUpdateSettings} maxLength={64} />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('ui.settings.payment.usepaymentconfig.displayOrder')}>
            <InputNumber disabled={!canUpdateSettings} min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabledScenes" label={t('ui.settings.payment.usepaymentconfig.enabledPaymentScenes')} rules={[{ required: true, message: t('ui.settings.payment.usepaymentconfig.enableAtLeastOnePaymentScene') }]}>
            <Checkbox.Group
              disabled={!canUpdateSettings}
              options={supportedScenes.map((scene) => ({ label: PAYMENT_SCENE_LABELS[scene] || scene, value: scene }))}
            />
          </Form.Item>
          <Form.Item name="currency" label={t('ui.settings.payment.usepaymentconfig.currency')} extra={t('ui.settings.payment.usepaymentconfig.leaveBlankToUseThePlatformDefaultCurrency')}>
            <Input disabled={!canUpdateSettings} maxLength={16} placeholder={t('ui.settings.payment.usepaymentconfig.eGCny')} />
          </Form.Item>
          {providerFields.map((field) => {
            const extraMessage =
              field.systemManaged
                ? t('ui.settings.payment.usepaymentconfig.generatedAutomaticallyFromTheCurrentSiteAndFixed')
                : field.password && configuredFields.has(field.name)
                ? t('ui.settings.payment.usepaymentconfig.leaveBlankToKeepTheCurrentKey.187505b1')
                : field.password
                  ? t('ui.settings.payment.usepaymentconfig.enterANewKeyOrLeaveBlank')
                  : undefined;

            return (
              <Form.Item
                key={String(field.name)}
                name={field.name}
                label={field.label}
                rules={field.required ? [{ required: true, message: t('ui.settings.payment.usepaymentconfig.pleaseEnter', { label: field.label }) }] : undefined}
                extra={extraMessage}
              >
                {renderFieldControl(field, canUpdateSettings)}
              </Form.Item>
            );
          })}
          <Form.Item name="extraConfig" label={t('ui.settings.payment.usepaymentconfig.extraConfig')} extra={t('ui.settings.payment.usepaymentconfig.youCanEnterAJsonStringOrPlatform')}>
            <Input.TextArea disabled={!canUpdateSettings} autoSize={{ minRows: 4, maxRows: 10 }} placeholder={t('ui.settings.payment.usepaymentconfig.eG')} />
          </Form.Item>
        </Form>
      </Space>
    );
  }, [canUpdateSettings, currentProviderSettings, editingProviderCode, form]);

  const footerActions = useMemo<ManagementDrawerAction[]>(
    () => [
      {
        key: 'close',
        label: t('ui.settings.payment.usepaymentconfig.close'),
        onClick: closeConfigDrawer,
      },
      {
        key: 'test',
        label: t('ui.settings.payment.usepaymentconfig.testConnectivity'),
        loading: testing,
        disabled: !editingProviderCode || !currentProviderSettings?.configured || !canTestSettings,
        onClick: () => void handleTestProvider(),
      },
      {
        key: 'save',
        label: t('ui.settings.payment.usepaymentconfig.save'),
        type: 'primary',
        loading: saving,
        disabled: !editingProviderCode || !canUpdateSettings,
        onClick: () => void handleSaveProviderSettings(),
      },
    ],
    [canTestSettings, canUpdateSettings, closeConfigDrawer, currentProviderSettings?.configured, editingProviderCode, handleSaveProviderSettings, handleTestProvider, saving, testing],
  );

  return {
    editingProviderCode,
    openConfigDrawer,
    closeConfigDrawer,
    handleTestProvider,
    form,
    drawerProps: {
      open: Boolean(editingProviderCode),
      onClose: closeConfigDrawer,
      configDrawerMode: editingProviderCode,
      footerActions,
      renderConfigDrawerContent,
    },
  };
};
