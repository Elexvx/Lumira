import { useSyncExternalStore } from 'react';

export type ViewportTier = 'mobile' | 'tablet' | 'desktop' | 'large' | 'wide' | 'ultra';

export interface ResponsiveProfile {
  tier: ViewportTier;
  bodyFontSize: number;
  fontSizeSM: number;
  fontSizeLG: number;
  fontSizeXL: number;
  pageTitleFontSize: number;
  sectionTitleFontSize: number;
  controlHeight: number;
  controlHeightSM: number;
  controlHeightLG: number;
  pageContentMaxWidth: string;
  pageGutter: number;
  pageSectionGap: number;
  cardPadding: number;
  headerHeight: number;
  siderWidth: number;
  formControlMaxWidth: number;
  drawerWidth: number;
  tableCellPadding: number;
  densityScale: number;
}

const RESPONSIVE_PROFILES: Record<ViewportTier, ResponsiveProfile> = {
  mobile: {
    tier: 'mobile',
    bodyFontSize: 14,
    fontSizeSM: 12,
    fontSizeLG: 16,
    fontSizeXL: 18,
    pageTitleFontSize: 22,
    sectionTitleFontSize: 18,
    controlHeight: 44,
    controlHeightSM: 32,
    controlHeightLG: 48,
    pageContentMaxWidth: '100%',
    pageGutter: 16,
    pageSectionGap: 16,
    cardPadding: 16,
    headerHeight: 56,
    siderWidth: 224,
    formControlMaxWidth: 720,
    drawerWidth: 700,
    tableCellPadding: 8,
    densityScale: 1,
  },
  tablet: {
    tier: 'tablet',
    bodyFontSize: 14,
    fontSizeSM: 12,
    fontSizeLG: 16,
    fontSizeXL: 18,
    pageTitleFontSize: 24,
    sectionTitleFontSize: 20,
    controlHeight: 40,
    controlHeightSM: 28,
    controlHeightLG: 44,
    pageContentMaxWidth: '100%',
    pageGutter: 20,
    pageSectionGap: 18,
    cardPadding: 18,
    headerHeight: 56,
    siderWidth: 224,
    formControlMaxWidth: 760,
    drawerWidth: 760,
    tableCellPadding: 10,
    densityScale: 1,
  },
  desktop: {
    tier: 'desktop',
    bodyFontSize: 14,
    fontSizeSM: 12,
    fontSizeLG: 16,
    fontSizeXL: 18,
    pageTitleFontSize: 26,
    sectionTitleFontSize: 22,
    controlHeight: 36,
    controlHeightSM: 28,
    controlHeightLG: 44,
    pageContentMaxWidth: '1280px',
    pageGutter: 24,
    pageSectionGap: 20,
    cardPadding: 20,
    headerHeight: 56,
    siderWidth: 224,
    formControlMaxWidth: 800,
    drawerWidth: 820,
    tableCellPadding: 10,
    densityScale: 1,
  },
  large: {
    tier: 'large',
    bodyFontSize: 15,
    fontSizeSM: 13,
    fontSizeLG: 17,
    fontSizeXL: 20,
    pageTitleFontSize: 30,
    sectionTitleFontSize: 24,
    controlHeight: 40,
    controlHeightSM: 30,
    controlHeightLG: 48,
    pageContentMaxWidth: '1480px',
    pageGutter: 28,
    pageSectionGap: 22,
    cardPadding: 22,
    headerHeight: 60,
    siderWidth: 232,
    formControlMaxWidth: 880,
    drawerWidth: 880,
    tableCellPadding: 12,
    densityScale: 1.04,
  },
  wide: {
    tier: 'wide',
    bodyFontSize: 16,
    fontSizeSM: 14,
    fontSizeLG: 18,
    fontSizeXL: 22,
    pageTitleFontSize: 34,
    sectionTitleFontSize: 28,
    controlHeight: 44,
    controlHeightSM: 32,
    controlHeightLG: 52,
    pageContentMaxWidth: '1760px',
    pageGutter: 32,
    pageSectionGap: 24,
    cardPadding: 24,
    headerHeight: 64,
    siderWidth: 248,
    formControlMaxWidth: 960,
    drawerWidth: 960,
    tableCellPadding: 14,
    densityScale: 1.08,
  },
  ultra: {
    tier: 'ultra',
    bodyFontSize: 18,
    fontSizeSM: 15,
    fontSizeLG: 20,
    fontSizeXL: 24,
    pageTitleFontSize: 38,
    sectionTitleFontSize: 30,
    controlHeight: 48,
    controlHeightSM: 36,
    controlHeightLG: 56,
    pageContentMaxWidth: '2160px',
    pageGutter: 40,
    pageSectionGap: 28,
    cardPadding: 28,
    headerHeight: 68,
    siderWidth: 264,
    formControlMaxWidth: 1120,
    drawerWidth: 1120,
    tableCellPadding: 16,
    densityScale: 1.14,
  },
};

export const resolveViewportTier = (width: number): ViewportTier => {
  if (width < 768) return 'mobile';
  if (width < 1200) return 'tablet';
  if (width < 1600) return 'desktop';
  if (width < 1920) return 'large';
  if (width < 2560) return 'wide';
  return 'ultra';
};

export const getResponsiveProfile = (tier: ViewportTier): ResponsiveProfile => RESPONSIVE_PROFILES[tier];

const getServerViewportTier = (): ViewportTier => 'desktop';
const getClientViewportTier = () =>
  typeof window === 'undefined' ? getServerViewportTier() : resolveViewportTier(window.innerWidth);

let clientViewportTier = getClientViewportTier();
let viewportListenerAttached = false;
const viewportSubscribers = new Set<() => void>();

const updateClientViewportTier = () => {
  const nextTier = getClientViewportTier();
  if (nextTier === clientViewportTier) return;

  clientViewportTier = nextTier;
  viewportSubscribers.forEach((subscriber) => subscriber());
};

const subscribeToViewportTier = (subscriber: () => void) => {
  viewportSubscribers.add(subscriber);
  if (!viewportListenerAttached && typeof window !== 'undefined') {
    window.addEventListener('resize', updateClientViewportTier, { passive: true });
    viewportListenerAttached = true;
  }

  return () => {
    viewportSubscribers.delete(subscriber);
    if (!viewportSubscribers.size && viewportListenerAttached && typeof window !== 'undefined') {
      window.removeEventListener('resize', updateClientViewportTier);
      viewportListenerAttached = false;
    }
  };
};

export const useViewportTier = () => {
  const tier = useSyncExternalStore(subscribeToViewportTier, () => clientViewportTier, getServerViewportTier);
  return getResponsiveProfile(tier);
};

export const applyResponsiveProfileToDocument = (profile: ResponsiveProfile) => {
  if (typeof document === 'undefined') return;

  const root = document.documentElement;
  root.dataset.viewportTier = profile.tier;
  const cssVariables: Record<string, string> = {
    '--saas-viewport-scale': String(profile.densityScale),
    '--saas-font-size-body': `${profile.bodyFontSize}px`,
    '--saas-font-size-sm': `${profile.fontSizeSM}px`,
    '--saas-font-size-lg': `${profile.fontSizeLG}px`,
    '--saas-font-size-xl': `${profile.fontSizeXL}px`,
    '--saas-font-size-page-title': `${profile.pageTitleFontSize}px`,
    '--saas-font-size-section-title': `${profile.sectionTitleFontSize}px`,
    '--saas-page-content-width': profile.pageContentMaxWidth,
    '--saas-page-gutter': `${profile.pageGutter}px`,
    '--saas-page-section-gap': `${profile.pageSectionGap}px`,
    '--saas-card-padding': `${profile.cardPadding}px`,
    '--saas-layout-header-height': `${profile.headerHeight}px`,
    '--saas-layout-sider-width': `${profile.siderWidth}px`,
    '--saas-form-control-max-width': `${profile.formControlMaxWidth}px`,
    '--saas-standard-drawer-width': `${profile.drawerWidth}px`,
    '--saas-table-cell-inline-padding': `${profile.tableCellPadding}px`,
  };

  Object.entries(cssVariables).forEach(([name, value]) => root.style.setProperty(name, value));
};
