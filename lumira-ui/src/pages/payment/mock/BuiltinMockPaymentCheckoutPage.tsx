import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExperimentOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  QRCode,
  Radio,
  Result,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getBuiltinMockPaymentCheckout,
  simulateBuiltinMockPayment,
} from '@/services/payment/api';
import type {
  BuiltinMockPaymentCheckout,
  BuiltinMockPaymentOutcome,
} from '@/types/api';
import { extractErrorMessage } from '@/utils/errorMessage';
import {
  isBuiltinMockCallbackPending,
  isBuiltinMockPaymentPending,
  isBuiltinMockPaymentSuccessful,
  resolveBuiltinMockReturnUrl,
} from './builtinMockCheckout';
import './BuiltinMockPaymentCheckoutPage.css';

const OUTCOME_OPTIONS: Array<{
  value: BuiltinMockPaymentOutcome;
  label: string;
  description: string;
}> = [
  { value: 'SUCCESS', label: '支付成功', description: '异步通知验签通过后确认订单与报名' },
  { value: 'FAILURE', label: '支付失败', description: '返回模拟业务错误码，不会确认报名' },
  { value: 'CANCEL', label: '用户取消', description: '关闭当前支付尝试，可重新选择支付方式' },
  { value: 'TIMEOUT', label: '订单超时', description: '模拟买家未付款并使订单过期' },
];

const CALLBACK_STATUS_LABELS: Record<string, string> = {
  PENDING: '等待回调',
  PROCESSING: '回调处理中',
  RETRY: '等待重试',
  DELIVERED: '回调已送达',
  DEAD: '回调失败',
  CANCELLED: '回调已取消',
};

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

const BuiltinMockPaymentCheckoutPage = () => {
  const location = useLocation();
  const orderNo = useMemo(
    () => new URLSearchParams(location.search).get('orderNo')?.trim() || '',
    [location.search],
  );
  const [checkout, setCheckout] = useState<BuiltinMockPaymentCheckout>();
  const [outcome, setOutcome] = useState<BuiltinMockPaymentOutcome>('SUCCESS');
  const [delaySeconds, setDelaySeconds] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const refresh = useCallback(async (initial = false) => {
    if (!orderNo) {
      setError('缺少支付订单号，请从赛事报名支付页重新进入。');
      setLoading(false);
      return;
    }
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
    } catch (requestError) {
      setError(extractErrorMessage(requestError, '模拟支付订单加载失败，请返回报名页重试。'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [orderNo]);

  useEffect(() => {
    void refresh(true);
  }, [refresh]);

  useEffect(() => {
    const shouldPoll = checkout
      && (isBuiltinMockPaymentPending(checkout.status)
        || isBuiltinMockCallbackPending(checkout.callbackStatus));
    if (!shouldPoll) {
      return undefined;
    }
    const timer = window.setInterval(() => void refresh(), 2000);
    return () => window.clearInterval(timer);
  }, [checkout, refresh]);

  const submit = async () => {
    if (!checkout || !checkout.allowedOutcomes.includes(outcome)) return;
    setSubmitting(true);
    setError(undefined);
    try {
      await simulateBuiltinMockPayment(checkout.orderNo, {
        outcome,
        callbackDelaySeconds: delaySeconds,
      });
      await refresh();
    } catch (requestError) {
      setError(extractErrorMessage(requestError, '模拟结果提交失败，请刷新订单后重试。'));
    } finally {
      setSubmitting(false);
    }
  };

  const returnToBusiness = () => {
    history.push(resolveBuiltinMockReturnUrl(checkout?.returnUrl));
  };

  const successful = isBuiltinMockPaymentSuccessful(checkout?.status);
  const terminal = checkout ? !isBuiltinMockPaymentPending(checkout.status) : false;
  const callbackPending = isBuiltinMockCallbackPending(checkout?.callbackStatus);
  const currentUrl = typeof window === 'undefined' ? '' : window.location.href;
  const selectedOutcome = OUTCOME_OPTIONS.find((item) => item.value === checkout?.scheduledOutcome);

  if (loading) {
    return (
      <main className="builtin-mock-checkout builtin-mock-checkout--centered">
        <Spin size="large" tip="正在加载模拟支付订单…" />
      </main>
    );
  }

  if (!checkout) {
    return (
      <main className="builtin-mock-checkout builtin-mock-checkout--centered">
        <Result
          status="error"
          title="无法打开模拟收银台"
          subTitle={error || '订单不存在、无权访问，或内置模拟支付插件已停用。'}
          extra={<Button type="primary" onClick={() => history.push('/competitions/register')}>返回赛事报名</Button>}
        />
      </main>
    );
  }

  return (
    <main className="builtin-mock-checkout">
      <header className="builtin-mock-checkout__header">
        <div className="builtin-mock-checkout__brand">
          <span className="builtin-mock-checkout__brand-mark"><ExperimentOutlined /></span>
          <span>Lumira</span>
        </div>
        <Tag color="orange">SANDBOX</Tag>
      </header>

      <section className="builtin-mock-checkout__content">
        <Alert
          className="builtin-mock-checkout__notice"
          type="warning"
          showIcon
          message={checkout.environmentNotice || '模拟环境，不会产生真实扣款'}
          description="模拟结果将通过 RSA2 签名的服务端异步通知处理；收银台提交本身不会直接把订单或报名置为已支付。"
        />

        <div className="builtin-mock-checkout__grid">
          <Card className="builtin-mock-checkout__card" bordered={false}>
            <div className="builtin-mock-checkout__title-row">
              <div>
                <Typography.Title level={2}>内置模拟支付</Typography.Title>
                <Typography.Text type="secondary">仿支付宝电脑网站支付语义的系统调试收银台</Typography.Text>
              </div>
              <Tag color={successful ? 'success' : terminal ? 'default' : 'processing'}>
                {PAYMENT_STATUS_LABELS[checkout.status] || checkout.status}
              </Tag>
            </div>

            <Descriptions className="builtin-mock-checkout__summary" column={1} size="small">
              <Descriptions.Item label="订单内容">{checkout.subject}</Descriptions.Item>
              <Descriptions.Item label="订单号">
                <Typography.Text copyable>{checkout.orderNo}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="应付金额">
                <Typography.Text className="builtin-mock-checkout__amount" strong>
                  {formatAmount(checkout.amountMinor, checkout.currency)}
                </Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="交易状态">{checkout.tradeStatus}</Descriptions.Item>
              <Descriptions.Item label="到期时间">{formatTime(checkout.expiresAt)}</Descriptions.Item>
            </Descriptions>

            {error ? <Alert className="builtin-mock-checkout__feedback" type="error" showIcon message={error} /> : null}

            {callbackPending ? (
              <Alert
                className="builtin-mock-checkout__feedback"
                type="info"
                showIcon
                icon={<ClockCircleOutlined />}
                message={`${selectedOutcome?.label || checkout.scheduledOutcome || '模拟结果'}已提交，${CALLBACK_STATUS_LABELS[checkout.callbackStatus || ''] || '等待服务端处理'}`}
                description={`计划回调时间：${formatTime(checkout.callbackScheduledAt)}。刷新页面或服务重启不会丢失该任务。`}
              />
            ) : null}

            {terminal ? (
              <Result
                className="builtin-mock-checkout__result"
                status={successful ? 'success' : 'info'}
                icon={successful ? <CheckCircleOutlined /> : undefined}
                title={successful ? '模拟支付成功' : PAYMENT_STATUS_LABELS[checkout.status] || checkout.status}
                subTitle={successful
                  ? '服务端异步通知已完成验签，订单结果已经同步。'
                  : '本次支付尝试已结束，可返回报名页查看结果或重新选择支付方式。'}
                extra={<Button type="primary" onClick={returnToBusiness}>查看报名支付结果</Button>}
              />
            ) : checkout.allowedOutcomes.length > 0 ? (
              <div className="builtin-mock-checkout__form">
                <Typography.Title level={4}>选择模拟结果</Typography.Title>
                <Radio.Group
                  className="builtin-mock-checkout__outcomes"
                  value={outcome}
                  onChange={(event) => setOutcome(event.target.value)}
                >
                  {OUTCOME_OPTIONS.filter((item) => checkout.allowedOutcomes.includes(item.value)).map((item) => (
                    <Radio.Button key={item.value} value={item.value}>
                      <strong>{item.label}</strong>
                      <small>{item.description}</small>
                    </Radio.Button>
                  ))}
                </Radio.Group>

                <div className="builtin-mock-checkout__delay">
                  <div>
                    <Typography.Text strong>异步通知延迟</Typography.Text>
                    <Typography.Paragraph type="secondary">可测试即时通知、轮询等待和服务重启恢复。</Typography.Paragraph>
                  </div>
                  <Select
                    aria-label="异步通知延迟"
                    value={delaySeconds}
                    onChange={setDelaySeconds}
                    options={checkout.delayOptions.map((seconds) => ({
                      value: seconds,
                      label: seconds === 0 ? '立即回调' : `${seconds} 秒后回调`,
                    }))}
                  />
                </div>

                <Space wrap>
                  <Button type="primary" size="large" loading={submitting} onClick={() => void submit()}>
                    提交模拟结果
                  </Button>
                  <Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void refresh()}>
                    刷新订单
                  </Button>
                  <Button onClick={returnToBusiness}>返回报名页</Button>
                </Space>
              </div>
            ) : (
              <Alert
                className="builtin-mock-checkout__feedback"
                type="info"
                showIcon
                message="模拟结果已经提交"
                description="系统正在等待异步通知处理，请稍候或刷新订单状态。"
              />
            )}
          </Card>

          <aside className="builtin-mock-checkout__qr">
            <Typography.Title level={4}>移动端继续</Typography.Title>
            <QRCode value={currentUrl} size={188} bordered={false} />
            <Typography.Paragraph type="secondary">
              使用已登录 Lumira 的移动设备扫描二维码，打开同一笔模拟订单。
            </Typography.Paragraph>
            <div className="builtin-mock-checkout__security-note">
              <Typography.Text strong>服务端可信结果</Typography.Text>
              <Typography.Text type="secondary">
                系统会校验 RSA2 签名、应用标识、订单号与金额，并对重复通知做幂等处理。
              </Typography.Text>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
};

export default BuiltinMockPaymentCheckoutPage;
