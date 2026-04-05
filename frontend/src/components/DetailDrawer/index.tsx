import { ProDescriptions } from '@ant-design/pro-components';
import { Alert, Drawer, Empty, Spin } from 'antd';
import type { ProDescriptionsItemProps } from '@ant-design/pro-components';
import type { CSSProperties, ReactNode } from 'react';
import { useResponsive } from '@/hooks/useResponsive';

export type DetailStatus = 'idle' | 'loading' | 'success' | 'error' | 'empty';

export interface DetailDrawerProps<T = Record<string, unknown>> {
  title: ReactNode;
  open: boolean;
  onClose: () => void;
  className?: string;
  width?: number | string;
  status?: DetailStatus;
  errorMessage?: string;
  dataSource?: T;
  columns?: ProDescriptionsItemProps<T>[];
  children?: ReactNode;
  footer?: ReactNode;
  extra?: ReactNode;
  bodyStyle?: CSSProperties;
}

export const DetailDrawer = <T = Record<string, unknown>>({
  title,
  open,
  onClose,
  className,
  width = 720,
  status = 'idle',
  errorMessage,
  dataSource,
  columns,
  children,
  footer,
  extra,
  bodyStyle,
}: DetailDrawerProps<T>) => {
  const { isMobile } = useResponsive();
  const mergedClassName = ['saas-detail-drawer', className].filter(Boolean).join(' ');

  const renderContent = () => {
    if (status === 'loading') {
      return <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}><Spin /></div>;
    }
    if (status === 'error') {
      return <Alert type="error" showIcon message="详情加载失败" description={errorMessage || '请稍后重试'} />;
    }
    if (status === 'empty' || (!dataSource && !children)) {
      return <Empty description="暂无详情数据" />;
    }

    return (
      <>
        {columns?.length && dataSource ? <ProDescriptions<T> dataSource={dataSource} columns={columns} column={isMobile ? 1 : 2} /> : null}
        {children}
      </>
    );
  };

  return (
    <Drawer
      className={mergedClassName}
      title={title}
      open={open}
      onClose={onClose}
      width={isMobile ? '100%' : width}
      destroyOnClose
      maskClosable
      extra={extra}
      styles={{ body: { display: 'flex', flexDirection: 'column', gap: 16, padding: 16, minHeight: 0, ...bodyStyle } }}
    >
      {renderContent()}
      {footer ? <div style={{ marginTop: 'auto' }}>{footer}</div> : null}
    </Drawer>
  );
};
