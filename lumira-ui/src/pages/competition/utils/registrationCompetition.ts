import type { CompetitionFeeMode, CompetitionRecord } from '@/services/competition/types';

export type RegistrationCompetitionDraftMeta = {
  competitionTitle?: string;
  competitionUuid?: string;
  feeMode?: CompetitionFeeMode | null;
  entryFeeMinor?: number | null;
  currency?: string | null;
};

export const hasRegistrationCompetitionPricing = (
  competition?: Pick<CompetitionRecord, 'feeMode' | 'entryFeeMinor'>,
) => Boolean(competition?.feeMode && competition.entryFeeMinor !== null && competition.entryFeeMinor !== undefined);

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
    feeMode: draftMeta?.feeMode,
    entryFeeMinor: draftMeta?.entryFeeMinor,
    currency: draftMeta?.currency,
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
