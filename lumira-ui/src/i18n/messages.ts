import { databaseMessage, hasDatabaseMessage } from './databaseMessage';
import { resolveBuiltinFallbackMessage, shouldUseBuiltinFallback } from './builtinFallbackMessages';

const looksLikeMessageKey = (value?: string | null) => Boolean(value && /^[a-z][\w-]*(?:\.[\w-]+)+$/.test(value));

export const resolveBuiltinMessage = (id?: string | null, fallback?: string | null) => {
  const normalizedId = id?.trim();
  const normalizedFallback = fallback?.trim();
  if (!normalizedId) {
    return normalizedFallback || '';
  }

  if (hasDatabaseMessage(normalizedId)) {
    const resolvedDatabaseMessage = databaseMessage(normalizedId);
    if (!shouldUseBuiltinFallback(normalizedId, resolvedDatabaseMessage)) {
      return resolvedDatabaseMessage;
    }
  }

  const builtinFallback = resolveBuiltinFallbackMessage(normalizedId);
  if (builtinFallback) {
    return builtinFallback;
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
