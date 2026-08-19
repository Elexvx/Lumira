import { ReloadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useRef, useState } from 'react';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useResponsive } from '@/hooks/useResponsive';
import {
  acceptReviewAssignment,
  declineReviewAssignment,
  saveReviewSheetDraft,
  submitReviewSheet,
} from '@/services/review/api';
import type { ReviewAssignmentTask, ReviewSheetPayload } from '@/services/review/types';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import {
  shouldShowGlobalExpertTasks,
  shouldShowReviewAdminWorkbench,
} from './reviewWorkspaceBehavior';
import {
  REVIEW_ADMIN_PERMISSIONS,
  ReviewAdminWorkbench,
  ReviewSnapshotDetails,
  assignmentStatusLabels,
  assignmentStatusValueEnum,
  reviewTaskTableRequest,
  statusTag,
  type ScoreFormValues,
} from './CompetitionReviewAdminWorkbench';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

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

      {!activeTask ? <Form form={scoreForm} component={false} /> : null}

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
