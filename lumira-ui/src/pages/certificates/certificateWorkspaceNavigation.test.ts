import { describe, expect, it } from 'vitest';
import { certificateWorkspaceSectionPath } from './certificateWorkspaceNavigation';

describe('certificate workspace navigation', () => {
  it.each(['generate', 'batches', 'records'] as const)('builds the %s route inside the selected competition', (section) => {
    expect(certificateWorkspaceSectionPath('competition uuid', section))
      .toBe(`/competitions/competition%20uuid/certificates/${section}`);
  });
});
