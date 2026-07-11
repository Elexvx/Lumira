const TABLE_CONTAINER_BREAKPOINTS = {
  xs: 0,
  sm: 576,
  md: 768,
  lg: 992,
  xl: 1200,
  xxl: 1600,
} as const;

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
