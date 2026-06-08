import { Form, Input, InputNumber, Select, Space, Switch, Typography } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { message } from '@/theme/antdFeedbackBridge';
import type { ManagementDrawerAction } from '@/features/management/ManagementDrawer';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import type { PaymentProviderSettings, PaymentProviderTestResult } from '@/types/api';

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
  { label: '沙箱', value: 'SANDBOX' },
  { label: '测试', value: 'TEST' },
  { label: '正式', value: 'PRODUCTION' },
];

const PAYMENT_PROVIDER_TITLES: Record<PaymentProviderCode, string> = {
  alipay: '支付宝',
  wechat_pay: '微信支付',
  stripe: 'Stripe',
  paypal: 'PayPal',
};

const PAYMENT_PROVIDER_FIELD_SCHEMAS: Record<PaymentProviderCode, PaymentFieldConfig[]> = {
  alipay: [
    { name: 'appId', label: 'App ID', required: true, placeholder: '支付宝应用 ID' },
    { name: 'publicKey', label: '公钥', required: true, placeholder: '平台公钥' },
    { name: 'privateKey', label: '私钥', required: true, placeholder: '留空则保持现有密钥', password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: '异步通知地址', required: true, placeholder: 'https://example.com/payment/alipay/notify' },
    { name: 'returnUrl', label: '同步跳转地址', placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: 'API 基地址', placeholder: '例如：https://openapi.alipay.com' },
  ],
  wechat_pay: [
    { name: 'appId', label: 'App ID', required: true, placeholder: '微信支付应用 ID' },
    { name: 'merchantId', label: '商户号', required: true, placeholder: '微信支付商户号' },
    { name: 'merchantSerialNo', label: '商户证书序列号', required: true, placeholder: '商户平台证书序列号' },
    { name: 'apiV3Key', label: 'APIv3 Key', required: true, placeholder: '留空则保持现有密钥', password: true },
    { name: 'platformCertSerialNo', label: '平台证书序列号', required: true, placeholder: '平台证书序列号' },
    { name: 'privateKey', label: '私钥', required: true, placeholder: '留空则保持现有密钥', password: true, inputMode: 'textarea' },
    { name: 'notifyUrl', label: '异步通知地址', required: true, placeholder: 'https://example.com/payment/wechat/notify' },
    { name: 'refundNotifyUrl', label: '退款通知地址', placeholder: 'https://example.com/payment/wechat/refund-notify' },
    { name: 'apiBaseUrl', label: 'API 基地址', placeholder: '例如：https://api.mch.weixin.qq.com' },
  ],
  stripe: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: 'Stripe 客户端 ID' },
    { name: 'publishableKey', label: 'Publishable Key', placeholder: 'Stripe 公钥' },
    { name: 'secretKey', label: 'Secret Key', required: true, placeholder: '留空则保持现有密钥', password: true },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: '留空则保持现有密钥', password: true },
    { name: 'successUrl', label: '成功跳转地址', placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: '取消跳转地址', placeholder: 'https://example.com/payment/cancel' },
    { name: 'apiBaseUrl', label: 'API 基地址', placeholder: '例如：https://api.stripe.com' },
    { name: 'currency', label: '结算币种', placeholder: '例如：USD' },
  ],
  paypal: [
    { name: 'clientId', label: 'Client ID', required: true, placeholder: 'PayPal 客户端 ID' },
    { name: 'clientSecret', label: 'Client Secret', required: true, placeholder: '留空则保持现有密钥', password: true },
    { name: 'webhookId', label: 'Webhook ID', required: true, placeholder: 'PayPal Webhook ID' },
    { name: 'webhookSecret', label: 'Webhook Secret', required: true, placeholder: '留空则保持现有密钥', password: true },
    { name: 'successUrl', label: '成功跳转地址', placeholder: 'https://example.com/payment/success' },
    { name: 'cancelUrl', label: '取消跳转地址', placeholder: 'https://example.com/payment/cancel' },
    { name: 'returnUrl', label: '同步跳转地址', placeholder: 'https://example.com/payment/result' },
    { name: 'apiBaseUrl', label: 'API 基地址', placeholder: '例如：https://api-m.paypal.com' },
    { name: 'currency', label: '结算币种', placeholder: '例如：USD' },
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
  canManageSettings: boolean;
  paymentSettingsData?: PaymentProviderSettings[];
  onRefetch: () => Promise<unknown> | void;
};

export const usePaymentConfigDrawer = ({ canManageSettings, paymentSettingsData, onRefetch }: UsePaymentConfigDrawerParams) => {
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
      message.success('支付配置已保存');
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
        message.success(result.message || '支付连通性测试完成');
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
          请选择一个支付平台进行配置。
        </Typography.Paragraph>
      );
    }

    const providerFields = PAYMENT_PROVIDER_FIELD_SCHEMAS[editingProviderCode];
    const configuredFields = new Set(currentProviderSettings?.configuredFields || []);

    return (
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          {(currentProviderSettings?.providerName || PAYMENT_PROVIDER_TITLES[editingProviderCode])} 配置已按平台字段分组保存，敏感项会以密文方式持久化。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" disabled={!canManageSettings}>
          <Form.Item name="providerCode" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="providerName" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" extra="停用后，新的支付和退款请求将被拦截。">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" disabled={!canManageSettings} />
          </Form.Item>
          <Form.Item name="environment" label="环境" rules={[{ required: true, message: '请选择环境' }]}>
            <Select options={PAYMENT_ENVIRONMENT_OPTIONS} disabled={!canManageSettings} />
          </Form.Item>
          <Form.Item name="currency" label="结算币种" extra="留空时使用平台默认币种。">
            <Input disabled={!canManageSettings} maxLength={16} placeholder="例如：CNY" />
          </Form.Item>
          <Form.Item name="sandboxEnabled" label="沙箱模式" valuePropName="checked" extra="开启后优先按沙箱环境理解配置。">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" disabled={!canManageSettings} />
          </Form.Item>
          {providerFields.map((field) => {
            const extraMessage =
              field.password && configuredFields.has(field.name)
                ? '留空则保留现有密钥'
                : field.password
                  ? '请输入新密钥，或保留空值'
                  : undefined;

            return (
              <Form.Item
                key={String(field.name)}
                name={field.name}
                label={field.label}
                rules={field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}
                extra={extraMessage}
              >
                {renderFieldControl(field, canManageSettings)}
              </Form.Item>
            );
          })}
          <Form.Item name="extraConfig" label="扩展参数" extra="可填写 JSON 字符串或平台约定的补充参数。">
            <Input.TextArea disabled={!canManageSettings} autoSize={{ minRows: 4, maxRows: 10 }} placeholder='例如：{"merchantMode":"DIRECT"}' />
          </Form.Item>
        </Form>
      </Space>
    );
  }, [canManageSettings, currentProviderSettings, editingProviderCode, form]);

  const footerActions = useMemo<ManagementDrawerAction[]>(
    () => [
      {
        key: 'close',
        label: '关闭',
        onClick: closeConfigDrawer,
      },
      {
        key: 'test',
        label: '测试连通性',
        loading: testing,
        disabled: !editingProviderCode || !canManageSettings,
        onClick: () => void handleTestProvider(),
      },
      {
        key: 'save',
        label: '保存',
        type: 'primary',
        loading: saving,
        disabled: !editingProviderCode || !canManageSettings,
        onClick: () => void handleSaveProviderSettings(),
      },
    ],
    [canManageSettings, closeConfigDrawer, editingProviderCode, handleSaveProviderSettings, handleTestProvider, saving, testing],
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
