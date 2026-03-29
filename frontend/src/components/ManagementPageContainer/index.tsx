import { Card, Space, Typography } from 'antd';
import type { CSSProperties, PropsWithChildren, ReactNode } from 'react';

export interface ManagementPageContainerProps extends PropsWithChildren {
  title: ReactNode;
  description?: ReactNode;
  extra?: ReactNode;
  footer?: ReactNode;
  bodyStyle?: CSSProperties;
  className?: string;
  style?: CSSProperties;
}

export const ManagementPageContainer = ({
  title,
  description,
  extra,
  footer,
  bodyStyle,
  className,
  style,
  children,
}: ManagementPageContainerProps) => {
  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        height: '100%',
        gap: 16,
        ...style,
      }}
    >
      <Card bodyStyle={{ padding: 20 }} bordered={false}>
        <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
          <div style={{ minWidth: 0 }}>
            <Typography.Title level={4} style={{ marginBottom: 8 }}>
              {title}
            </Typography.Title>
            {description ? (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {description}
              </Typography.Paragraph>
            ) : null}
          </div>
          {extra ? <div style={{ flexShrink: 0 }}>{extra}</div> : null}
        </Space>
      </Card>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
          flex: 1,
          gap: 16,
          ...bodyStyle,
        }}
      >
        {children}
      </div>
      {footer ? <div>{footer}</div> : null}
    </div>
  );
};
