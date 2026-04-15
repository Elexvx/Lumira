import { Descriptions, Typography } from 'antd';
import type { DescriptionsProps } from 'antd';
import type { ProDescriptionsProps } from '@ant-design/pro-components';
import type { FormProps } from 'antd';
import type { ReactNode } from 'react';
import { DETAIL_EMPTY_TEXT } from '@/constants/ui';
import { useDetailLayout } from '@/hooks/useDetailLayout';

const isPrimitiveNode = (value: unknown): value is ReactNode =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || value === null || value === undefined;

export const renderDetailValue = (value: unknown, emptyText: ReactNode = DETAIL_EMPTY_TEXT): ReactNode => {
  if (value === null || value === undefined || value === '') {
    return emptyText;
  }

  if (Array.isArray(value)) {
    const normalized = value
      .map((item) => renderDetailValue(item, emptyText))
      .filter((item) => item !== emptyText)
      .join(', ');
    return normalized || emptyText;
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

  if (isPrimitiveNode(value)) {
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

export const useDetailFormProps = (overrides: FormProps = {}): FormProps => {
  const detailLayout = useDetailLayout();
  const layout = overrides.layout ?? detailLayout.formLayout;
  const isHorizontal = layout === 'horizontal';

  return {
    layout,
    labelAlign: overrides.labelAlign ?? 'right',
    colon: overrides.colon ?? true,
    labelWrap: overrides.labelWrap ?? true,
    labelCol: isHorizontal ? overrides.labelCol ?? detailLayout.formLabelCol : overrides.labelCol,
    wrapperCol: isHorizontal ? overrides.wrapperCol ?? detailLayout.formWrapperCol : overrides.wrapperCol,
    ...overrides,
  };
};

export const useDetailDescriptionsProps = (
  overrides: DescriptionsProps = {},
  options: { detailLabelWidth?: number } = {},
): DescriptionsProps => {
  const detailLayout = useDetailLayout({
    mobileLabelWidth: options.detailLabelWidth,
  });
  const resolvedLabelWidth = options.detailLabelWidth ?? detailLayout.detailLabelWidth;

  return {
    bordered: true,
    size: 'small',
    colon: true,
    column: detailLayout.detailColumn,
    className: ['saas-detail-descriptions', overrides.className].filter(Boolean).join(' '),
    labelStyle: {
      ...detailLayout.detailLabelStyle,
      ...overrides.labelStyle,
      width: overrides.labelStyle?.width ?? `${resolvedLabelWidth}px`,
      minWidth: overrides.labelStyle?.minWidth ?? `${resolvedLabelWidth}px`,
      maxWidth: overrides.labelStyle?.maxWidth ?? `${resolvedLabelWidth}px`,
    },
    contentStyle: {
      ...detailLayout.detailContentStyle,
      ...overrides.contentStyle,
    },
    ...overrides,
  };
};

export const useDetailProDescriptionsProps = <
  RecordType extends Record<string, any>,
  ValueType = 'text',
>(
  overrides: ProDescriptionsProps<RecordType, ValueType>,
  options: { detailLabelWidth?: number; emptyText?: ReactNode } = {},
): ProDescriptionsProps<RecordType, ValueType> => {
  const descriptionsProps = useDetailDescriptionsProps(
    {
      ...overrides,
      column: overrides.column,
    },
    { detailLabelWidth: options.detailLabelWidth },
  );
  const emptyText = options.emptyText ?? DETAIL_EMPTY_TEXT;

  return {
    ...overrides,
    bordered: descriptionsProps.bordered,
    size: descriptionsProps.size,
    colon: descriptionsProps.colon,
    className: descriptionsProps.className,
    labelStyle: descriptionsProps.labelStyle,
    contentStyle: descriptionsProps.contentStyle,
    column: descriptionsProps.column,
    emptyText,
    columns: overrides.columns?.map((item) => ({
      ...item,
      renderText: item.renderText ?? ((value: unknown) => renderDetailValue(value, emptyText)),
    })),
  };
};

export const DetailDescriptions = Descriptions;
