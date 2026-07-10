const DEFAULT_TOKEN_REFRESH_SKEW_MS = 60_000;
const MIN_TOKEN_REFRESH_SKEW_MS = 1_000;
const TOKEN_REFRESH_SKEW_RATIO = 0.2;

export const resolveTokenRefreshDelayMs = (remainingMs: number) => {
  if (!Number.isFinite(remainingMs) || remainingMs <= 0) {
    return 0;
  }

  const proportionalSkewMs = Math.floor(remainingMs * TOKEN_REFRESH_SKEW_RATIO);
  const effectiveSkewMs = Math.min(
    DEFAULT_TOKEN_REFRESH_SKEW_MS,
    Math.max(MIN_TOKEN_REFRESH_SKEW_MS, proportionalSkewMs),
  );

  return Math.max(remainingMs - effectiveSkewMs, 0);
};
