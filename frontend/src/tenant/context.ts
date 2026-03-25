import { storage } from '@/cache/storage';

const TENANT_KEY = 'current_tenant';

export const tenantContext = {
  getTenantId: () => storage.get<string>(TENANT_KEY),
  setTenantId: (tenantId: string) => storage.set(TENANT_KEY, tenantId),
  clearTenantScopedCache: (tenantId: string) => storage.clearTenant(tenantId),
};

export const switchTenant = (nextTenantId: string) => {
  const current = tenantContext.getTenantId();
  if (current && current !== nextTenantId) {
    tenantContext.clearTenantScopedCache(current);
  }
  tenantContext.setTenantId(nextTenantId);
};
