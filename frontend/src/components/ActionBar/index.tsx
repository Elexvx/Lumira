import { ProCard } from '@ant-design/pro-components';
import { Space } from 'antd';
import type { CSSProperties, PropsWithChildren, ReactNode } from 'react';

export interface ActionBarProps extends PropsWithChildren {
  left?: ReactNode;
  right?: ReactNode;
  batch?: ReactNode;
  extra?: ReactNode;
  className?: string;
  style?: CSSProperties;
}

export const ActionBar = ({
  children,
  left,
  right,
  batch,
  extra,
  className,
  style,
}: ActionBarProps) => {
  const primary = left ?? children;
  const mergedClassName = ['saas-action-bar', className].filter(Boolean).join(' ');

  return (
    <ProCard className={mergedClassName} style={style}>
      <Space
        wrap
        style={{
          width: '100%',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
        }}
      >
        <Space wrap size={8} align="start" style={{ minWidth: 0, flex: 1 }}>
          {primary}
          {batch ? <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>{batch}</div> : null}
        </Space>
        <Space wrap size={8} align="end" style={{ flexShrink: 0 }}>
          {right}
          {extra}
        </Space>
      </Space>
    </ProCard>
  );
};
