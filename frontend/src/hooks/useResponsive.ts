import { useResponsiveTable } from '@/components/ResponsiveTable';

export const useResponsive = () => {
  const responsive = useResponsiveTable();
  return {
    screens: responsive.screens,
    isMobile: responsive.isMobile,
    isTablet: responsive.isTablet,
    isDesktop: responsive.isDesktop,
  };
};
