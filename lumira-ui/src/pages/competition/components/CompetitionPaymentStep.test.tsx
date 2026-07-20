import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { CompetitionPaymentStep } from './CompetitionPaymentStep';

describe('CompetitionPaymentStep', () => {
  it('renders the Alipay web option with branded hierarchy and friendly scene copy', () => {
    const markup = renderToStaticMarkup(
      <CompetitionPaymentStep
        registrationNo="REG-1001"
        amount="CNY 12.34"
        paymentStatus="PENDING_PAYMENT"
        paymentOptions={[{
          providerCode: 'alipay',
          displayName: '支付宝',
          paymentScene: 'PC_WEB',
        }]}
        selectedProvider="alipay"
        onSelectProvider={() => undefined}
      />,
    );

    expect(markup).toContain('订单已提交成功，请尽快付款');
    expect(markup).toContain('REG-1001');
    expect(markup).toContain('CNY 12.34');
    expect(markup).toContain('支付宝');
    expect(markup).toContain('推荐');
    expect(markup).toContain('网页支付');
    expect(markup).not.toContain('PC_WEB');
  });

  it('does not offer another payment method after payment completion', () => {
    const markup = renderToStaticMarkup(
      <CompetitionPaymentStep
        registrationNo="REG-1001"
        amount="CNY 12.34"
        paymentStatus="PAID"
        paymentOptions={[]}
        onSelectProvider={() => undefined}
      />,
    );

    expect(markup).toContain('支付已完成');
    expect(markup).not.toContain('请选择支付方式');
  });
});
