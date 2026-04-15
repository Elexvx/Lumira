import { useCallback, useState } from 'react';

export const useCrudDrawerState = <TRecord,>() => {
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [currentRecord, setCurrentRecord] = useState<TRecord | null>(null);

  const openCreate = useCallback(() => {
    setCurrentRecord(null);
    setEditingId(null);
    setOpen(true);
  }, []);

  const openEdit = useCallback((record: TRecord, id: number) => {
    setCurrentRecord(record);
    setEditingId(id);
    setOpen(true);
  }, []);

  const close = useCallback(() => {
    setOpen(false);
  }, []);

  const reset = useCallback(() => {
    setCurrentRecord(null);
    setEditingId(null);
    setOpen(false);
  }, []);

  return {
    open,
    editingId,
    currentRecord,
    isEditing: editingId !== null,
    setOpen,
    setCurrentRecord,
    setEditingId,
    openCreate,
    openEdit,
    close,
    reset,
  };
};
