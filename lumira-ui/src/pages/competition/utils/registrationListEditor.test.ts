import { describe, expect, it } from 'vitest';
import {
  buildFormalRegistrationListQuery,
  deleteRegistrationListEntry,
  REGISTRATION_LIST_PAGE_SIZE,
  saveRegistrationListEntry,
  shouldPaginateRegistrationList,
} from './registrationListEditor';

describe('registration modal list editor', () => {
  it('adds or edits exactly one row without mutating the displayed list', () => {
    const original = [{ name: 'A' }, { name: 'B' }];

    expect(saveRegistrationListEntry(original, 'new', { name: 'C' })).toEqual([
      { name: 'A' },
      { name: 'B' },
      { name: 'C' },
    ]);
    expect(saveRegistrationListEntry(original, 1, { name: 'B2' })).toEqual([
      { name: 'A' },
      { name: 'B2' },
    ]);
    expect(original).toEqual([{ name: 'A' }, { name: 'B' }]);
  });

  it('leaves data unchanged until save and deletes only the confirmed row', () => {
    const displayed = [{ name: 'A' }, { name: 'B' }, { name: 'C' }];
    const cancelled = displayed;

    expect(cancelled).toBe(displayed);
    expect(deleteRegistrationListEntry(displayed, 1)).toEqual([{ name: 'A' }, { name: 'C' }]);
    expect(displayed).toHaveLength(3);
  });

  it('starts pagination only after five rows', () => {
    expect(REGISTRATION_LIST_PAGE_SIZE).toBe(5);
    expect(shouldPaginateRegistrationList(5)).toBe(false);
    expect(shouldPaginateRegistrationList(6)).toBe(true);
  });

  it('forwards formal-list status and keyword filters to the server query', () => {
    expect(buildFormalRegistrationListQuery(2, 10, 'PENDING_PAYMENT', ' REG-2026 ')).toEqual({
      pageNo: 2,
      pageSize: 10,
      status: 'PENDING_PAYMENT',
      keyword: 'REG-2026',
    });
    expect(buildFormalRegistrationListQuery(1, 10, ' ', ' ')).toEqual({
      pageNo: 1,
      pageSize: 10,
      status: undefined,
      keyword: undefined,
    });
  });
});
