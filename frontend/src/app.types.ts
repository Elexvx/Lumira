import type {
  AgreementSettings,
  BrandingSettings,
  CurrentUser,
  LoginCapabilities,
  MenuNode,
  MyTenant,
  SecuritySettings,
  TenantPlugin,
  TenantSummary,
  WatermarkSettings,
} from '@/types/api';

export interface AppInitialState {
  currentUser?: CurrentUser;
  currentTenant?: TenantSummary | null;
  myTenants: MyTenant[];
  menuTree: MenuNode[];
  menuVersion: number;
  themeRevision?: number;
  availablePlugins: TenantPlugin[];
  securitySettings: SecuritySettings;
  brandingSettings: BrandingSettings;
  watermarkSettings?: WatermarkSettings;
  agreementSettings?: AgreementSettings;
  loginCapabilities?: LoginCapabilities;
}

export interface RuntimeMenuDataItem {
  path?: string;
  name?: string;
  icon?: React.ReactNode | string;
  children?: RuntimeMenuDataItem[];
  hideInMenu?: boolean;
}
