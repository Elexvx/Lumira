import type { CompetitionRecord } from '@/services/competition/types';

export type RegistrationCompetitionDraftMeta = {
  competitionTitle?: string;
  competitionUuid?: string;
};

export const buildRegistrationCompetitionFallback = (
  competitionId?: number,
  draftMeta?: RegistrationCompetitionDraftMeta,
): CompetitionRecord | undefined => {
  if (!competitionId || competitionId <= 0) {
    return undefined;
  }
  const competitionUuid = draftMeta?.competitionUuid?.trim();
  const competitionTitle = draftMeta?.competitionTitle?.trim();
  if (!competitionUuid && !competitionTitle) {
    return undefined;
  }
  return {
    id: competitionId,
    uuid: competitionUuid || undefined,
    code: competitionUuid || `competition-${competitionId}`,
    locale: 'zh',
    title: competitionTitle || `Competition ${competitionId}`,
    category: 'OTHER',
    competitionStart: '',
    location: '',
    status: 'published',
    featured: false,
    sort: competitionId,
  };
};

export const mergeRegistrationCompetitionOptions = (
  competitions: CompetitionRecord[],
  fallbackCompetition?: CompetitionRecord,
): CompetitionRecord[] => {
  if (!fallbackCompetition || competitions.some((item) => item.id === fallbackCompetition.id)) {
    return competitions;
  }
  return [...competitions, fallbackCompetition];
};
