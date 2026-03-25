import { storage } from '@/cache/storage';
import type { MyTenant, TenantSummary } from '@/types/api';

const CURRENT_TENANT_KEY = 'current_tenant';
const TENANT_LIST_KEY = 'tenant_list';
const TENANT_SWITCH_EVENT = 'saas:tenant-switched';

export const tenantContext = {
  getCurrentTenant: () => storage.get<TenantSummary>(CURRENT_TENANT_KEY),
  setCurrentTenant: (tenant: TenantSummary | null) => {
    if (tenant) {
      storage.set(CURRENT_TENANT_KEY, tenant);
      return;
    }
    storage.remove(CURRENT_TENANT_KEY);
  },
  getTenantId: () => tenantContext.getCurrentTenant()?.tenantId?.toString() || '',
  getMyTenants: () => storage.get<MyTenant[]>(TENANT_LIST_KEY) ?? [],
  setMyTenants: (tenants: MyTenant[]) => storage.set(TENANT_LIST_KEY, tenants),
  clearTenantContext: () => {
    storage.remove(CURRENT_TENANT_KEY);
    storage.remove(TENANT_LIST_KEY);
  },
  clearTenantScopedCache: (tenantId: string) => {
    if (tenantId) {
      storage.clearTenant(tenantId);
    }
  },
  emitTenantSwitched: () => window.dispatchEvent(new CustomEvent(TENANT_SWITCH_EVENT)),
  onTenantSwitched: (listener: () => void) => {
    const wrapped = () => listener();
    window.addEventListener(TENANT_SWITCH_EVENT, wrapped);
    return () => window.removeEventListener(TENANT_SWITCH_EVENT, wrapped);
  },
};
