import { message } from 'antd';
import { tenantService } from '@/services/tenant';
import { tokenManager } from '@/auth/token';
import { tenantContext } from '@/tenant/context';
import type { CurrentTenantResponse, MyTenant, SwitchTenantResponse, TenantSummary } from '@/types/api';

export const syncTenantFromServer = async (): Promise<{ currentTenant: TenantSummary | null; myTenants: MyTenant[] }> => {
  const [myTenants, currentTenantResp] = await Promise.all([
    tenantService.myTenants(),
    tenantService.currentTenant(),
  ]);

  const currentTenant = normalizeCurrentTenant(currentTenantResp, myTenants);
  tenantContext.setMyTenants(myTenants);
  tenantContext.setCurrentTenant(currentTenant);
  return { currentTenant, myTenants };
};

export const switchTenantAction = async (tenantId: number): Promise<SwitchTenantResponse> => {
  const response = await tenantService.switchTenant({ tenantId });
  const previousTenantId = tenantContext.getTenantId();
  if (previousTenantId && previousTenantId !== String(tenantId)) {
    tenantContext.clearTenantScopedCache(previousTenantId);
  }

  tokenManager.setTokens({
    accessToken: response.accessToken,
    refreshToken: tokenManager.getRefreshToken(),
    tokenType: response.tokenType,
    expiresIn: response.expiresIn,
  });
  tenantContext.setCurrentTenant(response.currentTenant);
  tenantContext.emitTenantSwitched();
  message.success('租户切换成功');
  return response;
};

const normalizeCurrentTenant = (
  currentTenantResp: CurrentTenantResponse,
  myTenants: MyTenant[],
): TenantSummary | null => {
  if (currentTenantResp?.hasCurrentTenant && currentTenantResp.currentTenant) {
    return currentTenantResp.currentTenant;
  }
  if (myTenants.length === 1) {
    return myTenants[0];
  }
  return null;
};
