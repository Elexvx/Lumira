import { describe, expect, it } from 'vitest';
import {
  buildLocalRegistrationDraftKey,
  clearLocalRegistrationDraft,
  hasNewerRegistrationDraft,
  isRegistrationDraftForRegistration,
  nextRegistrationDraftUpdatedAt,
  readLocalRegistrationDraft,
  resolveNewestRegistrationDraft,
  writeLocalRegistrationDraft,
} from './registrationDraftPersistence';

const createStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  };
};

describe('registration local and cloud draft persistence', () => {
  it('isolates local drafts by user and clears only the selected user', () => {
    const storage = createStorage();
    writeLocalRegistrationDraft(1001, { payload: { teamName: 'A' }, updatedAt: 10 }, storage);
    writeLocalRegistrationDraft(1002, { payload: { teamName: 'B' }, updatedAt: 20 }, storage);

    expect(readLocalRegistrationDraft<{ teamName: string }>(1001, storage)?.payload.teamName).toBe('A');
    expect(readLocalRegistrationDraft<{ teamName: string }>(1002, storage)?.payload.teamName).toBe('B');

    clearLocalRegistrationDraft(1001, storage);
    expect(readLocalRegistrationDraft(1001, storage)).toBeUndefined();
    expect(readLocalRegistrationDraft<{ teamName: string }>(1002, storage)?.payload.teamName).toBe('B');
  });

  it('removes corrupted or invalid local data instead of breaking hydration', () => {
    const storage = createStorage();
    const storageKey = buildLocalRegistrationDraftKey(1001);
    storage.setItem(storageKey, '{broken-json');
    expect(readLocalRegistrationDraft(1001, storage)).toBeUndefined();
    expect(storage.getItem(storageKey)).toBeNull();

    storage.setItem(storageKey, JSON.stringify({ payload: {}, updatedAt: 0 }));
    expect(readLocalRegistrationDraft(1001, storage)).toBeUndefined();
    expect(storage.getItem(storageKey)).toBeNull();
  });

  it('continues without a draft when browser storage is unavailable', () => {
    const unavailableStorage = {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
      removeItem: () => { throw new Error('blocked'); },
    };

    expect(readLocalRegistrationDraft(1001, unavailableStorage)).toBeUndefined();
    expect(() => clearLocalRegistrationDraft(1001, unavailableStorage)).not.toThrow();
  });

  it('matches successful payment cleanup only to the submitted registration draft', () => {
    expect(isRegistrationDraftForRegistration(
      { payload: { registrationId: 3001 }, updatedAt: 10 },
      3001,
    )).toBe(true);
    expect(isRegistrationDraftForRegistration(
      { payload: { registrationId: 3002 }, updatedAt: 20 },
      3001,
    )).toBe(false);
    expect(isRegistrationDraftForRegistration(
      { payload: { teamName: 'new draft' }, updatedAt: 30 },
      3001,
    )).toBe(false);
  });

  it('keeps local draft versions monotonic and detects edits made during cloud sync', () => {
    const submittedAt = nextRegistrationDraftUpdatedAt({ localUpdatedAt: 100 }, 100);
    const editedAt = nextRegistrationDraftUpdatedAt({ localUpdatedAt: submittedAt }, 100);

    expect(submittedAt).toBe(101);
    expect(editedAt).toBe(102);
    expect(hasNewerRegistrationDraft({ localUpdatedAt: editedAt }, submittedAt)).toBe(true);
    expect(hasNewerRegistrationDraft({ localUpdatedAt: submittedAt }, submittedAt)).toBe(false);
  });

  it('restores the newer draft and prefers cloud when timestamps are equal', () => {
    const local = { payload: { source: 'local' }, updatedAt: 20 };
    const cloud = { payload: { source: 'cloud' }, updatedAt: 10 };
    expect(resolveNewestRegistrationDraft(local, cloud)).toEqual({ envelope: local, source: 'local' });
    expect(resolveNewestRegistrationDraft(local, { ...cloud, updatedAt: 30 })).toEqual({
      envelope: { ...cloud, updatedAt: 30 },
      source: 'cloud',
    });
    expect(resolveNewestRegistrationDraft(local, { ...cloud, updatedAt: 20 })?.source).toBe('cloud');
  });
});
