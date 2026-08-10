import dayjs, { type Dayjs } from 'dayjs';

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
}

/**
 * Password managers may populate the optional reset field without the operator
 * choosing to reset a password. Existing users only submit that field after
 * the operator explicitly enables password reset; create-user still requires
 * and forwards the initial secret.
 */
export const buildUserEditorPayload = (
  values: UserEditorValues,
  { editing }: BuildUserEditorPayloadOptions,
) => {
  const { resetPassword, password, ...submittedValues } = values;
  const shouldSubmitPassword = !editing || resetPassword === true;
  const submittedPassword = shouldSubmitPassword
    && typeof password === 'string'
    && password.trim().length > 0
    ? password
    : undefined;

  return {
    ...submittedValues,
    birthMonth: values.birthMonth ? dayjs(values.birthMonth).format('YYYY-MM') : '',
    roleIds: values.roleIds || [],
    deptIds: values.deptIds || [],
    primaryDeptId: values.primaryDeptId || values.deptIds?.[0] || null,
    password: submittedPassword,
  };
};
