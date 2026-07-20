import { AlipayOutlined, CheckCircleFilled, CreditCardOutlined, WechatOutlined } from '@ant-design/icons';
import { Alert, Radio, Tag, Typography } from 'antd';
import type { CompetitionPaymentOptionRecord } from '@/services/competition/types';

type CompetitionPaymentStepProps = {
  registrationNo: string;
  amount: string;
  paymentStatus?: string | null;
  paymentOptions: CompetitionPaymentOptionRecord[];
  selectedProvider?: string;
  onSelectProvider: (providerCode: string) => void;
};

const isCompletedPayment = (status?: string | null) => status === 'CONFIRMED' || status === 'PAID';

const paymentSceneLabel = (paymentScene: string) => {
  const normalized = paymentScene.trim().toUpperCase();
  if (normalized === 'PC_WEB') {
    return '网页支付';
  }
  if (normalized === 'WAP') {
    return '手机网页支付';
  }
  if (normalized === 'APP') {
    return 'App 支付';
  }
  return paymentScene;
};

const PaymentProviderLogo = ({ providerCode }: { providerCode: string }) => {
  const normalized = providerCode.toLowerCase();
  if (normalized.includes('alipay')) {
    return <AlipayOutlined />;
  }
  if (normalized.includes('wechat')) {
    return <WechatOutlined />;
  }
  return <CreditCardOutlined />;
};

export const CompetitionPaymentStep = ({
  registrationNo,
  amount,
  paymentStatus,
  paymentOptions,
  selectedProvider,
  onSelectProvider,
}: CompetitionPaymentStepProps) => {
  const isCompleted = isCompletedPayment(paymentStatus);
  const recommendedProvider = paymentOptions.find((option) => option.providerCode.toLowerCase().includes('alipay'))?.providerCode
    || paymentOptions[0]?.providerCode;

  return (
    <section className="competition-payment-step" aria-labelledby="competition-payment-title">
      <div className="competition-payment-summary">
        <CheckCircleFilled className="competition-payment-summary__icon" aria-hidden />
        <div className="competition-payment-summary__content">
          <Typography.Title id="competition-payment-title" level={4} className="competition-payment-summary__title">
            {isCompleted ? '支付已完成' : '订单已提交成功，请尽快付款'}
          </Typography.Title>
          <Typography.Text type="secondary" className="competition-payment-summary__number">
            报名编号：{registrationNo}
          </Typography.Text>
          <div className="competition-payment-summary__amount-row">
            <Typography.Text className="competition-payment-summary__amount-label">应付金额</Typography.Text>
            <Typography.Text className="competition-payment-summary__amount">{amount}</Typography.Text>
          </div>
        </div>
      </div>

      {!isCompleted ? (
        <div className="competition-payment-methods">
          <div>
            <Typography.Title level={5} className="competition-payment-methods__title">请选择支付方式</Typography.Title>
            <Typography.Text type="secondary">选择后将生成安全支付链接，不会自动跳转。</Typography.Text>
          </div>
          {paymentOptions.length ? (
            <Radio.Group
              className="competition-payment-options"
              value={selectedProvider}
              onChange={(event) => onSelectProvider(event.target.value)}
            >
              {paymentOptions.map((option) => {
                const isRecommended = option.providerCode === recommendedProvider;
                return (
                  <Radio
                    key={option.providerCode}
                    className="competition-payment-option"
                    value={option.providerCode}
                  >
                    <span className="competition-payment-option__logo" aria-hidden>
                      <PaymentProviderLogo providerCode={option.providerCode} />
                    </span>
                    <span className="competition-payment-option__content">
                      <span className="competition-payment-option__name-row">
                        <Typography.Text strong className="competition-payment-option__name">{option.displayName}</Typography.Text>
                        {isRecommended ? <Tag color="volcano" className="competition-payment-option__tag">推荐</Tag> : null}
                      </span>
                      <Typography.Text type="secondary" className="competition-payment-option__scene">
                        {paymentSceneLabel(option.paymentScene)}
                      </Typography.Text>
                    </span>
                  </Radio>
                );
              })}
            </Radio.Group>
          ) : (
            <Alert type="warning" showIcon message="当前设备暂无可用支付方式，请联系管理员。" />
          )}
        </div>
      ) : null}
    </section>
  );
};
