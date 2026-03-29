import { Empty } from 'antd';
import type { ReactNode } from 'react';

export const EmptyState = ({
  description = '暂无数据',
  children,
}: {
  description?: ReactNode;
  children?: ReactNode;
}) => <Empty description={description}>{children}</Empty>;
