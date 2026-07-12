export const buildCleanSandboxOrderPath = (href: string) => {
  const url = new URL(href, 'https://localhost');
  const orderNo = url.searchParams.get('orderNo') || url.searchParams.get('out_trade_no');
  const params = new URLSearchParams({ tab: 'sandbox-orders' });
  if (orderNo) {
    params.set('orderNo', orderNo);
  }
  return `${url.pathname}?${params.toString()}`;
};
