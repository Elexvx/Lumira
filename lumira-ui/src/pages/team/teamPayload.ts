import type { TeamDraftMemberPayload, TeamUpsertPayload } from '@/services/team/types';

export const isBlankDraftMemberRow = (member?: Partial<TeamDraftMemberPayload> | null) =>
  !member?.memberName?.trim() &&
  !member?.employeeNo?.trim() &&
  !member?.departmentName?.trim() &&
  !member?.remark?.trim() &&
  !Object.values(member?.extraValues || {}).some((value) => value?.trim());

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
      extraValues: Object.fromEntries(
        Object.entries(member.extraValues || {})
          .map(([key, value]) => [key, value?.trim?.() || ''])
          .filter(([, value]) => Boolean(value)),
      ),
    }));

  return {
    ...values,
    initialMembers,
  };
};
