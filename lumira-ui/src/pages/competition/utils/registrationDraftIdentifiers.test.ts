import { describe, expect, it } from 'vitest';
import { buildRegistrationDraftIdentifiers } from './registrationDraftIdentifiers';

describe('buildRegistrationDraftIdentifiers', () => {
  it('generates stable, separate identifiers for one draft', () => {
    const identifiers = buildRegistrationDraftIdentifiers(1_755_235_083_842, 'competition:42');
    expect(identifiers).toEqual(buildRegistrationDraftIdentifiers(1_755_235_083_842, 'competition:42'));
    expect(identifiers.registrationNo).toMatch(/^REG-\d{17}-[A-Z0-9]{6}$/);
    expect(identifiers.participantNo).toMatch(/^PART-\d{17}-[A-Z0-9]{6}$/);
    expect(identifiers.registrationNo).not.toBe(identifiers.participantNo);
  });

  it('changes the identifiers when the draft seed changes', () => {
    expect(buildRegistrationDraftIdentifiers(1_755_235_083_842, 'competition:42'))
      .not.toEqual(buildRegistrationDraftIdentifiers(1_755_235_083_842, 'competition:43'));
  });
});
