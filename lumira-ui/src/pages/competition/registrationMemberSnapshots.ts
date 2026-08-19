export type RegistrationMemberSnapshot = Record<string, unknown>;

export const splitRegistrationMemberSnapshots = (
  members: RegistrationMemberSnapshot[],
) => members.reduce<{
  students: RegistrationMemberSnapshot[];
  teachers: RegistrationMemberSnapshot[];
}>((result, member) => {
  const participantType = typeof member.participantType === 'string'
    ? member.participantType.trim().toUpperCase()
    : '';
  if (participantType === 'TEACHER') {
    result.teachers.push(member);
  } else {
    result.students.push(member);
  }
  return result;
}, { students: [], teachers: [] });
