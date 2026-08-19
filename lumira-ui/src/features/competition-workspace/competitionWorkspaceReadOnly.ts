import type { CompetitionStatus } from '@/services/competition/types';

export const isCompetitionWorkspaceReadOnly = (
  status?: CompetitionStatus | null,
  readOnly?: boolean | null,
) => readOnly ?? status === 'archived';

export const canMutateCompetitionWorkspace = (
  hasPermission: boolean,
  status?: CompetitionStatus | null,
  readOnly?: boolean | null,
) => hasPermission && !isCompetitionWorkspaceReadOnly(status, readOnly);
