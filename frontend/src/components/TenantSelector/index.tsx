import { useMemo, useState } from 'react';
import { useModel } from '@umijs/max';
import { Select } from 'antd';
import { switchTenantAction } from '@/tenant/actions';
import type { AppInitialState } from '@/app';

export const TenantSelector = () => {
  const { initialState, setInitialState } = useModel('@@initialState');
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
      setInitialState((prev: AppInitialState | undefined) => ({
        ...prev,
        currentTenant: response.currentTenant,
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
