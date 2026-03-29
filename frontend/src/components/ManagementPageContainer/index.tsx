import { PageContainer } from '@ant-design/pro-components';
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
    <PageContainer
      className={className}
      ghost
      title={title}
      subTitle={description}
      extra={extra}
      style={{
        height: '100%',
        minHeight: 0,
        ...style,
      }}
      content={null}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
          height: '100%',
          gap: 16,
          ...bodyStyle,
        }}
      >
        {children}
      </div>
      {footer ? <div>{footer}</div> : null}
    </PageContainer>
  );
};
