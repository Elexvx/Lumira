import { request, type RequestOptions } from '@/services/common/request';
import type { PlatformUpdateStatus, RedisMonitorSnapshot, ServiceMonitorSnapshot } from '@/types/api';

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
  updateStatus: (options: RequestOptions = {}) =>
    request<PlatformUpdateStatus>('/v1/system/update/status', {
      method: 'GET',
      ...options,
    }),
  checkUpdate: (options: RequestOptions = {}) =>
    request<PlatformUpdateStatus>('/v1/system/update/check', {
      method: 'POST',
      ...options,
    }),
};
