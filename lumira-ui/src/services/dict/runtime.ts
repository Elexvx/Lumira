import { request } from '@/services/common/request';
import type { DictItemRecord } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';

export const listEnabledDictItemsByCode = (dictCode: string) =>
  request<DictItemRecord[]>('/v1/system/dict-items', {
    method: 'GET',
    params: { dictCode },
    ...API_OPTS.NO_REDIRECT,
  });
