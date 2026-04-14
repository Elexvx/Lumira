import { useResponsive } from '@/hooks/useResponsive';
import {
  DETAIL_LAYOUT_COLUMNS,
  DETAIL_LAYOUT_LABEL_WIDTHS,
  FORM_LAYOUT_LABEL_WIDTHS,
} from '@/constants/ui';

export interface DetailLayoutOptions {
  desktopColumns?: number;
  tabletColumns?: number;
  mobileColumns?: number;
  desktopLabelWidth?: number;
  tabletLabelWidth?: number;
  mobileLabelWidth?: number;
  desktopFormLabelWidth?: number;
  tabletFormLabelWidth?: number;
  mobileFormLabelWidth?: number;
}

const buildLabelCol = (width: number) => ({ flex: `${width}px` });

export const useDetailLayout = (options: DetailLayoutOptions = {}) => {
  const responsive = useResponsive();

  const detailLabelWidth = responsive.isDesktop
    ? options.desktopLabelWidth ?? DETAIL_LAYOUT_LABEL_WIDTHS.desktop
    : responsive.isTablet
      ? options.tabletLabelWidth ?? DETAIL_LAYOUT_LABEL_WIDTHS.tablet
      : options.mobileLabelWidth ?? DETAIL_LAYOUT_LABEL_WIDTHS.mobile;

  const detailColumn = responsive.isDesktop
    ? options.desktopColumns ?? DETAIL_LAYOUT_COLUMNS.desktop
    : responsive.isTablet
      ? options.tabletColumns ?? DETAIL_LAYOUT_COLUMNS.tablet
      : options.mobileColumns ?? DETAIL_LAYOUT_COLUMNS.mobile;

  const formLabelWidth = responsive.isDesktop
    ? options.desktopFormLabelWidth ?? FORM_LAYOUT_LABEL_WIDTHS.desktop
    : responsive.isTablet
      ? options.tabletFormLabelWidth ?? FORM_LAYOUT_LABEL_WIDTHS.tablet
      : options.mobileFormLabelWidth ?? FORM_LAYOUT_LABEL_WIDTHS.mobile;

  return {
    ...responsive,
    detailColumn,
    detailLabelWidth,
    detailLabelStyle: {
      width: `${detailLabelWidth}px`,
      minWidth: `${detailLabelWidth}px`,
      maxWidth: `${detailLabelWidth}px`,
      textAlign: 'right' as const,
      whiteSpace: 'normal' as const,
    },
    detailContentStyle: {
      textAlign: 'left' as const,
      minWidth: 0,
    },
    formLayout: responsive.isMobile ? ('vertical' as const) : ('horizontal' as const),
    formLabelCol: {
      ...buildLabelCol(formLabelWidth),
    },
    formWrapperCol: {
      flex: 1,
    },
    formLabelWidth,
  };
};
