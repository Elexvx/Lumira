export const APP_SPACING = {
  layout: {
    headerHeight: {
      desktop: 56,
      mobile: 56,
    },
    siderWidth: {
      desktop: 224,
      mobile: 224,
    },
  },
  pageContainerPaddingInline: {
    desktop: 25,
    mobile: 20,
  },
  detailLayoutLabelWidth: {
    desktop: 140,
    tablet: 120,
    mobile: 96,
  },
  pageContainerPaddingBlock: {
    desktop: 24,
    mobile: 16,
  },
  sectionGap: {
    desktop: 16,
    mobile: 12,
  },
  microGap: {
    desktop: 4,
    mobile: 4,
  },
  microOffset: {
    desktop: 4,
    mobile: 4,
  },
  modalFooterGap: {
    desktop: 12,
    mobile: 12,
  },
  rowGutterHero: {
    desktop: [24, 24] as [number, number],
    mobile: [16, 16] as [number, number],
  },
  rowGutterPanel: {
    desktop: [16, 16] as [number, number],
    mobile: [16, 16] as [number, number],
  },
  tagWrapGap: {
    desktop: [8, 8] as [number, number],
    mobile: [8, 8] as [number, number],
  },
  mobileProfileSectionGap: {
    desktop: 16,
    mobile: 14,
  },
  compactSectionGap: {
    desktop: 10,
    mobile: 10,
  },
  standardDrawerWidth: {
    desktop: 560,
    mobile: 560,
  },
  avatarSize: {
    tiny: { desktop: 32, mobile: 32 },
    normal: { desktop: 64, mobile: 64 },
    large: { desktop: 96, mobile: 96 },
  },
  qrCodeSize: {
    desktop: 188,
    mobile: 188,
  },
  monitoringTrendChart: {
    width: 420,
    height: 220,
    axisOffsetX: 8,
    axisOffsetY: 4,
    axisFontSize: 11,
    padding: {
      top: 24,
      right: 56,
      bottom: 54,
      left: 64,
    },
  },
  sliderCaptcha: {
    width: { desktop: 320, mobile: 320 },
    height: { desktop: 160, mobile: 160 },
    puzzleSize: { desktop: 58, mobile: 58 },
    puzzleTop: { desktop: 48, mobile: 48 },
  },
  antdDesktopTokens: {
    sizeUnit: 4,
    sizeStep: 4,
    controlHeight: 32,
    controlHeightSM: 24,
    controlHeightLG: 40,
    marginXS: 8,
    marginSM: 12,
    margin: 16,
    marginLG: 24,
    marginXL: 32,
    marginXXL: 48,
    paddingXS: 8,
    paddingSM: 12,
    padding: 16,
    paddingLG: 24,
    paddingXL: 32,
    paddingXXL: 40,
  },
  antdMobileTokens: {
    sizeUnit: 4,
    sizeStep: 4,
    controlHeight: 40,
    controlHeightSM: 32,
    controlHeightLG: 44,
    marginXS: 8,
    marginSM: 12,
    margin: 12,
    marginLG: 16,
    marginXL: 24,
    marginXXL: 36,
    paddingXS: 8,
    paddingSM: 10,
    padding: 12,
    paddingLG: 16,
    paddingXL: 24,
    paddingXXL: 32,
  },
};

export const resolveResponsiveValue = <T,>(
  value: {
    desktop: T;
    mobile: T;
  },
  isMobile: boolean,
): T => (isMobile ? value.mobile : value.desktop);
