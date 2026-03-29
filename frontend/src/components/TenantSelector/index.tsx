import { useMemo, useState } from 'react';
import { Select } from 'antd';
import { authService } from '@/services/auth';
import { pluginService } from '@/services/plugin';
import { switchTenantAction } from '@/tenant/actions';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';

export const TenantSelector = () => {
  const { initialState, setInitialState } = useInitialStateModel();
  const [switching, setSwitching] = useState(false);

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
      }));
    } finally {
      setSwitching(false);
    }
  };

  return (
    <Select
      size="small"
      style={{ width: 180 }}
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
