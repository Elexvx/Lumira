import { Grid } from 'antd';
import { useMemo } from 'react';

export const useResponsive = () => {
  const screens = Grid.useBreakpoint();

  return useMemo(() => {
    const isDesktop = Boolean(screens.lg);
    const isTablet = Boolean(screens.md && !screens.lg);
    const isMobile = !screens.md;

    return {
      screens,
      isMobile,
      isTablet,
      isDesktop,
      level: isDesktop ? 'desktop' : isTablet ? 'tablet' : 'mobile',
    };
  }, [screens]);
};
