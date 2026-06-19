import type { ActionType } from '@ant-design/pro-components';
import { useCallback, useRef, useState } from 'react';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';

export const useCrudPageState = <TRecord,>() => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const drawer = useCrudDrawerState<TRecord>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailCurrentRecord, setDetailCurrentRecord] = useState<TRecord | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const openDetail = useCallback((record: TRecord) => {
    setDetailCurrentRecord(record);
    setDetailOpen(true);
  }, []);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setDetailCurrentRecord(null);
    setDetailLoading(false);
  }, []);

  const reloadTable = useCallback(() => {
    actionRef.current?.reload();
  }, []);

  const reloadAndCloseEditor = useCallback(() => {
    drawer.close();
    actionRef.current?.reload();
  }, [drawer]);

  const closeAllDrawers = useCallback(() => {
    drawer.close();
    closeDetail();
  }, [closeDetail, drawer]);

  return {
    actionRef,
    drawer,
    detail: {
      open: detailOpen,
      currentRecord: detailCurrentRecord,
      loading: detailLoading,
      setOpen: setDetailOpen,
      setCurrentRecord: setDetailCurrentRecord,
      setLoading: setDetailLoading,
      openDetail,
      close: closeDetail,
    },
    reloadTable,
    reloadAndCloseEditor,
    closeAllDrawers,
  };
};
