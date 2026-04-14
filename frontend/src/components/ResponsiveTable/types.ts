import type { ReactNode } from 'react';
import type { ProColumns } from '@ant-design/pro-components';

export type ResponsiveTableLevel = 'mobile' | 'tablet' | 'desktop';

export type ResponsiveBreakpointMap = Partial<Record<'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'xxl', boolean>>;

export interface ResponsiveTableState {
  screens: ResponsiveBreakpointMap;
  level: ResponsiveTableLevel;
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
}

export interface ResponsiveColumnMeta {
  responsiveLevel?: ResponsiveTableLevel | ResponsiveTableLevel[];
  importance?: number;
  mobileHidden?: boolean;
  tabletHidden?: boolean;
  desktopFixed?: 'left' | 'right';
  mobileInDescriptions?: boolean;
  ellipsisText?: boolean;
  copyableText?: boolean;
}

export type ResponsiveColumn<T extends object> = ProColumns<T> & ResponsiveColumnMeta;

export interface ResponsiveActionItem {
  key: string;
  label: ReactNode;
  onClick?: () => void;
  icon?: ReactNode;
  danger?: boolean;
  disabled?: boolean;
  hidden?: boolean;
}

