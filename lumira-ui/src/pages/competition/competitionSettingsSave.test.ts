import { describe, expect, it } from 'vitest';
import {
  getCompetitionCreateMissingFields,
  isConfigModuleDraftSaveCurrent,
  isConfigModuleItemKeyDuplicate,
  isBasicSettingsPageReadyToSave,
  isConfigModuleReadyToSave,
  isPaymentSettingsPageReadyToSave,
  isTimelineSettingsPageReadyToSave,
  mergeStageMaterialSaveItems,
  shouldValidateTeamMemberLimitsForPage,
  shouldHydrateConfigModuleDraft,
} from './competitionSettingsSave';

describe('competition settings page-level save guards', () => {
  it('validates the basic page from only the fields rendered on that page', () => {
    expect(isBasicSettingsPageReadyToSave({
      title: 'AI application contest',
      category: 'OTHER',
      competitionLevel: 'NATIONAL',
      organizers: [{ role: 'Host', name: 'Contest committee' }],
      participationScope: 'Students',
    })).toBe(true);
  });

  it('keeps an incomplete basic page quiet instead of autosaving', () => {
    expect(isBasicSettingsPageReadyToSave({
      title: '',
      category: 'OTHER',
      competitionLevel: 'NATIONAL',
      organizers: [{ role: 'Host', name: 'Contest committee' }],
      participationScope: 'Students',
    })).toBe(false);
  });

  it('validates the payment page from the same fee fields used during creation', () => {
    expect(isPaymentSettingsPageReadyToSave({
      feeMode: 'TEAM',
      entryFeeMinor: 0,
      currency: 'CNY',
    })).toBe(true);
    expect(isPaymentSettingsPageReadyToSave({
      feeMode: 'TEAM',
      currency: 'CNY',
    })).toBe(false);
  });

  it('allows a timeline page to save independently from basic fields', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026.07.01 09:00', '2026.07.31 18:00'],
      schedules: [{ timeMode: 'TBD' }],
    })).toBe(true);
  });

  it('keeps legacy registration input fields compatible with field-module saves', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        itemType: 'REGISTRATION_FIELD',
        title: '联系人',
        itemKey: 'contact-name',
        metadata: {},
      },
    ])).toBe(true);
  });

  it('requires confirmed timeline rows to be complete before autosave', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026.07.01 09:00', '2026.07.31 18:00'],
      schedules: [{ timeMode: 'CONFIRMED', title: 'Final' }],
    })).toBe(false);
  });

  it('requires a storage space and complete upload constraints for material items', () => {
    const material = {
      title: '作品文件',
      itemKey: 'work-file',
      metadata: {
        stageCode: 'PRELIMINARY',
        fileFormat: 'DOCUMENT',
        maxSizeMb: 100,
      },
    };

    expect(isConfigModuleReadyToSave('files', [material])).toBe(false);
    expect(isConfigModuleReadyToSave('files', [{
      ...material,
      metadata: { ...material.metadata, storageKey: 'competition_materials' },
    }])).toBe(true);
  });

  it.each(['PRELIMINARY', 'FINAL'])(
    'replaces a same-key legacy general material when saving the %s stage',
    (stageCode) => {
      const existingItems = [
        { id: 3, itemKey: 'work-file', stageCode: 'GENERAL' },
        { id: 8, itemKey: 'registration-proof', stageCode: 'GENERAL' },
        { id: 13, itemKey: 'final-report', stageCode: 'FINAL' },
      ];
      const currentStageItems = [
        { id: 32, itemKey: 'work-file', stageCode },
      ];

      expect(mergeStageMaterialSaveItems(
        existingItems,
        stageCode,
        currentStageItems,
        (item) => item.stageCode,
      )).toEqual([
        { id: 8, itemKey: 'registration-proof', stageCode: 'GENERAL' },
        ...(stageCode === 'PRELIMINARY'
          ? [{ id: 13, itemKey: 'final-report', stageCode: 'FINAL' }]
          : []),
        { id: 32, itemKey: 'work-file', stageCode },
      ]);
    },
  );

  it('requires the material and review ranges rendered in detailed timeline settings', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026-07-01 00:00', '2026-09-30 00:00'],
      schedules: [{
        timeMode: 'CONFIRMED',
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-09-30 00:00'],
      }],
    })).toBe(false);
  });

  it('allows a competition timeline to stay within the registration window', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026-07-01 00:00', '2026-09-30 00:00'],
      schedules: [{
        timeMode: 'CONFIRMED',
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-07-10 00:00'],
        reviewRange: ['2026-07-10 00:00', '2026-07-20 00:00'],
      }],
    })).toBe(true);
  });

  it('rejects every schedule range that exceeds the registration window', () => {
    const values = {
      registrationRange: ['2026-07-01 00:00', '2026-09-30 00:00'] as [string, string],
      schedules: [{
        timeMode: 'CONFIRMED' as const,
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-10-01 00:00'] as [string, string],
        reviewRange: ['2026-10-02 00:00', '2026-10-03 00:00'] as [string, string],
      }],
    };

    expect(isTimelineSettingsPageReadyToSave(values)).toBe(false);
    expect(getCompetitionCreateMissingFields(values)).toEqual(expect.arrayContaining([
      '提交材料时间必须在报名时间范围内',
      '评审时间必须在报名时间范围内',
    ]));
  });

  it('rejects a review window that starts before material submission closes', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026-07-01 00:00', '2026-09-30 00:00'],
      schedules: [{
        timeMode: 'CONFIRMED',
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-07-10 00:00'],
        reviewRange: ['2026-07-09 23:59', '2026-07-20 00:00'],
      }],
    })).toBe(false);
  });

  it('reports the direct material-to-review ordering error', () => {
    const values = {
      registrationRange: ['2026-07-01 00:00', '2026-10-20 00:00'] as [string, string],
      schedules: [{
        timeMode: 'CONFIRMED' as const,
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-10-01 00:00'] as [string, string],
        reviewRange: ['2026-09-30 00:00', '2026-10-20 00:00'] as [string, string],
      }],
    };

    expect(getCompetitionCreateMissingFields(values)).toContain('评审开始时间不得早于材料提交截止时间');
  });

  it('allows review to start exactly when material submission closes', () => {
    expect(isTimelineSettingsPageReadyToSave({
      registrationRange: ['2026-07-01 00:00', '2026-10-20 00:00'],
      schedules: [{
        timeMode: 'CONFIRMED',
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-09-30 00:00'],
        reviewRange: ['2026-09-30 00:00', '2026-10-20 00:00'],
      }],
    })).toBe(true);
  });

  it('uses the same basic, payment, and timeline validation for competition creation', () => {
    const completeValues = {
      title: 'AI application contest',
      category: 'OTHER',
      competitionLevel: 'NATIONAL',
      organizers: [{ role: 'Host', name: 'Contest committee' }],
      participationScope: 'Students',
      feeMode: 'TEAM' as const,
      entryFeeMinor: 0,
      currency: 'CNY',
      registrationRange: ['2026-07-01 00:00', '2026-10-20 00:00'] as [string, string],
      schedules: [{
        timeMode: 'CONFIRMED' as const,
        title: 'Preliminary',
        materialRange: ['2026-07-01 00:00', '2026-09-30 00:00'] as [string, string],
        reviewRange: ['2026-09-30 00:00', '2026-10-20 00:00'] as [string, string],
      }],
    };

    expect(getCompetitionCreateMissingFields(completeValues)).toEqual([]);
    expect(getCompetitionCreateMissingFields({
      ...completeValues,
      schedules: [{ ...completeValues.schedules[0], reviewRange: undefined }],
    })).toContain('评审时间');
    expect(getCompetitionCreateMissingFields({
      ...completeValues,
      schedules: [{ ...completeValues.schedules[0], materialRange: undefined }],
    })).toContain('提交材料时间');
  });

  it('keeps field-module autosave quiet while a new row title is still blank', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '',
        itemKey: 'registration_field-1783613006',
        metadata: { fieldType: 'TEXT' },
      },
    ])).toBe(false);
  });

  it('allows field-module autosave after the new row becomes complete', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '学校',
        itemKey: 'school',
        metadata: { fieldType: 'TEXT' },
      },
    ])).toBe(true);
  });

  it('validates only the registration field scope rendered on the current page', () => {
    const items = [
      {
        itemType: 'MEMBER_FIELD',
        title: 'Student name',
        itemKey: 'memberName',
        metadata: { fieldScope: 'MEMBER_FIELD', fieldType: 'TEXT' },
      },
      {
        itemType: 'TEAM_FIELD',
        title: '',
        itemKey: 'team-school',
        metadata: { fieldScope: 'TEAM_FIELD', fieldType: 'TEXT' },
      },
    ];

    expect(isConfigModuleReadyToSave('fields', items, { fieldScope: 'MEMBER_FIELD' })).toBe(true);
    expect(isConfigModuleReadyToSave('fields', items, { fieldScope: 'TEAM_FIELD' })).toBe(false);
  });

  it('validates project and intellectual-property groups as separate pages', () => {
    const items = [
      {
        itemType: 'PROJECT_FIELD',
        title: 'Project title',
        itemKey: 'title',
        metadata: { fieldScope: 'PROJECT_FIELD', fieldType: 'TEXT' },
      },
      {
        itemType: 'PROJECT_FIELD',
        title: '',
        itemKey: 'patent-number',
        metadata: { fieldScope: 'PROJECT_FIELD', groupLabel: 'Intellectual property', fieldType: 'TEXT' },
      },
    ];

    expect(isConfigModuleReadyToSave('fields', items, {
      fieldScope: 'PROJECT_FIELD',
      excludeGroupLabel: 'Intellectual property',
    })).toBe(true);
    expect(isConfigModuleReadyToSave('fields', items, {
      fieldScope: 'PROJECT_FIELD',
      includeGroupLabel: 'Intellectual property',
    })).toBe(false);
  });

  it('checks team member limits only on the team-information page', () => {
    expect(shouldValidateTeamMemberLimitsForPage('fields', 'TEAM_FIELD')).toBe(true);
    expect(shouldValidateTeamMemberLimitsForPage('fields', 'MEMBER_FIELD')).toBe(false);
    expect(shouldValidateTeamMemberLimitsForPage('documents')).toBe(false);
  });

  it('isolates required-field validation across every registration field page', () => {
    const intellectualPropertyGroup = 'Intellectual property';
    const completeItems = [
      {
        itemType: 'MEMBER_FIELD',
        title: 'Student name',
        itemKey: 'memberName',
        metadata: { fieldScope: 'MEMBER_FIELD', fieldType: 'TEXT' },
      },
      {
        itemType: 'TEAM_FIELD',
        title: 'Team name',
        itemKey: 'teamName',
        metadata: { fieldScope: 'TEAM_FIELD', fieldType: 'TEXT' },
      },
      {
        itemType: 'PROJECT_FIELD',
        title: 'Project title',
        itemKey: 'title',
        metadata: { fieldScope: 'PROJECT_FIELD', fieldType: 'TEXT' },
      },
      {
        itemType: 'PROJECT_FIELD',
        title: 'Patent number',
        itemKey: 'patentNumber',
        metadata: {
          fieldScope: 'PROJECT_FIELD',
          groupLabel: intellectualPropertyGroup,
          fieldType: 'TEXT',
        },
      },
    ];
    const pages = [
      { itemIndex: 0, validationScope: { fieldScope: 'MEMBER_FIELD' } },
      { itemIndex: 1, validationScope: { fieldScope: 'TEAM_FIELD' } },
      {
        itemIndex: 2,
        validationScope: {
          fieldScope: 'PROJECT_FIELD',
          excludeGroupLabel: intellectualPropertyGroup,
        },
      },
      {
        itemIndex: 3,
        validationScope: {
          fieldScope: 'PROJECT_FIELD',
          includeGroupLabel: intellectualPropertyGroup,
        },
      },
    ];

    pages.forEach((currentPage) => {
      pages.forEach((pageWithMissingValue) => {
        const draft = completeItems.map((item, index) => index === pageWithMissingValue.itemIndex
          ? { ...item, title: '' }
          : { ...item });
        const snapshot = structuredClone(draft);

        expect(isConfigModuleReadyToSave('fields', draft, currentPage.validationScope)).toBe(
          currentPage.itemIndex !== pageWithMissingValue.itemIndex,
        );
        expect(draft).toEqual(snapshot);
      });
    });
  });

  it('rejects field keys that only differ by letter case in the same scope', () => {
    const items = [
      { itemType: 'MEMBER_FIELD', itemKey: 'School', title: '学校', metadata: { fieldType: 'TEXT' } },
      { itemType: 'MEMBER_FIELD', itemKey: 'school', title: '院校', metadata: { fieldType: 'TEXT' } },
    ];

    expect(isConfigModuleItemKeyDuplicate(items, 0)).toBe(true);
    expect(isConfigModuleReadyToSave('fields', items)).toBe(false);
  });

  it('allows the same field key in different collection scopes', () => {
    const items = [
      { itemType: 'TEAM_FIELD', itemKey: 'school', title: '团队学校', metadata: { fieldType: 'TEXT' } },
      { itemType: 'MEMBER_FIELD', itemKey: 'School', title: '成员学校', metadata: { fieldType: 'TEXT' } },
    ];

    expect(isConfigModuleItemKeyDuplicate(items, 0)).toBe(false);
    expect(isConfigModuleReadyToSave('fields', items)).toBe(true);
  });

  it('requires configured options for a select field', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '性别',
        itemKey: 'gender',
        metadata: { fieldType: 'SELECT', options: '' },
      },
    ])).toBe(false);

    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '性别',
        itemKey: 'gender',
        metadata: { fieldType: 'SELECT', options: '男\n女' },
      },
    ])).toBe(true);
  });

  it('requires configured options for a multi-select field', () => {
    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '兴趣方向',
        itemKey: 'interests',
        metadata: { fieldType: 'MULTI_SELECT', options: '' },
      },
    ])).toBe(false);

    expect(isConfigModuleReadyToSave('fields', [
      {
        title: '兴趣方向',
        itemKey: 'interests',
        metadata: { fieldType: 'MULTI_SELECT', options: '人工智能\n机器人' },
      },
    ])).toBe(true);
  });

  it('preserves an incomplete local field when authoritative items are re-rendered', () => {
    expect(shouldHydrateConfigModuleDraft({
      hydratedContextKey: 'competition-1:fields',
      nextContextKey: 'competition-1:fields',
      draftRevision: 1,
      syncedRevision: 0,
    })).toBe(false);
  });

  it('hydrates authoritative items initially and after changing data context', () => {
    expect(shouldHydrateConfigModuleDraft({
      hydratedContextKey: undefined,
      nextContextKey: 'competition-1:fields',
      draftRevision: 0,
      syncedRevision: 0,
    })).toBe(true);
    expect(shouldHydrateConfigModuleDraft({
      hydratedContextKey: 'competition-1:fields',
      nextContextKey: 'competition-2:fields',
      draftRevision: 2,
      syncedRevision: 1,
    })).toBe(true);
  });

  it('hydrates the saved server response after the local revision becomes clean', () => {
    expect(shouldHydrateConfigModuleDraft({
      hydratedContextKey: 'competition-1:fields',
      nextContextKey: 'competition-1:fields',
      draftRevision: 2,
      syncedRevision: 2,
    })).toBe(true);
  });

  it('only marks the saved revision clean when no newer field edit exists', () => {
    expect(isConfigModuleDraftSaveCurrent(3, 3)).toBe(true);
    expect(isConfigModuleDraftSaveCurrent(3, 4)).toBe(false);
  });
});
