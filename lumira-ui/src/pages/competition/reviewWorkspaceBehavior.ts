export interface ReviewPlanReloadContext {
  canManagePlans: boolean;
  workspaceUuid?: string;
  competitionId?: number;
  stageId?: number;
}

export const shouldReloadReviewPlans = ({
  canManagePlans,
  workspaceUuid,
  competitionId,
  stageId,
}: ReviewPlanReloadContext) =>
  canManagePlans && Boolean(workspaceUuid || competitionId) && Boolean(stageId);

export const shouldShowGlobalExpertTasks = (
  canViewTasks: boolean,
  embeddedInCompetitionWorkspace: boolean,
) => canViewTasks && !embeddedInCompetitionWorkspace;

export const shouldShowReviewAdminWorkbench = (
  canManageReview: boolean,
  embeddedInCompetitionWorkspace: boolean,
) => canManageReview && embeddedInCompetitionWorkspace;
