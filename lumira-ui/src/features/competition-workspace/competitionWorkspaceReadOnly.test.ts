import { describe, expect, it } from 'vitest';
import {
  canMutateCompetitionWorkspace,
  isCompetitionWorkspaceReadOnly,
} from './competitionWorkspaceReadOnly';

describe('competition workspace read-only state', () => {
  it('keeps draft and published competitions mutable when permission is present', () => {
    expect(canMutateCompetitionWorkspace(true, 'draft')).toBe(true);
    expect(canMutateCompetitionWorkspace(true, 'published')).toBe(true);
  });

  it('makes archived competitions read-only even when the role has manage permission', () => {
    expect(isCompetitionWorkspaceReadOnly('archived')).toBe(true);
    expect(canMutateCompetitionWorkspace(true, 'archived')).toBe(false);
  });

  it('prefers the backend read-only contract over the legacy status fallback', () => {
    expect(isCompetitionWorkspaceReadOnly('published', true)).toBe(true);
    expect(isCompetitionWorkspaceReadOnly('archived', false)).toBe(false);
    expect(canMutateCompetitionWorkspace(true, 'published', true)).toBe(false);
  });

  it('does not grant mutation access without permission', () => {
    expect(canMutateCompetitionWorkspace(false, 'published')).toBe(false);
  });
});
