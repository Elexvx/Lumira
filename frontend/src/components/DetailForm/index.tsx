import { Form, type FormProps } from 'antd';
import type { ReactNode } from 'react';
import { useDetailLayout } from '@/hooks/useDetailLayout';

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export interface DetailFormProps extends FormProps {
  mobileLayout?: 'vertical' | 'horizontal';
  detailLabelWidth?: number;
  children: ReactNode;
}

export const DetailForm = ({
  className,
  layout,
  labelAlign,
  labelCol,
  wrapperCol,
  colon,
  labelWrap,
  detailLabelWidth,
  children,
  ...rest
}: DetailFormProps) => {
  const detailLayout = useDetailLayout({ mobileLabelWidth: detailLabelWidth });
  const resolvedLayout = layout ?? 'vertical';
  const resolvedLabelCol = resolvedLayout === 'horizontal' ? (labelCol ?? detailLayout.formLabelCol) : labelCol;
  const resolvedWrapperCol = resolvedLayout === 'horizontal' ? (wrapperCol ?? detailLayout.formWrapperCol) : wrapperCol;

  return (
    <Form
      {...rest}
      layout={resolvedLayout}
      labelAlign={labelAlign ?? 'right'}
      colon={colon ?? true}
      labelWrap={labelWrap ?? true}
      labelCol={resolvedLabelCol}
      wrapperCol={resolvedWrapperCol}
      className={mergeClassName('saas-detail-form', className)}
    >
      {children}
    </Form>
  );
};
