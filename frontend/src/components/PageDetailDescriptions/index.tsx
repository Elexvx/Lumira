import { Descriptions, Typography } from 'antd';
import type { DescriptionsProps } from 'antd';
import { ProDescriptions, type ProDescriptionsProps } from '@ant-design/pro-components';
import type { ReactNode } from 'react';
import { DETAIL_EMPTY_TEXT } from '@/constants/ui';
import { useDetailLayout } from '@/hooks/useDetailLayout';

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

const isRenderableNode = (value: unknown): value is ReactNode => {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || value === null || value === undefined;
};

export const renderDetailValue = (value: unknown, emptyText: ReactNode = DETAIL_EMPTY_TEXT): ReactNode => {
  if (value === null || value === undefined || value === '') {
    return emptyText;
  }

  if (Array.isArray(value)) {
    if (!value.length) {
      return emptyText;
    }
    return value.map((item) => renderDetailValue(item, emptyText)).filter((item) => item !== emptyText).join(', ') || emptyText;
  }

  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'bigint') {
    return String(value);
  }

  if (value instanceof Date) {
    return value.toLocaleString('zh-CN', { hour12: false });
  }

  if (isRenderableNode(value)) {
    return value;
  }

  try {
    return (
      <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
        {JSON.stringify(value, null, 2)}
      </Typography.Paragraph>
    );
  } catch {
    return String(value);
  }
};

export interface PageDetailDescriptionsProps extends DescriptionsProps {
  detailColumns?: number;
  detailLabelWidth?: number;
  emptyText?: ReactNode;
}

export const PageDetailDescriptions = ({
  className,
  column,
  bordered = true,
  size = 'small',
  colon = true,
  labelStyle,
  contentStyle,
  detailColumns,
  detailLabelWidth,
  ...rest
}: PageDetailDescriptionsProps) => {
  const layout = useDetailLayout();
  const resolvedColumn = column ?? detailColumns ?? layout.detailColumn;
  const resolvedLabelWidth = detailLabelWidth ?? layout.detailLabelWidth;

  return (
    <Descriptions
      {...rest}
      className={mergeClassName('saas-detail-descriptions', className)}
      column={resolvedColumn}
      bordered={bordered}
      size={size}
      colon={colon}
      labelStyle={{
        ...layout.detailLabelStyle,
        ...labelStyle,
        width: labelStyle?.width ?? `${resolvedLabelWidth}px`,
        minWidth: labelStyle?.minWidth ?? `${resolvedLabelWidth}px`,
        maxWidth: labelStyle?.maxWidth ?? `${resolvedLabelWidth}px`,
      }}
      contentStyle={{
        ...layout.detailContentStyle,
        ...contentStyle,
      }}
    />
  );
};

export type PageDetailProDescriptionsProps<RecordType extends Record<string, any> = Record<string, any>, ValueType = 'text'> =
  ProDescriptionsProps<RecordType, ValueType> & {
    detailColumns?: number;
    detailLabelWidth?: number;
    emptyText?: ReactNode;
  };

export const PageDetailProDescriptions = <RecordType extends Record<string, any>, ValueType = 'text'>({
  className,
  column,
  bordered = true,
  size = 'small',
  colon = true,
  labelStyle,
  contentStyle,
  detailColumns,
  detailLabelWidth,
  emptyText = DETAIL_EMPTY_TEXT,
  columns,
  ...rest
}: PageDetailProDescriptionsProps<RecordType, ValueType>) => {
  const layout = useDetailLayout();
  const resolvedColumn = column ?? detailColumns ?? layout.detailColumn;
  const resolvedLabelWidth = detailLabelWidth ?? layout.detailLabelWidth;
  const nextColumns = columns?.map((item) => ({
    ...item,
    renderText: item.renderText ?? ((value: unknown) => renderDetailValue(value, emptyText)),
  }));

  return (
    <ProDescriptions<RecordType, ValueType>
      {...rest}
      className={mergeClassName('saas-detail-descriptions', className)}
      column={resolvedColumn}
      bordered={bordered}
      size={size}
      colon={colon}
      labelStyle={{
        ...layout.detailLabelStyle,
        ...labelStyle,
        width: labelStyle?.width ?? `${resolvedLabelWidth}px`,
        minWidth: labelStyle?.minWidth ?? `${resolvedLabelWidth}px`,
        maxWidth: labelStyle?.maxWidth ?? `${resolvedLabelWidth}px`,
      }}
      contentStyle={{
        ...layout.detailContentStyle,
        ...contentStyle,
      }}
      emptyText={emptyText}
      columns={nextColumns}
    />
  );
};
