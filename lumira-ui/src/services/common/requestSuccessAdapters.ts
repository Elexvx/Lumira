export type RequestSuccessAdapter = (data: unknown) => void;

const requestSuccessAdapters = new Map<string | RequestSuccessAdapter, RequestSuccessAdapter>();

export const registerRequestSuccessAdapter = (adapter: RequestSuccessAdapter, key?: string) => {
  const adapterKey = key || adapter;
  requestSuccessAdapters.set(adapterKey, adapter);
  return () => {
    if (requestSuccessAdapters.get(adapterKey) === adapter) {
      requestSuccessAdapters.delete(adapterKey);
    }
  };
};

export const adaptRequestSuccessData = (data: unknown) => {
  for (const adapter of requestSuccessAdapters.values()) {
    try {
      adapter(data);
    } catch {
      // Presentation adapters must never change the result of a business request.
    }
  }
};
