import { request, type RequestOptions } from '@/services/common/request';
import type { RedisMonitorSnapshot, ServiceMonitorSnapshot } from '@/types/api';

export const monitorService = {
  service: (options: RequestOptions = {}) =>
    request<ServiceMonitorSnapshot>('/v1/system/monitor/service', {
      method: 'GET',
      ...options,
    }),
  redis: (options: RequestOptions = {}) =>
    request<RedisMonitorSnapshot>('/v1/system/monitor/redis', {
      method: 'GET',
      ...options,
    }),
};
