import { Grid } from 'antd';

export const useResponsive = () => {
  const screens = Grid.useBreakpoint();
  return {
    screens,
    isMobile: !screens.md,
    isTablet: Boolean(screens.md && !screens.lg),
    isDesktop: Boolean(screens.lg),
  };
};
