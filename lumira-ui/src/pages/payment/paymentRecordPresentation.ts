export const EMPTY_PAYMENT_VALUE = '—';

export const PAYMENT_IDENTIFIER_LABELS = {
  orderNo: '订单号',
  providerOrderNo: '支付流水号',
  registrationNo: '报名号',
} as const;

export const PAYMENT_DETAIL_PROVIDER_ORDER_LABEL = '支付流水号';

export const PAYMENT_STATUS_VALUE_ENUM = {
  NOT_REQUIRED: { text: '无需支付' },
  PENDING: { text: '待付款' },
  PAID: { text: '已支付' },
  SUCCESS: { text: '支付成功' },
  SETTLED: { text: '已结算' },
  CONFIRMED: { text: '已确认' },
  REFUNDING: { text: '退款中' },
  REFUNDED: { text: '已退款' },
  FAILED: { text: '支付失败' },
  CANCELLED: { text: '已取消' },
  EXPIRED: { text: '已超时' },
  CLOSED: { text: '已关闭' },
  PENDING_PAYMENT: { text: '待生成订单' },
} satisfies Record<string, { text: string }>;

export const formatPaymentIdentifier = (value?: string | null) => value?.trim() || EMPTY_PAYMENT_VALUE;

export const getPaymentStatusText = (status?: string | null) => {
  const normalized = status || 'PENDING_PAYMENT';
  return PAYMENT_STATUS_VALUE_ENUM[normalized as keyof typeof PAYMENT_STATUS_VALUE_ENUM]?.text || normalized;
};
