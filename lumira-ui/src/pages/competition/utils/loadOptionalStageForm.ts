import type { CompetitionStageFormRecord, CompetitionStageRecord } from '@/services/competition/types';

type ListCompetitionStages = (competitionId: number) => Promise<CompetitionStageRecord[]>;
type GetCompetitionStageForm = (stageId: number) => Promise<CompetitionStageFormRecord>;

export const loadOptionalPreliminaryStageForm = async (
  competitionId: number,
  listStages: ListCompetitionStages,
  getStageForm: GetCompetitionStageForm,
): Promise<CompetitionStageFormRecord | undefined> => {
  try {
    const stages = await listStages(competitionId);
    const preliminary = stages.find((item) => item.stageCode === 'PRELIMINARY') || stages[0];
    return preliminary ? await getStageForm(preliminary.id) : undefined;
  } catch {
    return undefined;
  }
};
