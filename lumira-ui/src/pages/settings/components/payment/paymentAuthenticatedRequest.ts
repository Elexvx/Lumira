import { request, type RequestOptions } from '@/services/common/request';

export const requestPaymentApi = <T>(url: string, options: RequestOptions = {}) =>
  request<T>(url, options);
