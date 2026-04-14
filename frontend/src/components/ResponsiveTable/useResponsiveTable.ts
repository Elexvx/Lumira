import { Grid } from 'antd';
import { useMemo } from 'react';
import type { ResponsiveTableState } from './types';

export const useResponsiveTable = (): ResponsiveTableState => {
  const screens = Grid.useBreakpoint();

  return useMemo(() => {
    const isDesktop = Boolean(screens.lg);
    const isTablet = Boolean(screens.md && !screens.lg);
    const isMobile = !screens.md;

    return {
      screens,
      level: isDesktop ? 'desktop' : isTablet ? 'tablet' : 'mobile',
      isMobile,
      isTablet,
      isDesktop,
    };
  }, [screens]);
};

