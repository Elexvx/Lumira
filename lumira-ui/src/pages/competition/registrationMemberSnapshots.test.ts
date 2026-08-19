import { describe, expect, it } from 'vitest';
import { splitRegistrationMemberSnapshots } from './registrationMemberSnapshots';

describe('splitRegistrationMemberSnapshots', () => {
  it('keeps students and teachers in their matching detail sections', () => {
    const student = { participantType: 'STUDENT', memberName: '测试学生' };
    const teacher = { participantType: 'TEACHER', memberName: '测试教师' };

    expect(splitRegistrationMemberSnapshots([student, teacher])).toEqual({
      students: [student],
      teachers: [teacher],
    });
  });

  it('keeps legacy members without a participant type in the student section', () => {
    const legacyMember = { memberName: '历史成员' };

    expect(splitRegistrationMemberSnapshots([legacyMember])).toEqual({
      students: [legacyMember],
      teachers: [],
    });
  });
});
