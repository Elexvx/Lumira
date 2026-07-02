import { useEffect, useMemo, useState } from 'react';
import type { SelectProps } from 'antd';
import { listEnabledDictItemsByCode } from '@/services/dict/runtime';
import type { DictItemRecord } from '@/types/api';

export type DictOption = NonNullable<SelectProps['options']>[number];

export const dictItemsToOptions = (items: DictItemRecord[], fallbackOptions: DictOption[] = []): DictOption[] => {
  if (!items.length) {
    return fallbackOptions;
  }
  const fallbackByValue = new Map(fallbackOptions.map((option) => [option.value, option]));
  return items.map((item) => {
    const fallback = fallbackByValue.get(item.itemValue);
    return {
      ...fallback,
      value: item.itemValue,
      label: fallback?.label ?? item.itemLabel,
    };
  });
};

export const useDictOptions = (dictCode: string, fallbackOptions: DictOption[] = []) => {
  const [items, setItems] = useState<DictItemRecord[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    if (!dictCode) {
      setItems([]);
      return () => {
        active = false;
      };
    }

    setLoading(true);
    listEnabledDictItemsByCode(dictCode)
      .then((nextItems) => {
        if (active) {
          setItems(nextItems);
        }
      })
      .catch(() => {
        if (active) {
          setItems([]);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [dictCode]);

  const options = useMemo(() => dictItemsToOptions(items, fallbackOptions), [fallbackOptions, items]);

  return { options, items, loading };
};

export const useDictOptionMap = (dictCode: string, fallbackOptions: DictOption[] = []) => {
  const runtime = useDictOptions(dictCode, fallbackOptions);
  const optionMap = useMemo(
    () => new Map(runtime.options.map((option) => [option.value, option])),
    [runtime.options],
  );
  return { ...runtime, optionMap };
};
