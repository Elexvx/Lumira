import { Typography } from 'antd';
import type { ReactNode } from 'react';

interface ResponsiveTextProps {
  value?: ReactNode;
  copyable?: boolean;
  className?: string;
  maxWidth?: number | string;
}

export const ResponsiveText = ({ value, copyable = false, className, maxWidth = '100%' }: ResponsiveTextProps) => {
  if (value === null || value === undefined || value === '') {
    return <span className={className}>-</span>;
  }

  const text = typeof value === 'string' || typeof value === 'number' ? String(value) : value;

  if (typeof text !== 'string') {
    return <span className={className}>{text}</span>;
  }

  return (
    <Typography.Text
      className={className}
      copyable={copyable ? { text } : undefined}
      ellipsis={{ tooltip: text }}
      style={{ display: 'inline-block', maxWidth, minWidth: 0, verticalAlign: 'middle' }}
    >
      {text}
    </Typography.Text>
  );
};

