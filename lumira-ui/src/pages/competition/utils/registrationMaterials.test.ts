import { describe, expect, it } from 'vitest';
import type { CompetitionMaterialSubmissionRecord } from '@/services/competition/types';
import {
  getMissingRequiredRegistrationMaterials,
  restoreRegistrationMaterialValues,
} from './registrationMaterials';

const submission = (
  id: number,
  stageId: number,
  values: CompetitionMaterialSubmissionRecord['values'],
): CompetitionMaterialSubmissionRecord => ({
  id,
  registrationId: 11,
  competitionId: 22,
  stageId,
  formVersion: 1,
  submitterUserId: 33,
  status: 'SUBMITTED',
  values,
});

describe('restoreRegistrationMaterialValues', () => {
  it('restores the material values for the active stage', () => {
    expect(restoreRegistrationMaterialValues([
      submission(1, 101, [{
        id: 1,
        submissionId: 1,
        fieldKey: 'project-plan',
        fieldType: 'file',
        fileId: 9001,
      }]),
      submission(2, 202, [{
        id: 2,
        submissionId: 2,
        fieldKey: 'summary',
        fieldType: 'text',
        textValue: 'final material',
      }]),
    ], 101)).toEqual({ 'project-plan': 9001 });
  });

  it('uses json values when text is absent', () => {
    expect(restoreRegistrationMaterialValues([
      submission(1, 101, [{
        id: 1,
        submissionId: 1,
        fieldKey: 'structured',
        fieldType: 'text',
        jsonValue: '{"accepted":true}',
      }]),
    ], 101)).toEqual({ structured: '{"accepted":true}' });
  });
});

describe('getMissingRequiredRegistrationMaterials', () => {
  const fields = [
    { key: 'plan', label: '项目计划书', type: 'file', required: true },
    { key: 'work', label: '初赛作品文件', type: 'file', required: true },
    { key: 'note', label: '补充说明', type: 'text', required: false },
  ];

  it('lists required materials that have not been uploaded', () => {
    expect(getMissingRequiredRegistrationMaterials(fields, { plan: 9001 }))
      .toEqual([fields[1]]);
  });

  it('accepts positive uploaded file ids', () => {
    expect(getMissingRequiredRegistrationMaterials(fields, { plan: 9001, work: 9002 }))
      .toEqual([]);
  });
});
