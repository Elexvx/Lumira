import { Drawer, Empty, Spin, Descriptions, type DescriptionsProps } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { useResponsive } from '@/hooks/useResponsive';

export interface DetailDrawerProps {
  title: ReactNode;
  open: boolean;
  onClose: () => void;
  className?: string;
  width?: number | string;
  loading?: boolean;
  descriptionItems?: DescriptionsProps['items'];
  children?: ReactNode;
  footer?: ReactNode;
  extra?: ReactNode;
  bodyStyle?: CSSProperties;
}

export const DetailDrawer = ({
  title,
  open,
  onClose,
  className,
  width = 720,
  loading = false,
  descriptionItems,
  children,
  footer,
  extra,
  bodyStyle,
}: DetailDrawerProps) => {
  const { isMobile } = useResponsive();
  const mergedClassName = ['saas-detail-drawer', className].filter(Boolean).join(' ');

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
      styles={{
        body: {
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          padding: 16,
          minHeight: 0,
          ...bodyStyle,
        },
      }}
    >
      {loading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
          <Spin />
        </div>
      ) : (
        <>
          {descriptionItems && descriptionItems.length > 0 ? (
            <Descriptions bordered size="small" column={isMobile ? 1 : 2} items={descriptionItems} />
          ) : null}
          {children ? children : !descriptionItems ? <Empty description="暂无详情数据" /> : null}
          {footer ? <div style={{ marginTop: 'auto' }}>{footer}</div> : null}
        </>
      )}
    </Drawer>
  );
};
