import { QueryClient } from '@tanstack/react-query';

const shouldRetryQuery = (failureCount: number, error: unknown) => {
  if (failureCount >= 1) {
    return false;
  }

  const httpStatus = (error as { httpStatus?: number })?.httpStatus;
  if (!httpStatus) {
    return true;
  }
  return httpStatus >= 500;
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetryQuery,
      retryDelay: 1000,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});
