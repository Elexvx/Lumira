import { describe, expect, it } from 'vitest';
import { dictItemsToOptions } from './useDictOptions';
import type { DictItemRecord } from '@/types/api';

describe('dictItemsToOptions', () => {
  it('uses fallback options when runtime dictionary is missing', () => {
    expect(dictItemsToOptions([], [{ value: 'GENERAL', label: 'General' }])).toEqual([
      { value: 'GENERAL', label: 'General' },
    ]);
  });

  it('maps enabled dictionary items into select options', () => {
    const items: DictItemRecord[] = [
      {
        id: 1,
        dictTypeId: 2,
        itemLabel: 'Public',
        itemValue: 'PUBLIC',
        sortNo: 10,
        status: 'ENABLED',
        remark: '',
      },
    ];

    expect(dictItemsToOptions(items, [])).toEqual([{ value: 'PUBLIC', label: 'Public' }]);
  });

  it('uses fallback labels for matching runtime values', () => {
    const items: DictItemRecord[] = [
      {
        id: 1,
        dictTypeId: 2,
        itemLabel: 'Innovation',
        itemValue: 'INNOVATION',
        sortNo: 10,
        status: 'ENABLED',
        remark: '',
      },
    ];

    expect(dictItemsToOptions(items, [{ value: 'INNOVATION', label: '创新赛' }])).toEqual([
      { value: 'INNOVATION', label: '创新赛' },
    ]);
  });
});
