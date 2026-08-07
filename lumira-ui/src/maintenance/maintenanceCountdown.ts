import { useEffect, useState } from 'react';

export const getMaintenanceRemainingSeconds = (endAt?: string | null, now = Date.now()) => {
  if (!endAt || !Number.isFinite(now)) {
    return null;
  }
  const endAtMillis = Date.parse(endAt);
  if (!Number.isFinite(endAtMillis)) {
    return null;
  }
  return Math.max(0, Math.ceil((endAtMillis - now) / 1000));
};

export const formatMaintenanceCountdown = (remainingSeconds: number) => {
  const safeSeconds = Math.max(0, Math.floor(remainingSeconds));
  const days = Math.floor(safeSeconds / 86_400);
  const hours = Math.floor((safeSeconds % 86_400) / 3_600);
  const minutes = Math.floor((safeSeconds % 3_600) / 60);
  const seconds = safeSeconds % 60;
  const clock = [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':');
  return days > 0 ? `${days}d ${clock}` : clock;
};

export const useMaintenanceCountdown = (endAt?: string | null) => {
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(() =>
    getMaintenanceRemainingSeconds(endAt),
  );

  useEffect(() => {
    const update = () => setRemainingSeconds(getMaintenanceRemainingSeconds(endAt));
    update();
    if (getMaintenanceRemainingSeconds(endAt) === null) {
      return undefined;
    }
    const intervalId = window.setInterval(update, 1000);
    return () => window.clearInterval(intervalId);
  }, [endAt]);

  return remainingSeconds;
};
