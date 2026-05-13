import { Tag } from 'antd';

type StatusMeta = {
  color: string;
  text: string;
};

export const renderStatusTag = (value?: string | null, meta: Record<string, StatusMeta> = {}) => {
  const status = value || '';
  const resolved = meta[status] || { color: 'default', text: value || '-' };
  return <Tag color={resolved.color}>{resolved.text}</Tag>;
};
