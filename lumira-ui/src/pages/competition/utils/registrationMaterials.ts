import type { CompetitionMaterialSubmissionRecord } from '@/services/competition/types';

type RegistrationMaterialField = {
  key: string;
  label?: string;
  type?: string;
  required?: boolean;
};

export const getMissingRequiredRegistrationMaterials = <T extends RegistrationMaterialField>(
  fields: T[],
  values: Record<string, unknown>,
) => fields.filter((field) => {
  if (!field.required) {
    return false;
  }
  const value = values[field.key];
  if (field.type === 'file') {
    return !Number.isSafeInteger(Number(value)) || Number(value) <= 0;
  }
  return value === undefined || value === null || !String(value).trim();
});

export const restoreRegistrationMaterialValues = (
  submissions: CompetitionMaterialSubmissionRecord[],
  stageId?: number,
) => {
  if (!stageId) {
    return {};
  }
  const submission = submissions.find((item) => item.stageId === stageId);
  if (!submission) {
    return {};
  }
  return Object.fromEntries(submission.values.map((value) => [
    value.fieldKey,
    value.fieldType === 'file'
      ? value.fileId ?? undefined
      : value.textValue ?? value.jsonValue ?? undefined,
  ]));
};
