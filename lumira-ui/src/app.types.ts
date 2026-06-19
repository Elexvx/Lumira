import type {
  AgreementSettings,
  BrandingSettings,
  CurrentUser,
  FloatingWindowSettings,
  LoginCapabilities,
  MenuNode,
  SecuritySettings,
  TenantPlugin,
  WatermarkSettings,
} from '@/types/api';

export interface AppInitialState {
  currentUser?: CurrentUser;
  menuTree: MenuNode[];
  menuVersion: number;
  themeRevision?: number;
  availablePlugins: TenantPlugin[];
  securitySettings: SecuritySettings;
  brandingSettings: BrandingSettings;
  watermarkSettings?: WatermarkSettings;
  floatingWindowSettings?: FloatingWindowSettings;
  agreementSettings?: AgreementSettings;
  loginCapabilities?: LoginCapabilities;
}

export interface RuntimeMenuDataItem {
  path?: string;
  name?: string;
  title?: string;
  locale?: false | string;
  icon?: React.ReactNode | string;
  children?: RuntimeMenuDataItem[];
  hideInMenu?: boolean;
}
