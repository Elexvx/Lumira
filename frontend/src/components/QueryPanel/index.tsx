import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Card, Form, Space, Typography } from 'antd';
import type { FormInstance, FormProps } from 'antd';
import { Children, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { useResponsive } from '@/hooks/useResponsive';

export interface QueryPanelProps<T extends object = Record<string, unknown>> {
  form?: FormInstance<T>;
  title?: ReactNode;
  description?: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  actions?: ReactNode;
  footer?: ReactNode;
  onSearch?: FormProps<T>['onFinish'];
  onReset?: () => void;
  columns?: number;
  collapseCount?: number;
  defaultCollapsed?: boolean;
  collapsible?: boolean;
  className?: string;
  style?: CSSProperties;
}

const DEFAULT_COLUMNS = 4;

export const QueryPanel = <T extends object = Record<string, unknown>>({
  form,
  title,
  description,
  extra,
  children,
  actions,
  footer,
  onSearch,
  onReset,
  columns = DEFAULT_COLUMNS,
  collapseCount = DEFAULT_COLUMNS,
  defaultCollapsed = true,
  collapsible = true,
  className,
  style,
}: QueryPanelProps<T>) => {
  const { isMobile, isTablet } = useResponsive();
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const childList = useMemo(() => Children.toArray(children), [children]);
  const visibleChildren = collapsed && collapsible ? childList.slice(0, collapseCount) : childList;
  const gridColumns = isMobile ? 1 : isTablet ? Math.min(2, columns) : columns;

  const handleReset: FormProps<T>['onReset'] = () => {
    onReset?.();
  };

  return (
    <Card
      bordered={false}
      className={className}
      bodyStyle={{ padding: 20 }}
      style={{
        ...style,
      }}
    >
      {(title || description || extra) ? (
        <Space align="start" style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
          <div style={{ minWidth: 0 }}>
            {title ? (
              <Typography.Title level={5} style={{ marginBottom: description ? 8 : 0 }}>
                {title}
              </Typography.Title>
            ) : null}
            {description ? (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {description}
              </Typography.Paragraph>
            ) : null}
          </div>
          {extra ? <div style={{ flexShrink: 0 }}>{extra}</div> : null}
        </Space>
      ) : null}

      <Form<T> form={form} layout="vertical" onFinish={onSearch} onReset={handleReset}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${gridColumns}, minmax(0, 1fr))`,
            gap: 16,
          }}
        >
          {visibleChildren}
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            marginTop: 16,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ minWidth: 0 }}>{footer}</div>
          <Space wrap size={8} style={{ justifyContent: 'flex-end' }}>
            {onReset ? (
              <Button htmlType="reset">
                重置
              </Button>
            ) : null}
            {onSearch ? (
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            ) : null}
            {actions}
            {collapsible && childList.length > collapseCount ? (
              <a
                onClick={() => setCollapsed((current) => !current)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
              >
                {collapsed ? '展开' : '收起'}
                {collapsed ? <DownOutlined /> : <UpOutlined />}
              </a>
            ) : null}
          </Space>
        </div>
      </Form>
    </Card>
  );
};
