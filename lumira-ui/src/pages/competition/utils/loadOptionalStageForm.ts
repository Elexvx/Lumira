import type { CompetitionStageFormRecord, CompetitionStageRecord } from '@/services/competition/types';
import { ErrorCode } from '@/enums/errorCode';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';

type ListCompetitionStages = (competitionId: number) => Promise<CompetitionStageRecord[]>;
type GetCompetitionStageForm = (stageId: number) => Promise<CompetitionStageFormRecord>;

export const loadOptionalPreliminaryStageForm = async (
  competitionId: number,
  listStages: ListCompetitionStages,
  getStageForm: GetCompetitionStageForm,
): Promise<CompetitionStageFormRecord | undefined> => {
  const stages = await listStages(competitionId);
  const editableStages = stages.filter((item) => item.materialEditable !== false);
  const active = editableStages.find((item) => item.stageCode === 'PRELIMINARY')
    || editableStages.find((item) => item.stageCode === 'FINAL')
    || editableStages[0]
    || stages.find((item) => item.stageCode === 'PRELIMINARY')
    || stages[0];
  if (!active) {
    return undefined;
  }
  try {
    return await getStageForm(active.id);
  } catch (error) {
    if (error instanceof ApiRequestError
      && (error.httpStatus === 404 || error.code === ErrorCode.NOT_FOUND)) {
      return undefined;
    }
    throw error;
  }
};
