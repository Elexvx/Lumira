import { tenantContext } from '@/tenant/context';

export const useTenantContext = () => ({
  tenantId: tenantContext.getTenantId(),
  currentTenant: tenantContext.getCurrentTenant(),
  myTenants: tenantContext.getMyTenants(),
});
