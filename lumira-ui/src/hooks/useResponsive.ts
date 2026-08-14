import { Grid } from 'antd';
import { useMemo } from 'react';
import { useViewportTier } from '@/theme/responsive';

export const useResponsive = () => {
  const screens = Grid.useBreakpoint();
  const profile = useViewportTier();

  return useMemo(() => {
    const isDesktop = Boolean(screens.lg);
    const isTablet = Boolean(screens.md && !screens.lg);
    const isMobile = !screens.md;

    return {
      screens,
      profile,
      tier: profile.tier,
      isMobile,
      isTablet,
      isDesktop,
      level: isDesktop ? 'desktop' : isTablet ? 'tablet' : 'mobile',
    };
  }, [profile, screens]);
};
