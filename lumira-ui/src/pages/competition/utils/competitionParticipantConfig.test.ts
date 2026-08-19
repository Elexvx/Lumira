import { describe, expect, it } from 'vitest';
import {
  buildRegistrationParticipantLimitMetadata,
  filterRegistrationParticipants,
  findRegistrationParticipantSourceIndex,
  getRegistrationParticipantLimits,
  normalizeRegistrationParticipantType,
} from './competitionParticipantConfig';

describe('competition participant configuration', () => {
  it('uses the new defaults when no limits exist', () => {
    expect(getRegistrationParticipantLimits()).toEqual({
      studentMinMembers: 1,
      studentMaxMembers: 15,
      teacherMinMembers: 0,
      teacherMaxMembers: 3,
    });
  });

  it('keeps legacy team limits as student limits', () => {
    expect(getRegistrationParticipantLimits({ teamMinMembers: 2, teamMaxMembers: 20 })).toEqual({
      studentMinMembers: 2,
      studentMaxMembers: 20,
      teacherMinMembers: 0,
      teacherMaxMembers: 3,
    });
  });

  it('writes new limits together with legacy student aliases', () => {
    expect(buildRegistrationParticipantLimitMetadata({
      studentMinMembers: 1,
      studentMaxMembers: 15,
      teacherMinMembers: 0,
      teacherMaxMembers: 3,
    })).toEqual({
      studentMinMembers: 1,
      studentMaxMembers: 15,
      teacherMinMembers: 0,
      teacherMaxMembers: 3,
      teamMinMembers: 1,
      teamMaxMembers: 15,
    });
  });

  it('treats legacy participants as students and locates filtered indexes', () => {
    const participants = [
      { memberName: '学生甲' },
      { memberName: '老师甲', participantType: 'TEACHER' },
      { memberName: '学生乙', participantType: 'STUDENT' },
    ];
    expect(normalizeRegistrationParticipantType(undefined)).toBe('STUDENT');
    expect(filterRegistrationParticipants(participants, 'STUDENT').map((item) => item.memberName)).toEqual(['学生甲', '学生乙']);
    expect(filterRegistrationParticipants(participants, 'TEACHER').map((item) => item.memberName)).toEqual(['老师甲']);
    expect(findRegistrationParticipantSourceIndex(participants, 'STUDENT', 1)).toBe(2);
    expect(findRegistrationParticipantSourceIndex(participants, 'TEACHER', 0)).toBe(1);
  });
});
