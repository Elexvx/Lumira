import {
  CheckCircleOutlined,
  EyeOutlined,
  PlayCircleOutlined,
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
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import {
  listCompetitions,
  listCompetitionStages,
} from '@/services/competition/api';
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
  publishReviewBatch,
  reopenReviewBatchForCorrection,
  revokeReviewAssignment,
  resolveReviewAppeal,
  saveReviewSheetDraft,
  startReviewBatch,
  submitReviewSheet,
} from '@/services/review/api';
import type {
  ReviewAdminAssignment,
  ReviewAggregate,
  ReviewAppeal,
  ReviewAssignmentTask,
  ReviewBatch,
  ReviewCandidate,
  ReviewCriterionPayload,
  ReviewDecision,
  ReviewPlan,
  ReviewPlanCreatePayload,
  ReviewSheetPayload,
} from '@/services/review/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

type PlanFormValues = Omit<ReviewPlanCreatePayload, 'competitionId' | 'stageId'>;
type BatchFormValues = { batchName: string };
type AssignmentFormValues = { candidateIds: number[]; expertIds: number[] };
type ScoreFormValues = {
  scores: Record<string, number>;
  comments?: Record<string, string>;
  reviewComment?: string;
};

const REVIEW_ADMIN_PERMISSIONS = [
  'review:plan:manage',
  'review:batch:create',
  'review:assignment:manage',
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
  const [planForm] = Form.useForm<PlanFormValues>();
  const [batchForm] = Form.useForm<BatchFormValues>();
  const [assignmentForm] = Form.useForm<AssignmentFormValues>();
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
  const [aggregates, setAggregates] = useState<ReviewAggregate[]>([]);
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

  const canManagePlans = access.hasPermission('review:plan:manage');
  const canManageBatches = access.hasPermission('review:batch:create');
  const canManageAssignments = access.hasPermission('review:assignment:manage');
  const canAggregate = access.hasPermission('review:result:aggregate');
  const canFinalize = access.hasPermission('review:result:finalize');
  const canPublish = access.hasPermission('review:result:publish');
  const canManageAppeals = access.hasPermission('review:appeal:manage');
  const selectedPlan = plans.find((item) => item.id === planId);
  const selectedBatch = batches.find((item) => item.id === batchId);

  const loadPlans = useCallback(async (nextCompetitionId?: number, nextStageId?: number) => {
    if (!canManagePlans || !nextCompetitionId || !nextStageId) {
      setPlans([]);
      setPlanId(undefined);
      return;
    }
    const result = await listReviewPlans({ competitionId: nextCompetitionId, stageId: nextStageId });
    setPlans(result || []);
    setPlanId((current) => result.some((item) => item.id === current) ? current : result[0]?.id);
  }, [canManagePlans]);

  const loadBatches = useCallback(async (nextPlanId?: number) => {
    if (!canManageBatches || !nextPlanId) {
      setBatches([]);
      setBatchId(undefined);
      return;
    }
    const result = await listReviewBatches({ planId: nextPlanId });
    setBatches(result || []);
    setBatchId((current) => result.some((item) => item.id === current) ? current : result[0]?.id);
  }, [canManageBatches]);

  const loadBatchDetails = useCallback(async (nextBatchId?: number) => {
    if (!nextBatchId) {
      setCandidates([]);
      setAssignments([]);
      setAggregates([]);
      setAppeals([]);
      return;
    }
    const [nextCandidates, nextAssignments, nextAggregates, nextAppeals] = await Promise.all([
      canManageAssignments ? listReviewCandidates(nextBatchId) : Promise.resolve([]),
      canManageAssignments ? listReviewAssignments(nextBatchId) : Promise.resolve([]),
      canAggregate ? listReviewAggregates(nextBatchId) : Promise.resolve([]),
      canManageAppeals ? listReviewAppeals({ batchId: nextBatchId }) : Promise.resolve([]),
    ]);
    setCandidates(nextCandidates || []);
    setAssignments(nextAssignments || []);
    setAggregates(nextAggregates || []);
    setAppeals(nextAppeals || []);
  }, [canAggregate, canManageAppeals, canManageAssignments]);

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
    void listCompetitions({ pageNo: 1, pageSize: 100 })
      .then((result) => {
        if (!active) return;
        const records = result.records || [];
        setCompetitions(records);
        setCompetitionId(records[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '赛事列表加载失败'));
    if (canManageAssignments) {
      void listExperts({ pageNo: 1, pageSize: 200, status: 'active', approvalStatus: 'APPROVED' })
        .then((result) => { if (active) setExperts(result.records || []); })
        .catch((error) => showErrorMessage(error, '专家列表加载失败'));
    }
    return () => { active = false; };
  }, [canManageAssignments]);

  useEffect(() => {
    if (!competitionId) {
      setStages([]);
      setStageId(undefined);
      return;
    }
    let active = true;
    void listCompetitionStages(competitionId)
      .then((result) => {
        if (!active) return;
        setStages(result || []);
        setStageId(result[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '评审阶段加载失败'));
    return () => { active = false; };
  }, [competitionId]);

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

  const runAction = async (key: string, action: () => Promise<unknown>, success: string) => {
    setActionLoading(key);
    try {
      await action();
      message.success(success);
      const nextPlans = competitionId && stageId && canManagePlans
        ? await listReviewPlans({ competitionId, stageId })
        : plans;
      setPlans(nextPlans);
      const nextPlanId = planId || nextPlans[0]?.id;
      setPlanId(nextPlanId);
      const nextBatches = nextPlanId && canManageBatches
        ? await listReviewBatches({ planId: nextPlanId })
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
    if (!competitionId || !stageId) return;
    const values = await planForm.validateFields();
    const weight = values.criteria.reduce((total, item) => total + Number(item.weight || 0), 0);
    if (Math.abs(weight - 1) > 0.000001) {
      message.error('全部评分项权重之和必须等于 1');
      return;
    }
    await runAction(
      'create-plan',
      () => createReviewPlan({ ...values, competitionId, stageId }),
      '评审方案创建成功',
    );
    setPlanModalOpen(false);
  };

  const submitBatch = async () => {
    if (!planId) return;
    const values = await batchForm.validateFields();
    await runAction(
      'create-batch',
      () => createReviewBatch({
        planId,
        batchName: values.batchName,
        assignmentStrategy: 'MANUAL',
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
      () => assignReviewExperts(batchId, { assignments: pairs }),
      '专家任务分配成功',
    );
    setAssignmentModalOpen(false);
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
      const saved = await decideReviewCandidate(
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
      const saved = await resolveReviewAppeal(
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
      const saved = await revokeReviewAssignment(
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
      () => reopenReviewBatchForCorrection(batchId, correctionReason.trim()),
      '已撤回当前发布版本并进入更正流程',
    );
    setCorrectionModalOpen(false);
    setCorrectionReason('');
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
        const required = selectedPlan?.requiredReviewerCount || selectedBatch?.minimumReviewerCount || 1;
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
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        showIcon
        type="info"
        message="评审数据与报名业务解耦"
        description="候选团队在冻结时生成不可变快照；专家仅访问自己的盲审任务。汇总、终审和发布分别授权并保留版本记录。"
      />
      <Card>
        <Space wrap>
          <Typography.Text>赛事</Typography.Text>
          <Select
            showSearch
            optionFilterProp="label"
            style={{ width: 300 }}
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
                disabled={!competitionId || !stageId || plans.length > 0}
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
            <Space direction="vertical" style={{ width: '100%' }}>
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
                    () => activateReviewPlan(selectedPlan.id),
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
                  batchForm.setFieldsValue({ batchName: `${selectedPlan?.planName || '评审'}批次` });
                  setBatchModalOpen(true);
                }}
              >
                新建批次
              </Button>
            )}
          >
            <Space direction="vertical" style={{ width: '100%' }}>
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
                    () => freezeReviewBatch(selectedBatch.id),
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
              {canManageAssignments && ['READY', 'ASSIGNING', 'IN_REVIEW'].includes(selectedBatch.status) && (
                <>
                  <Popconfirm
                    title="系统将按当前未完成任务量均衡分配，并自动跳过身份冲突，确认执行？"
                    onConfirm={() => runAction(
                      'auto-assign',
                      () => autoAssignReviewExperts(selectedBatch.id),
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
                    () => startReviewBatch(selectedBatch.id),
                    '评审已开始',
                  )}
                >
                  <Button type="primary" icon={<PlayCircleOutlined />} loading={actionLoading === 'start'}>
                    开始评审
                  </Button>
                </Popconfirm>
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
          <Table
            rowKey="id"
            size="small"
            columns={candidateColumns}
            dataSource={candidates}
            pagination={{ pageSize: 10 }}
          />
          <Typography.Title level={5} style={{ marginTop: 24 }}>任务明细</Typography.Title>
          <Table<ReviewAdminAssignment>
            rowKey="id"
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
                    () => aggregateReviewBatch(selectedBatch.id),
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
                    () => finalizeReviewBatch(selectedBatch.id),
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
                    () => publishReviewBatch(selectedBatch.id),
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
          <Table
            rowKey="candidateId"
            size="small"
            columns={aggregateColumns}
            dataSource={aggregates}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      )}

      {selectedBatch && canManageAppeals && (
        <Card
          title="5. 结果申诉处理"
          extra={<Tag color={appeals.some((item) => item.status === 'SUBMITTED') ? 'processing' : 'default'}>
            待处理 {appeals.filter((item) => item.status === 'SUBMITTED').length}
          </Tag>}
        >
          <Alert
            type="info"
            showIcon
            message="处理申诉不会覆盖专家原始评分和已发布结果快照"
            description="申诉成立后，应通过更正批次和新发布版本修正结果，保留完整审计链。"
            style={{ marginBottom: 16 }}
          />
          <Table<ReviewAppeal>
            rowKey="id"
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
              <Space direction="vertical" style={{ width: '100%' }}>
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
          <Alert type="info" showIcon message="批次创建后，先冻结候选团队快照，再分配专家。" />
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
              options={experts.map((item) => ({
                value: item.id,
                label: `${item.name} · ${item.organization || item.expertise}`,
              }))}
            />
          </Form.Item>
          <Alert
            type="warning"
            showIcon
            message="系统会为每个所选候选团队分配全部所选专家，并自动跳过已存在的组合。"
          />
        </Form>
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
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
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
            <Alert type="warning" showIcon message={appealToResolve.appealReason} />
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
          message="撤回后该任务不能继续评分，已保存的草稿仍保留在审计记录中。"
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
          message="当前发布版本将标记为已撤回，不会被删除"
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
          message={`拟设置结论：${decisionOptions.find((item) => item.value === decisionDraft?.decision)?.label || '-'}`}
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
  const [scoreForm] = Form.useForm<ScoreFormValues>();
  const [declineForm] = Form.useForm<{ reason: string }>();
  const [tasks, setTasks] = useState<ReviewAssignmentTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string>();
  const [activeTask, setActiveTask] = useState<ReviewAssignmentTask>();
  const [declineTask, setDeclineTask] = useState<ReviewAssignmentTask>();
  const canScore = access.hasPermission('review:score:submit');

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      setTasks(await listMyReviewAssignments());
    } catch (error) {
      showErrorMessage(error, '我的评审任务加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

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

  const columns: ColumnsType<ReviewAssignmentTask> = [
    { title: '评审批次', dataIndex: 'batchName', width: 200 },
    {
      title: '候选编号',
      width: 150,
      render: (_, record) => record.blindCode || `候选 #${record.candidateId}`,
    },
    {
      title: '状态',
      dataIndex: 'assignmentStatus',
      width: 110,
      render: (value) => statusTag(value, assignmentStatusLabels),
    },
    { title: '截止时间', dataIndex: 'dueAt', width: 180, render: (value) => value || '-' },
    {
      title: '最新得分',
      dataIndex: 'latestTotalScore',
      width: 100,
      render: (value) => value ?? '-',
    },
    {
      title: '操作',
      width: 250,
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
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        showIcon
        type="info"
        message="只展示分配给当前专家的评审任务"
        description="匿名评审场景不会返回团队、负责人和成员身份字段；提交评分后将锁定，草稿可在截止时间前继续修改。"
      />
      <Card
        title="我的评审任务"
        extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadTasks()}>刷新</Button>}
      >
        <Table
          rowKey="assignmentId"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          scroll={{ x: 950 }}
          pagination={{ pageSize: 10 }}
        />
      </Card>

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
  const canAdmin = REVIEW_ADMIN_PERMISSIONS.some((permission) => access.hasPermission(permission));
  const canViewTasks = access.hasPermission('review:task:view');
  const items = [
    ...(canAdmin ? [{
      key: 'admin',
      label: '评审管理',
      children: <ReviewAdminWorkbench />,
    }] : []),
    ...(canViewTasks ? [{
      key: 'mine',
      label: '我的评审任务',
      children: <ExpertTaskWorkbench />,
    }] : []),
  ];

  return (
    <ManagementPage title="评审工作台">
      <ManagementPageBody>
        {items.length
          ? <Tabs items={items} />
          : <Empty description="当前角色没有评审操作权限，请联系管理员配置角色权限。" />}
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default CompetitionReviewPage;
