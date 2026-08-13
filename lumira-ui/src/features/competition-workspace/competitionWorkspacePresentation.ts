import type { CompetitionStatus } from '@/services/competition/types';

export const competitionWorkspaceStatusMeta: Record<
  CompetitionStatus,
  { color: string; label: string }
> = {
  draft: { color: 'processing', label: '草稿' },
  published: { color: 'success', label: '已发布' },
  archived: { color: 'default', label: '已归档' },
};
