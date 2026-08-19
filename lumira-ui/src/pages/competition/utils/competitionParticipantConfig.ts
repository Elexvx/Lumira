export type RegistrationParticipantType = 'STUDENT' | 'TEACHER';

export type RegistrationParticipantLimits = {
  studentMinMembers: number;
  studentMaxMembers: number;
  teacherMinMembers: number;
  teacherMaxMembers: number;
};

export const MAX_REGISTRATION_PARTICIPANTS_PER_TYPE = 20;
export const DEFAULT_STUDENT_MIN_MEMBERS = 1;
export const DEFAULT_STUDENT_MAX_MEMBERS = 15;
export const DEFAULT_TEACHER_MIN_MEMBERS = 0;
export const DEFAULT_TEACHER_MAX_MEMBERS = 3;

type ParticipantLimitMetadata = Partial<RegistrationParticipantLimits> & {
  teamMinMembers?: number;
  teamMaxMembers?: number;
};

const normalizeLimit = (
  value: unknown,
  fallback: number,
  minimum: number,
) => {
  const numericValue = Number(value);
  return Number.isInteger(numericValue)
    && numericValue >= minimum
    && numericValue <= MAX_REGISTRATION_PARTICIPANTS_PER_TYPE
    ? numericValue
    : fallback;
};

export const getRegistrationParticipantLimits = (
  metadata: ParticipantLimitMetadata = {},
): RegistrationParticipantLimits => {
  const studentMinMembers = normalizeLimit(
    metadata.studentMinMembers ?? metadata.teamMinMembers,
    DEFAULT_STUDENT_MIN_MEMBERS,
    1,
  );
  const studentMaxMembers = normalizeLimit(
    metadata.studentMaxMembers ?? metadata.teamMaxMembers,
    DEFAULT_STUDENT_MAX_MEMBERS,
    1,
  );
  const teacherMinMembers = normalizeLimit(
    metadata.teacherMinMembers,
    DEFAULT_TEACHER_MIN_MEMBERS,
    0,
  );
  const teacherMaxMembers = normalizeLimit(
    metadata.teacherMaxMembers,
    DEFAULT_TEACHER_MAX_MEMBERS,
    0,
  );

  return {
    studentMinMembers: Math.min(studentMinMembers, studentMaxMembers),
    studentMaxMembers: Math.max(studentMinMembers, studentMaxMembers),
    teacherMinMembers: Math.min(teacherMinMembers, teacherMaxMembers),
    teacherMaxMembers: Math.max(teacherMinMembers, teacherMaxMembers),
  };
};

export const buildRegistrationParticipantLimitMetadata = (
  limits: RegistrationParticipantLimits,
) => ({
  studentMinMembers: limits.studentMinMembers,
  studentMaxMembers: limits.studentMaxMembers,
  teacherMinMembers: limits.teacherMinMembers,
  teacherMaxMembers: limits.teacherMaxMembers,
  // Keep old clients and older server builds reading the student limits.
  teamMinMembers: limits.studentMinMembers,
  teamMaxMembers: limits.studentMaxMembers,
});

export const normalizeRegistrationParticipantType = (
  value?: string | null,
): RegistrationParticipantType => value?.toUpperCase() === 'TEACHER' ? 'TEACHER' : 'STUDENT';

export const filterRegistrationParticipants = <T extends { participantType?: string }>(
  participants: T[],
  participantType: RegistrationParticipantType,
) => participants.filter((participant) => (
  normalizeRegistrationParticipantType(participant.participantType) === participantType
));

export const findRegistrationParticipantSourceIndex = <T extends { participantType?: string }>(
  participants: T[],
  participantType: RegistrationParticipantType,
  participantIndex: number,
) => {
  let matchedIndex = -1;
  return participants.findIndex((participant) => {
    if (normalizeRegistrationParticipantType(participant.participantType) !== participantType) {
      return false;
    }
    matchedIndex += 1;
    return matchedIndex === participantIndex;
  });
};
