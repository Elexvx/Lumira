import { request } from '@/services/common/request';
import type {
  ReviewAdminAssignment,
  ReviewAggregate,
  ReviewAppeal,
  ReviewAssignmentResult,
  ReviewAssignmentTask,
  ReviewBatch,
  ReviewBatchCreatePayload,
  ReviewCandidate,
  ReviewDecision,
  ReviewPlan,
  ReviewPlanCreatePayload,
  ReviewPublication,
  ReviewPublishedResult,
  ReviewInvitation,
  ReviewRosterExpert,
  ReviewSheet,
  ReviewSheetPayload,
} from './types';

const REVIEW_API = '/v2/reviews';
const competitionWorkspaceReviewApi = (competitionUuid: string, suffix: string) =>
  `/v2/aiadc/competitions/${encodeURIComponent(competitionUuid)}/reviews${suffix}`;

export const listWorkspaceReviewPlans = (competitionUuid: string, params: { stageId?: number } = {}) =>
  request<ReviewPlan[]>(competitionWorkspaceReviewApi(competitionUuid, '/plans'), { method: 'GET', params });

export const createWorkspaceReviewPlan = (
  competitionUuid: string,
  data: Omit<ReviewPlanCreatePayload, 'competitionId'> & { competitionId?: number },
) => request<ReviewPlan>(competitionWorkspaceReviewApi(competitionUuid, '/plans'), {
  method: 'POST',
  data,
});

export const activateWorkspaceReviewPlan = (competitionUuid: string, planId: number) =>
  request<ReviewPlan>(competitionWorkspaceReviewApi(competitionUuid, `/plans/${planId}/activate`), { method: 'POST' });

export const listWorkspaceReviewBatches = (competitionUuid: string, params: { planId?: number } = {}) =>
  request<ReviewBatch[]>(competitionWorkspaceReviewApi(competitionUuid, '/batches'), { method: 'GET', params });

export const createWorkspaceReviewBatch = (competitionUuid: string, data: ReviewBatchCreatePayload) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, '/batches'), { method: 'POST', data });

export const freezeWorkspaceReviewBatch = (competitionUuid: string, batchId: number, registrationIds?: number[]) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/freeze`), {
    method: 'POST',
    data: registrationIds?.length ? { registrationIds } : {},
  });

export const listWorkspaceReviewCandidates = (competitionUuid: string, batchId: number) =>
  request<ReviewCandidate[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/candidates`), { method: 'GET' });

export const listWorkspaceReviewAssignments = (competitionUuid: string, batchId: number) =>
  request<ReviewAdminAssignment[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/assignments`), { method: 'GET' });

export const listWorkspaceReviewRoster = (competitionUuid: string, batchId: number) =>
  request<ReviewRosterExpert[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/roster`), { method: 'GET' });

export const saveWorkspaceReviewRoster = (competitionUuid: string, batchId: number, expertIds: number[]) =>
  request<ReviewRosterExpert[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/roster`), {
    method: 'PUT',
    data: { expertIds },
  });

export const confirmWorkspaceReviewAssignments = (competitionUuid: string, batchId: number) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/assignments/confirm`), { method: 'POST' });

export const listWorkspaceReviewInvitations = (competitionUuid: string, batchId: number) =>
  request<ReviewRosterExpert[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/invitations`), { method: 'GET' });

export const sendWorkspaceReviewInvitations = (competitionUuid: string, batchId: number) =>
  request<ReviewRosterExpert[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/invitations`), { method: 'POST' });

export const scanWorkspaceReviewCheckIn = (competitionUuid: string, batchId: number, qrToken: string) =>
  request<ReviewInvitation>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/check-ins`), {
    method: 'POST',
    data: { qrToken },
  });

export const assignWorkspaceReviewExperts = (
  competitionUuid: string,
  batchId: number,
  data: { assignments: Array<{ candidateId: number; expertId: number; reviewerWeight?: number }>; dueAt?: string },
) => request<ReviewAssignmentResult>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/assignments`), {
  method: 'POST',
  data,
});

export const autoAssignWorkspaceReviewExperts = (
  competitionUuid: string,
  batchId: number,
  data: { expertIds?: number[]; dueAt?: string; reviewerWeight?: number } = {},
) => request<ReviewAssignmentResult>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/auto-assign`), {
  method: 'POST',
  data,
});

export const startWorkspaceReviewBatch = (competitionUuid: string, batchId: number) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/start`), { method: 'POST' });

export const listWorkspaceReviewAggregates = (competitionUuid: string, batchId: number) =>
  request<ReviewAggregate[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/aggregates`), { method: 'GET' });

export const aggregateWorkspaceReviewBatch = (competitionUuid: string, batchId: number) =>
  request<ReviewAggregate[]>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/aggregate`), { method: 'POST' });

export const decideWorkspaceReviewCandidate = (
  competitionUuid: string,
  batchId: number,
  candidateId: number,
  decision: ReviewDecision,
  reason?: string,
) => request<ReviewAggregate>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/candidates/${candidateId}/decision`), {
  method: 'PUT',
  data: { decision, reason },
});

export const finalizeWorkspaceReviewBatch = (competitionUuid: string, batchId: number) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/finalize`), { method: 'POST' });

export const publishWorkspaceReviewBatch = (competitionUuid: string, batchId: number) =>
  request<ReviewPublication>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/publish`), { method: 'POST' });

export const reopenWorkspaceReviewBatchForCorrection = (competitionUuid: string, batchId: number, reason: string) =>
  request<ReviewBatch>(competitionWorkspaceReviewApi(competitionUuid, `/batches/${batchId}/correction`), {
    method: 'POST',
    data: { reason },
  });

export const listWorkspaceReviewAppeals = (competitionUuid: string, params: { batchId?: number; status?: string } = {}) =>
  request<ReviewAppeal[]>(competitionWorkspaceReviewApi(competitionUuid, '/appeals'), { method: 'GET', params });

export const resolveWorkspaceReviewAppeal = (
  competitionUuid: string,
  appealId: number,
  decision: 'ACCEPTED' | 'REJECTED',
  resolution: string,
) => request<ReviewAppeal>(competitionWorkspaceReviewApi(competitionUuid, `/appeals/${appealId}/resolution`), {
  method: 'PUT',
  data: { decision, resolution },
});

export const listReviewPlans = (params: { competitionId?: number; stageId?: number } = {}) =>
  request<ReviewPlan[]>(`${REVIEW_API}/plans`, { method: 'GET', params });

export const createReviewPlan = (data: ReviewPlanCreatePayload) =>
  request<ReviewPlan>(`${REVIEW_API}/plans`, { method: 'POST', data });

export const getReviewPlan = (planId: number) =>
  request<ReviewPlan>(`${REVIEW_API}/plans/${planId}`, { method: 'GET' });

export const activateReviewPlan = (planId: number) =>
  request<ReviewPlan>(`${REVIEW_API}/plans/${planId}/activate`, { method: 'POST' });

export const listReviewBatches = (params: { planId?: number; competitionId?: number } = {}) =>
  request<ReviewBatch[]>(`${REVIEW_API}/batches`, { method: 'GET', params });

export const createReviewBatch = (data: ReviewBatchCreatePayload) =>
  request<ReviewBatch>(`${REVIEW_API}/batches`, { method: 'POST', data });

export const getReviewBatch = (batchId: number) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}`, { method: 'GET' });

export const freezeReviewBatch = (batchId: number, registrationIds?: number[]) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}/freeze`, {
    method: 'POST',
    data: registrationIds?.length ? { registrationIds } : {},
  });

export const listReviewCandidates = (batchId: number) =>
  request<ReviewCandidate[]>(`${REVIEW_API}/batches/${batchId}/candidates`, { method: 'GET' });

export const listReviewAssignments = (batchId: number) =>
  request<ReviewAdminAssignment[]>(`${REVIEW_API}/batches/${batchId}/assignments`, { method: 'GET' });

export const listReviewRoster = (batchId: number) =>
  request<ReviewRosterExpert[]>(`${REVIEW_API}/batches/${batchId}/roster`, { method: 'GET' });

export const saveReviewRoster = (batchId: number, expertIds: number[]) =>
  request<ReviewRosterExpert[]>(`${REVIEW_API}/batches/${batchId}/roster`, {
    method: 'PUT',
    data: { expertIds },
  });

export const confirmReviewAssignments = (batchId: number) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}/assignments/confirm`, { method: 'POST' });

export const listReviewInvitations = (batchId: number) =>
  request<ReviewRosterExpert[]>(`${REVIEW_API}/batches/${batchId}/invitations`, { method: 'GET' });

export const sendReviewInvitations = (batchId: number) =>
  request<ReviewRosterExpert[]>(`${REVIEW_API}/batches/${batchId}/invitations`, { method: 'POST' });

export const scanReviewCheckIn = (batchId: number, qrToken: string) =>
  request<ReviewInvitation>(`${REVIEW_API}/batches/${batchId}/check-ins`, {
    method: 'POST',
    data: { qrToken },
  });

export const assignReviewExperts = (
  batchId: number,
  data: {
    assignments: Array<{ candidateId: number; expertId: number; reviewerWeight?: number }>;
    dueAt?: string;
  },
) =>
  request<ReviewAssignmentResult>(`${REVIEW_API}/batches/${batchId}/assignments`, {
    method: 'POST',
    data,
  });

export const autoAssignReviewExperts = (
  batchId: number,
  data: { expertIds?: number[]; dueAt?: string; reviewerWeight?: number } = {},
) =>
  request<ReviewAssignmentResult>(`${REVIEW_API}/batches/${batchId}/auto-assign`, {
    method: 'POST',
    data,
  });

export const startReviewBatch = (batchId: number) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}/start`, { method: 'POST' });

export const listMyReviewAssignments = () =>
  request<ReviewAssignmentTask[]>(`${REVIEW_API}/assignments/mine`, { method: 'GET' });

export const acceptReviewAssignment = (assignmentId: number) =>
  request<ReviewAssignmentTask>(`${REVIEW_API}/assignments/${assignmentId}/accept`, { method: 'POST' });

export const declineReviewAssignment = (assignmentId: number, reason: string) =>
  request<ReviewAssignmentTask>(`${REVIEW_API}/assignments/${assignmentId}/decline`, {
    method: 'POST',
    data: { reason },
  });

export const revokeReviewAssignment = (
  batchId: number,
  assignmentId: number,
  reason: string,
) =>
  request<ReviewAdminAssignment>(
    `${REVIEW_API}/batches/${batchId}/assignments/${assignmentId}/revoke`,
    { method: 'POST', data: { reason } },
  );

export const saveReviewSheetDraft = (assignmentId: number, data: ReviewSheetPayload) =>
  request<ReviewSheet>(`${REVIEW_API}/assignments/${assignmentId}/sheet`, {
    method: 'PUT',
    data,
  });

export const submitReviewSheet = (assignmentId: number, data: ReviewSheetPayload) =>
  request<ReviewSheet>(`${REVIEW_API}/assignments/${assignmentId}/submit`, {
    method: 'POST',
    data,
  });

export const aggregateReviewBatch = (batchId: number) =>
  request<ReviewAggregate[]>(`${REVIEW_API}/batches/${batchId}/aggregate`, { method: 'POST' });

export const listReviewAggregates = (batchId: number) =>
  request<ReviewAggregate[]>(`${REVIEW_API}/batches/${batchId}/aggregates`, { method: 'GET' });

export const decideReviewCandidate = (
  batchId: number,
  candidateId: number,
  decision: ReviewDecision,
  reason?: string,
) =>
  request<ReviewAggregate>(`${REVIEW_API}/batches/${batchId}/candidates/${candidateId}/decision`, {
    method: 'PUT',
    data: { decision, reason },
  });

export const finalizeReviewBatch = (batchId: number) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}/finalize`, { method: 'POST' });

export const publishReviewBatch = (batchId: number) =>
  request<ReviewPublication>(`${REVIEW_API}/batches/${batchId}/publish`, { method: 'POST' });

export const reopenReviewBatchForCorrection = (batchId: number, reason: string) =>
  request<ReviewBatch>(`${REVIEW_API}/batches/${batchId}/correction`, {
    method: 'POST',
    data: { reason },
  });

export const getLatestReviewPublication = (batchId: number) =>
  request<ReviewPublication>(`${REVIEW_API}/batches/${batchId}/publication`, { method: 'GET' });

export const listMyPublishedReviewResults = () =>
  request<ReviewPublishedResult[]>(`${REVIEW_API}/results/mine`, { method: 'GET' });

export const listMyReviewAppeals = () =>
  request<ReviewAppeal[]>(`${REVIEW_API}/appeals/mine`, { method: 'GET' });

export const submitReviewAppeal = (
  publicationId: number,
  registrationId: number,
  reason: string,
) =>
  request<ReviewAppeal>(
    `${REVIEW_API}/publications/${publicationId}/registrations/${registrationId}/appeals`,
    { method: 'POST', data: { reason } },
  );

export const listReviewAppeals = (params: { batchId?: number; status?: string } = {}) =>
  request<ReviewAppeal[]>(`${REVIEW_API}/appeals`, { method: 'GET', params });

export const resolveReviewAppeal = (
  appealId: number,
  decision: 'ACCEPTED' | 'REJECTED',
  resolution: string,
) =>
  request<ReviewAppeal>(`${REVIEW_API}/appeals/${appealId}/resolution`, {
    method: 'PUT',
    data: { decision, resolution },
  });

export const openReviewInvitation = (token: string) =>
  request<ReviewInvitation>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}`, {
    method: 'GET',
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const getReviewInvitationStatus = (token: string) =>
  request<ReviewInvitation>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/status`, {
    method: 'GET',
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const listReviewInvitationAssignments = (token: string) =>
  request<ReviewAssignmentTask[]>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/assignments`, {
    method: 'GET',
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const acceptReviewInvitationAssignment = (token: string, assignmentId: number) =>
  request<ReviewAssignmentTask>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/assignments/${assignmentId}/accept`, {
    method: 'POST',
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const declineReviewInvitationAssignment = (token: string, assignmentId: number, reason: string) =>
  request<ReviewAssignmentTask>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/assignments/${assignmentId}/decline`, {
    method: 'POST',
    data: { reason },
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const saveReviewInvitationDraft = (token: string, assignmentId: number, data: ReviewSheetPayload) =>
  request<ReviewSheet>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/assignments/${assignmentId}/sheet`, {
    method: 'PUT',
    data,
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });

export const submitReviewInvitationSheet = (token: string, assignmentId: number, data: ReviewSheetPayload) =>
  request<ReviewSheet>(`${REVIEW_API}/invitations/${encodeURIComponent(token)}/assignments/${assignmentId}/submit`, {
    method: 'POST',
    data,
    skipAuth: true,
    allowUnauthorizedWithoutRedirect: true,
  });
