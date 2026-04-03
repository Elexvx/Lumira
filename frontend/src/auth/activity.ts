import { buildStorageKey, storage } from '@/cache/storage';

const ACTIVITY_KEY = 'session_last_activity';

export const getSessionActivityStorageKey = () => buildStorageKey(ACTIVITY_KEY);

export const getStoredSessionActivityAt = (): number | null => storage.get<number>(ACTIVITY_KEY);

export const persistSessionActivity = (timestamp: number) => {
  storage.set(ACTIVITY_KEY, timestamp);
};

export const clearSessionActivity = () => {
  storage.remove(ACTIVITY_KEY);
};
