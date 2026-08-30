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
  structureType?: 'FLAT' | 'TREE';
  remark?: string;
}

export interface DictItemMutationPayload {
  itemLabel: string;
  itemValue: string;
  sortNo: number;
  status: string;
  remark?: string;
  parentItemValue?: string;
  levelNo?: number;
  leaf?: boolean;
}

export type { DictItemRecord, DictTypeRecord, PagedResult };
