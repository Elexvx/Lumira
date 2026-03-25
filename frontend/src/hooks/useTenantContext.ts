import { tenantContext, switchTenant } from '@/tenant/context';

export const useTenantContext = () => ({
  tenantId: tenantContext.getTenantId(),
  switchTenant,
});
