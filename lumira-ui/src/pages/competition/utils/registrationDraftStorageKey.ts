const REGISTRATION_DRAFT_PREFIX = 'lumira.registration.create.draft.v2';

export const buildRegistrationDraftStorageKey = (userId?: number | string) => {
  const normalizedUserId = String(userId ?? '').trim();
  return `${REGISTRATION_DRAFT_PREFIX}:${normalizedUserId || 'anonymous'}`;
};
