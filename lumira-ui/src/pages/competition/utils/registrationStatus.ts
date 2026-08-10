export const registrationStatusLabels = {
  DRAFT: '草稿',
  CREATED: '待提交材料',
  MATERIAL_SUBMITTED: '材料已提交',
  PENDING_PAYMENT: '待付款',
  PAID: '已支付',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
} as const;

export const registrationStatusValueEnum: Record<string, { text: string }> = Object.fromEntries(
  Object.entries(registrationStatusLabels).map(([status, text]) => [status, { text }]),
);

export const getRegistrationStatusLabel = (
  status?: string | null,
  fallbackStatus: keyof typeof registrationStatusLabels = 'CREATED',
) => {
  const normalized = status?.trim().toUpperCase() || fallbackStatus;
  return registrationStatusLabels[normalized as keyof typeof registrationStatusLabels] || normalized;
};
