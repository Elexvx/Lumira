import { useCallback, useState } from 'react';
import { ApiRequestError } from '@/services/common/request';

export type DetailStatus = 'idle' | 'loading' | 'success' | 'error' | 'empty';

export const useDetailState = <T,>() => {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<DetailStatus>('idle');
  const [data, setData] = useState<T | undefined>();
  const [errorMessage, setErrorMessage] = useState<string>();

  const close = useCallback(() => {
    setOpen(false);
    setStatus('idle');
    setData(undefined);
    setErrorMessage(undefined);
  }, []);

  const load = useCallback(async (loader: () => Promise<T | null | undefined>) => {
    setOpen(true);
    setStatus('loading');
    setErrorMessage(undefined);
    try {
      const result = await loader();
      if (!result) {
        setData(undefined);
        setStatus('empty');
        return;
      }
      setData(result);
      setStatus('success');
    } catch (error) {
      const message = error instanceof ApiRequestError
        ? `${error.userMessage || error.message} (${error.code || 'unknown'})`
        : error instanceof Error
          ? error.message
          : 'unknown error';
      setErrorMessage(message);
      setStatus('error');
    }
  }, []);

  return { open, setOpen, status, data, errorMessage, load, close };
};
