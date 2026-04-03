import { useMemo, useState } from 'react';
import { Select } from 'antd';
import { DEFAULT_BRANDING_SETTINGS } from '@/branding/settings';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { switchTenantAction } from '@/tenant/actions';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettings';

export const TenantSelector = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const [switching, setSwitching] = useState(false);
  const { isMobile } = useResponsive();

  const tenantOptions = useMemo(
    () =>
      (initialState?.myTenants ?? []).map((tenant) => ({
        label: tenant.tenantShortName || tenant.tenantName,
        value: tenant.tenantId,
      })),
    [initialState?.myTenants],
  );

  const handleSwitch = async (nextTenantId: number) => {
    const currentTenantId = initialState?.currentTenant?.tenantId;
    if (currentTenantId === nextTenantId) {
      return;
    }

    setSwitching(true);
    try {
      const response = await switchTenantAction(nextTenantId);
      const [currentUser, menuTree, availablePlugins] = await Promise.all([
        authService.currentUser({ autoRedirectOnUnauthorized: false }),
        pluginService.currentMenus({ autoRedirectOnUnauthorized: false }),
        pluginService.currentAvailable({ autoRedirectOnUnauthorized: false }),
      ]);
      setInitialState((prev: AppInitialState | undefined) => ({
        ...prev,
        currentUser,
        currentTenant: response.currentTenant,
        myTenants: prev?.myTenants ?? [],
        menuTree,
        availablePlugins,
        securitySettings: prev?.securitySettings || initialState?.securitySettings || DEFAULT_SECURITY_SETTINGS,
        brandingSettings: prev?.brandingSettings || initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS,
      }));
    } finally {
      setSwitching(false);
    }
  };

  return (
    <Select
      size="small"
      variant="borderless"
      className="tenant-selector"
      style={{ width: isMobile ? 120 : 150, maxWidth: '100%' }}
      dropdownMatchSelectWidth={false}
      placeholder="选择租户"
      loading={switching}
      disabled={switching || tenantOptions.length <= 1}
      value={initialState?.currentTenant?.tenantId}
      onChange={handleSwitch}
      options={tenantOptions}
      notFoundContent="暂无可用租户"
    />
  );
};
