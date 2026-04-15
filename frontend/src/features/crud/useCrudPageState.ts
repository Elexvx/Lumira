import type { ActionType } from '@ant-design/pro-components';
import { useCallback, useRef } from 'react';
import { useCrudDetailState } from '@/features/crud/useCrudDetailState';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';

export const useCrudPageState = <TRecord,>() => {
  const actionRef = useRef<ActionType>();
  const drawer = useCrudDrawerState<TRecord>();
  const detail = useCrudDetailState<TRecord>();

  const reloadTable = useCallback(() => {
    actionRef.current?.reload();
  }, []);

  const reloadAndCloseEditor = useCallback(() => {
    drawer.close();
    actionRef.current?.reload();
  }, [drawer]);

  const closeAllDrawers = useCallback(() => {
    drawer.close();
    detail.close();
  }, [detail, drawer]);

  return {
    actionRef,
    drawer,
    detail,
    reloadTable,
    reloadAndCloseEditor,
    closeAllDrawers,
  };
};
