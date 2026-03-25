import { request } from '@/services/common/request';

export const tenantService = {
  listMine: () => request('/tenant/my-tenants'),
};
