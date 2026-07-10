const FALLBACK_QR_REFRESH_DELAY_MS = 4 * 60_000;
const MIN_QR_REFRESH_LEAD_MS = 15_000;
const MAX_QR_REFRESH_LEAD_MS = 60_000;
const MIN_QR_REFRESH_DELAY_MS = 30_000;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

export const resolveWechatQrRefreshDelayMs = (stateExpireMinutes?: number) => {
  if (!Number.isFinite(stateExpireMinutes) || !stateExpireMinutes || stateExpireMinutes <= 0) {
    return FALLBACK_QR_REFRESH_DELAY_MS;
  }

  const ttlMs = stateExpireMinutes * 60_000;
  const leadMs = clamp(Math.floor(ttlMs * 0.2), MIN_QR_REFRESH_LEAD_MS, MAX_QR_REFRESH_LEAD_MS);
  return Math.max(ttlMs - leadMs, MIN_QR_REFRESH_DELAY_MS);
};
