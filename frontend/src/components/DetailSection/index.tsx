import { Card, type CardProps } from 'antd';
import type { ReactNode } from 'react';
import { DETAIL_SECTION_TITLE_LEVEL } from '@/constants/ui';

type DetailSectionProps = Pick<CardProps, 'loading' | 'extra' | 'style' | 'bodyStyle' | 'className'> & {
  title: ReactNode;
  children: ReactNode;
};

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const DetailSection = ({ title, children, className, bodyStyle, ...rest }: DetailSectionProps) => {
  return (
    <Card
      {...rest}
      size="small"
      title={typeof title === 'string' ? title : <span style={{ fontSize: 16, fontWeight: 600, lineHeight: 1.2 }}>{title}</span>}
      className={mergeClassName('saas-detail-section', className)}
      bodyStyle={{
        paddingTop: 16,
        ...bodyStyle,
      }}
    >
      {children}
    </Card>
  );
};

export { DETAIL_SECTION_TITLE_LEVEL };
