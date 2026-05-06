import type { HTMLAttributes } from 'react';

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const ManagementPageBody = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div {...props} className={mergeClassName('saas-management-page-body', className)} />
);
