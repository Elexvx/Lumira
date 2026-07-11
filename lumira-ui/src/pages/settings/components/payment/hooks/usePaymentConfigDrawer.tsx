import { Checkbox, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { message } from '@/theme/antdFeedbackBridge';
import type { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import type { PaymentProviderSettings, PaymentProviderTestResult } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { buildSystemPaymentWebhookUrl } from '../paymentWebhookUrl';
import { requestPaymentApi } from '../paymentAuthenticatedRequest';
import { localizePaymentMessage } from '../paymentMessage';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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

const PAYMENT_ENVIRONMENT_OPTIONS = [
  { label: t('沙箱', 'Sandbox'), value: 'SANDBOX' },
  { label: t('测试', 'Test'), value: 'TEST' },
  { label: t('正式', 'Production'), value: 'PRODUCTION' },
];

const PAYMENT_PROVIDER_TITLES: Record<PaymentProviderCode, string> = {
  alipay: t('支付宝', 'Alipay'),
  wechat_pay: t('微信支付', 'WeChat Pay'),
  stripe: 'Stripe',
  paypal: 'PayPal',
};

const PAYMENT_PROVIDER_FIELD_SCHEMAS: Record<PaymentProviderCode, PaymentFieldConfig[]> = {
  alipay: [
    { name: 'appId', label: t('App ID', 'App ID'), required: true, placeholder: t('支付宝应用 ID', 'Alipay app ID') },
    { name: 'publicKey', label: t('支付宝公钥', 'Alipay public key'), required: true, placeholder: t('支付宝公钥', 'Alipay public key') },
    { name: 'privateKey', label: t('应用私钥', 'Application private key'), required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: t('异步通知地址', 'Async notification URL'), required: true, systemManaged: true },
    { name: 'returnUrl', label: t('同步跳转地址', 'Return URL'), placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: t('API 基地址', 'API base URL'), placeholder: t('例如：https://openapi.alipay.com', 'e.g. https://openapi.alipay.com') },
  ],
  wechat_pay: [
    { name: 'appId', label: t('App ID', 'App ID'), required: true, placeholder: t('微信支付应用 ID', 'WeChat Pay app ID') },
    { name: 'merchantId', label: t('商户号', 'Merchant ID'), required: true, placeholder: t('微信支付商户号', 'WeChat Pay merchant ID') },
    { name: 'merchantSerialNo', label: t('商户证书序列号', 'Merchant certificate serial number'), required: true, placeholder: t('商户平台证书序列号', 'Merchant platform certificate serial number') },
    { name: 'apiV3Key', label: 'APIv3 Key', required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true },
    { name: 'platformCertSerialNo', label: t('平台证书序列号', 'Platform certificate serial number'), required: true, placeholder: t('平台证书序列号', 'Platform certificate serial number') },
    { name: 'privateKey', label: t('私钥', 'Private key'), required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: t('异步通知地址', 'Async notification URL'), required: true, systemManaged: true },
    { name: 'refundNotifyUrl', label: t('退款通知地址', 'Refund notification URL'), placeholder: 'https://example.com/payment/wechat/refund-notify' },
    { name: 'apiBaseUrl', label: t('API 基地址', 'API base URL'), placeholder: t('例如：https://api.mch.weixin.qq.com', 'e.g. https://api.mch.weixin.qq.com') },
  ],
  stripe: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: t('Stripe 客户端 ID', 'Stripe client ID') },
    { name: 'publishableKey', label: 'Publishable Key', placeholder: t('Stripe 公钥', 'Stripe publishable key') },
    { name: 'secretKey', label: 'Secret Key', required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true },
    { name: 'successUrl', label: t('成功跳转地址', 'Success URL'), placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: t('取消跳转地址', 'Cancel URL'), placeholder: 'https://example.com/payment/cancel' },
    { name: 'apiBaseUrl', label: t('API 基地址', 'API base URL'), placeholder: t('例如：https://api.stripe.com', 'e.g. https://api.stripe.com') },
    { name: 'currency', label: t('结算币种', 'Currency'), placeholder: t('例如：USD', 'e.g. USD') },
  ],
  paypal: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: t('PayPal 客户端 ID', 'PayPal client ID') },
    { name: 'clientSecret', label: 'Client Secret', required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true },
    { name: 'webhookId', label: 'Webhook ID', required: true, placeholder: 'PayPal Webhook ID' },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true },
    { name: 'successUrl', label: t('成功跳转地址', 'Success URL'), placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: t('取消跳转地址', 'Cancel URL'), placeholder: 'https://example.com/payment/cancel' },
    { name: 'returnUrl', label: t('同步跳转地址', 'Return URL'), placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: t('API 基地址', 'API base URL'), placeholder: t('例如：https://api-m.paypal.com', 'e.g. https://api-m.paypal.com') },
    { name: 'currency', label: t('结算币种', 'Currency'), placeholder: t('例如：USD', 'e.g. USD') },
  ],
};

const PAYMENT_SCENE_LABELS: Record<string, string> = {
  NATIVE: t('电脑二维码支付', 'Desktop QR payment'),
  H5: t('手机浏览器支付', 'Mobile browser payment'),
  JSAPI: t('微信内支付', 'WeChat in-app browser payment'),
  PC_WEB: t('电脑网站支付', 'Desktop web payment'),
  WAP: t('手机网站支付', 'Mobile web payment'),
  QR_CODE: t('扫码支付', 'QR code payment'),
  CHECKOUT: t('托管收银台', 'Hosted checkout'),
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

const resolveSandboxEnabled = (environment?: string | null) => environment?.trim().toUpperCase() === 'SANDBOX';

const buildFormValues = (settings: PaymentProviderSettings): PaymentProviderSettings => ({
  ...settings,
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
        providerName: PAYMENT_PROVIDER_TITLES[providerCode],
        supportedScenes,
        enabledScenes: supportedScenes,
        enabled: false,
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
      message.success(t('支付配置已保存', 'Payment configuration saved'));
      await onRefetch();
      closeConfigDrawer();
    } catch (error) {
      if (isFormValidationError(error)) {
        const firstError = error.errorFields?.[0];
        if (firstError?.name?.length) {
          form.scrollToField(firstError.name, { block: 'center' });
        }
        message.warning(firstError?.errors?.[0] || t('请先补全支付配置中的必填项', 'Please complete the required payment configuration fields first'));
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
        const feedback = localizePaymentMessage(result.message, isEnglishLocale())
          || t('支付连通性测试完成', 'Payment connectivity test completed');
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
          {t('请选择一个支付平台进行配置。', 'Please select a payment platform to configure.')}
        </Typography.Paragraph>
      );
    }

    const providerFields = PAYMENT_PROVIDER_FIELD_SCHEMAS[editingProviderCode];
    const configuredFields = new Set(currentProviderSettings?.configuredFields || []);
    const supportedScenes = resolveSupportedScenes(editingProviderCode, currentProviderSettings);

    return (
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          {t('{provider} 配置已按平台字段分组保存，敏感项会以密文方式持久化。', '{provider} configuration is grouped by platform fields and sensitive items are stored encrypted.').replace('{provider}', currentProviderSettings?.providerName || PAYMENT_PROVIDER_TITLES[editingProviderCode])}
        </Typography.Paragraph>
        <Form form={form} layout="vertical" disabled={!canUpdateSettings}>
          <Form.Item name="providerCode" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="providerName" hidden>
            <Input />
          </Form.Item>
          <Typography.Paragraph type="secondary">
            {t('必填配置完整后，保存即自动启用；配置不完整时保持待配置状态。', 'A complete configuration is enabled automatically when saved; incomplete configurations remain pending.')}
          </Typography.Paragraph>
          <Form.Item
            name="environment"
            label={t('环境', 'Environment')}
            rules={[{ required: true, message: t('请选择环境', 'Please select an environment') }]}
            extra={t('选择沙箱时，系统会自动按沙箱配置保存。', 'Sandbox environment is saved as sandbox configuration automatically.')}
          >
            <Select options={PAYMENT_ENVIRONMENT_OPTIONS} disabled={!canUpdateSettings} />
          </Form.Item>
          <Form.Item name="displayName" label={t('前台展示名称', 'Checkout display name')} rules={[{ required: true, message: t('请输入前台展示名称', 'Please enter the checkout display name') }]}>
            <Input disabled={!canUpdateSettings} maxLength={64} />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('展示排序', 'Display order')}>
            <InputNumber disabled={!canUpdateSettings} min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabledScenes" label={t('可用支付场景', 'Enabled payment scenes')} rules={[{ required: true, message: t('请至少启用一种支付场景', 'Enable at least one payment scene') }]}>
            <Checkbox.Group
              disabled={!canUpdateSettings}
              options={supportedScenes.map((scene) => ({ label: PAYMENT_SCENE_LABELS[scene] || scene, value: scene }))}
            />
          </Form.Item>
          <Form.Item name="currency" label={t('结算币种', 'Currency')} extra={t('留空时使用平台默认币种。', 'Leave blank to use the platform default currency.')}>
            <Input disabled={!canUpdateSettings} maxLength={16} placeholder={t('例如：CNY', 'e.g. CNY')} />
          </Form.Item>
          {providerFields.map((field) => {
            const extraMessage =
              field.systemManaged
                ? t('由系统根据当前站点自动生成，固定为支付平台回调地址。', 'Generated automatically from the current site and fixed as the payment provider callback URL.')
                : field.password && configuredFields.has(field.name)
                ? t('留空则保留现有密钥', 'Leave blank to keep the current key')
                : field.password
                  ? t('请输入新密钥，或保留空值', 'Enter a new key or leave blank')
                  : undefined;

            return (
              <Form.Item
                key={String(field.name)}
                name={field.name}
                label={field.label}
                rules={field.required ? [{ required: true, message: t(`请输入${field.label}`, `Please enter ${field.label}`) }] : undefined}
                extra={extraMessage}
              >
                {renderFieldControl(field, canUpdateSettings)}
              </Form.Item>
            );
          })}
          <Form.Item name="extraConfig" label={t('扩展参数', 'Extra config')} extra={t('可填写 JSON 字符串或平台约定的补充参数。', 'You can enter a JSON string or platform-specific extra parameters.')}>
            <Input.TextArea disabled={!canUpdateSettings} autoSize={{ minRows: 4, maxRows: 10 }} placeholder={t('例如：{"merchantMode":"DIRECT"}', 'e.g. {"merchantMode":"DIRECT"}')} />
          </Form.Item>
        </Form>
      </Space>
    );
  }, [canUpdateSettings, currentProviderSettings, editingProviderCode, form]);

  const footerActions = useMemo<ManagementDrawerAction[]>(
    () => [
      {
        key: 'close',
        label: t('关闭', 'Close'),
        onClick: closeConfigDrawer,
      },
      {
        key: 'test',
        label: t('测试连通性', 'Test connectivity'),
        loading: testing,
        disabled: !editingProviderCode || !currentProviderSettings?.configured || !canTestSettings,
        onClick: () => void handleTestProvider(),
      },
      {
        key: 'save',
        label: t('保存', 'Save'),
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
