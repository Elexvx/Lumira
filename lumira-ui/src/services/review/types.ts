export type ReviewPlanStatus = 'DRAFT' | 'READY' | 'ARCHIVED';
export type ReviewBatchStatus =
  | 'DRAFT'
  | 'READY'
  | 'ASSIGNING'
  | 'IN_REVIEW'
  | 'AGGREGATING'
  | 'FINALIZED'
  | 'PUBLISHED'
  | 'CANCELLED';
export type ReviewAssignmentStatus =
  | 'ASSIGNED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'SUBMITTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'REVOKED';
export type ReviewDecision =
  | 'PENDING'
  | 'PASS'
  | 'FAIL'
  | 'WAITLIST'
  | 'ADVANCED'
  | 'ELIMINATED'
  | 'REVIEW_REQUIRED';

export interface ReviewCriterion {
  id: number;
  criteriaVersionId: number;
  criterionCode: string;
  criterionName: string;
  description?: string | null;
  weight: number;
  maximumScore: number;
  required: boolean;
  sortOrder: number;
}

export interface ReviewCriterionPayload {
  code: string;
  name: string;
  description?: string;
  weight: number;
  maximumScore: number;
  required?: boolean;
  sortOrder?: number;
}

export interface ReviewPlan {
  id: number;
  competitionId: number;
  stageId: number;
  planName: string;
  status: ReviewPlanStatus;
  blindMode: 'NONE' | 'SINGLE_BLIND' | 'DOUBLE_BLIND';
  requiredReviewerCount: number;
  minimumSubmittedCount: number;
  aggregateMethod: 'AVERAGE' | 'MEDIAN' | 'WEIGHTED_AVERAGE' | 'TRIMMED_MEAN';
  scoreScale: number;
  trimHighestCount: number;
  trimLowestCount: number;
  criteriaVersionId?: number | null;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  criteria: ReviewCriterion[];
}

export interface ReviewPlanCreatePayload {
  competitionId: number;
  stageId: number;
  planName: string;
  blindMode: ReviewPlan['blindMode'];
  requiredReviewerCount: number;
  minimumSubmittedCount: number;
  aggregateMethod: ReviewPlan['aggregateMethod'];
  scoreScale: number;
  trimHighestCount: number;
  trimLowestCount: number;
  criteria: ReviewCriterionPayload[];
}

export interface ReviewBatch {
  id: number;
  planId: number;
  competitionId: number;
  stageId: number;
  criteriaVersionId: number;
  batchNo: string;
  batchName: string;
  batchType: string;
  status: ReviewBatchStatus;
  assignmentStrategy: 'MANUAL' | 'ROUND_ROBIN' | 'BALANCED' | 'TAG_MATCH';
  minimumReviewerCount: number;
  candidateCount: number;
  freezeToken?: string | null;
  frozenAt?: string | null;
  reviewDeadline?: string | null;
  finalizedAt?: string | null;
  publishedAt?: string | null;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReviewBatchCreatePayload {
  planId: number;
  batchName: string;
  assignmentStrategy: ReviewBatch['assignmentStrategy'];
  reviewDeadline?: string;
}

export interface ReviewCandidate {
  id: number;
  batchId: number;
  registrationId: number;
  blindCode?: string | null;
  status: string;
  snapshotJson: string;
  reviewSnapshotJson: string;
  snapshotHash: string;
  createdAt?: string;
}

export interface ReviewAdminAssignment {
  id: number;
  batchId: number;
  candidateId: number;
  expertId: number;
  expertUserId?: number | null;
  expertUserUuid?: string | null;
  reviewerWeight: number;
  status: ReviewAssignmentStatus;
  dueAt?: string | null;
  acceptedAt?: string | null;
  declinedAt?: string | null;
  declineReason?: string | null;
  expiredAt?: string | null;
  revokedAt?: string | null;
  revokeReason?: string | null;
  submittedAt?: string | null;
  version: number;
}

export interface ReviewAssignmentResult {
  batchId: number;
  batchStatus: ReviewBatchStatus;
  createdCount: number;
  candidateCount: number;
  candidatesBelowMinimum: number;
}

export interface ReviewScoreItem {
  criterionId: number;
  score: number;
  comment?: string | null;
}

export interface ReviewAssignmentTask {
  assignmentId: number;
  batchId: number;
  batchName: string;
  candidateId: number;
  blindCode?: string | null;
  candidateSnapshotJson: string;
  assignmentStatus: ReviewAssignmentStatus;
  criteriaVersionId: number;
  scoreScale: number;
  criteria: ReviewCriterion[];
  dueAt?: string | null;
  acceptedAt?: string | null;
  submittedAt?: string | null;
  assignmentVersion: number;
  latestSheetId?: number | null;
  latestSheetVersion?: number | null;
  latestSheetStatus?: 'DRAFT' | 'SUBMITTED' | null;
  latestTotalScore?: number | null;
  latestReviewComment?: string | null;
  latestScores: ReviewScoreItem[];
}

export interface ReviewSheetPayload {
  reviewComment?: string;
  scores: ReviewScoreItem[];
}

export interface ReviewSheet {
  id: number;
  assignmentId: number;
  batchId: number;
  candidateId: number;
  versionNo: number;
  status: 'DRAFT' | 'SUBMITTED';
  totalScore: number;
  reviewComment?: string | null;
  submittedAt?: string | null;
  scores: ReviewScoreItem[];
}

export interface ReviewAggregate {
  id: number;
  batchId: number;
  candidateId: number;
  aggregateScore?: number | null;
  minimumScore?: number | null;
  maximumScore?: number | null;
  scoreStddev?: number | null;
  submittedReviewerCount: number;
  validReviewerCount: number;
  rankNo?: number | null;
  decision: ReviewDecision;
  decisionReason?: string | null;
  decidedBy?: number | null;
  decidedByUuid?: string | null;
  decidedAt?: string | null;
  anomalyFlagsJson?: string | null;
  status: string;
  calculatedAt?: string | null;
  finalizedAt?: string | null;
}

export interface ReviewPublication {
  id: number;
  batchId: number;
  publicationVersion: number;
  status: string;
  payloadJson: string;
  payloadHash: string;
  publishedAt?: string | null;
}

export type ReviewAppealStatus = 'SUBMITTED' | 'ACCEPTED' | 'REJECTED';

export interface ReviewPublishedResult {
  publicationId: number;
  publicationVersion: number;
  batchId: number;
  competitionId: number;
  stageId: number;
  candidateId: number;
  registrationId: number;
  competitionTitle?: string | null;
  stageName?: string | null;
  registrationNo?: string | null;
  aggregateScore?: number | null;
  rankNo?: number | null;
  decision: ReviewDecision;
  publishedAt?: string | null;
  appealId?: number | null;
  appealStatus?: ReviewAppealStatus | null;
}

export interface ReviewAppeal {
  id: number;
  publicationId: number;
  batchId: number;
  competitionId: number;
  stageId: number;
  candidateId: number;
  registrationId: number;
  appealNo: string;
  aggregateScore?: number | null;
  rankNo?: number | null;
  decision: ReviewDecision;
  appealReason: string;
  status: ReviewAppealStatus;
  resolution?: string | null;
  resolvedBy?: number | null;
  resolvedByUuid?: string | null;
  resolvedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}
