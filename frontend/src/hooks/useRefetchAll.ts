import { useCallback } from 'react';
import type { QueryObserverResult, RefetchOptions } from '@tanstack/react-query';

type RefetchableQuery = {
  refetch: (options?: RefetchOptions) => Promise<QueryObserverResult<unknown, unknown>>;
};

export const useRefetchAll = (queries: RefetchableQuery[]) =>
  useCallback(() => {
    void Promise.all(queries.map((query) => query.refetch()));
  }, queries);
