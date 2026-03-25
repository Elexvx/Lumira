import { Select } from 'antd';
import { switchTenant, tenantContext } from '@/tenant/context';

export const TenantSelector = () => {
  return (
    <Select
      size="small"
      style={{ width: 140 }}
      placeholder="选择租户"
      value={tenantContext.getTenantId() || undefined}
      onChange={switchTenant}
      options={[
        { label: '默认租户', value: 'default' },
        { label: '演示租户', value: 'demo' },
      ]}
    />
  );
};
