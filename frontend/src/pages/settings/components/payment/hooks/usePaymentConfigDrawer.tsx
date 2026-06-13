import { Form, Input, InputNumber, Select, Space, Switch, Typography } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { message } from '@/theme/antdFeedbackBridge';
import type { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import type { PaymentProviderSettings, PaymentProviderTestResult } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

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
    { name: 'publicKey', label: t('公钥', 'Public key'), required: true, placeholder: t('平台公钥', 'Platform public key') },
    { name: 'privateKey', label: t('私钥', 'Private key'), required: true, placeholder: t('留空则保持现有密钥', 'Leave blank to keep the current key'), password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: t('异步通知地址', 'Async notification URL'), required: true, placeholder: 'https://example.com/payment/alipay/notify' },
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
    { name: 'notifyUrl', label: t('异步通知地址', 'Async notification URL'), required: true, placeholder: 'https://example.com/payment/wechat/notify' },
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
  notifyUrl: settings.notifyUrl ?? '',
  returnUrl: settings.returnUrl ?? '',
  refundNotifyUrl: settings.refundNotifyUrl ?? '',
  successUrl: settings.successUrl ?? '',
  cancelUrl: settings.cancelUrl ?? '',
  webhookSecret: settings.configuredFields?.includes('webhookSecret') ? MASKED_SECRET : '',
  webhookId: settings.webhookId ?? '',
  currency: settings.currency ?? '',
  extraConfig: settings.extraConfig ?? '',
  sandboxEnabled: settings.sandboxEnabled ?? false,
});

const renderFieldControl = (field: PaymentFieldConfig, canManageSettings: boolean) => {
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
      setEditingProviderCode(providerCode);
      form.setFieldsValue(buildFormValues(provider || {
        providerCode,
        providerName: PAYMENT_PROVIDER_TITLES[providerCode],
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
      await request<PaymentProviderSettings>(`/v1/payment/providers/${editingProviderCode}`, {
        method: 'PUT',
        data: values,
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(t('支付配置已保存', 'Payment configuration saved'));
      await onRefetch();
      closeConfigDrawer();
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
        const result = await request<PaymentProviderTestResult>(`/v1/payment/providers/${code}/test`, {
          method: 'POST',
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(result.message || t('支付连通性测试完成', 'Payment connectivity test completed'));
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
          <Form.Item name="enabled" label={t('启用', 'Enabled')} valuePropName="checked" extra={t('停用后，新的支付和退款请求将被拦截。', 'When disabled, new payment and refund requests will be blocked.')}>
            <Switch disabled={!canUpdateSettings} />
          </Form.Item>
          <Form.Item name="environment" label={t('环境', 'Environment')} rules={[{ required: true, message: t('请选择环境', 'Please select an environment') }]}>
            <Select options={PAYMENT_ENVIRONMENT_OPTIONS} disabled={!canUpdateSettings} />
          </Form.Item>
          <Form.Item name="currency" label={t('结算币种', 'Currency')} extra={t('留空时使用平台默认币种。', 'Leave blank to use the platform default currency.')}>
            <Input disabled={!canUpdateSettings} maxLength={16} placeholder={t('例如：CNY', 'e.g. CNY')} />
          </Form.Item>
          <Form.Item name="sandboxEnabled" label={t('沙箱模式', 'Sandbox mode')} valuePropName="checked" extra={t('开启后优先按沙箱环境理解配置。', 'When enabled, the configuration is interpreted as sandbox first.')}>
            <Switch disabled={!canUpdateSettings} />
          </Form.Item>
          {providerFields.map((field) => {
            const extraMessage =
              field.password && configuredFields.has(field.name)
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
