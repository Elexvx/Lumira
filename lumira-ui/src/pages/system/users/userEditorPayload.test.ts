import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs';
import { buildUserEditorPayload } from '@/pages/system/users/userEditorPayload';

describe('buildUserEditorPayload', () => {
  it('omits an untouched password-manager value while editing a user', () => {
    const payload = buildUserEditorPayload(
      { username: 'admin', password: 'Autofilled1!', resetPassword: false, roleIds: [1] },
      { editing: true },
    );

    expect(payload.password).toBeUndefined();
    expect(payload).not.toHaveProperty('resetPassword');
  });

  it('includes a password that the operator deliberately entered', () => {
    const payload = buildUserEditorPayload(
      { username: 'admin', password: 'NewSecret1!', resetPassword: true, roleIds: [1] },
      { editing: true },
    );

    expect(payload.password).toBe('NewSecret1!');
  });

  it('keeps the required initial password when creating a user', () => {
    const payload = buildUserEditorPayload(
      { username: 'new-user', password: 'InitialSecret1!', roleIds: [2] },
      { editing: false },
    );

    expect(payload.password).toBe('InitialSecret1!');
  });

  it('omits blank optional passwords', () => {
    const payload = buildUserEditorPayload(
      { username: 'admin', password: '   ', resetPassword: true, roleIds: [1] },
      { editing: true },
    );

    expect(payload.password).toBeUndefined();
  });

  it('preserves custom profile values in the admin user payload', () => {
    const payload = buildUserEditorPayload(
      {
        username: 'admin',
        roleIds: [1],
        extraProfileValues: { employeeCode: 'A-100', note: '' },
      },
      { editing: true },
    );

    expect(payload.extraProfileValues).toEqual({ employeeCode: 'A-100', note: '' });
  });

  it('serializes date-based custom profile values using their configured format', () => {
    const payload = buildUserEditorPayload(
      {
        username: 'admin',
        roleIds: [1],
        extraProfileValues: {
          joiningDate: dayjs('2026-08-12'),
          birthMonth: '2026-08',
        },
      },
      {
        editing: true,
        profileFields: [
          { fieldKey: 'joiningDate', fieldLabel: 'Joining date', fieldType: 'DATE', visible: true },
          { fieldKey: 'birthMonth', fieldLabel: 'Birth month', fieldType: 'MONTH', visible: true },
        ],
      },
    );

    expect(payload.extraProfileValues).toEqual({ joiningDate: '2026-08-12', birthMonth: '2026-08' });
  });
});
