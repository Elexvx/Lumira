const ORDER_TIMESTAMP_PATTERN = /^(?:SBX|SIM)-(\d{13})-/i;

export const getPaymentOrderCreatedAtMillis = (orderNo: string, createdAt?: string | null) => {
  const embeddedTimestamp = ORDER_TIMESTAMP_PATTERN.exec(orderNo)?.[1];
  if (embeddedTimestamp) {
    return Number(embeddedTimestamp);
  }

  if (!createdAt) {
    return 0;
  }

  const parsed = Date.parse(createdAt.includes('T') ? createdAt : createdAt.replace(' ', 'T'));
  return Number.isFinite(parsed) ? parsed : 0;
};

export const sortPaymentOrdersNewestFirst = <T extends { orderNo: string; createdAt?: string | null }>(orders: T[]) =>
  [...orders].sort((left, right) =>
    getPaymentOrderCreatedAtMillis(right.orderNo, right.createdAt)
      - getPaymentOrderCreatedAtMillis(left.orderNo, left.createdAt));

export const formatPaymentOrderCreatedAt = (
  orderNo: string,
  createdAt: string | null | undefined,
  locale: string,
) => {
  const timestamp = getPaymentOrderCreatedAtMillis(orderNo, createdAt);
  if (!timestamp) {
    return '-';
  }

  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp));
};
