import type { DescriptionsProps } from 'antd';
import type { ProDescriptionsProps } from '@ant-design/pro-components';
import type { FormProps } from 'antd';
import type { ReactNode } from 'react';
import { DETAIL_EMPTY_TEXT, DETAIL_LAYOUT_COLUMNS, DETAIL_LAYOUT_LABEL_WIDTHS, FORM_LAYOUT_LABEL_WIDTHS } from '@/constants/ui';
import { useResponsive } from '@/hooks/useResponsive';

interface DetailLayoutSnapshot {
  detailColumn: number;
  detailLabelWidth: number;
  formLabelWidth: number;
  isMobile: boolean;
}

const resolveDetailLayoutSnapshot = (isMobile: boolean, isTablet: boolean): DetailLayoutSnapshot => {
  const detailColumn = isMobile ? DETAIL_LAYOUT_COLUMNS.mobile : isTablet ? DETAIL_LAYOUT_COLUMNS.tablet : DETAIL_LAYOUT_COLUMNS.desktop;
  const detailLabelWidth = isMobile
    ? DETAIL_LAYOUT_LABEL_WIDTHS.mobile
    : isTablet
      ? DETAIL_LAYOUT_LABEL_WIDTHS.tablet
      : DETAIL_LAYOUT_LABEL_WIDTHS.desktop;
  const formLabelWidth = isMobile ? FORM_LAYOUT_LABEL_WIDTHS.mobile : isTablet ? FORM_LAYOUT_LABEL_WIDTHS.tablet : FORM_LAYOUT_LABEL_WIDTHS.desktop;

  return {
    detailColumn,
    detailLabelWidth,
    formLabelWidth,
    isMobile,
  };
};

export const buildDetailFormProps = (layout: DetailLayoutSnapshot, overrides: FormProps = {}): FormProps => {
  const normalizedLayout = overrides.layout ?? (layout.isMobile ? 'vertical' : 'horizontal');
  const isHorizontal = normalizedLayout === 'horizontal';

  return {
    layout: normalizedLayout,
    labelAlign: overrides.labelAlign ?? 'right',
    colon: overrides.colon ?? true,
    labelWrap: overrides.labelWrap ?? true,
    labelCol: isHorizontal ? overrides.labelCol ?? { flex: `${layout.formLabelWidth}px` } : overrides.labelCol,
    wrapperCol: isHorizontal ? overrides.wrapperCol ?? { flex: 1 } : overrides.wrapperCol,
    ...overrides,
  };
};

export const useDetailFormProps = (overrides: FormProps = {}): FormProps => {
  const responsive = useResponsive();
  const layout = resolveDetailLayoutSnapshot(responsive.isMobile, responsive.isTablet);

  return buildDetailFormProps(layout, overrides);
};

export const buildDetailDescriptionsProps = (
  layout: DetailLayoutSnapshot,
  overrides: DescriptionsProps = {},
  options: { detailLabelWidth?: number } = {},
): DescriptionsProps => {
  const resolvedLabelWidth = options.detailLabelWidth ?? layout.detailLabelWidth;

  return {
    bordered: true,
    size: 'small',
    colon: true,
    column: overrides.column ?? layout.detailColumn,
    className: ['saas-detail-descriptions', overrides.className].filter(Boolean).join(' '),
    labelStyle: {
      ...overrides.labelStyle,
      width: overrides.labelStyle?.width ?? `${resolvedLabelWidth}px`,
      minWidth: overrides.labelStyle?.minWidth ?? `${resolvedLabelWidth}px`,
      maxWidth: overrides.labelStyle?.maxWidth ?? `${resolvedLabelWidth}px`,
      textAlign: overrides.labelStyle?.textAlign ?? 'right',
      whiteSpace: overrides.labelStyle?.whiteSpace ?? 'normal',
    },
    contentStyle: {
      ...overrides.contentStyle,
      textAlign: overrides.contentStyle?.textAlign ?? 'left',
      minWidth: overrides.contentStyle?.minWidth ?? 0,
    },
    ...overrides,
  };
};

export const useDetailDescriptionsProps = (
  overrides: DescriptionsProps = {},
  options: { detailLabelWidth?: number } = {},
): DescriptionsProps => {
  const responsive = useResponsive();
  const layout = resolveDetailLayoutSnapshot(responsive.isMobile, responsive.isTablet);

  return buildDetailDescriptionsProps(layout, overrides, options);
};

export const useDetailProDescriptionsProps = <
  RecordType extends Record<string, any>,
  ValueType = 'text',
>(
  overrides: ProDescriptionsProps<RecordType, ValueType>,
  options: { detailLabelWidth?: number; emptyText?: ReactNode } = {},
): ProDescriptionsProps<RecordType, ValueType> => {
  const descriptionsProps = useDetailDescriptionsProps(overrides, { detailLabelWidth: options.detailLabelWidth });
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
  };
};
