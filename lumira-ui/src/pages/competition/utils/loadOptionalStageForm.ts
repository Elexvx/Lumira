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
  // Registration confirmation can submit only preliminary materials. Falling
  // back to a final-stage form makes the registration wizard send a future
  // stageId, which then fails the final-stage material window check.
  const candidates = stages.filter((item) => item.stageCode === 'PRELIMINARY');

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
