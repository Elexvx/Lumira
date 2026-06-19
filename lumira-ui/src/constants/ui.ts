import { APP_SPACING } from '@/theme/spacing';

export const DETAIL_LAYOUT_LABEL_WIDTHS = {
  desktop: APP_SPACING.detailLayoutLabelWidth.desktop,
  tablet: APP_SPACING.detailLayoutLabelWidth.tablet,
  mobile: APP_SPACING.detailLayoutLabelWidth.mobile,
} as const;

export const DETAIL_LAYOUT_COLUMNS = {
  desktop: 2,
  tablet: 2,
  mobile: 1,
} as const;

export const DETAIL_EMPTY_TEXT = '-';

export const DETAIL_SECTION_GAP = APP_SPACING.sectionGap.desktop;

export const DETAIL_SECTION_TITLE_LEVEL = 5;

export const STANDARD_DRAWER_WIDTH_BY_BREAKPOINT = {
  desktop: `min(var(--saas-spacing-${APP_SPACING.standardDrawerWidth.desktop}), 100vw)`,
  mobile: `min(var(--saas-spacing-${APP_SPACING.standardDrawerWidth.mobile}), 100vw)`,
} as const;

export const STANDARD_DRAWER_WIDTH = STANDARD_DRAWER_WIDTH_BY_BREAKPOINT.desktop;
export const MESSAGE_CENTER_DRAWER_WIDTH_BY_BREAKPOINT = STANDARD_DRAWER_WIDTH_BY_BREAKPOINT;
export const MESSAGE_CENTER_DRAWER_WIDTH = MESSAGE_CENTER_DRAWER_WIDTH_BY_BREAKPOINT.desktop;

export const LLM_SERVICE_DRAWER_WIDTH_BY_BREAKPOINT = {
  desktop: 'min(var(--saas-spacing-700), 100vw)',
  mobile: '100vw',
} as const;

export const AUTH_AGREEMENT_MODAL_WIDTH_BY_BREAKPOINT = {
  desktop: 'min(var(--saas-spacing-720), 100vw)',
  mobile: '100vw',
} as const;

export const PROFILE_2FA_BINDING_MODAL_WIDTH_BY_BREAKPOINT = {
  desktop: 'min(var(--saas-spacing-780), 100vw)',
  mobile: '100vw',
} as const;

export const LOGIN_SLIDER_CAPTCHA_MODAL_WIDTH_BY_BREAKPOINT = {
  desktop: 'min(var(--saas-spacing-368), 100vw)',
  mobile: '100vw',
} as const;
