const TABLE_CONTAINER_BREAKPOINTS = {
  xs: 0,
  sm: 576,
  md: 768,
  lg: 992,
  xl: 1200,
  xxl: 1600,
} as const;

const TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD = 1100;
const TABLE_HORIZONTAL_SCROLL_BUFFER = 1;

export type TableContainerBreakpoint = keyof typeof TABLE_CONTAINER_BREAKPOINTS;

export const isResponsiveColumnVisible = (
  responsive: readonly string[] | undefined,
  containerWidth: number,
) => {
  if (!responsive?.length || containerWidth <= 0) {
    return true;
  }

  return responsive.some((breakpoint) => {
    const minimumWidth = TABLE_CONTAINER_BREAKPOINTS[breakpoint as TableContainerBreakpoint];
    return minimumWidth !== undefined && containerWidth >= minimumWidth;
  });
};

export const resolveEstimatedTableScrollX = (
  estimatedWidth: number,
  hasFixedColumn: boolean,
  isMobile: boolean,
) => {
  if (hasFixedColumn) {
    return estimatedWidth + TABLE_HORIZONTAL_SCROLL_BUFFER;
  }
  if (estimatedWidth >= TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD || isMobile) {
    return Math.max(
      estimatedWidth + TABLE_HORIZONTAL_SCROLL_BUFFER,
      TABLE_HORIZONTAL_SCROLL_WIDTH_THRESHOLD,
    );
  }
  return undefined;
};
