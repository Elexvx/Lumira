import type { DictItemRecord, DictTypeRecord, PagedResult } from '@/types/api';

export interface DictTypeListQuery extends Record<string, unknown> {
  keyword?: string;
  dictCode?: string;
  dictName?: string;
  status?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface DictTypeMutationPayload {
  dictCode: string;
  dictName: string;
  status: string;
  remark?: string;
}

export interface DictItemMutationPayload {
  itemLabel: string;
  itemValue: string;
  sortNo: number;
  status: string;
  remark?: string;
}

export type { DictItemRecord, DictTypeRecord, PagedResult };
