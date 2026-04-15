import { useCallback, useState } from 'react';

export const useCrudDetailState = <TRecord,>() => {
  const [open, setOpen] = useState(false);
  const [currentRecord, setCurrentRecord] = useState<TRecord | null>(null);
  const [loading, setLoading] = useState(false);

  const openDetail = useCallback((record: TRecord) => {
    setCurrentRecord(record);
    setOpen(true);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
    setCurrentRecord(null);
    setLoading(false);
  }, []);

  return {
    open,
    currentRecord,
    loading,
    setOpen,
    setCurrentRecord,
    setLoading,
    openDetail,
    close,
  };
};
