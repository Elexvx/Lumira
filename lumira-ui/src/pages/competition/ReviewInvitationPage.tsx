import { CheckCircleOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useLocation } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  QRCode,
  Space,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DataTable } from '@/features/table/DataTable';
import { useResponsive } from '@/hooks/useResponsive';
import {
  acceptReviewInvitationAssignment,
  declineReviewInvitationAssignment,
  getReviewInvitationStatus,
  listReviewInvitationAssignments,
  openReviewInvitation,
  saveReviewInvitationDraft,
  submitReviewInvitationSheet,
} from '@/services/review/api';
import type {
  ReviewAssignmentTask,
  ReviewInvitation,
  ReviewSheetPayload,
} from '@/services/review/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

type ScoreFormValues = {
  scores: Record<string, number>;
  comments?: Record<string, string>;
  reviewComment?: string;
};

const parseSnapshot = (value?: string | null) => {
  if (!value) return {};
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return { snapshot: value };
  }
};

const ReviewInvitationPage = () => {
  const location = useLocation();
  const responsive = useResponsive();
  const token = useMemo(() => new URLSearchParams(location.search).get('token') || '', [location.search]);
  const [invitation, setInvitation] = useState<ReviewInvitation>();
  const [tasks, setTasks] = useState<ReviewAssignmentTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTask, setActiveTask] = useState<ReviewAssignmentTask>();
  const [declineTask, setDeclineTask] = useState<ReviewAssignmentTask>();
  const [declineReason, setDeclineReason] = useState('');
  const [actionLoading, setActionLoading] = useState<string>();
  const [scoreForm] = Form.useForm<ScoreFormValues>();

  const loadTasks = useCallback(async () => {
    if (!token || invitation?.checkinStatus !== 'CHECKED_IN') return;
    setRefreshing(true);
    try {
      setTasks((await listReviewInvitationAssignments(token)) || []);
    } catch (error) {
      showErrorMessage(error, '评审任务加载失败');
    } finally {
      setRefreshing(false);
    }
  }, [invitation?.checkinStatus, token]);

  const refreshStatus = useCallback(async () => {
    if (!token) return;
    try {
      const status = await getReviewInvitationStatus(token);
      setInvitation(status);
      if (status.checkinStatus === 'CHECKED_IN') {
        setTasks((await listReviewInvitationAssignments(token)) || []);
      }
    } catch (error) {
      showErrorMessage(error, '评审邀请状态加载失败');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    let active = true;
    void openReviewInvitation(token)
      .then((opened) => {
        if (!active) return;
        setInvitation(opened);
        if (opened.checkinStatus === 'CHECKED_IN') {
          void listReviewInvitationAssignments(token)
            .then((nextTasks) => { if (active) setTasks(nextTasks || []); })
            .catch((error) => showErrorMessage(error, '评审任务加载失败'));
        }
      })
      .catch((error) => showErrorMessage(error, '评审邀请无效或已过期'))
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [token]);

  useEffect(() => {
    if (!token || invitation?.checkinStatus === 'CHECKED_IN') return undefined;
    const timer = window.setInterval(() => void refreshStatus(), 4000);
    return () => window.clearInterval(timer);
  }, [invitation?.checkinStatus, refreshStatus, token]);

  const runAction = useCallback(async (key: string, action: () => Promise<unknown>, success: string) => {
    setActionLoading(key);
    try {
      await action();
      message.success(success);
      await loadTasks();
    } catch (error) {
      showErrorMessage(error, `${success.replace('成功', '')}失败`);
    } finally {
      setActionLoading(undefined);
    }
  }, [loadTasks]);

  const openScore = useCallback((task: ReviewAssignmentTask) => {
    scoreForm.setFieldsValue({
      scores: Object.fromEntries((task.latestScores || []).map((item) => [String(item.criterionId), item.score])),
      comments: Object.fromEntries((task.latestScores || []).map((item) => [String(item.criterionId), item.comment || ''])),
      reviewComment: task.latestReviewComment || '',
    });
    setActiveTask(task);
  }, [scoreForm]);

  const submitScores = async (submit: boolean) => {
    if (!activeTask || !token) return;
    const values = await scoreForm.validateFields();
    const data: ReviewSheetPayload = {
      reviewComment: values.reviewComment,
      scores: activeTask.criteria.map((criterion) => ({
        criterionId: criterion.id,
        score: values.scores[String(criterion.id)],
        comment: values.comments?.[String(criterion.id)],
      })),
    };
    await runAction(
      submit ? 'submit-score' : 'save-score',
      () => submit
        ? submitReviewInvitationSheet(token, activeTask.assignmentId, data)
        : saveReviewInvitationDraft(token, activeTask.assignmentId, data),
      submit ? '评分提交成功' : '评分草稿保存成功',
    );
    setActiveTask(undefined);
  };

  const columns = useMemo<ColumnsType<ReviewAssignmentTask>>(() => [
    { title: '评审批次', dataIndex: 'batchName', width: 200 },
    {
      title: '候选编号',
      width: 150,
      render: (_, record) => record.blindCode || `候选 #${record.candidateId}`,
    },
    {
      title: '状态',
      width: 110,
      render: (_, record) => <Tag color={record.assignmentStatus === 'SUBMITTED' ? 'success' : 'processing'}>{record.assignmentStatus}</Tag>,
    },
    { title: '截止时间', dataIndex: 'dueAt', width: 180, render: (value) => value || '-' },
    { title: '最新得分', dataIndex: 'latestTotalScore', width: 100, render: (value) => value ?? '-' },
    {
      title: '操作',
      width: 260,
      render: (_, record) => (
        <Space>
          {record.assignmentStatus === 'ASSIGNED' ? (
            <>
              <Button
                type="primary"
                size="small"
                loading={actionLoading === `accept-${record.assignmentId}`}
                onClick={() => void runAction(
                  `accept-${record.assignmentId}`,
                  () => acceptReviewInvitationAssignment(token, record.assignmentId),
                  '评审任务接受成功',
                )}
              >
                接受
              </Button>
              <Button size="small" danger onClick={() => { setDeclineReason(''); setDeclineTask(record); }}>
                拒绝
              </Button>
            </>
          ) : null}
          {['ACCEPTED', 'IN_PROGRESS'].includes(record.assignmentStatus) ? (
            <Button type="primary" size="small" onClick={() => openScore(record)}>
              {record.latestSheetStatus === 'DRAFT' ? '继续评分' : '开始评分'}
            </Button>
          ) : null}
          {record.assignmentStatus === 'SUBMITTED' ? <Tag color="success">评分已锁定</Tag> : null}
        </Space>
      ),
    },
  ], [actionLoading, openScore, runAction, token]);

  if (!token) {
    return <Card style={{ maxWidth: 720, margin: '12vh auto' }}><Empty description="缺少评审邀请令牌" /></Card>;
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto', padding: 24 }}>
      <Card title="专家评审签到" loading={loading}>
        {invitation ? (
          <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <Typography.Title level={3} style={{ margin: 0 }}>{invitation.batchName}</Typography.Title>
            <Typography.Text>专家：{invitation.expertName}</Typography.Text>
            {invitation.checkinStatus !== 'CHECKED_IN' ? (
              <>
                <Alert
                  type="info"
                  showIcon
                  icon={<SafetyCertificateOutlined />}
                  message="请将此二维码展示给现场管理员扫描签到"
                  description="签到完成前不会开放项目资料和评分入口；二维码 5 分钟内有效，过期可刷新。"
                />
                <div style={{ display: 'flex', justifyContent: 'center' }}>
                  {invitation.qrValue ? <QRCode value={invitation.qrValue} size={240} /> : <Empty description="签到二维码暂不可用" />}
                </div>
                <Space>
                  <Button icon={<ReloadOutlined />} onClick={() => {
                    setLoading(true);
                    void openReviewInvitation(token).then(setInvitation).catch((error) => showErrorMessage(error, '二维码刷新失败')).finally(() => setLoading(false));
                  }}>刷新二维码</Button>
                  <Typography.Text type="secondary">管理员扫码后页面会自动进入评审任务</Typography.Text>
                </Space>
              </>
            ) : (
              <Alert
                type="success"
                showIcon
                icon={<CheckCircleOutlined />}
                message="签到成功，可以开始现场评审"
                description={`签到时间：${invitation.checkedInAt || '-'}`}
              />
            )}
          </Space>
        ) : <Empty description="评审邀请无效或已过期" />}
      </Card>

      {invitation?.checkinStatus === 'CHECKED_IN' ? (
        <Card
          title="已分配项目"
          style={{ marginTop: 16 }}
          extra={<Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void loadTasks()}>刷新任务</Button>}
        >
          <DataTable
            rowKey="assignmentId"
            columns={columns}
            dataSource={tasks}
            isMobile={responsive.isMobile}
            scroll={{ x: 980 }}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      ) : null}

      <Modal
        title={`评审评分 · ${activeTask?.blindCode || `候选 #${activeTask?.candidateId || ''}`}`}
        width={900}
        open={Boolean(activeTask)}
        onCancel={() => setActiveTask(undefined)}
        footer={[
          <Button key="cancel" onClick={() => setActiveTask(undefined)}>取消</Button>,
          <Button key="draft" loading={actionLoading === 'save-score'} onClick={() => void submitScores(false)}>保存草稿</Button>,
          <Popconfirm key="submit" title="提交后评分将锁定，确认提交？" onConfirm={() => void submitScores(true)}>
            <Button type="primary" loading={actionLoading === 'submit-score'}>提交评分</Button>
          </Popconfirm>,
        ]}
      >
        <Card size="small" title="候选资料快照" style={{ marginBottom: 16 }}>
          <pre style={{ maxHeight: 300, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
            {JSON.stringify(parseSnapshot(activeTask?.candidateSnapshotJson), null, 2)}
          </pre>
        </Card>
        <Form form={scoreForm} layout="vertical">
          {(activeTask?.criteria || []).map((criterion) => (
            <div key={criterion.id}>
              <Typography.Text strong>{criterion.criterionName}</Typography.Text>
              <Typography.Paragraph type="secondary">权重 {Number(criterion.weight) * 100}% · 满分 {criterion.maximumScore}</Typography.Paragraph>
              <Form.Item name={['scores', String(criterion.id)]} label="得分" rules={[{ required: criterion.required, message: '请输入得分' }]}>
                <InputNumber min={0} max={criterion.maximumScore} precision={2} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name={['comments', String(criterion.id)]} label="单项意见">
                <Input maxLength={2000} />
              </Form.Item>
            </div>
          ))}
          <Form.Item name="reviewComment" label="综合评审意见"><Input.TextArea rows={4} maxLength={4000} showCount /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title="拒绝评审任务"
        open={Boolean(declineTask)}
        okButtonProps={{ danger: true, disabled: !declineReason.trim() }}
        okText="确认拒绝"
        onCancel={() => setDeclineTask(undefined)}
        onOk={() => {
          if (!declineTask) return;
          void runAction(
            `decline-${declineTask.assignmentId}`,
            () => declineReviewInvitationAssignment(token, declineTask.assignmentId, declineReason.trim()),
            '评审任务已拒绝',
          ).then(() => setDeclineTask(undefined));
        }}
      >
        <Input.TextArea rows={4} maxLength={1000} showCount value={declineReason} onChange={(event) => setDeclineReason(event.target.value)} placeholder="请填写拒绝原因" />
      </Modal>
    </div>
  );
};

export default ReviewInvitationPage;
