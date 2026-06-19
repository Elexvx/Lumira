import type { RequestOptions } from './requestInternalsTypes';

export const buildDuplicateRequestKey = (url: string, options: RequestOptions) => {
  const method = options.method || 'GET';
  if (options.allowDuplicate || !isWriteMethod(method)) {
    return '';
  }
  return [
    method,
    url,
    stableSerialize(options.params || {}),
    stableSerialize(options.data),
  ].join('|');
};

export const isWriteMethod = (method: RequestOptions['method']) => {
  return method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE';
};

export const stableSerialize = (value: unknown): string => {
  return JSON.stringify(toStableValue(value));
};

const toStableValue = (value: unknown): unknown => {
  if (value === undefined) {
    return { __type: 'undefined' };
  }
  if (value === null) {
    return null;
  }
  if (value instanceof FormData) {
    return Array.from(value.entries()).map(([key, entry]) => [key, serializeFormDataEntry(entry)]);
  }
  if (value instanceof Blob) {
    return serializeBlob(value);
  }
  if (Array.isArray(value)) {
    return value.map(toStableValue);
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    const sorted: Record<string, unknown> = {};
    Object.keys(record).sort().forEach((key) => {
      sorted[key] = toStableValue(record[key]);
    });
    return sorted;
  }
  return value;
};

const serializeFormDataEntry = (entry: FormDataEntryValue) => {
  if (entry instanceof File) {
    return {
      name: entry.name,
      size: entry.size,
      type: entry.type,
      lastModified: entry.lastModified,
    };
  }
  return entry;
};

const serializeBlob = (value: Blob) => ({
  size: value.size,
  type: value.type,
});
