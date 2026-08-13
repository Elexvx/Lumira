import dayjs, { type Dayjs } from 'dayjs';
import type { ProfileFieldSetting } from '@/types/api';

export interface UserEditorValues extends Record<string, unknown> {
  birthMonth?: Dayjs | string | null;
  roleIds?: number[];
  deptIds?: number[];
  primaryDeptId?: number | null;
  password?: string;
  resetPassword?: boolean;
}

interface BuildUserEditorPayloadOptions {
  editing: boolean;
  profileFields?: ProfileFieldSetting[];
}

export interface UserEditorPayload extends Record<string, unknown> {
  birthMonth: string;
  roleIds: number[];
  deptIds: number[];
  primaryDeptId: number | null;
  password: string | undefined;
}

const profileFieldDateFormat = (field: ProfileFieldSetting) =>
  (field.fieldType || '').toUpperCase() === 'MONTH' ? 'YYYY-MM' : 'YYYY-MM-DD';

const isDateProfileField = (field: ProfileFieldSetting) =>
  ['DATE', 'MONTH'].includes((field.fieldType || '').toUpperCase());

export const normalizeExtraProfileValuesForEditor = (
  profileFields: ProfileFieldSetting[],
  extraProfileValues?: Record<string, unknown> | null,
) => {
  const fieldByKey = new Map(profileFields.map((field) => [field.fieldKey, field]));
  return Object.fromEntries(
    Object.entries(extraProfileValues || {}).map(([fieldKey, value]) => {
      const field = fieldByKey.get(fieldKey);
      if (!field || !isDateProfileField(field) || value === null || value === undefined || value === '') {
        return [fieldKey, value];
      }
      return [fieldKey, dayjs.isDayjs(value) ? value : dayjs(String(value), profileFieldDateFormat(field))];
    }),
  );
};

const serializeExtraProfileValue = (field: ProfileFieldSetting | undefined, value: unknown) => {
  if (value === null || value === undefined) {
    return '';
  }
  if (field && isDateProfileField(field) && dayjs.isDayjs(value)) {
    return value.format(profileFieldDateFormat(field));
  }
  return String(value);
};

/**
 * Password managers may populate the optional reset field without the operator
 * choosing to reset a password. Existing users only submit that field after
 * the operator explicitly enables password reset; create-user still requires
 * and forwards the initial secret.
 */
export const buildUserEditorPayload = (
  values: UserEditorValues,
  { editing, profileFields = [] }: BuildUserEditorPayloadOptions,
): UserEditorPayload => {
  const { resetPassword, password, ...submittedValues } = values;
  const shouldSubmitPassword = !editing || resetPassword === true;
  const submittedPassword = shouldSubmitPassword
    && typeof password === 'string'
    && password.trim().length > 0
    ? password
    : undefined;

  const profileFieldByKey = new Map(profileFields.map((field) => [field.fieldKey, field]));
  const extraProfileValues = submittedValues.extraProfileValues;
  if (extraProfileValues && typeof extraProfileValues === 'object' && !Array.isArray(extraProfileValues)) {
    submittedValues.extraProfileValues = Object.fromEntries(
      Object.entries(extraProfileValues as Record<string, unknown>).map(([fieldKey, value]) => [
        fieldKey,
        serializeExtraProfileValue(profileFieldByKey.get(fieldKey), value),
      ]),
    );
  }

  return {
    ...submittedValues,
    birthMonth: values.birthMonth ? dayjs(values.birthMonth).format('YYYY-MM') : '',
    roleIds: values.roleIds || [],
    deptIds: values.deptIds || [],
    primaryDeptId: values.primaryDeptId || values.deptIds?.[0] || null,
    password: submittedPassword,
  };
};
