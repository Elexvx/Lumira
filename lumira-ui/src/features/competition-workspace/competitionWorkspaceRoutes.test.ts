import { describe, expect, it } from 'vitest';
import {
  competitionWorkspaceModuleFromPath,
  competitionWorkspacePath,
  isCertificateRecordsPath,
  isCompetitionUuid,
  normalizeCompetitionUuid,
} from './competitionWorkspaceRoutes';

const UUID = 'ca5e4e82-5be1-4d06-8aba-3c9cb45acad1';

describe('competition workspace route contract', () => {
  it('accepts canonical UUIDs and normalizes case without accepting arbitrary IDs', () => {
    expect(isCompetitionUuid(UUID)).toBe(true);
    expect(normalizeCompetitionUuid(UUID.toUpperCase())).toBe(UUID);
    expect(isCompetitionUuid('11')).toBe(false);
    expect(normalizeCompetitionUuid('11')).toBeUndefined();
  });

  it('keeps the workspace UUID in every module path', () => {
    expect(competitionWorkspacePath(UUID, 'registrations')).toBe(`/competitions/${UUID}/registrations`);
    expect(competitionWorkspaceModuleFromPath(`/competitions/${UUID}/reviews`)).toBe('reviews');
    expect(competitionWorkspaceModuleFromPath(`/competitions/${UUID}`)).toBe('overview');
  });

  it('falls back when the retired materials path is requested', () => {
    expect(competitionWorkspaceModuleFromPath(`/competitions/${UUID}/materials`)).toBe('overview');
  });

  it('identifies certificate records as the list-return workspace page', () => {
    expect(isCertificateRecordsPath(`/competitions/${UUID}/certificates/records`)).toBe(true);
    expect(isCertificateRecordsPath(`/competitions/${UUID}/certificates/records/`)).toBe(true);
    expect(isCertificateRecordsPath(`/competitions/${UUID}/certificates/batches`)).toBe(false);
  });

});
