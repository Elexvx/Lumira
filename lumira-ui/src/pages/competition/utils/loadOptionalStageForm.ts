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
  const candidates = [
    ...stages.filter((item) => item.stageCode === 'PRELIMINARY'),
    ...stages.filter((item) => item.stageCode !== 'PRELIMINARY' && item.materialEditable !== false),
    ...stages.filter((item) => item.stageCode !== 'PRELIMINARY'),
  ].filter((item, index, items) => items.findIndex((candidate) => candidate.id === item.id) === index);

  for (const candidate of candidates) {
    try {
      return await getStageForm(candidate.id);
    } catch (error) {
      if (error instanceof ApiRequestError
        && (error.httpStatus === 404 || error.code === ErrorCode.NOT_FOUND)) {
        continue;
      }
      throw error;
    }
  }
  return undefined;
};
