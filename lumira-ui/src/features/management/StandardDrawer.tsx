import { Drawer } from 'antd';
import type { DrawerProps } from 'antd';
import { STANDARD_DRAWER_WIDTH_BY_BREAKPOINT } from '@/constants/ui';
import { useResponsive } from '@/hooks/useResponsive';
import { resolveResponsiveValue } from '@/theme/spacing';

export type StandardDrawerProps = Omit<DrawerProps, 'size' | 'width'>;

export const StandardDrawer = ({ className, ...props }: StandardDrawerProps) => {
  const responsive = useResponsive();
  const drawerSize = resolveResponsiveValue(STANDARD_DRAWER_WIDTH_BY_BREAKPOINT, responsive.isMobile);
  const drawerClassName = ['saas-standard-drawer', className].filter(Boolean).join(' ');

  return <Drawer {...props} className={drawerClassName} size={drawerSize} />;
};
