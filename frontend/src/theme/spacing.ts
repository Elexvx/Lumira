export const APP_SPACING = {
  pageContainerPaddingInline: {
    desktop: 25,
    mobile: 20,
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
  modalFooterGap: {
    desktop: 12,
    mobile: 12,
  },
  rowGutterHero: {
    desktop: [24, 24],
    mobile: [16, 16],
  },
  rowGutterPanel: {
    desktop: [16, 16],
    mobile: [16, 16],
  },
  tagWrapGap: {
    desktop: [8, 8],
    mobile: [8, 8],
  },
  mobileProfileSectionGap: {
    desktop: 16,
    mobile: 14,
  },
  compactSectionGap: {
    desktop: 10,
    mobile: 10,
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
