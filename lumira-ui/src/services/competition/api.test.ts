import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CompetitionConfigItem } from './types';
import { saveCompetitionSettingsModule } from './api';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

const registrationDraft: CompetitionConfigItem[] = [
  { itemType: 'MEMBER_FIELD', itemKey: 'memberName', title: 'Student name' },
  { itemType: 'TEAM_FIELD', itemKey: 'teamName', title: 'Team name' },
  { itemType: 'PROJECT_FIELD', itemKey: 'title', title: 'Project title' },
  {
    itemType: 'PROJECT_FIELD',
    itemKey: 'patentNumber',
    title: 'Patent number',
    contentJson: JSON.stringify({ groupLabel: 'Intellectual property' }),
  },
];

describe('competition settings submission', () => {
  beforeEach(() => {
    mocks.request.mockReset();
  });

  it('submits the latest complete field draft after every registration-page switch', () => {
    let draft = registrationDraft.map((item) => ({ ...item }));
    const pageEdits = [
      { itemKey: 'memberName', title: 'Updated student name' },
      { itemKey: 'teamName', title: 'Updated team name' },
      { itemKey: 'title', title: 'Updated project title' },
      { itemKey: 'patentNumber', title: 'Updated patent number' },
      { itemKey: 'title', title: 'Project title after switching back' },
      { itemKey: 'memberName', title: 'Student name after switching back' },
    ];

    pageEdits.forEach((edit, index) => {
      draft = draft.map((item) => item.itemKey === edit.itemKey ? { ...item, title: edit.title } : item);
      saveCompetitionSettingsModule('competition-uuid', 'fields', draft);

      expect(mocks.request).toHaveBeenNthCalledWith(
        index + 1,
        '/v2/aiadc/competitions/competition-uuid/settings/fields',
        {
          method: 'PUT',
          data: { items: draft },
        },
      );
      expect(draft).toHaveLength(registrationDraft.length);
      expect(draft.map((item) => item.itemKey)).toEqual(registrationDraft.map((item) => item.itemKey));
    });
  });

  it('keeps registration documents on their independent submission page', () => {
    const documents: CompetitionConfigItem[] = [
      { itemType: 'AGREEMENT', itemKey: 'commitment', title: 'Competition commitment' },
      { itemType: 'CONSENT', itemKey: 'consent', title: 'Informed consent' },
    ];

    saveCompetitionSettingsModule('competition-uuid', 'documents', documents);

    expect(mocks.request).toHaveBeenCalledWith(
      '/v2/aiadc/competitions/competition-uuid/settings/documents',
      {
        method: 'PUT',
        data: { items: documents },
      },
    );
  });
});
