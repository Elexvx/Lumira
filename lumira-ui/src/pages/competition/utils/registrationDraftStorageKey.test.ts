import { describe, expect, it } from 'vitest';
import { buildRegistrationDraftStorageKey } from './registrationDraftStorageKey';

describe('buildRegistrationDraftStorageKey', () => {
  it('isolates registration drafts by user', () => {
    expect(buildRegistrationDraftStorageKey(1001)).not.toBe(buildRegistrationDraftStorageKey(1002));
  });

  it('uses a stable anonymous scope before user bootstrap completes', () => {
    expect(buildRegistrationDraftStorageKey()).toBe('lumira.registration.create.draft.v2:anonymous');
  });
});
