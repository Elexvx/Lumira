import { describe, expect, it } from 'vitest';
import {
  buildCompetitionMaterialFileStorageContext,
  shouldResetCompetitionMaterialValues,
} from './competitionMaterialFileStorage';

describe('competition material file storage', () => {
  it('uses a distinct physical directory and logical tags for each competition', () => {
    const first = buildCompetitionMaterialFileStorageContext(
      'ca5e4e82-5be1-4d06-8aba-3c9cb45acad1',
      'PRELIMINARY',
      'work-file',
    );
    const second = buildCompetitionMaterialFileStorageContext(
      '9b53cf22-ce80-4c78-8cd4-832ad025bb46',
      'PRELIMINARY',
      'work-file',
    );

    expect(first?.directory).toBe('competitions/ca5e4e825be14d068aba3c9cb45acad1');
    expect(second?.directory).not.toBe(first?.directory);
    expect(first?.tags).toContain('competition:ca5e4e82-5be1-4d06-8aba-3c9cb45acad1');
    expect(first?.tags).toContain('stage:preliminary');
    expect(first?.tags).toContain('field:work-file');
  });

  it('requires a valid competition UUID before allowing scoped upload metadata', () => {
    expect(buildCompetitionMaterialFileStorageContext(undefined, 'PRELIMINARY', 'work-file')).toBeUndefined();
    expect(buildCompetitionMaterialFileStorageContext('competition-1', 'PRELIMINARY', 'work-file')).toBeUndefined();
  });

  it('resets material values only when the user switches between two competitions', () => {
    expect(shouldResetCompetitionMaterialValues(1, 2)).toBe(true);
    expect(shouldResetCompetitionMaterialValues(1, 1)).toBe(false);
    expect(shouldResetCompetitionMaterialValues(undefined, 1)).toBe(false);
  });
});
