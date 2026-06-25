import type {
  AgreementSettings,
  BrandingSettings,
  CurrentUser,
  FloatingWindowSettings,
  LoginCapabilities,
  MenuNode,
  SecuritySettings,
  PluginAvailability,
  WatermarkSettings,
} from '@/types/api';

export interface AppInitialState {
  currentUser?: CurrentUser;
  menuTree: MenuNode[];
  menuVersion: number;
  themeRevision?: number;
  brandingRevision?: number;
  availablePlugins: PluginAvailability[];
  securitySettings: SecuritySettings;
  brandingSettings: BrandingSettings;
  watermarkSettings?: WatermarkSettings;
  floatingWindowSettings?: FloatingWindowSettings;
  agreementSettings?: AgreementSettings;
  loginCapabilities?: LoginCapabilities;
}

export interface RuntimeMenuDataItem {
  key?: string;
  path?: string;
  name?: string;
  title?: string;
  locale?: false | string;
  icon?: React.ReactNode | string;
  parentKeys?: string[];
  children?: RuntimeMenuDataItem[];
  hideInMenu?: boolean;
}
