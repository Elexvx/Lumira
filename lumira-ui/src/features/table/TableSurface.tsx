import { forwardRef, type HTMLAttributes } from 'react';

const mergeClassName = (...classNames: Array<string | false | undefined>) =>
  classNames.filter(Boolean).join(' ');

export interface TableSurfaceProps extends HTMLAttributes<HTMLDivElement> {
  adaptiveSpacing?: boolean;
}

export const TableSurface = forwardRef<HTMLDivElement, TableSurfaceProps>(({
  adaptiveSpacing = false,
  className,
  ...props
}, ref) => (
  <div
    {...props}
    ref={ref}
    className={mergeClassName(
      'saas-table-wrap',
      adaptiveSpacing && 'saas-table-wrap--adaptive-spacing',
      className,
    )}
  />
));

TableSurface.displayName = 'TableSurface';
