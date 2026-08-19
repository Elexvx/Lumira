const REGISTRATION_LOCAL_DRAFT_PREFIX = 'lumira.registration.create.local.draft.v3';

export interface RegistrationDraftEnvelope<T> {
  payload: T;
  updatedAt: number;
}

export type RegistrationDraftRestoreSource = 'local' | 'cloud';

export interface RegistrationDraftRestoreResult<T> {
  envelope: RegistrationDraftEnvelope<T>;
  source: RegistrationDraftRestoreSource;
}

type RegistrationDraftVersion = {
  localUpdatedAt?: number;
  savedAt?: number;
};

type DraftStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

const getLocalDraftStorage = (): DraftStorage | undefined => {
  try {
    if (typeof globalThis === 'undefined' || !('localStorage' in globalThis)) {
      return undefined;
    }
    return globalThis.localStorage;
  } catch {
    return undefined;
  }
};

export const buildLocalRegistrationDraftKey = (userId?: number | string) => {
  const normalizedUserId = String(userId ?? '').trim();
  return `${REGISTRATION_LOCAL_DRAFT_PREFIX}:${normalizedUserId || 'anonymous'}`;
};

export const getRegistrationDraftUpdatedAt = (draft?: RegistrationDraftVersion) => (
  Math.max(0, Number(draft?.localUpdatedAt) || 0, Number(draft?.savedAt) || 0)
);

export const nextRegistrationDraftUpdatedAt = (
  draft?: RegistrationDraftVersion,
  now = Date.now(),
) => Math.max(now, getRegistrationDraftUpdatedAt(draft) + 1);

export const hasNewerRegistrationDraft = (
  draft: RegistrationDraftVersion | undefined,
  submittedAt: number,
) => getRegistrationDraftUpdatedAt(draft) > submittedAt;

const isRegistrationDraftEnvelope = <T,>(value: unknown): value is RegistrationDraftEnvelope<T> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false;
  }
  const candidate = value as Partial<RegistrationDraftEnvelope<T>>;
  return candidate.payload !== undefined
    && typeof candidate.updatedAt === 'number'
    && Number.isFinite(candidate.updatedAt)
    && candidate.updatedAt > 0;
};

export const readLocalRegistrationDraft = <T,>(
  userId?: number | string,
  storage: DraftStorage | undefined = getLocalDraftStorage(),
): RegistrationDraftEnvelope<T> | undefined => {
  if (!storage) {
    return undefined;
  }
  const storageKey = buildLocalRegistrationDraftKey(userId);
  try {
    const serialized = storage.getItem(storageKey);
    if (!serialized) {
      return undefined;
    }
    const parsed = JSON.parse(serialized) as unknown;
    if (isRegistrationDraftEnvelope<T>(parsed)) {
      return parsed;
    }
  } catch {
    // Ignore unavailable storage and corrupted local drafts.
  }
  try {
    storage.removeItem(storageKey);
  } catch {
    // Storage can be unavailable in restricted browsing contexts.
  }
  return undefined;
};

export const writeLocalRegistrationDraft = <T,>(
  userId: number | string | undefined,
  envelope: RegistrationDraftEnvelope<T>,
  storage: DraftStorage | undefined = getLocalDraftStorage(),
) => {
  storage?.setItem(buildLocalRegistrationDraftKey(userId), JSON.stringify(envelope));
};

export const clearLocalRegistrationDraft = (
  userId?: number | string,
  storage: DraftStorage | undefined = getLocalDraftStorage(),
) => {
  try {
    storage?.removeItem(buildLocalRegistrationDraftKey(userId));
  } catch {
    // Storage can be unavailable in restricted browsing contexts.
  }
};

export const isRegistrationDraftForRegistration = (
  envelope: RegistrationDraftEnvelope<unknown> | null | undefined,
  registrationId: number,
) => {
  const payload = envelope?.payload;
  return Boolean(
    payload
      && typeof payload === 'object'
      && !Array.isArray(payload)
      && (payload as { registrationId?: unknown }).registrationId === registrationId,
  );
};

export const resolveNewestRegistrationDraft = <T,>(
  localDraft?: RegistrationDraftEnvelope<T>,
  cloudDraft?: RegistrationDraftEnvelope<T>,
): RegistrationDraftRestoreResult<T> | undefined => {
  if (!localDraft && !cloudDraft) {
    return undefined;
  }
  if (!cloudDraft || (localDraft && localDraft.updatedAt > cloudDraft.updatedAt)) {
    return localDraft ? { envelope: localDraft, source: 'local' } : undefined;
  }
  return { envelope: cloudDraft, source: 'cloud' };
};
