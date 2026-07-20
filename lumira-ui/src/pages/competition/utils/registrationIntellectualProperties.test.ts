import { describe, expect, it } from 'vitest';
import {
  buildRegistrationProjectExtraValues,
  hasRegistrationIntellectualPropertyContent,
  INTELLECTUAL_PROPERTY_ENTRIES_KEY,
  migrateRegistrationIntellectualPropertyValues,
  normalizeRegistrationIntellectualPropertyEntries,
} from './registrationIntellectualProperties';

const intellectualPropertyFieldKeys = ['ipType', 'ipName', 'applicationNo'];

describe('registration intellectual property values', () => {
  it('migrates legacy flat fields into the first intellectual property entry', () => {
    expect(migrateRegistrationIntellectualPropertyValues({
      projectTrack: 'AI',
      ipType: 'SOFTWARE_COPYRIGHT',
      ipName: 'Lumira',
    }, intellectualPropertyFieldKeys)).toEqual({
      projectTrack: 'AI',
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [{
        ipType: 'SOFTWARE_COPYRIGHT',
        ipName: 'Lumira',
      }],
    });
  });

  it('keeps multiple mixed intellectual property entries and removes blank rows', () => {
    expect(normalizeRegistrationIntellectualPropertyEntries({
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [
        { ipType: 'SOFTWARE_COPYRIGHT', ipName: '软件平台' },
        {},
        { ipType: 'INVENTION_PATENT', applicationNo: 'CN-1', ignored: 'value' },
      ],
    }, intellectualPropertyFieldKeys)).toEqual([
      { ipType: 'SOFTWARE_COPYRIGHT', ipName: '软件平台' },
      { ipType: 'INVENTION_PATENT', applicationNo: 'CN-1' },
    ]);
  });

  it('serializes project fields separately from repeatable intellectual property entries', () => {
    expect(buildRegistrationProjectExtraValues({
      projectTrack: 'AI',
      ipType: 'legacy-value',
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [
        { ipType: 'SOFTWARE_COPYRIGHT', ipName: '软件平台' },
        { ipType: 'INVENTION_PATENT', applicationNo: 'CN-1' },
      ],
    }, ['projectTrack'], intellectualPropertyFieldKeys)).toEqual({
      projectTrack: 'AI',
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [
        { ipType: 'SOFTWARE_COPYRIGHT', ipName: '软件平台' },
        { ipType: 'INVENTION_PATENT', applicationNo: 'CN-1' },
      ],
    });
  });

  it('does not treat an empty repeatable row as draft content', () => {
    expect(hasRegistrationIntellectualPropertyContent({
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [{}],
    })).toBe(false);
  });

  it('preserves stored values while field configuration is still loading', () => {
    expect(migrateRegistrationIntellectualPropertyValues({
      ipType: 'SOFTWARE_COPYRIGHT',
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [{ ipName: '软件平台' }],
    }, [])).toEqual({
      ipType: 'SOFTWARE_COPYRIGHT',
      [INTELLECTUAL_PROPERTY_ENTRIES_KEY]: [{ ipName: '软件平台' }],
    });
  });
});
