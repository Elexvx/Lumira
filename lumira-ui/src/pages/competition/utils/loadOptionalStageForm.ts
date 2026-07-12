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
    const editableStages = stages.filter((item) => item.materialEditable !== false);
    const active = editableStages.find((item) => item.stageCode === 'PRELIMINARY')
      || editableStages.find((item) => item.stageCode === 'FINAL')
      || editableStages[0]
      || stages.find((item) => item.stageCode === 'PRELIMINARY')
      || stages[0];
    return active ? await getStageForm(active.id) : undefined;
  } catch {
    return undefined;
  }
};
