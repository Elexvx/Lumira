import type { TeamDraftMemberPayload, TeamUpsertPayload } from '@/services/team/types';

export const isBlankDraftMemberRow = (member?: Partial<TeamDraftMemberPayload> | null) =>
  !member?.memberName?.trim() &&
  !member?.employeeNo?.trim() &&
  !member?.departmentName?.trim() &&
  !member?.remark?.trim();

export const pruneBlankDraftMembers = (members?: TeamDraftMemberPayload[]) =>
  (members || []).filter((member) => !isBlankDraftMemberRow(member));

export const normalizeTeamCreatePayload = (values: TeamUpsertPayload): TeamUpsertPayload => {
  const initialMembers = pruneBlankDraftMembers(values.initialMembers)
    .filter((member) => member?.memberName?.trim())
    .map((member) => ({
      memberName: member.memberName.trim(),
      employeeNo: member.employeeNo?.trim(),
      departmentName: member.departmentName?.trim(),
      role: member.role || 'MEMBER',
      remark: member.remark?.trim(),
    }));

  return {
    ...values,
    initialMembers,
  };
};
