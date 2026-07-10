import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { message } from '@/theme/antdFeedbackBridge';
import type { PaymentCreateOrderRequest, PaymentOrderRecord, PaymentProviderSettings } from '@/types/api';
import { createSandboxPaymentOrder } from '@/services/payment/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const SANDBOX_ENVIRONMENT = 'SANDBOX';
const STATUS_COLORS: Record<string, string> = {
  PENDING: 'processing',
  CREATED: 'processing',
  PAID: 'success',
  SUCCESS: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
};

type ManualSandboxOrderFormValues = {
  providerCode: string;
  orderNo: string;
  subject: string;
  amountYuan: number;
  currency: string;
  notifyUrl?: string;
  returnUrl?: string;
  metadataText?: string;
};

const buildSandboxOrderNo = () => {
  const now = new Date();
  const timestamp = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
    String(now.getHours()).padStart(2, '0'),
    String(now.getMinutes()).padStart(2, '0'),
    String(now.getSeconds()).padStart(2, '0'),
  ].join('');
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `SANDBOX-${timestamp}-${suffix}`;
};

const formatAmount = (amountMinor?: number | null, currency?: string | null) => {
  if (typeof amountMinor !== 'number') {
    return '-';
  }
  return `${(amountMinor / 100).toFixed(2)} ${currency || 'CNY'}`;
};

const formatTime = (value?: string | null) => (value ? value.replace('T', ' ') : '-');

export const SandboxPaymentOrderTab = ({
  paymentSettings,
  canCreateOrders,
}: {
  paymentSettings: PaymentProviderSettings[];
  canCreateOrders: boolean;
}) => {
  const [form] = Form.useForm<ManualSandboxOrderFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [createdOrder, setCreatedOrder] = useState<PaymentOrderRecord>();
  const selectedProviderCode = Form.useWatch('providerCode', form);

  const sandboxProviders = useMemo(
    () =>
      paymentSettings.filter((item) =>
        item.persisted
        && item.enabled
        && item.configured
        && item.environment?.trim().toUpperCase() === SANDBOX_ENVIRONMENT,
      ),
    [paymentSettings],
  );

  const providerMap = useMemo(
    () => new Map(sandboxProviders.map((item) => [String(item.providerCode), item])),
    [sandboxProviders],
  );

  const selectedProvider = selectedProviderCode ? providerMap.get(String(selectedProviderCode)) : undefined;

  useEffect(() => {
    if (!sandboxProviders.length) {
      form.resetFields(['providerCode', 'currency']);
      return;
    }
    const currentProviderCode = form.getFieldValue('providerCode');
    const hasCurrentProvider = currentProviderCode && providerMap.has(String(currentProviderCode));
    const nextProvider = hasCurrentProvider ? providerMap.get(String(currentProviderCode)) : sandboxProviders[0];
    form.setFieldsValue({
      providerCode: String(nextProvider?.providerCode || ''),
      currency: nextProvider?.currency || 'CNY',
    });
  }, [form, providerMap, sandboxProviders]);

  useEffect(() => {
    if (!selectedProvider) {
      return;
    }
    form.setFieldValue('currency', selectedProvider.currency || 'CNY');
  }, [form, selectedProvider]);

  useEffect(() => {
    if (!form.getFieldValue('orderNo')) {
      form.setFieldValue('orderNo', buildSandboxOrderNo());
    }
  }, [form]);

  const handleRefreshOrderNo = () => {
    form.setFieldValue('orderNo', buildSandboxOrderNo());
  };

  const handleSubmit = async () => {
    try {
      setSubmitting(true);
      const values = await form.validateFields();
      const metadata = values.metadataText?.trim() ? JSON.parse(values.metadataText) : undefined;
      const requestPayload: PaymentCreateOrderRequest = {
        providerCode: values.providerCode.trim(),
        orderNo: values.orderNo.trim(),
        subject: values.subject.trim(),
        amountMinor: Math.round(values.amountYuan * 100),
        currency: values.currency.trim().toUpperCase(),
        notifyUrl: values.notifyUrl?.trim() || undefined,
        returnUrl: values.returnUrl?.trim() || undefined,
        metadata,
        idempotencyKey: `sandbox:${values.orderNo.trim()}`,
      };
      const order = await createSandboxPaymentOrder(requestPayload);
      setCreatedOrder(order);
      message.success(t('沙箱支付订单已生成', 'Sandbox payment order created'));
    } catch (error) {
      if (error instanceof SyntaxError) {
        message.error(t('扩展参数必须是合法的 JSON', 'Extra config must be valid JSON'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const providerOptions = sandboxProviders.map((item) => ({
    label: `${item.providerName} (${item.providerCode})`,
    value: String(item.providerCode),
  }));

  const isFormDisabled = !canCreateOrders || !sandboxProviders.length;

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        showIcon
        type="info"
        message={t('这里的手动下单已写死为“仅沙箱环境”', 'Manual orders here are hard-coded to sandbox only')}
        description={t(
          '第二个 tab 只会使用已启用且已配置完成的沙箱支付平台，正式环境配置不会从这里发起下单。',
          'This tab only uses enabled and fully configured sandbox payment providers. Production configurations cannot create orders here.',
        )}
      />
      {!canCreateOrders ? (
        <Alert
          showIcon
          type="warning"
          message={t('当前账号没有创建支付订单权限', 'The current account cannot create payment orders')}
        />
      ) : null}
      {!sandboxProviders.length ? (
        <Alert
          showIcon
          type="warning"
          message={t('暂无可用的沙箱支付平台', 'No sandbox payment providers are available')}
          description={t(
            '请先在“支付设置”tab 中启用并完成至少一个沙箱环境支付平台的配置。',
            'Please enable and complete at least one sandbox payment provider in the Payment settings tab first.',
          )}
        />
      ) : null}
      <Card title={t('手动生成支付订单', 'Create payment order manually')}>
        <Form<ManualSandboxOrderFormValues> form={form} layout="vertical" disabled={isFormDisabled}>
          <Form.Item
            name="providerCode"
            label={t('支付平台', 'Payment platform')}
            rules={[{ required: true, message: t('请选择支付平台', 'Please select a payment platform') }]}
          >
            <Select options={providerOptions} placeholder={t('请选择一个沙箱支付平台', 'Select a sandbox payment provider')} />
          </Form.Item>
          <Form.Item
            name="orderNo"
            label={t('订单号', 'Order number')}
            rules={[{ required: true, message: t('请输入订单号', 'Please enter an order number') }]}
          >
            <Input
              placeholder={t('建议保留系统生成值，避免重复', 'Use the generated value to avoid duplicates')}
              addonAfter={
                <Button type="link" size="small" onClick={handleRefreshOrderNo}>
                  {t('刷新', 'Refresh')}
                </Button>
              }
            />
          </Form.Item>
          <Form.Item
            name="subject"
            label={t('订单标题', 'Order subject')}
            rules={[{ required: true, message: t('请输入订单标题', 'Please enter an order subject') }]}
          >
            <Input placeholder={t('例如：赛事报名测试订单', 'e.g. competition registration sandbox order')} maxLength={128} />
          </Form.Item>
          <Form.Item
            name="amountYuan"
            label={t('金额（元）', 'Amount')}
            rules={[
              { required: true, message: t('请输入金额', 'Please enter the amount') },
              { type: 'number', min: 0.01, message: t('金额必须大于 0', 'Amount must be greater than 0') },
            ]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} placeholder="0.01" />
          </Form.Item>
          <Form.Item
            name="currency"
            label={t('币种', 'Currency')}
            rules={[{ required: true, message: t('请输入币种', 'Please enter the currency') }]}
          >
            <Input maxLength={16} placeholder={selectedProvider?.currency || 'CNY'} />
          </Form.Item>
          <Form.Item name="notifyUrl" label={t('异步通知地址', 'Notify URL')}>
            <Input placeholder="https://example.com/payment/notify" />
          </Form.Item>
          <Form.Item name="returnUrl" label={t('同步跳转地址', 'Return URL')}>
            <Input placeholder="https://example.com/payment/result" />
          </Form.Item>
          <Form.Item
            name="metadataText"
            label={t('扩展参数', 'Metadata')}
            extra={t('可选，填写 JSON 对象，例如 {"scene":"manual-sandbox"}', 'Optional JSON object, for example {"scene":"manual-sandbox"}')}
            rules={[
              {
                validator: async (_, value) => {
                  if (!value || !String(value).trim()) {
                    return;
                  }
                  JSON.parse(String(value));
                },
              },
            ]}
          >
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder='{"scene":"manual-sandbox"}' />
          </Form.Item>
          <Space>
            <Button type="primary" loading={submitting} onClick={() => void handleSubmit()} disabled={isFormDisabled}>
              {t('生成沙箱支付订单', 'Create sandbox payment order')}
            </Button>
          </Space>
        </Form>
      </Card>
      {createdOrder ? (
        <Card title={t('最近生成结果', 'Latest order result')}>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('订单号', 'Order number')}>
              <Typography.Text copyable>{createdOrder.orderNo}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('支付平台', 'Payment platform')}>
              {createdOrder.providerCode}
            </Descriptions.Item>
            <Descriptions.Item label={t('状态', 'Status')}>
              <Tag color={STATUS_COLORS[createdOrder.status] || 'default'}>{createdOrder.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('金额', 'Amount')}>
              {formatAmount(createdOrder.amountMinor, createdOrder.currency)}
            </Descriptions.Item>
            <Descriptions.Item label={t('支付链接', 'Payment URL')}>
              {createdOrder.paymentUrl ? (
                <Typography.Link href={createdOrder.paymentUrl} target="_blank" rel="noreferrer">
                  {createdOrder.paymentUrl}
                </Typography.Link>
              ) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('创建时间', 'Created at')}>
              {formatTime(createdOrder.createdAt)}
            </Descriptions.Item>
            <Descriptions.Item label={t('失败信息', 'Failure message')}>
              {createdOrder.failureMessage || createdOrder.failureCode || '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      ) : null}
    </Space>
  );
};
