export const statusLabelMap: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

export const dictStatusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

export const renderStatusLabel = (status?: string | null) => statusLabelMap[status || ''] || status || '-';
