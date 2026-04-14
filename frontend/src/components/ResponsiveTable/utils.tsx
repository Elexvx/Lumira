import type { ProColumns } from '@ant-design/pro-components';
import type { ReactNode } from 'react';
import { ResponsiveText } from './ResponsiveText';
import type { ResponsiveColumn, ResponsiveTableLevel, ResponsiveTableState } from './types';

const RESPONSIVE_LIMITS: Record<ResponsiveTableLevel, number> = {
  desktop: Number.POSITIVE_INFINITY,
  tablet: 2,
  mobile: 1,
};

const isLevelMatched = (columnLevel: ResponsiveColumn<any>['responsiveLevel'], level: ResponsiveTableLevel) => {
  if (!columnLevel) {
    return true;
  }

  if (Array.isArray(columnLevel)) {
    return columnLevel.includes(level);
  }

  return columnLevel === level;
};

const getValueFromDataIndex = <T extends object>(record: T, dataIndex: ProColumns<T>['dataIndex']) => {
  if (!dataIndex) {
    return undefined;
  }

  if (Array.isArray(dataIndex)) {
    return dataIndex.reduce<any>((current, key) => (current == null ? current : current[key as keyof typeof current]), record as any);
  }

  return (record as any)[dataIndex as keyof T];
};

const isRenderableString = (value: ReactNode) => typeof value === 'string' || typeof value === 'number';

const shouldShowColumn = <T extends object>(column: ResponsiveColumn<T>, level: ResponsiveTableLevel) => {
  if (!isLevelMatched(column.responsiveLevel, level)) {
    return false;
  }

  if (level === 'mobile' && column.mobileHidden) {
    return false;
  }

  if (level === 'tablet' && column.tabletHidden) {
    return false;
  }

  const importance = column.importance ?? (column.valueType === 'option' ? 0 : 2);
  return importance <= RESPONSIVE_LIMITS[level];
};

const normalizeColumn = <T extends object>(column: ResponsiveColumn<T>, level: ResponsiveTableLevel): ProColumns<T> => {
  const nextColumn: ResponsiveColumn<T> = { ...column };

  delete nextColumn.responsiveLevel;
  delete nextColumn.importance;
  delete nextColumn.mobileHidden;
  delete nextColumn.tabletHidden;
  delete nextColumn.desktopFixed;
  delete nextColumn.mobileInDescriptions;
  delete nextColumn.ellipsisText;
  delete nextColumn.copyableText;

  if (level !== 'desktop') {
    delete nextColumn.fixed;
  } else if (!nextColumn.fixed && column.desktopFixed) {
    nextColumn.fixed = column.desktopFixed;
  }

  if (column.ellipsisText) {
    nextColumn.ellipsis = true;
  }

  if ((column.ellipsisText || column.copyableText) && !column.render) {
    nextColumn.render = (_, record) => {
      const rawValue = getValueFromDataIndex(record, column.dataIndex);
      const fallbackValue = rawValue === null || rawValue === undefined || rawValue === '' ? '-' : rawValue;

      if (!isRenderableString(fallbackValue)) {
        return fallbackValue as ReactNode;
      }

      return <ResponsiveText value={String(fallbackValue)} copyable={Boolean(column.copyableText)} />;
    };
  }

  if (Array.isArray(nextColumn.children) && nextColumn.children.length) {
    nextColumn.children = nextColumn.children
      .filter((child) => shouldShowColumn(child as ResponsiveColumn<T>, level))
      .map((child) => normalizeColumn(child as ResponsiveColumn<T>, level));
  }

  return nextColumn as ProColumns<T>;
};

export const normalizeResponsiveColumns = <T extends object>(columns: ResponsiveColumn<T>[], level: ResponsiveTableLevel) =>
  columns
    .filter((column) => shouldShowColumn(column, level))
    .map((column) => normalizeColumn(column, level));

export const buildResponsivePagination = <T extends { simple?: boolean; showSizeChanger?: boolean } | boolean | undefined>(
  pagination: T,
  state: ResponsiveTableState,
) => {
  if (!pagination) {
    return pagination;
  }

  if (pagination === true) {
    return state.isMobile ? { simple: true, showSizeChanger: false } : pagination;
  }

  if (typeof pagination !== 'object') {
    return pagination;
  }

  return {
    ...pagination,
    simple: state.isMobile || pagination.simple,
    showSizeChanger: state.isMobile ? false : pagination.showSizeChanger !== false,
  };
};

export const buildResponsiveScroll = <T extends object>(
  columns: ResponsiveColumn<T>[],
  state: ResponsiveTableState,
  options?: {
    wide?: boolean;
    fallbackX?: number | string;
  },
) => {
  if (state.isMobile) {
    return undefined;
  }

  if (options?.wide) {
    return {
      x: options.fallbackX || 'max-content',
    };
  }

  const estimatedWidth = columns.reduce((sum, column) => {
    if (typeof column.width === 'number') {
      return sum + column.width;
    }
    if (typeof column.width === 'string' && /^\d+$/.test(column.width)) {
      return sum + Number(column.width);
    }
    if (column.valueType === 'option') {
      return sum + 160;
    }
    return sum;
  }, 0);

  return estimatedWidth > 0 ? { x: estimatedWidth } : undefined;
};

