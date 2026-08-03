import { databaseMessage, hasDatabaseMessage } from './databaseMessage';

const looksLikeMessageKey = (value?: string | null) => Boolean(value && /^[a-z][\w-]*(?:\.[\w-]+)+$/.test(value));

export const resolveBuiltinMessage = (id?: string | null, fallback?: string | null) => {
  const normalizedId = id?.trim();
  const normalizedFallback = fallback?.trim();
  if (!normalizedId) {
    return normalizedFallback || '';
  }

  if (hasDatabaseMessage(normalizedId)) {
    return databaseMessage(normalizedId);
  }

  if (normalizedFallback && looksLikeMessageKey(normalizedFallback)) {
    if (hasDatabaseMessage(normalizedFallback)) {
      return databaseMessage(normalizedFallback);
    }
  }

  if (normalizedFallback && !looksLikeMessageKey(normalizedFallback)) {
    return normalizedFallback;
  }

  return normalizedFallback || normalizedId;
};
