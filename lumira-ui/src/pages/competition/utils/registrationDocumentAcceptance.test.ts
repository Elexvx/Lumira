import { describe, expect, it } from 'vitest';
import {
  buildRegistrationDocumentAcceptanceStorageKey,
  buildRegistrationDocumentCountdowns,
  getLegacyRegistrationDocumentKey,
  getRegistrationDocumentAcceptanceKey,
  resolveAcceptedRegistrationDocumentKeys,
} from './registrationDocumentAcceptance';

const document = {
  id: 12,
  itemKey: 'promise',
  itemType: 'AGREEMENT',
  title: '赛事承诺书',
  contentText: '请遵守竞赛规则。',
  contentJson: '{"readingSeconds":10}',
  updatedAt: '2026-07-28T10:00:00',
};

describe('registration document acceptance', () => {
  it('keeps the same acceptance key while the document version is unchanged', () => {
    expect(getRegistrationDocumentAcceptanceKey(document, 0))
      .toBe(getRegistrationDocumentAcceptanceKey({ ...document }, 0));
  });

  it('requires confirmation again after the document content changes', () => {
    expect(getRegistrationDocumentAcceptanceKey(document, 0))
      .not.toBe(getRegistrationDocumentAcceptanceKey({ ...document, contentText: '条款已更新。' }, 0));
  });

  it('restores remembered confirmation and skips the reading countdown', () => {
    const key = getRegistrationDocumentAcceptanceKey(document, 0);
    expect(resolveAcceptedRegistrationDocumentKeys([document], [key])).toEqual([key]);
    expect(buildRegistrationDocumentCountdowns([document], [key], () => 10)).toEqual({ [key]: 0 });
  });

  it('migrates the legacy acceptance stored in an active registration draft', () => {
    const key = getRegistrationDocumentAcceptanceKey(document, 0);
    expect(resolveAcceptedRegistrationDocumentKeys(
      [document],
      [],
      [getLegacyRegistrationDocumentKey(document, 0)],
    )).toEqual([key]);
  });

  it('isolates remembered confirmation by competition', () => {
    expect(buildRegistrationDocumentAcceptanceStorageKey('competition-a'))
      .not.toBe(buildRegistrationDocumentAcceptanceStorageKey('competition-b'));
  });
});
