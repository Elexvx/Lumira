import {
  CheckCircleOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  QrcodeOutlined,
  ReloadOutlined,
  SendOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementTable } from '@/features/management/ManagementTable';
import { DataTable } from '@/features/table/DataTable';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import {
  listCompetitionWorkspaceStages,
  listCompetitions,
  listCompetitionStages,
} from '@/services/competition/api';
import {
  clearCompetitionWorkspaceCertificateAwardRuleOverride,
  grantCompetitionWorkspacePublishedAwards,
  listCompetitionWorkspaceAwardGrants,
  listCompetitionWorkspaceCertificateAwardRules,
  saveCompetitionWorkspaceCertificateAwardRules,
} from '@/services/certificates/api';
import type {
  CertificateAwardGrant,
  CertificateAwardRule,
} from '@/services/certificates/types';
import type {
  CompetitionRecord,
  CompetitionStageRecord,
} from '@/services/competition/types';
import { listExperts } from '@/services/expert/api';
import type { ExpertRecord } from '@/services/expert/types';
import {
  acceptReviewAssignment,
  activateReviewPlan,
  aggregateReviewBatch,
  assignReviewExperts,
  autoAssignReviewExperts,
  createReviewBatch,
  createReviewPlan,
  decideReviewCandidate,
  declineReviewAssignment,
  finalizeReviewBatch,
  freezeReviewBatch,
  listMyReviewAssignments,
  listReviewAggregates,
  listReviewAssignments,
  listReviewAppeals,
  listReviewBatches,
  listReviewCandidates,
  listReviewPlans,
  listReviewRoster,
  publishReviewBatch,
  reopenReviewBatchForCorrection,
  revokeReviewAssignment,
  resolveReviewAppeal,
  saveReviewSheetDraft,
  saveReviewRoster,
  sendReviewInvitations,
  scanReviewCheckIn,
  startReviewBatch,
  confirmReviewAssignments,
  submitReviewSheet,
  aggregateWorkspaceReviewBatch,
  assignWorkspaceReviewExperts,
  activateWorkspaceReviewPlan,
  autoAssignWorkspaceReviewExperts,
  confirmWorkspaceReviewAssignments,
  createWorkspaceReviewBatch,
  createWorkspaceReviewPlan,
  decideWorkspaceReviewCandidate,
  finalizeWorkspaceReviewBatch,
  freezeWorkspaceReviewBatch,
  listWorkspaceReviewAggregates,
  listWorkspaceReviewAppeals,
  listWorkspaceReviewAssignments,
  listWorkspaceReviewBatches,
  listWorkspaceReviewCandidates,
  listWorkspaceReviewPlans,
  listWorkspaceReviewRoster,
  publishWorkspaceReviewBatch,
  reopenWorkspaceReviewBatchForCorrection,
  revokeWorkspaceReviewAssignment,
  resolveWorkspaceReviewAppeal,
  saveWorkspaceReviewRoster,
  scanWorkspaceReviewCheckIn,
  sendWorkspaceReviewInvitations,
  startWorkspaceReviewBatch,
} from '@/services/review/api';
import type {
  ReviewAdminAssignment,
  ReviewAggregate,
  ReviewAppeal,
  ReviewAssignmentStatus,
  ReviewAssignmentTask,
  ReviewBatch,
  ReviewCandidate,
  ReviewCriterionPayload,
  ReviewDecision,
  ReviewPlan,
  ReviewPlanCreatePayload,
  ReviewRosterExpert,
  ReviewSheetPayload,
} from '@/services/review/types';
import {
  shouldReloadReviewPlans,
  shouldShowGlobalExpertTasks,
  shouldShowReviewAdminWorkbench,
} from './reviewWorkspaceBehavior';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';

type PlanFormValues = Omit<ReviewPlanCreatePayload, 'competitionId' | 'stageId'>;
type BatchFormValues = {
  batchName: string;
  reviewerCountPerCandidate: number;
  expertMinAssignments: number;
  expertTargetAssignments: number;
  expertMaxAssignments: number;
};
type AssignmentFormValues = { candidateIds: number[]; expertIds: number[] };
type AwardRuleFormValues = { rules: CertificateAwardRule[] };
type ScoreFormValues = {
  scores: Record<string, number>;
  comments?: Record<string, string>;
  reviewComment?: string;
};

type BarcodeDetectorLike = {
  detect: (source: CanvasImageSource) => Promise<Array<{ rawValue?: string }>>;
};

type BarcodeDetectorConstructorLike = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

const REVIEW_ADMIN_PERMISSIONS = [
  'review:plan:manage',
  'review:batch:create',
  'review:assignment:manage',
  'review:roster:manage',
  'review:notification:send',
  'review:checkin:scan',
  'review:result:aggregate',
  'review:result:finalize',
  'review:result:publish',
  'review:appeal:manage',
];

const batchStatusLabels: Record<string, { color: string; text: string }> = {
  DRAFT: { color: 'default', text: '草稿' },
  READY: { color: 'cyan', text: '候选已冻结' },
  ASSIGNING: { color: 'blue', text: '分配中' },
  IN_REVIEW: { color: 'processing', text: '评审中' },
  AGGREGATING: { color: 'purple', text: '待终审' },
  FINALIZED: { color: 'gold', text: '已终审' },
  PUBLISHED: { color: 'success', text: '已发布' },
  CANCELLED: { color: 'error', text: '已取消' },
};

const assignmentStatusLabels: Record<string, { color: string; text: string }> = {
  ASSIGNED: { color: 'blue', text: '待接受' },
  ACCEPTED: { color: 'cyan', text: '已接受' },
  IN_PROGRESS: { color: 'processing', text: '评分中' },
  SUBMITTED: { color: 'success', text: '已提交' },
  DECLINED: { color: 'error', text: '已拒绝' },
  EXPIRED: { color: 'default', text: '已过期' },
  REVOKED: { color: 'default', text: '已撤回' },
};

const assignmentStatusValueEnum = Object.fromEntries(
  Object.entries(assignmentStatusLabels).map(([value, item]) => [value, { text: item.text }]),
);

const appealStatusLabels: Record<string, { color: string; text: string }> = {
  SUBMITTED: { color: 'processing', text: '待处理' },
  ACCEPTED: { color: 'success', text: '申诉成立' },
  REJECTED: { color: 'error', text: '申诉驳回' },
};

const decisionOptions: Array<{ label: string; value: ReviewDecision }> = [
  { label: '通过', value: 'PASS' },
  { label: '不通过', value: 'FAIL' },
  { label: '候补', value: 'WAITLIST' },
  { label: '晋级', value: 'ADVANCED' },
  { label: '淘汰', value: 'ELIMINATED' },
  { label: '需复核', value: 'REVIEW_REQUIRED' },
];

const defaultCriteria: ReviewCriterionPayload[] = [
  { code: 'INNOVATION', name: '创新性', weight: 0.4, maximumScore: 100, required: true, sortOrder: 10 },
  { code: 'FEASIBILITY', name: '可行性', weight: 0.3, maximumScore: 100, required: true, sortOrder: 20 },
  { code: 'IMPACT', name: '应用价值', weight: 0.3, maximumScore: 100, required: true, sortOrder: 30 },
];

const statusTag = (status: string, dictionary: Record<string, { color: string; text: string }>) => {
  const item = dictionary[status] || { color: 'default', text: status };
  return <Tag color={item.color}>{item.text}</Tag>;
};

const normalizeReviewTaskKeyword = (value: unknown) => (
  typeof value === 'string' ? value.trim().toLocaleLowerCase() : ''
);

const reviewTaskTableRequest = buildTableRequest<ReviewAssignmentTask>(async (params) => {
  const tasks = await listMyReviewAssignments();
  const batchKeyword = normalizeReviewTaskKeyword(params.batchName);
  const candidateKeyword = normalizeReviewTaskKeyword(params.blindCode);
  const status = typeof params.assignmentStatus === 'string'
    ? params.assignmentStatus as ReviewAssignmentStatus
    : undefined;
  const filteredTasks = tasks.filter((task) => {
    const candidateLabel = `${task.blindCode || ''} 候选 #${task.candidateId}`.toLocaleLowerCase();
    return (
      (!batchKeyword || task.batchName.toLocaleLowerCase().includes(batchKeyword))
      && (!candidateKeyword || candidateLabel.includes(candidateKeyword))
      && (!status || task.assignmentStatus === status)
    );
  });
  const pageNo = Math.max(1, Number(params.pageNo) || 1);
  const pageSize = Math.max(1, Number(params.pageSize) || 10);
  const start = (pageNo - 1) * pageSize;

  return {
    records: filteredTasks.slice(start, start + pageSize),
    total: filteredTasks.length,
  };
});

const parseSnapshot = (value?: string | null): Record<string, unknown> => {
  if (!value?.trim()) return {};
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
};

type SnapshotDetail = { key: string; label: string; children: ReactNode };

const snapshotDetailItems = (
  value: unknown,
  path = '',
  items: SnapshotDetail[] = [],
): SnapshotDetail[] => {
  if (value === null || value === undefined || value === '') {
    return items;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => snapshotDetailItems(item, `${path}[${index + 1}]`, items));
    return items;
  }
  if (typeof value === 'object') {
    Object.entries(value as Record<string, unknown>).forEach(([key, child]) => {
      snapshotDetailItems(child, path ? `${path}.${key}` : key, items);
    });
    return items;
  }
  const text = typeof value === 'boolean' ? (value ? '是' : '否') : String(value);
  items.push({
    key: path || `value-${items.length}`,
    label: path || '值',
    children: text,
  });
  return items;
};

const ReviewSnapshotDetails = ({ snapshotJson }: { snapshotJson?: string | null }) => {
  const items = snapshotDetailItems(parseSnapshot(snapshotJson));
  return items.length ? (
    <div style={{ maxHeight: 420, overflow: 'auto' }}>
      <Descriptions bordered size="small" column={1} items={items} />
    </div>
  ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无候选资料" />;
};

const snapshotLabel = (candidate: ReviewCandidate) => {
  const snapshot = parseSnapshot(candidate.snapshotJson);
  return String(
    candidate.blindCode
    || snapshot.registrationNo
    || `报名 #${candidate.registrationId}`,
  );
};

const ReviewAdminWorkbench = () => {
  const access = useAccess();
  const responsive = useResponsive();
  const [planForm] = Form.useForm<PlanFormValues>();
  const [batchForm] = Form.useForm<BatchFormValues>();
  const [assignmentForm] = Form.useForm<AssignmentFormValues>();
  const [awardRuleForm] = Form.useForm<AwardRuleFormValues>();
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [competitionId, setCompetitionId] = useState<number>();
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [stageId, setStageId] = useState<number>();
  const [plans, setPlans] = useState<ReviewPlan[]>([]);
  const [planId, setPlanId] = useState<number>();
  const [batches, setBatches] = useState<ReviewBatch[]>([]);
  const [batchId, setBatchId] = useState<number>();
  const [candidates, setCandidates] = useState<ReviewCandidate[]>([]);
  const [assignments, setAssignments] = useState<ReviewAdminAssignment[]>([]);
  const [roster, setRoster] = useState<ReviewRosterExpert[]>([]);
  const [aggregates, setAggregates] = useState<ReviewAggregate[]>([]);
  const [awardRules, setAwardRules] = useState<CertificateAwardRule[]>([]);
  const [awardGrants, setAwardGrants] = useState<CertificateAwardGrant[]>([]);
  const [appeals, setAppeals] = useState<ReviewAppeal[]>([]);
  const [experts, setExperts] = useState<ExpertRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string>();
  const [planModalOpen, setPlanModalOpen] = useState(false);
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [assignmentModalOpen, setAssignmentModalOpen] = useState(false);
  const [snapshotCandidate, setSnapshotCandidate] = useState<ReviewCandidate>();
  const [decisionDraft, setDecisionDraft] = useState<{
    record: ReviewAggregate;
    decision: ReviewDecision;
  }>();
  const [decisionReason, setDecisionReason] = useState('');
  const [appealToResolve, setAppealToResolve] = useState<ReviewAppeal>();
  const [appealResolution, setAppealResolution] = useState('');
  const [assignmentToRevoke, setAssignmentToRevoke] = useState<ReviewAdminAssignment>();
  const [assignmentRevokeReason, setAssignmentRevokeReason] = useState('');
  const [correctionModalOpen, setCorrectionModalOpen] = useState(false);
  const [correctionReason, setCorrectionReason] = useState('');
  const [rosterModalOpen, setRosterModalOpen] = useState(false);
  const [selectedRosterExpertIds, setSelectedRosterExpertIds] = useState<number[]>([]);
  const [checkinModalOpen, setCheckinModalOpen] = useState(false);
  const [checkinToken, setCheckinToken] = useState('');
  const [scannerActive, setScannerActive] = useState(false);
  const [scannerError, setScannerError] = useState('');
  const scannerVideoRef = useRef<HTMLVideoElement>(null);
  const workspaceContext = useOptionalCompetitionWorkspace();
  const workspaceUuid = workspaceContext?.competitionUuid;
  const reviewApi = useMemo(() => ({
    listPlans: (nextCompetitionId?: number, nextStageId?: number) => workspaceUuid
      ? listWorkspaceReviewPlans(workspaceUuid, { stageId: nextStageId })
      : listReviewPlans({ competitionId: nextCompetitionId, stageId: nextStageId }),
    createPlan: (data: ReviewPlanCreatePayload) => workspaceUuid
      ? createWorkspaceReviewPlan(workspaceUuid, data)
      : createReviewPlan(data),
    activatePlan: (nextPlanId: number) => workspaceUuid
      ? activateWorkspaceReviewPlan(workspaceUuid, nextPlanId)
      : activateReviewPlan(nextPlanId),
    listBatches: (nextPlanId?: number) => workspaceUuid
      ? listWorkspaceReviewBatches(workspaceUuid, { planId: nextPlanId })
      : listReviewBatches({ planId: nextPlanId }),
    createBatch: (data: Parameters<typeof createReviewBatch>[0]) => workspaceUuid
      ? createWorkspaceReviewBatch(workspaceUuid, data)
      : createReviewBatch(data),
    freezeBatch: (nextBatchId: number, registrationIds?: number[]) => workspaceUuid
      ? freezeWorkspaceReviewBatch(workspaceUuid, nextBatchId, registrationIds)
      : freezeReviewBatch(nextBatchId, registrationIds),
    listCandidates: (nextBatchId: number) => workspaceUuid
      ? listWorkspaceReviewCandidates(workspaceUuid, nextBatchId)
      : listReviewCandidates(nextBatchId),
    listAssignments: (nextBatchId: number) => workspaceUuid
      ? listWorkspaceReviewAssignments(workspaceUuid, nextBatchId)
      : listReviewAssignments(nextBatchId),
    listRoster: (nextBatchId: number) => workspaceUuid
      ? listWorkspaceReviewRoster(workspaceUuid, nextBatchId)
      : listReviewRoster(nextBatchId),
    saveRoster: (nextBatchId: number, expertIds: number[]) => workspaceUuid
      ? saveWorkspaceReviewRoster(workspaceUuid, nextBatchId, expertIds)
      : saveReviewRoster(nextBatchId, expertIds),
    confirmAssignments: (nextBatchId: number) => workspaceUuid
      ? confirmWorkspaceReviewAssignments(workspaceUuid, nextBatchId)
      : confirmReviewAssignments(nextBatchId),
    sendInvitations: (nextBatchId: number) => workspaceUuid
      ? sendWorkspaceReviewInvitations(workspaceUuid, nextBatchId)
      : sendReviewInvitations(nextBatchId),
    scanCheckIn: (nextBatchId: number, qrToken: string) => workspaceUuid
      ? scanWorkspaceReviewCheckIn(workspaceUuid, nextBatchId, qrToken)
      : scanReviewCheckIn(nextBatchId, qrToken),
    assignExperts: (nextBatchId: number, data: Parameters<typeof assignReviewExperts>[1]) => workspaceUuid
      ? assignWorkspaceReviewExperts(workspaceUuid, nextBatchId, data)
      : assignReviewExperts(nextBatchId, data),
    autoAssign: (nextBatchId: number, data: Parameters<typeof autoAssignReviewExperts>[1]) => workspaceUuid
      ? autoAssignWorkspaceReviewExperts(workspaceUuid, nextBatchId, data)
      : autoAssignReviewExperts(nextBatchId, data),
    startBatch: (nextBatchId: number) => workspaceUuid
      ? startWorkspaceReviewBatch(workspaceUuid, nextBatchId)
      : startReviewBatch(nextBatchId),
    listAggregates: (nextBatchId: number) => workspaceUuid
      ? listWorkspaceReviewAggregates(workspaceUuid, nextBatchId)
      : listReviewAggregates(nextBatchId),
    aggregateBatch: (nextBatchId: number) => workspaceUuid
      ? aggregateWorkspaceReviewBatch(workspaceUuid, nextBatchId)
      : aggregateReviewBatch(nextBatchId),
    decideCandidate: (nextBatchId: number, candidateId: number, decision: ReviewDecision, reason?: string) => workspaceUuid
      ? decideWorkspaceReviewCandidate(workspaceUuid, nextBatchId, candidateId, decision, reason)
      : decideReviewCandidate(nextBatchId, candidateId, decision, reason),
    finalizeBatch: (nextBatchId: number) => workspaceUuid
      ? finalizeWorkspaceReviewBatch(workspaceUuid, nextBatchId)
      : finalizeReviewBatch(nextBatchId),
    publishBatch: (nextBatchId: number) => workspaceUuid
      ? publishWorkspaceReviewBatch(workspaceUuid, nextBatchId)
      : publishReviewBatch(nextBatchId),
    reopenBatch: (nextBatchId: number, reason: string) => workspaceUuid
      ? reopenWorkspaceReviewBatchForCorrection(workspaceUuid, nextBatchId, reason)
      : reopenReviewBatchForCorrection(nextBatchId, reason),
    listAppeals: (params: { batchId?: number; status?: string } = {}) => workspaceUuid
      ? listWorkspaceReviewAppeals(workspaceUuid, params)
      : listReviewAppeals(params),
    resolveAppeal: (appealId: number, decision: 'ACCEPTED' | 'REJECTED', resolution: string) => workspaceUuid
      ? resolveWorkspaceReviewAppeal(workspaceUuid, appealId, decision, resolution)
      : resolveReviewAppeal(appealId, decision, resolution),
    revokeAssignment: (nextBatchId: number, assignmentId: number, reason: string) => workspaceUuid
      ? revokeWorkspaceReviewAssignment(workspaceUuid, nextBatchId, assignmentId, reason)
      : revokeReviewAssignment(nextBatchId, assignmentId, reason),
    listAwardRules: (nextReviewBatchId?: number) => workspaceUuid && nextReviewBatchId
      ? listCompetitionWorkspaceCertificateAwardRules(workspaceUuid, nextReviewBatchId)
      : Promise.resolve([] as CertificateAwardRule[]),
    saveAwardRules: (nextBatchId: number, rules: CertificateAwardRule[]) => workspaceUuid
      ? saveCompetitionWorkspaceCertificateAwardRules(workspaceUuid, { reviewBatchId: nextBatchId, rules })
      : Promise.resolve([] as CertificateAwardRule[]),
    clearAwardRuleOverride: (nextBatchId: number) => workspaceUuid
      ? clearCompetitionWorkspaceCertificateAwardRuleOverride(workspaceUuid, nextBatchId)
      : Promise.resolve([] as CertificateAwardRule[]),
    listAwardGrants: (nextBatchId: number) => workspaceUuid
      ? listCompetitionWorkspaceAwardGrants(workspaceUuid, nextBatchId)
      : Promise.resolve([] as CertificateAwardGrant[]),
    grantAwards: (nextBatchId: number, rules: CertificateAwardRule[]) => workspaceUuid
      ? grantCompetitionWorkspacePublishedAwards(workspaceUuid, { reviewBatchId: nextBatchId, rules })
      : Promise.resolve([] as CertificateAwardGrant[]),
  }), [workspaceUuid]);

  const canManagePlans = access.hasPermission('review:plan:manage');
  const canManageBatches = access.hasPermission('review:batch:create');
  const canManageAssignments = access.hasPermission('review:assignment:manage');
  const canManageRoster = access.hasPermission('review:roster:manage');
  const canSendNotifications = access.hasPermission('review:notification:send');
  const canScanCheckin = access.hasPermission('review:checkin:scan');
  const canAggregate = access.hasPermission('review:result:aggregate');
  const canFinalize = access.hasPermission('review:result:finalize');
  const canPublish = access.hasPermission('review:result:publish');
  const canManageAppeals = access.hasPermission('review:appeal:manage');
  const canGenerateAwards = Boolean(workspaceUuid) && access.hasPermission('aiadc:certificate-batch:create');
  const selectedPlan = plans.find((item) => item.id === planId);
  const selectedBatch = batches.find((item) => item.id === batchId);
  const eligibleExperts = experts.filter((item) =>
    item.status === 'active'
    && item.approvalStatus === 'APPROVED'
    && item.accountStatus === 'ENABLED'
    && Boolean(item.userId)
    && Boolean(item.email?.trim())
  );

  const loadPlans = useCallback(async (nextCompetitionId?: number, nextStageId?: number) => {
    if (!canManagePlans || (!workspaceUuid && !nextCompetitionId) || !nextStageId) {
      setPlans([]);
      setPlanId(undefined);
      return;
    }
    const result = await reviewApi.listPlans(nextCompetitionId, nextStageId);
    setPlans(result || []);
    setPlanId((current) => result.some((item) => item.id === current) ? current : result[0]?.id);
  }, [canManagePlans, reviewApi, workspaceUuid]);

  const loadBatches = useCallback(async (nextPlanId?: number) => {
    if (!canManageBatches || !nextPlanId) {
      setBatches([]);
      setBatchId(undefined);
      return;
    }
    const result = await reviewApi.listBatches(nextPlanId);
    setBatches(result || []);
    setBatchId((current) => result.some((item) => item.id === current) ? current : result[0]?.id);
  }, [canManageBatches, reviewApi]);

  const loadBatchDetails = useCallback(async (nextBatchId?: number) => {
    if (!nextBatchId) {
      setCandidates([]);
      setAssignments([]);
      setRoster([]);
      setAggregates([]);
      setAwardGrants([]);
      setAppeals([]);
      return;
    }
    const [nextCandidates, nextAssignments, nextRoster, nextAggregates, nextAwardGrants, nextAppeals] = await Promise.all([
      canManageAssignments ? reviewApi.listCandidates(nextBatchId) : Promise.resolve([]),
      canManageAssignments ? reviewApi.listAssignments(nextBatchId) : Promise.resolve([]),
      canManageRoster ? reviewApi.listRoster(nextBatchId) : Promise.resolve([]),
      canAggregate ? reviewApi.listAggregates(nextBatchId) : Promise.resolve([]),
      canGenerateAwards ? reviewApi.listAwardGrants(nextBatchId) : Promise.resolve([]),
      canManageAppeals ? reviewApi.listAppeals({ batchId: nextBatchId }) : Promise.resolve([]),
    ]);
    setCandidates(nextCandidates || []);
    setAssignments(nextAssignments || []);
    setRoster(nextRoster || []);
    setSelectedRosterExpertIds((nextRoster || []).map((item) => item.expertId));
    setAggregates(nextAggregates || []);
    setAwardGrants(nextAwardGrants || []);
    setAppeals(nextAppeals || []);
  }, [canAggregate, canGenerateAwards, canManageAppeals, canManageAssignments, canManageRoster, reviewApi]);

  const refreshWorkbench = useCallback(async () => {
    setLoading(true);
    try {
      await loadPlans(competitionId, stageId);
      if (planId) await loadBatches(planId);
      if (batchId) await loadBatchDetails(batchId);
    } catch (error) {
      showErrorMessage(error, '评审工作台刷新失败');
    } finally {
      setLoading(false);
    }
  }, [batchId, competitionId, loadBatchDetails, loadBatches, loadPlans, planId, stageId]);

  useEffect(() => {
    let active = true;
    if (!workspaceUuid) void listCompetitions({ pageNo: 1, pageSize: 100 })
      .then((result) => {
        if (!active) return;
        const records = result.records || [];
        setCompetitions(records);
        setCompetitionId(records[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '赛事列表加载失败'));
    if (workspaceUuid) {
      setCompetitions([]);
      setCompetitionId(undefined);
    }
    if (canManageAssignments || canManageRoster) {
      void listExperts({ pageNo: 1, pageSize: 200, status: 'active', approvalStatus: 'APPROVED' })
        .then((result) => { if (active) setExperts(result.records || []); })
        .catch((error) => showErrorMessage(error, '专家列表加载失败'));
    }
    return () => { active = false; };
  }, [canManageAssignments, canManageRoster, workspaceUuid]);

  useEffect(() => {
    if (!workspaceUuid && !competitionId) {
      setStages([]);
      setStageId(undefined);
      return;
    }
    let active = true;
    const stageRequest = workspaceUuid
      ? listCompetitionWorkspaceStages(workspaceUuid)
      : listCompetitionStages(competitionId!);
    void stageRequest
      .then((result) => {
        if (!active) return;
        setStages(result || []);
        setStageId(result[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '评审阶段加载失败'));
    return () => { active = false; };
  }, [competitionId, workspaceUuid]);

  useEffect(() => {
    void loadPlans(competitionId, stageId)
      .catch((error) => showErrorMessage(error, '评审方案加载失败'));
  }, [competitionId, loadPlans, stageId]);

  useEffect(() => {
    void loadBatches(planId)
      .catch((error) => showErrorMessage(error, '评审批次加载失败'));
  }, [loadBatches, planId]);

  useEffect(() => {
    void loadBatchDetails(batchId)
      .catch((error) => showErrorMessage(error, '评审批次详情加载失败'));
  }, [batchId, loadBatchDetails]);

  useEffect(() => {
    if (!canGenerateAwards || !selectedBatch || selectedBatch.status !== 'PUBLISHED') {
      setAwardRules([]);
      awardRuleForm.resetFields();
      return;
    }
    void reviewApi.listAwardRules(selectedBatch.id)
      .then((result) => {
        const rules = result || [];
        setAwardRules(rules);
        awardRuleForm.setFieldsValue({ rules });
      })
      .catch((error) => showErrorMessage(error, '赛事获奖设置加载失败'));
  }, [awardRuleForm, canGenerateAwards, reviewApi, selectedBatch]);

  useEffect(() => {
    if (!checkinModalOpen || !scannerActive) return undefined;
    let disposed = false;
    let timer: number | undefined;
    let stream: MediaStream | undefined;
    const video = scannerVideoRef.current;
    const detectorConstructor = (window as Window & {
      BarcodeDetector?: BarcodeDetectorConstructorLike;
    }).BarcodeDetector;

    const stop = () => {
      if (timer !== undefined) window.clearTimeout(timer);
      stream?.getTracks().forEach((track) => track.stop());
      if (video) video.srcObject = null;
    };

    if (!detectorConstructor) {
      setScannerError('当前浏览器不支持摄像头二维码识别，请使用扫码枪或粘贴二维码内容。');
      setScannerActive(false);
      return stop;
    }
    if (!navigator.mediaDevices?.getUserMedia || !video) {
      setScannerError('无法访问摄像头，请检查浏览器权限；也可以使用下方输入框。');
      setScannerActive(false);
      return stop;
    }

    const scan = async (detector: BarcodeDetectorLike) => {
      if (disposed || !video) return;
      try {
        const results = await detector.detect(video);
        const value = results.find((item) => item.rawValue?.trim())?.rawValue?.trim();
        if (value) {
          setCheckinToken(value);
          setScannerError('');
          setScannerActive(false);
          message.success('已读取二维码，请确认签到');
          return;
        }
      } catch {
        if (!disposed) setScannerError('二维码识别失败，请调整摄像头距离或改用输入框。');
      }
      if (!disposed) timer = window.setTimeout(() => void scan(detector), 350);
    };

    const start = async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } } });
        if (disposed || !video) {
          stream?.getTracks().forEach((track) => track.stop());
          return;
        }
        video.srcObject = stream;
        await video.play();
        const detector = new detectorConstructor({ formats: ['qr_code'] });
        void scan(detector);
      } catch {
        if (!disposed) {
          setScannerError('摄像头启动失败，请允许浏览器访问摄像头，或改用输入框。');
          setScannerActive(false);
        }
      }
    };
    void start();
    return () => {
      disposed = true;
      stop();
    };
  }, [checkinModalOpen, scannerActive]);

  const runAction = async (key: string, action: () => Promise<unknown>, success: string) => {
    setActionLoading(key);
    try {
      await action();
      message.success(success);
      const nextPlans = shouldReloadReviewPlans({ canManagePlans, workspaceUuid, competitionId, stageId })
        ? await reviewApi.listPlans(competitionId, stageId)
        : plans;
      setPlans(nextPlans);
      const nextPlanId = planId || nextPlans[0]?.id;
      setPlanId(nextPlanId);
      const nextBatches = nextPlanId && canManageBatches
        ? await reviewApi.listBatches(nextPlanId)
        : batches;
      setBatches(nextBatches);
      const nextBatchId = batchId || nextBatches[0]?.id;
      setBatchId(nextBatchId);
      await loadBatchDetails(nextBatchId);
    } catch (error) {
      showErrorMessage(error, `${success.replace('成功', '')}失败`);
      throw error;
    } finally {
      setActionLoading(undefined);
    }
  };

  const submitPlan = async () => {
    if ((!workspaceUuid && !competitionId) || !stageId) return;
    const values = await planForm.validateFields();
    const weight = values.criteria.reduce((total, item) => total + Number(item.weight || 0), 0);
    if (Math.abs(weight - 1) > 0.000001) {
      message.error('全部评分项权重之和必须等于 1');
      return;
    }
    await runAction(
      'create-plan',
      () => reviewApi.createPlan({ ...values, competitionId: competitionId || 0, stageId }),
      '评审方案创建成功',
    );
    setPlanModalOpen(false);
  };

  const submitBatch = async () => {
    if (!planId) return;
    const values = await batchForm.validateFields();
    await runAction(
      'create-batch',
      () => reviewApi.createBatch({
        planId,
        batchName: values.batchName,
        assignmentStrategy: 'MANUAL',
        reviewerCountPerCandidate: values.reviewerCountPerCandidate,
        expertMinAssignments: values.expertMinAssignments,
        expertTargetAssignments: values.expertTargetAssignments,
        expertMaxAssignments: values.expertMaxAssignments,
      }),
      '评审批次创建成功',
    );
    setBatchModalOpen(false);
  };

  const submitAssignments = async () => {
    if (!batchId) return;
    const values = await assignmentForm.validateFields();
    const existing = new Set(assignments.map((item) => `${item.candidateId}:${item.expertId}`));
    const pairs = values.candidateIds.flatMap((candidateId) =>
      values.expertIds.map((expertId) => ({ candidateId, expertId })),
    ).filter((item) => !existing.has(`${item.candidateId}:${item.expertId}`));
    if (!pairs.length) {
      message.info('所选专家已全部分配给所选候选团队');
      return;
    }
    await runAction(
      'assign',
      () => reviewApi.assignExperts(batchId, { assignments: pairs }),
      '专家任务分配成功',
    );
    setAssignmentModalOpen(false);
  };

  const submitRoster = async () => {
    if (!batchId || !selectedRosterExpertIds.length) {
      message.error('请勾选本次评审的全部专家');
      return;
    }
    await runAction(
      'save-roster',
      () => reviewApi.saveRoster(batchId, selectedRosterExpertIds),
      '评审专家名单保存成功',
    );
    setRosterModalOpen(false);
  };

  const sendInvitationsAction = async () => {
    if (!batchId) return;
    await runAction(
      'send-invitations',
      () => reviewApi.sendInvitations(batchId),
      '评审邀请发送完成，请查看投递状态',
    );
  };

  const confirmAssignmentsAction = async () => {
    if (!batchId) return;
    await runAction(
      'confirm-assignments',
      () => reviewApi.confirmAssignments(batchId),
      '项目与专家分配已确认并锁定快照',
    );
  };

  const submitCheckin = async () => {
    if (!batchId || !checkinToken.trim()) {
      message.error('请录入或扫描签到二维码内容');
      return;
    }
    await runAction(
      'check-in',
      () => reviewApi.scanCheckIn(batchId, checkinToken.trim()),
      '专家签到成功',
    );
    setCheckinToken('');
    setCheckinModalOpen(false);
  };

  const submitDecision = async () => {
    if (!batchId || !decisionDraft) return;
    if (!decisionReason.trim()) {
      message.error('请填写终审或仲裁依据');
      return;
    }
    const { record, decision } = decisionDraft;
    setActionLoading(`decision-${record.candidateId}`);
    try {
      const saved = await reviewApi.decideCandidate(
        batchId,
        record.candidateId,
        decision,
        decisionReason.trim(),
      );
      setAggregates((current) => current.map((item) => item.candidateId === saved.candidateId ? saved : item));
      message.success('终审结论已保存');
      setDecisionDraft(undefined);
      setDecisionReason('');
    } catch (error) {
      showErrorMessage(error, '终审结论保存失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const submitAppealResolution = async (decision: 'ACCEPTED' | 'REJECTED') => {
    if (!appealToResolve || !appealResolution.trim()) {
      message.error('请填写申诉处理结论');
      return;
    }
    setActionLoading(`appeal-${appealToResolve.id}`);
    try {
      const saved = await reviewApi.resolveAppeal(
        appealToResolve.id,
        decision,
        appealResolution.trim(),
      );
      setAppeals((current) => current.map((item) => item.id === saved.id ? saved : item));
      message.success(decision === 'ACCEPTED' ? '已确认申诉成立' : '已驳回申诉');
      setAppealToResolve(undefined);
      setAppealResolution('');
    } catch (error) {
      showErrorMessage(error, '申诉处理失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const submitAssignmentRevoke = async () => {
    if (!batchId || !assignmentToRevoke || !assignmentRevokeReason.trim()) {
      message.error('请填写撤回原因');
      return;
    }
    setActionLoading(`revoke-${assignmentToRevoke.id}`);
    try {
      const saved = await reviewApi.revokeAssignment(
        batchId,
        assignmentToRevoke.id,
        assignmentRevokeReason.trim(),
      );
      setAssignments((current) => current.map((item) => item.id === saved.id ? saved : item));
      message.success('评审任务已撤回，可使用补充分配重新补足专家');
      setAssignmentToRevoke(undefined);
      setAssignmentRevokeReason('');
    } catch (error) {
      showErrorMessage(error, '评审任务撤回失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const submitPublicationCorrection = async () => {
    if (!batchId || !correctionReason.trim()) {
      message.error('请填写发布结果更正原因');
      return;
    }
    await runAction(
      'correction',
      () => reviewApi.reopenBatch(batchId, correctionReason.trim()),
      '已撤回当前发布版本并进入更正流程',
    );
    setCorrectionModalOpen(false);
    setCorrectionReason('');
  };

  const persistAwardRuleOverride = async (showSuccess = true) => {
    if (!selectedBatch || selectedBatch.status !== 'PUBLISHED') {
      message.warning('请先发布评审结果');
      return undefined;
    }
    try {
      const values = await awardRuleForm.validateFields();
      const rules = values.rules.map((rule) => ({
        awardName: rule.awardName.trim(),
        minRank: Number(rule.minRank),
        maxRank: Number(rule.maxRank),
      }));
      const names = new Set<string>();
      let previousMaxRank = 0;
      for (const rule of rules) {
        if (!rule.awardName || names.has(rule.awardName)) {
          throw new Error('奖项名称不能为空且不能重复');
        }
        if (rule.minRank < 1 || rule.maxRank < rule.minRank || rule.maxRank > 10000 || rule.minRank <= previousMaxRank) {
          throw new Error('名次范围必须按顺序排列且不能重叠');
        }
        names.add(rule.awardName);
        previousMaxRank = rule.maxRank;
      }
      const saved = await reviewApi.saveAwardRules(selectedBatch.id, rules);
      setAwardRules(saved || rules);
      awardRuleForm.setFieldsValue({ rules: saved || rules });
      if (showSuccess) message.success('当前评审批次的奖项名次范围已保存');
      return saved || rules;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return undefined;
      }
      showErrorMessage(error, '奖项名次范围保存失败');
      return undefined;
    }
  };

  const clearAwardRuleOverride = async () => {
    if (!selectedBatch || selectedBatch.status !== 'PUBLISHED') return;
    setActionLoading('award-rules-clear');
    try {
      const rules = await reviewApi.clearAwardRuleOverride(selectedBatch.id);
      setAwardRules(rules || []);
      awardRuleForm.setFieldsValue({ rules: rules || [] });
      message.success('已恢复按赛事设置自动计算的名次范围');
    } catch (error) {
      showErrorMessage(error, '恢复自动名次范围失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const generateAwardList = async () => {
    if (!selectedBatch || selectedBatch.status !== 'PUBLISHED') {
      message.warning('请先发布评审结果');
      return;
    }
    if (!awardRules.length) {
      message.warning('当前赛事没有可用的获奖设置');
      return;
    }
    setActionLoading('award-list');
    try {
      const savedRules = await persistAwardRuleOverride(false);
      if (!savedRules) return;
      const nextGrants = await reviewApi.grantAwards(selectedBatch.id, savedRules);
      setAwardGrants(nextGrants || []);
      message.success(`已按赛事设置生成 ${nextGrants?.length || 0} 条获奖名单`);
    } catch (error) {
      showErrorMessage(error, '获奖名单生成失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const assignmentCountByCandidate = useMemo(() => {
    const counts = new Map<number, number>();
    assignments
      .filter((item) => !['DECLINED', 'EXPIRED', 'REVOKED'].includes(item.status))
      .forEach((item) => counts.set(item.candidateId, (counts.get(item.candidateId) || 0) + 1));
    return counts;
  }, [assignments]);

  const submittedCount = assignments.filter((item) => item.status === 'SUBMITTED').length;
  const candidateColumns: ColumnsType<ReviewCandidate> = [
    {
      title: '候选编号',
      width: 180,
      render: (_, record) => <Typography.Text strong>{snapshotLabel(record)}</Typography.Text>,
    },
    { title: '报名 ID', dataIndex: 'registrationId', width: 110 },
    {
      title: '专家覆盖',
      width: 140,
      render: (_, record) => {
        const count = assignmentCountByCandidate.get(record.id) || 0;
        const required = selectedBatch?.reviewerCountPerCandidate
          || selectedPlan?.requiredReviewerCount
          || selectedBatch?.minimumReviewerCount
          || 1;
        return <Tag color={count >= required ? 'success' : 'warning'}>{count} / {required}</Tag>;
      },
    },
    {
      title: '快照',
      width: 100,
      render: (_, record) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => setSnapshotCandidate(record)}>查看</Button>
      ),
    },
  ];

  const aggregateColumns: ColumnsType<ReviewAggregate> = [
    { title: '排名', dataIndex: 'rankNo', width: 80 },
    {
      title: '候选团队',
      width: 180,
      render: (_, record) => snapshotLabel(candidates.find((item) => item.id === record.candidateId) || {
        id: record.candidateId,
        batchId: record.batchId,
        registrationId: record.candidateId,
        status: '',
        snapshotJson: '',
        reviewSnapshotJson: '',
        snapshotHash: '',
      }),
    },
    { title: '汇总分', dataIndex: 'aggregateScore', width: 100 },
    {
      title: '分数区间',
      width: 150,
      render: (_, record) => `${record.minimumScore ?? '-'} ～ ${record.maximumScore ?? '-'}`,
    },
    {
      title: '有效评审',
      width: 110,
      render: (_, record) => `${record.validReviewerCount}/${record.submittedReviewerCount}`,
    },
    {
      title: '异常',
      width: 170,
      render: (_, record) => {
        const flags = (() => {
          try {
            return JSON.parse(record.anomalyFlagsJson || '[]') as string[];
          } catch {
            return [];
          }
        })();
        return flags.length
          ? <Space size={[4, 4]} wrap>{flags.map((flag) => <Tag color="warning" key={flag}>{flag}</Tag>)}</Space>
          : <Tag color="success">正常</Tag>;
      },
    },
    {
      title: '终审结论',
      width: 160,
      render: (_, record) => (
        <Select
          style={{ width: 130 }}
          value={record.decision}
          options={decisionOptions}
          disabled={!canFinalize || selectedBatch?.status !== 'AGGREGATING'}
          loading={actionLoading === `decision-${record.candidateId}`}
          onChange={(value) => {
            setDecisionReason(record.decisionReason || '');
            setDecisionDraft({ record, decision: value });
          }}
        />
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <Typography.Text>赛事</Typography.Text>
          <Select
            showSearch
            optionFilterProp="label"
            style={{ width: 300, display: workspaceUuid ? 'none' : undefined }}
            value={competitionId}
            options={competitions.map((item) => ({ label: item.title, value: item.id }))}
            onChange={setCompetitionId}
          />
          <Typography.Text>阶段</Typography.Text>
          <Select
            style={{ width: 220 }}
            value={stageId}
            options={stages.map((item) => ({ label: item.stageName, value: item.id }))}
            onChange={setStageId}
          />
          <Button loading={loading} icon={<ReloadOutlined />} onClick={() => void refreshWorkbench()}>
            刷新
          </Button>
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card
            title="1. 评审方案"
            extra={canManagePlans && (
              <Button
                type="primary"
                icon={<SettingOutlined />}
                disabled={(!workspaceUuid && !competitionId) || !stageId || plans.length > 0}
                onClick={() => {
                  planForm.setFieldsValue({
                    planName: `${stages.find((item) => item.id === stageId)?.stageName || '阶段'}评审方案`,
                    blindMode: 'DOUBLE_BLIND',
                    requiredReviewerCount: 2,
                    minimumSubmittedCount: 2,
                    aggregateMethod: 'AVERAGE',
                    scoreScale: 100,
                    trimHighestCount: 0,
                    trimLowestCount: 0,
                    criteria: defaultCriteria,
                  });
                  setPlanModalOpen(true);
                }}
              >
                新建方案
              </Button>
            )}
          >
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Select
                style={{ width: '100%' }}
                placeholder="选择评审方案"
                value={planId}
                options={plans.map((item) => ({
                  label: `${item.planName} · ${item.status === 'READY' ? '已启用' : '草稿'}`,
                  value: item.id,
                }))}
                onChange={setPlanId}
              />
              {selectedPlan ? (
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    { key: 'status', label: '状态', children: <Tag color={selectedPlan.status === 'READY' ? 'success' : 'default'}>{selectedPlan.status}</Tag> },
                    { key: 'blind', label: '匿名模式', children: selectedPlan.blindMode },
                    { key: 'reviewers', label: '专家数', children: selectedPlan.requiredReviewerCount },
                    { key: 'minimum', label: '最低提交数', children: selectedPlan.minimumSubmittedCount },
                    { key: 'method', label: '汇总方式', children: selectedPlan.aggregateMethod },
                    { key: 'criteria', label: '评分项', children: selectedPlan.criteria?.length || 0 },
                  ]}
                />
              ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评审方案" />}
              {canManagePlans && selectedPlan?.status === 'DRAFT' && (
                <Popconfirm
                  title="启用后将锁定评分标准，确认启用？"
                  onConfirm={() => runAction(
                    'activate-plan',
                    () => reviewApi.activatePlan(selectedPlan.id),
                    '评审方案启用成功',
                  )}
                >
                  <Button loading={actionLoading === 'activate-plan'}>启用并锁定评分标准</Button>
                </Popconfirm>
              )}
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card
            title="2. 评审批次"
            extra={canManageBatches && (
              <Button
                type="primary"
                disabled={selectedPlan?.status !== 'READY'}
                onClick={() => {
                  batchForm.setFieldsValue({
                    batchName: `${selectedPlan?.planName || '评审'}批次`,
                    reviewerCountPerCandidate: 3,
                    expertMinAssignments: 5,
                    expertTargetAssignments: 6,
                    expertMaxAssignments: 6,
                  });
                  setBatchModalOpen(true);
                }}
              >
                新建批次
              </Button>
            )}
          >
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Select
                style={{ width: '100%' }}
                placeholder="选择评审批次"
                value={batchId}
                options={batches.map((item) => ({
                  label: `${item.batchName} · ${batchStatusLabels[item.status]?.text || item.status}`,
                  value: item.id,
                }))}
                onChange={setBatchId}
              />
              {selectedBatch ? (
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    { key: 'no', label: '批次号', children: selectedBatch.batchNo },
                    { key: 'status', label: '状态', children: statusTag(selectedBatch.status, batchStatusLabels) },
                    { key: 'candidates', label: '候选数', children: selectedBatch.candidateCount },
                    { key: 'deadline', label: '截止时间', children: selectedBatch.reviewDeadline || '-' },
                  ]}
                />
              ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评审批次" />}
              {canManageBatches && selectedBatch?.status === 'DRAFT' && (
                <Popconfirm
                  title="将当前符合条件的报名全部冻结为候选快照，确认继续？"
                  onConfirm={() => runAction(
                    'freeze',
                    () => reviewApi.freezeBatch(selectedBatch.id),
                    '候选团队冻结成功',
                  )}
                >
                  <Button loading={actionLoading === 'freeze'}>冻结候选团队</Button>
                </Popconfirm>
              )}
            </Space>
          </Card>
        </Col>
      </Row>

      {selectedBatch && (
        <Card
          title="3. 候选与专家分配"
          extra={(
            <Space>
              {canManageRoster && ['READY', 'ASSIGNING'].includes(selectedBatch.status) && (
                <Button
                  icon={<TeamOutlined />}
                  onClick={() => {
                    setSelectedRosterExpertIds(
                      roster.length ? roster.map((item) => item.expertId) : eligibleExperts.map((item) => item.id),
                    );
                    setRosterModalOpen(true);
                  }}
                >
                  评审人员名单
                </Button>
              )}
              {canManageAssignments && ['READY', 'ASSIGNING', 'IN_REVIEW'].includes(selectedBatch.status) && (
                <>
                  <Popconfirm
                    title="系统将按当前未完成任务量均衡分配，并自动跳过身份冲突，确认执行？"
                    onConfirm={() => runAction(
                      'auto-assign',
                      () => reviewApi.autoAssign(selectedBatch.id, {}),
                      '专家自动分配成功',
                    )}
                  >
                    <Button
                      type="primary"
                      icon={<TeamOutlined />}
                      disabled={!candidates.length}
                      loading={actionLoading === 'auto-assign'}
                    >
                      {selectedBatch.status === 'IN_REVIEW' ? '补充分配' : '自动均衡分配'}
                    </Button>
                  </Popconfirm>
                  <Button
                    disabled={!candidates.length}
                    onClick={() => {
                      assignmentForm.resetFields();
                      setAssignmentModalOpen(true);
                    }}
                  >
                    手工分配
                  </Button>
                </>
              )}
              {canManageAssignments && selectedBatch.status === 'ASSIGNING' && (
                <Popconfirm
                  title="确认所有候选团队已达到最低专家数后开始评审？"
                  onConfirm={() => runAction(
                    'start',
                    () => reviewApi.startBatch(selectedBatch.id),
                    '评审已开始',
                  )}
                >
                  <Button type="primary" icon={<PlayCircleOutlined />} loading={actionLoading === 'start'}>
                    开始评审
                  </Button>
                </Popconfirm>
              )}
              {canManageAssignments && selectedBatch.status === 'ASSIGNING' && !selectedBatch.assignmentConfirmedAt && (
                <Popconfirm
                  title="确认当前项目×专家分配及工作量范围？"
                  onConfirm={() => void confirmAssignmentsAction()}
                >
                  <Button loading={actionLoading === 'confirm-assignments'}>确认分配</Button>
                </Popconfirm>
              )}
              {canSendNotifications && ['ASSIGNING', 'IN_REVIEW'].includes(selectedBatch.status) && (
                <Popconfirm
                  title="重发邀请会让旧链接立即失效，确认发送？"
                  onConfirm={() => void sendInvitationsAction()}
                >
                  <Button icon={<SendOutlined />} loading={actionLoading === 'send-invitations'}>
                    发送评审通知
                  </Button>
                </Popconfirm>
              )}
              {canScanCheckin && ['ASSIGNING', 'IN_REVIEW'].includes(selectedBatch.status) && (
                <Button icon={<QrcodeOutlined />} onClick={() => setCheckinModalOpen(true)}>
                  扫码签到
                </Button>
              )}
            </Space>
          )}
        >
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={12} md={6}><Statistic title="候选团队" value={candidates.length} /></Col>
            <Col xs={12} md={6}><Statistic title="已分配任务" value={assignments.length} /></Col>
            <Col xs={12} md={6}><Statistic title="已提交评分" value={submittedCount} /></Col>
            <Col xs={12} md={6}><Statistic title="汇总结果" value={aggregates.length} /></Col>
          </Row>
          <Card size="small" title="本批次评审人员、通知与签到" style={{ marginBottom: 16 }}>
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Space wrap>
                <Typography.Text>已选专家：{roster.length}</Typography.Text>
                <Typography.Text>已签到：{roster.filter((item) => item.checkedInAt).length}</Typography.Text>
                <Typography.Text>已发送：{roster.filter((item) => item.invitationStatus === 'SENT').length}</Typography.Text>
                <Typography.Text>
                  配置：每项目 {selectedBatch.reviewerCountPerCandidate || selectedBatch.minimumReviewerCount || 1} 名，专家项目数 {selectedBatch.expertMinAssignments ?? 5}-{selectedBatch.expertMaxAssignments ?? 6}（目标 {selectedBatch.expertTargetAssignments ?? 6}）
                </Typography.Text>
              </Space>
              <DataTable<ReviewRosterExpert>
                rowKey="id"
                isMobile={responsive.isMobile}
                size="small"
                pagination={{ pageSize: 5 }}
                dataSource={roster}
                columns={[
                  { title: '专家', dataIndex: 'expertName', width: 140 },
                  { title: '邮箱', dataIndex: 'email', width: 220 },
                  { title: '邀请', dataIndex: 'invitationStatus', width: 110, render: (value) => value || '未发送' },
                  { title: '签到', width: 110, render: (_, record) => record.checkedInAt ? <Tag color="success">已签到</Tag> : <Tag>待签到</Tag> },
                  { title: '失败原因', dataIndex: 'invitationFailureReason', ellipsis: true, render: (value) => value || '-' },
                ]}
              />
            </Space>
          </Card>
          <DataTable
            rowKey="id"
            isMobile={responsive.isMobile}
            size="small"
            columns={candidateColumns}
            dataSource={candidates}
            pagination={{ pageSize: 10 }}
          />
          <Typography.Title level={5} style={{ marginTop: 24 }}>任务明细</Typography.Title>
          <DataTable<ReviewAdminAssignment>
            rowKey="id"
            isMobile={responsive.isMobile}
            size="small"
            pagination={{ pageSize: 10 }}
            dataSource={assignments}
            columns={[
              { title: '候选 ID', dataIndex: 'candidateId', width: 100 },
              { title: '专家 ID', dataIndex: 'expertId', width: 100 },
              {
                title: '状态',
                dataIndex: 'status',
                width: 110,
                render: (value: string) => statusTag(value, assignmentStatusLabels),
              },
              {
                title: '签到',
                width: 90,
                render: (_, record) => record.checkedInAt ? <Tag color="success">已签到</Tag> : <Tag>待签到</Tag>,
              },
              { title: '截止时间', dataIndex: 'dueAt', render: (value) => value || '-' },
              {
                title: '原因',
                ellipsis: true,
                render: (_, record) => record.declineReason || record.revokeReason || '-',
              },
              {
                title: '操作',
                fixed: 'right',
                width: 100,
                render: (_, record) => canManageAssignments
                  && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS'].includes(record.status)
                  ? (
                    <Button
                      danger
                      type="link"
                      onClick={() => {
                        setAssignmentRevokeReason('');
                        setAssignmentToRevoke(record);
                      }}
                    >
                      撤回
                    </Button>
                  )
                  : null,
              },
            ]}
          />
        </Card>
      )}

      {selectedBatch && ['IN_REVIEW', 'AGGREGATING', 'FINALIZED', 'PUBLISHED'].includes(selectedBatch.status) && (
        <Card
          title="4. 汇总、终审与发布"
          extra={(
            <Space>
              {canAggregate && selectedBatch.status === 'IN_REVIEW' && (
                <Popconfirm
                  title="只有全部候选达到最低提交数才能汇总，确认执行？"
                  onConfirm={() => runAction(
                    'aggregate',
                    () => reviewApi.aggregateBatch(selectedBatch.id),
                    '评审结果汇总成功',
                  )}
                >
                  <Button loading={actionLoading === 'aggregate'}>汇总评分</Button>
                </Popconfirm>
              )}
              {canFinalize && selectedBatch.status === 'AGGREGATING' && (
                <Popconfirm
                  title="终审后结果将锁定，确认所有候选均已填写结论？"
                  onConfirm={() => runAction(
                    'finalize',
                    () => reviewApi.finalizeBatch(selectedBatch.id),
                    '评审结果终审成功',
                  )}
                >
                  <Button type="primary" icon={<CheckCircleOutlined />} loading={actionLoading === 'finalize'}>
                    终审确认
                  </Button>
                </Popconfirm>
              )}
              {canPublish && selectedBatch.status === 'FINALIZED' && (
                <Popconfirm
                  title="发布会生成不可变结果版本并同步晋级结果，确认发布？"
                  onConfirm={() => runAction(
                    'publish',
                    () => reviewApi.publishBatch(selectedBatch.id),
                    '评审结果发布成功',
                  )}
                >
                  <Button type="primary" icon={<SendOutlined />} loading={actionLoading === 'publish'}>
                    发布结果
                  </Button>
                </Popconfirm>
              )}
              {canPublish && selectedBatch.status === 'PUBLISHED' && (
                <Button
                  danger
                  onClick={() => {
                    setCorrectionReason('');
                    setCorrectionModalOpen(true);
                  }}
                >
                  撤回并更正
                </Button>
              )}
            </Space>
          )}
        >
          <DataTable
            rowKey="candidateId"
            isMobile={responsive.isMobile}
            size="small"
            columns={aggregateColumns}
            dataSource={aggregates}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      )}

      {selectedBatch && workspaceUuid && canGenerateAwards && selectedBatch.status === 'PUBLISHED' && (
        <Card
          title="5. 获奖名单生成"
          extra={<Tag color={awardGrants.length ? 'success' : 'default'}>已生成 {awardGrants.length} 条</Tag>}
        >
          <Alert
            type="info"
            showIcon
            title="名次范围可按当前评审批次调整"
            description="系统会先按赛事设置生成连续名次范围；保存后的手工调整仅作用于当前评审批次，重新生成会撤销本批次尚未制证的旧名单，已经制证的记录会保留。"
            style={{ marginBottom: 16 }}
          />
          <Form form={awardRuleForm} layout="vertical">
            <Space orientation="vertical" size={10} style={{ width: '100%', marginBottom: 16 }}>
              {awardRules.map((rule, index) => (
                <Space key={`${rule.awardName}-${index}`} align="end" wrap>
                  <Form.Item name={['rules', index, 'awardName']} hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item label={index === 0 ? '奖项' : undefined} style={{ marginBottom: 0 }}>
                    <Typography.Text strong style={{ display: 'inline-block', minWidth: 110 }}>
                      {rule.awardName}
                    </Typography.Text>
                  </Form.Item>
                  <Form.Item
                    name={['rules', index, 'minRank']}
                    label={index === 0 ? '起始名次' : undefined}
                    rules={[{ required: true, type: 'number', min: 1, max: 10000, message: '请输入有效起始名次' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <InputNumber min={1} max={10000} addonAfter="名" />
                  </Form.Item>
                  <Form.Item
                    name={['rules', index, 'maxRank']}
                    label={index === 0 ? '结束名次' : undefined}
                    rules={[{ required: true, type: 'number', min: 1, max: 10000, message: '请输入有效结束名次' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <InputNumber min={1} max={10000} addonAfter="名" />
                  </Form.Item>
                </Space>
              ))}
            </Space>
          </Form>
          <Space style={{ marginBottom: 16 }}>
            <Button
              loading={actionLoading === 'award-rules-save'}
              disabled={!awardRules.length}
              onClick={() => {
                setActionLoading('award-rules-save');
                void persistAwardRuleOverride().finally(() => setActionLoading(undefined));
              }}
            >
              保存名次范围
            </Button>
            <Button
              loading={actionLoading === 'award-rules-clear'}
              disabled={!awardRules.length}
              onClick={() => void clearAwardRuleOverride()}
            >
              恢复自动计算
            </Button>
            <Button
              type="primary"
              loading={actionLoading === 'award-list'}
              disabled={!awardRules.length}
              onClick={() => void generateAwardList()}
            >
              根据赛事设置生成获奖名单
            </Button>
            <Typography.Text type="secondary">生成后可在赛事“证书”页继续制证。</Typography.Text>
          </Space>
          <DataTable<CertificateAwardGrant>
            rowKey="id"
            isMobile={responsive.isMobile}
            size="small"
            pagination={{ pageSize: 10 }}
            dataSource={awardGrants}
            columns={[
              { title: '排名', dataIndex: 'rankNo', width: 80 },
              { title: '奖项', dataIndex: 'awardName', width: 110 },
              { title: '获奖人', dataIndex: 'recipientName', width: 130 },
              { title: '团队', dataIndex: 'teamName', width: 180, render: (value) => value || '-' },
              { title: '项目', dataIndex: 'projectName', width: 180, render: (value) => value || '-' },
              { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => statusTag(value, { GRANTED: { color: 'processing', text: '待制证' }, ISSUED: { color: 'success', text: '已制证' }, REVOKED: { color: 'default', text: '已取消' } }) },
            ]}
          />
        </Card>
      )}

      {selectedBatch && canManageAppeals && (
        <Card
          title="6. 结果申诉处理"
          extra={<Tag color={appeals.some((item) => item.status === 'SUBMITTED') ? 'processing' : 'default'}>
            待处理 {appeals.filter((item) => item.status === 'SUBMITTED').length}
          </Tag>}
        >
          <Alert
            type="info"
            showIcon
            title="处理申诉不会覆盖专家原始评分和已发布结果快照"
            description="申诉成立后，应通过更正批次和新发布版本修正结果，保留完整审计链。"
            style={{ marginBottom: 16 }}
          />
          <DataTable<ReviewAppeal>
            rowKey="id"
            isMobile={responsive.isMobile}
            size="small"
            pagination={{ pageSize: 10 }}
            dataSource={appeals}
            columns={[
              { title: '申诉编号', dataIndex: 'appealNo', width: 210 },
              { title: '报名 ID', dataIndex: 'registrationId', width: 100 },
              { title: '原始结果', dataIndex: 'decision', width: 110 },
              { title: '原始分数', dataIndex: 'aggregateScore', width: 100 },
              { title: '申诉理由', dataIndex: 'appealReason', ellipsis: true },
              {
                title: '状态',
                dataIndex: 'status',
                width: 110,
                render: (value: string) => statusTag(value, appealStatusLabels),
              },
              {
                title: '操作',
                fixed: 'right',
                width: 110,
                render: (_, record) => record.status === 'SUBMITTED' ? (
                  <Button
                    type="link"
                    onClick={() => {
                      setAppealResolution('');
                      setAppealToResolve(record);
                    }}
                  >
                    处理
                  </Button>
                ) : <Typography.Text type="secondary">{record.resolution || '已处理'}</Typography.Text>,
              },
            ]}
          />
        </Card>
      )}

      <Modal
        title="新建评审方案"
        width={900}
        open={planModalOpen}
        confirmLoading={actionLoading === 'create-plan'}
        onCancel={() => setPlanModalOpen(false)}
        onOk={() => void submitPlan()}
        destroyOnHidden
      >
        <Form form={planForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="planName" label="方案名称" rules={[{ required: true }]}>
                <Input maxLength={255} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="blindMode" label="匿名模式" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'NONE', label: '实名评审' },
                  { value: 'SINGLE_BLIND', label: '单盲' },
                  { value: 'DOUBLE_BLIND', label: '双盲' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="aggregateMethod" label="汇总方式" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'AVERAGE', label: '平均分' },
                  { value: 'MEDIAN', label: '中位数' },
                  { value: 'WEIGHTED_AVERAGE', label: '专家加权平均' },
                  { value: 'TRIMMED_MEAN', label: '去极值平均' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="requiredReviewerCount" label="每队专家数" rules={[{ required: true }]}>
                <InputNumber min={1} max={20} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="minimumSubmittedCount" label="最低提交数" rules={[{ required: true }]}>
                <InputNumber min={1} max={20} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="scoreScale" label="总分制" rules={[{ required: true }]}>
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={3}>
              <Form.Item name="trimHighestCount" label="去最高">
                <InputNumber min={0} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={3}>
              <Form.Item name="trimLowestCount" label="去最低">
                <InputNumber min={0} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Typography.Title level={5}>评分标准（权重总和必须为 1）</Typography.Title>
          <Form.List name="criteria">
            {(fields) => (
              <Space orientation="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Card key={field.key} size="small">
                    <Row gutter={12}>
                      <Col span={6}>
                        <Form.Item name={[field.name, 'code']} label="编码" rules={[{ required: true }]}>
                          <Input />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item name={[field.name, 'name']} label="名称" rules={[{ required: true }]}>
                          <Input />
                        </Form.Item>
                      </Col>
                      <Col span={5}>
                        <Form.Item name={[field.name, 'weight']} label="权重" rules={[{ required: true }]}>
                          <InputNumber min={0.01} max={1} step={0.05} style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                      <Col span={5}>
                        <Form.Item name={[field.name, 'maximumScore']} label="单项满分" rules={[{ required: true }]}>
                          <InputNumber min={1} style={{ width: '100%' }} />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Card>
                ))}
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title="新建评审批次"
        open={batchModalOpen}
        confirmLoading={actionLoading === 'create-batch'}
        onCancel={() => setBatchModalOpen(false)}
        onOk={() => void submitBatch()}
        destroyOnHidden
      >
        <Form form={batchForm} layout="vertical">
          <Form.Item name="batchName" label="批次名称" rules={[{ required: true }]}>
            <Input maxLength={255} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={6}>
              <Form.Item name="reviewerCountPerCandidate" label="每项目专家数" rules={[{ required: true }]}>
                <InputNumber min={1} max={20} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="expertMinAssignments" label="专家最少项目数" rules={[{ required: true }]}>
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="expertTargetAssignments" label="专家目标项目数" rules={[{ required: true }]}>
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="expertMaxAssignments" label="专家最多项目数" rules={[{ required: true }]}>
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Alert type="info" showIcon title="默认每个项目 3 名专家，每位专家最少 5 个、目标 6 个、最多 6 个项目，可按本批次调整。" />
        </Form>
      </Modal>

      <Modal
        title="批量分配专家"
        open={assignmentModalOpen}
        confirmLoading={actionLoading === 'assign'}
        onCancel={() => setAssignmentModalOpen(false)}
        onOk={() => void submitAssignments()}
        destroyOnHidden
      >
        <Form form={assignmentForm} layout="vertical">
          <Form.Item name="candidateIds" label="候选团队" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              maxTagCount="responsive"
              options={candidates.map((item) => ({ value: item.id, label: snapshotLabel(item) }))}
            />
          </Form.Item>
          <Form.Item name="expertIds" label="评审专家" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              maxTagCount="responsive"
              options={eligibleExperts.map((item) => ({
                value: item.id,
                label: `${item.name} · ${item.organization || item.expertise}`,
              }))}
            />
          </Form.Item>
          <Alert
            type="warning"
            showIcon
            title="系统会为每个所选候选团队分配全部所选专家，并自动跳过已存在的组合。"
          />
        </Form>
      </Modal>

      <Modal
        title="勾选本次评审专家"
        width={720}
        open={rosterModalOpen}
        confirmLoading={actionLoading === 'save-roster'}
        onCancel={() => setRosterModalOpen(false)}
        onOk={() => void submitRoster()}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          title="只有已审批、启用且已绑定账号和邮箱的专家可以被保存到名单。"
          description="名单保存后，自动分配默认只使用这些专家；若要替换已分配专家，请先以原因撤回原任务。"
          style={{ marginBottom: 16 }}
        />
        <Select
          mode="multiple"
          showSearch
          optionFilterProp="label"
          value={selectedRosterExpertIds}
          onChange={setSelectedRosterExpertIds}
          options={eligibleExperts.map((item) => ({
            value: item.id,
            label: `${item.name} · ${item.email || '未配置邮箱'}`,
          }))}
          placeholder="请选择本次评审的全部专家"
          style={{ width: '100%' }}
        />
      </Modal>

      <Modal
        title="管理员扫码签到"
        open={checkinModalOpen}
        confirmLoading={actionLoading === 'check-in'}
        onCancel={() => setCheckinModalOpen(false)}
        onOk={() => void submitCheckin()}
        okText="确认签到"
      >
        <Alert
          type="warning"
          showIcon
          title="请使用摄像头或扫码枪扫描专家签到二维码"
          description="服务端会校验评审批次、有效期和一次性使用状态；摄像头不可用时可直接粘贴扫码结果。"
          style={{ marginBottom: 16 }}
        />
        <Space orientation="vertical" style={{ width: '100%', marginBottom: 12 }}>
          <Button
            icon={<QrcodeOutlined />}
            onClick={() => {
              setScannerError('');
              setScannerActive(true);
            }}
            disabled={scannerActive}
          >
            {scannerActive ? '正在识别二维码…' : '开启摄像头扫描'}
          </Button>
          {scannerActive && (
            <video
              ref={scannerVideoRef}
              muted
              playsInline
              style={{ width: '100%', maxHeight: 320, borderRadius: 'var(--saas-card-radius)', background: '#111' }}
            />
          )}
          {scannerError ? <Alert type="error" showIcon title={scannerError} /> : null}
        </Space>
        <Input.TextArea
          rows={4}
          value={checkinToken}
          onChange={(event) => setCheckinToken(event.target.value)}
          placeholder="扫描二维码后粘贴 token"
          autoFocus
        />
      </Modal>

      <Modal
        title={appealToResolve ? `处理申诉 ${appealToResolve.appealNo}` : '处理申诉'}
        open={Boolean(appealToResolve)}
        footer={(
          <Space>
            <Button onClick={() => setAppealToResolve(undefined)}>取消</Button>
            <Button
              danger
              loading={Boolean(appealToResolve && actionLoading === `appeal-${appealToResolve.id}`)}
              onClick={() => void submitAppealResolution('REJECTED')}
            >
              驳回申诉
            </Button>
            <Button
              type="primary"
              loading={Boolean(appealToResolve && actionLoading === `appeal-${appealToResolve.id}`)}
              onClick={() => void submitAppealResolution('ACCEPTED')}
            >
              确认成立
            </Button>
          </Space>
        )}
        onCancel={() => setAppealToResolve(undefined)}
        destroyOnHidden
      >
        {appealToResolve && (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                { key: 'registration', label: '报名 ID', children: appealToResolve.registrationId },
                { key: 'result', label: '原始结果', children: appealToResolve.decision },
                { key: 'score', label: '原始分数', children: appealToResolve.aggregateScore ?? '-' },
                { key: 'rank', label: '原始名次', children: appealToResolve.rankNo ?? '-' },
              ]}
            />
            <Alert type="warning" showIcon title={appealToResolve.appealReason} />
            <Input.TextArea
              value={appealResolution}
              onChange={(event) => setAppealResolution(event.target.value)}
              rows={6}
              showCount
              maxLength={4000}
              placeholder="填写核查过程、事实依据和处理结论"
            />
          </Space>
        )}
      </Modal>

      <Modal
        title="撤回评审任务"
        open={Boolean(assignmentToRevoke)}
        okText="确认撤回"
        okButtonProps={{ danger: true }}
        confirmLoading={Boolean(
          assignmentToRevoke && actionLoading === `revoke-${assignmentToRevoke.id}`,
        )}
        onOk={() => void submitAssignmentRevoke()}
        onCancel={() => setAssignmentToRevoke(undefined)}
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          title="撤回后该任务不能继续评分，已保存的草稿仍保留在审计记录中。"
          style={{ marginBottom: 16 }}
        />
        <Input.TextArea
          value={assignmentRevokeReason}
          onChange={(event) => setAssignmentRevokeReason(event.target.value)}
          rows={5}
          showCount
          maxLength={1000}
          placeholder="填写撤回原因"
        />
      </Modal>

      <Modal
        title="撤回已发布结果并进入更正流程"
        open={correctionModalOpen}
        okText="确认撤回"
        okButtonProps={{ danger: true }}
        confirmLoading={actionLoading === 'correction'}
        onOk={() => void submitPublicationCorrection()}
        onCancel={() => setCorrectionModalOpen(false)}
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          title="当前发布版本将标记为已撤回，不会被删除"
          description="系统会保留原发布快照与哈希，批次返回终审阶段。修正结论并再次发布时会生成新的发布版本。"
          style={{ marginBottom: 16 }}
        />
        <Input.TextArea
          value={correctionReason}
          onChange={(event) => setCorrectionReason(event.target.value)}
          rows={5}
          showCount
          maxLength={1000}
          placeholder="填写申诉编号、核查依据或其他更正原因"
        />
      </Modal>

      <Modal
        title="候选团队冻结快照"
        width={900}
        open={Boolean(snapshotCandidate)}
        footer={null}
        onCancel={() => setSnapshotCandidate(undefined)}
      >
        <Typography.Paragraph type="secondary">
          哈希：{snapshotCandidate?.snapshotHash}
        </Typography.Paragraph>
        <ReviewSnapshotDetails snapshotJson={snapshotCandidate?.snapshotJson} />
      </Modal>

      <Modal
        title="记录终审 / 仲裁结论"
        open={Boolean(decisionDraft)}
        confirmLoading={Boolean(
          decisionDraft
          && actionLoading === `decision-${decisionDraft.record.candidateId}`,
        )}
        okText="保存结论"
        onCancel={() => {
          setDecisionDraft(undefined);
          setDecisionReason('');
        }}
        onOk={() => void submitDecision()}
      >
        <Alert
          type="warning"
          showIcon
          title={`拟设置结论：${decisionOptions.find((item) => item.value === decisionDraft?.decision)?.label || '-'}`}
          description="该说明会与处理人、处理时间一起保存；专家原始评分不会被覆盖。"
          style={{ marginBottom: 16 }}
        />
        <Typography.Text>终审或仲裁依据</Typography.Text>
        <Input.TextArea
          rows={5}
          maxLength={2000}
          showCount
          value={decisionReason}
          onChange={(event) => setDecisionReason(event.target.value)}
        />
      </Modal>
    </Space>
  );
};

const ExpertTaskWorkbench = () => {
  const access = useAccess();
  const responsive = useResponsive();
  const [scoreForm] = Form.useForm<ScoreFormValues>();
  const [declineForm] = Form.useForm<{ reason: string }>();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [actionLoading, setActionLoading] = useState<string>();
  const [activeTask, setActiveTask] = useState<ReviewAssignmentTask>();
  const [declineTask, setDeclineTask] = useState<ReviewAssignmentTask>();
  const canScore = access.hasPermission('review:score:submit');

  const loadTasks = useCallback(async () => {
    await actionRef.current?.reload();
  }, []);

  const runTaskAction = async (
    key: string,
    action: () => Promise<unknown>,
    success: string,
  ) => {
    setActionLoading(key);
    try {
      await action();
      message.success(success);
      await loadTasks();
    } catch (error) {
      showErrorMessage(error, `${success.replace('成功', '')}失败`);
      throw error;
    } finally {
      setActionLoading(undefined);
    }
  };

  const openScore = (task: ReviewAssignmentTask) => {
    const scoreValues = Object.fromEntries((task.latestScores || []).map((item) => [String(item.criterionId), item.score]));
    const commentValues = Object.fromEntries((task.latestScores || []).map((item) => [String(item.criterionId), item.comment || '']));
    scoreForm.setFieldsValue({
      scores: scoreValues,
      comments: commentValues,
      reviewComment: task.latestReviewComment || '',
    });
    setActiveTask(task);
  };

  const submitScores = async (submit: boolean) => {
    if (!activeTask) return;
    const values = await scoreForm.validateFields();
    const data: ReviewSheetPayload = {
      reviewComment: values.reviewComment,
      scores: activeTask.criteria.map((criterion) => ({
        criterionId: criterion.id,
        score: values.scores[String(criterion.id)],
        comment: values.comments?.[String(criterion.id)],
      })),
    };
    await runTaskAction(
      submit ? 'submit-score' : 'save-score',
      () => submit
        ? submitReviewSheet(activeTask.assignmentId, data)
        : saveReviewSheetDraft(activeTask.assignmentId, data),
      submit ? '评分提交成功' : '评分草稿保存成功',
    );
    setActiveTask(undefined);
  };

  const submitDecline = async () => {
    if (!declineTask) return;
    const values = await declineForm.validateFields();
    await runTaskAction(
      'decline',
      () => declineReviewAssignment(declineTask.assignmentId, values.reason),
      '评审任务已拒绝',
    );
    setDeclineTask(undefined);
  };

  const columns: ProColumns<ReviewAssignmentTask>[] = [
    {
      title: '评审批次',
      dataIndex: 'batchName',
      width: 200,
      fieldProps: { placeholder: '输入评审批次' },
    },
    {
      title: '候选编号',
      dataIndex: 'blindCode',
      width: 150,
      fieldProps: { placeholder: '输入候选编号' },
      render: (_, record) => record.blindCode || `候选 #${record.candidateId}`,
    },
    {
      title: '状态',
      dataIndex: 'assignmentStatus',
      valueType: 'select',
      valueEnum: assignmentStatusValueEnum,
      width: 110,
      render: (_, record) => statusTag(record.assignmentStatus, assignmentStatusLabels),
    },
    { title: '截止时间', dataIndex: 'dueAt', search: false, width: 180, render: (value) => value || '-' },
    {
      title: '最新得分',
      dataIndex: 'latestTotalScore',
      search: false,
      width: 100,
      render: (value) => value ?? '-',
    },
    {
      title: '操作',
      width: 250,
      search: false,
      valueType: 'option',
      fixed: 'right',
      render: (_, record) => (
        <Space>
          {record.assignmentStatus === 'ASSIGNED' && (
            <>
              <Button
                type="primary"
                size="small"
                loading={actionLoading === `accept-${record.assignmentId}`}
                onClick={() => void runTaskAction(
                  `accept-${record.assignmentId}`,
                  () => acceptReviewAssignment(record.assignmentId),
                  '评审任务接受成功',
                )}
              >
                接受
              </Button>
              <Button
                danger
                size="small"
                onClick={() => {
                  declineForm.resetFields();
                  setDeclineTask(record);
                }}
              >
                拒绝
              </Button>
            </>
          )}
          {['ACCEPTED', 'IN_PROGRESS'].includes(record.assignmentStatus) && (
            <Button type="primary" size="small" disabled={!canScore} onClick={() => openScore(record)}>
              {record.latestSheetStatus === 'DRAFT' ? '继续评分' : '开始评分'}
            </Button>
          )}
          {record.assignmentStatus === 'SUBMITTED' && <Tag color="success">评分已锁定</Tag>}
        </Space>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <ManagementTable<ReviewAssignmentTask>
        actionRef={actionRef}
        rowKey="assignmentId"
        columns={columns}
        isMobile={responsive.isMobile}
        scroll={{ x: 950 }}
        request={reviewTaskTableRequest}
        search={{ defaultCollapsed: true }}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
            刷新
          </Button>,
        ]}
      />

      <Modal
        title={`评审评分 · ${activeTask?.blindCode || `候选 #${activeTask?.candidateId || ''}`}`}
        width={900}
        open={Boolean(activeTask)}
        onCancel={() => setActiveTask(undefined)}
        footer={[
          <Button key="cancel" onClick={() => setActiveTask(undefined)}>取消</Button>,
          <Button
            key="draft"
            loading={actionLoading === 'save-score'}
            onClick={() => void submitScores(false)}
          >
            保存草稿
          </Button>,
          <Popconfirm
            key="submit"
            title="提交后评分将锁定，确认提交？"
            onConfirm={() => submitScores(true)}
          >
            <Button type="primary" loading={actionLoading === 'submit-score'}>提交评分</Button>
          </Popconfirm>,
        ]}
      >
        <Card size="small" title="候选资料快照" style={{ marginBottom: 16 }}>
          <ReviewSnapshotDetails snapshotJson={activeTask?.candidateSnapshotJson} />
        </Card>
        <Form form={scoreForm} layout="vertical">
          {(activeTask?.criteria || []).map((criterion) => (
            <Row gutter={16} key={criterion.id}>
              <Col span={7}>
                <Typography.Text strong>{criterion.criterionName}</Typography.Text>
                <Typography.Paragraph type="secondary">
                  权重 {Number(criterion.weight) * 100}% · 满分 {criterion.maximumScore}
                </Typography.Paragraph>
              </Col>
              <Col span={5}>
                <Form.Item
                  name={['scores', String(criterion.id)]}
                  label="得分"
                  rules={[{ required: criterion.required, message: '请输入得分' }]}
                >
                  <InputNumber min={0} max={criterion.maximumScore} precision={2} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name={['comments', String(criterion.id)]} label="单项意见">
                  <Input maxLength={2000} />
                </Form.Item>
              </Col>
            </Row>
          ))}
          <Form.Item name="reviewComment" label="综合评审意见">
            <Input.TextArea rows={4} maxLength={4000} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="拒绝评审任务"
        open={Boolean(declineTask)}
        confirmLoading={actionLoading === 'decline'}
        okButtonProps={{ danger: true }}
        okText="确认拒绝"
        onCancel={() => setDeclineTask(undefined)}
        onOk={() => void submitDecline()}
        destroyOnHidden
      >
        <Form form={declineForm} layout="vertical">
          <Form.Item name="reason" label="拒绝原因" rules={[{ required: true, message: '请填写拒绝原因' }]}>
            <Input.TextArea rows={4} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

const CompetitionReviewPage = () => {
  const access = useAccess();
  const workspace = useOptionalCompetitionWorkspace();
  const canAdmin = REVIEW_ADMIN_PERMISSIONS.some((permission) => access.hasPermission(permission));
  const canViewTasks = access.hasPermission('review:task:view');
  const items = [
    ...(shouldShowReviewAdminWorkbench(canAdmin, Boolean(workspace)) ? [{
      key: 'admin',
      label: '评审管理',
      children: <ReviewAdminWorkbench />,
    }] : []),
    ...(shouldShowGlobalExpertTasks(canViewTasks, Boolean(workspace)) ? [{
      key: 'mine',
      label: '我的评审',
      children: <ExpertTaskWorkbench />,
    }] : []),
  ];

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspace)}
      title={workspace ? '评审' : '我的评审'}
      showWorkspaceHeader={Boolean(workspace)}
      workspaceVariant="content"
    >
      {items.length
        ? items[0].children
        : <Empty description="当前角色没有评审操作权限，请联系管理员配置角色权限。" />}
    </CompetitionWorkspacePageFrame>
  );
};

export default CompetitionReviewPage;
