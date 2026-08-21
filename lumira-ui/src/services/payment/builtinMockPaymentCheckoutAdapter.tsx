import {
  Alert,
  Button,
  Descriptions,
  Radio,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getBuiltinMockPaymentCheckout, simulateBuiltinMockPayment } from './api';
import {
  isBuiltinMockCallbackPending,
  isBuiltinMockPaymentPending,
  isBuiltinMockPaymentSuccessful,
} from './builtinMockPaymentState';
import {
  registerPaymentCheckoutAdapter,
  type PaymentCheckoutOrder,
  type PaymentCheckoutPresentationOptions,
} from './paymentCheckout';
import { modal } from '@/theme/antdFeedbackBridge';
import type {
  BuiltinMockPaymentCheckout,
  BuiltinMockPaymentOutcome,
} from '@/types/api';
import { extractErrorMessage } from '@/utils/errorMessage';

const PROVIDER_CODE = 'builtin_mock';

const OUTCOME_OPTIONS: Array<{
  value: BuiltinMockPaymentOutcome;
  label: string;
  description: string;
}> = [
  { value: 'SUCCESS', label: '支付成功', description: '通过正式异步通知确认订单' },
  { value: 'FAILURE', label: '支付失败', description: '返回模拟业务失败结果' },
  { value: 'CANCEL', label: '用户取消', description: '关闭当前支付尝试' },
  { value: 'TIMEOUT', label: '订单超时', description: '模拟买家未付款' },
];

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  CREATED: '待支付',
  PENDING: '待支付',
  PAID: '支付成功',
  SUCCESS: '支付成功',
  SETTLED: '支付成功',
  FAILED: '支付失败',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
  EXPIRED: '已超时',
  REFUNDED: '已退款',
};

const CALLBACK_STATUS_LABELS: Record<string, string> = {
  PENDING: '等待回调',
  PROCESSING: '回调处理中',
  RETRY: '等待重试',
  DELIVERED: '回调已送达',
  DEAD: '回调失败',
  CANCELLED: '回调已取消',
};

const formatAmount = (amountMinor: number, currency: string) => {
  try {
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency: currency || 'CNY',
      minimumFractionDigits: 2,
    }).format((amountMinor || 0) / 100);
  } catch {
    return `${((amountMinor || 0) / 100).toFixed(2)} ${currency || 'CNY'}`;
  }
};

const formatTime = (value?: string | null) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '-';

const toPaymentOrder = (checkout: BuiltinMockPaymentCheckout): PaymentCheckoutOrder => ({
  orderNo: checkout.orderNo,
  providerCode: PROVIDER_CODE,
  subject: checkout.subject,
  amountMinor: checkout.amountMinor,
  currency: checkout.currency,
  status: checkout.status,
  paymentUrl: null,
  returnUrl: checkout.returnUrl,
});

export const BuiltinMockPaymentCheckoutPanel = ({
  orderNo,
  onOrderUpdated,
}: {
  orderNo: string;
  onOrderUpdated?: (order: PaymentCheckoutOrder) => void;
}) => {
  const [checkout, setCheckout] = useState<BuiltinMockPaymentCheckout>();
  const [outcome, setOutcome] = useState<BuiltinMockPaymentOutcome>('SUCCESS');
  const [delaySeconds, setDelaySeconds] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const refresh = useCallback(async (initial = false) => {
    if (!initial) setRefreshing(true);
    try {
      const next = await getBuiltinMockPaymentCheckout(orderNo);
      setCheckout(next);
      setError(undefined);
      setOutcome((current) => next.allowedOutcomes.includes(current) || next.allowedOutcomes.length === 0
        ? current
        : next.allowedOutcomes[0]);
      setDelaySeconds((current) => next.delayOptions.includes(current) || next.delayOptions.length === 0
        ? current
        : next.delayOptions[0]);
      onOrderUpdated?.(toPaymentOrder(next));
    } catch (requestError) {
      setError(extractErrorMessage(requestError, '模拟支付状态加载失败，请重试。'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [onOrderUpdated, orderNo]);

  useEffect(() => {
    void refresh(true);
  }, [refresh]);

  useEffect(() => {
    const shouldPoll = checkout
      && (isBuiltinMockPaymentPending(checkout.status)
        || isBuiltinMockCallbackPending(checkout.callbackStatus));
    if (!shouldPoll) return undefined;
    const timer = window.setInterval(() => void refresh(), 2000);
    return () => window.clearInterval(timer);
  }, [checkout, refresh]);

  const submit = async () => {
    if (!checkout || !checkout.allowedOutcomes.includes(outcome)) return;
    setSubmitting(true);
    setError(undefined);
    try {
      const result = await simulateBuiltinMockPayment(checkout.orderNo, {
        outcome,
        callbackDelaySeconds: delaySeconds,
      });
      onOrderUpdated?.(result.order);
      await refresh();
    } catch (requestError) {
      setError(extractErrorMessage(requestError, '模拟结果提交失败，请重试。'));
    } finally {
      setSubmitting(false);
    }
  };

  const successful = isBuiltinMockPaymentSuccessful(checkout?.status);
  const terminal = checkout ? !isBuiltinMockPaymentPending(checkout.status) : false;
  const callbackPending = isBuiltinMockCallbackPending(checkout?.callbackStatus);
  const selectedOutcome = useMemo(
    () => OUTCOME_OPTIONS.find((item) => item.value === checkout?.scheduledOutcome),
    [checkout?.scheduledOutcome],
  );

  if (loading) {
    return <div style={{ padding: 32, textAlign: 'center' }}><Spin tip="正在加载支付订单…" /></div>;
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', marginTop: 12 }}>
      <Alert
        type="warning"
        showIcon
        message={checkout?.environmentNotice || '仅用于本地调试，不会产生真实扣款'}
        description="结果仍通过正式支付 Webhook、Outbox 和业务确认链路处理。"
      />
      {error ? <Alert type="error" showIcon message={error} /> : null}
      {checkout ? (
        <>
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="订单内容">{checkout.subject}</Descriptions.Item>
            <Descriptions.Item label="订单号">
              <Typography.Text copyable>{checkout.orderNo}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="应付金额">
              <Typography.Text strong>{formatAmount(checkout.amountMinor, checkout.currency)}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="支付状态">
              <Tag color={successful ? 'success' : terminal ? 'default' : 'processing'}>
                {PAYMENT_STATUS_LABELS[checkout.status] || checkout.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="到期时间">{formatTime(checkout.expiresAt)}</Descriptions.Item>
          </Descriptions>

          {callbackPending ? (
            <Alert
              type="info"
              showIcon
              message={`${selectedOutcome?.label || checkout.scheduledOutcome || '模拟结果'}已提交，${CALLBACK_STATUS_LABELS[checkout.callbackStatus || ''] || '等待服务端处理'}`}
              description={`计划回调时间：${formatTime(checkout.callbackScheduledAt)}`}
            />
          ) : null}

          {!terminal && checkout.allowedOutcomes.length > 0 ? (
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Radio.Group
                value={outcome}
                onChange={(event) => setOutcome(event.target.value)}
                options={OUTCOME_OPTIONS
                  .filter((item) => checkout.allowedOutcomes.includes(item.value))
                  .map((item) => ({ value: item.value, label: `${item.label}：${item.description}` }))}
              />
              <Space wrap>
                <Typography.Text strong>异步通知延迟</Typography.Text>
                <Select
                  aria-label="异步通知延迟"
                  value={delaySeconds}
                  onChange={setDelaySeconds}
                  options={checkout.delayOptions.map((seconds) => ({
                    value: seconds,
                    label: seconds === 0 ? '立即回调' : `${seconds} 秒后回调`,
                  }))}
                />
                <Button type="primary" loading={submitting} onClick={() => void submit()}>
                  提交模拟结果
                </Button>
                <Button loading={refreshing} onClick={() => void refresh()}>刷新状态</Button>
              </Space>
            </Space>
          ) : (
            <Alert
              type={successful ? 'success' : 'info'}
              showIcon
              message={successful ? '模拟支付成功' : PAYMENT_STATUS_LABELS[checkout.status] || checkout.status}
              description={successful
                ? '正式异步通知已完成验签，支付订单状态已经同步。'
                : '本次支付尝试已经结束。'}
            />
          )}
        </>
      ) : null}
    </Space>
  );
};

let activeModalDestroy: (() => void) | undefined;

export const presentBuiltinMockPaymentCheckout = (
  order: PaymentCheckoutOrder,
  options: PaymentCheckoutPresentationOptions,
) => {
  const orderNo = order.orderNo?.trim();
  if (!orderNo) return;
  activeModalDestroy?.();
  const instance = modal.info({
    title: '模拟支付调试',
    icon: null,
    width: 760,
    closable: true,
    maskClosable: false,
    okText: '关闭',
    content: (
      <BuiltinMockPaymentCheckoutPanel
        orderNo={orderNo}
        onOrderUpdated={options.onOrderUpdated}
      />
    ),
    afterClose: () => {
      if (activeModalDestroy === instance.destroy) {
        activeModalDestroy = undefined;
      }
    },
  });
  activeModalDestroy = instance.destroy;
};

registerPaymentCheckoutAdapter({
  providerCode: PROVIDER_CODE,
  present: presentBuiltinMockPaymentCheckout,
});
