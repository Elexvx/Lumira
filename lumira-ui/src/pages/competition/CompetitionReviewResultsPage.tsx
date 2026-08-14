import { FileSearchOutlined, FormOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Modal, Space, Spin, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { DataTable } from '@/features/table/DataTable';
import { useResponsive } from '@/hooks/useResponsive';
import { message } from '@/theme/antdFeedbackBridge';
import {
  listMyPublishedReviewResults,
  listMyReviewAppeals,
  submitReviewAppeal,
} from '@/services/review/api';
import type {
  ReviewAppeal,
  ReviewPublishedResult,
} from '@/services/review/types';
import { showErrorMessage } from '@/utils/errorMessage';

const decisionLabels: Record<string, { color: string; text: string }> = {
  PASS: { color: 'success', text: '通过' },
  FAIL: { color: 'error', text: '未通过' },
  WAITLIST: { color: 'warning', text: '候补' },
  ADVANCED: { color: 'success', text: '晋级' },
  ELIMINATED: { color: 'error', text: '淘汰' },
  REVIEW_REQUIRED: { color: 'processing', text: '待复核' },
  PENDING: { color: 'default', text: '待确认' },
};

const appealStatusLabels: Record<string, { color: string; text: string }> = {
  SUBMITTED: { color: 'processing', text: '处理中' },
  ACCEPTED: { color: 'success', text: '申诉成立' },
  REJECTED: { color: 'error', text: '申诉驳回' },
};

type AppealFormValues = {
  reason: string;
};

const reviewAppealColumns: ColumnsType<ReviewAppeal> = [
  { title: '申诉编号', dataIndex: 'appealNo', width: 210 },
  { title: '报名编号', dataIndex: 'registrationId', render: (value) => `#${value}` },
  {
    title: '状态',
    dataIndex: 'status',
    render: (value: string) => {
      const config = appealStatusLabels[value] || { color: 'default', text: value };
      return <Tag color={config.color}>{config.text}</Tag>;
    },
  },
  { title: '申诉理由', dataIndex: 'appealReason', ellipsis: true },
  { title: '处理结论', dataIndex: 'resolution', ellipsis: true, render: (value) => value || '-' },
  { title: '提交时间', dataIndex: 'createdAt', render: (value) => value || '-' },
];

const CompetitionReviewResultsPage = () => {
  const responsive = useResponsive();
  const [form] = Form.useForm<AppealFormValues>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [results, setResults] = useState<ReviewPublishedResult[]>([]);
  const [appeals, setAppeals] = useState<ReviewAppeal[]>([]);
  const [appealTarget, setAppealTarget] = useState<ReviewPublishedResult>();

  const load = async () => {
    setLoading(true);
    try {
      const [publishedResults, appealRecords] = await Promise.all([
        listMyPublishedReviewResults(),
        listMyReviewAppeals(),
      ]);
      setResults(publishedResults || []);
      setAppeals(appealRecords || []);
    } catch (error) {
      showErrorMessage(error, '评审结果加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const appealByResult = useMemo(
    () => new Map(appeals.map((appeal) => [
      `${appeal.publicationId}:${appeal.registrationId}`,
      appeal,
    ])),
    [appeals],
  );

  const reviewResultColumns = useMemo<ColumnsType<ReviewPublishedResult>>(() => [
    {
      title: '赛事 / 阶段',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{record.competitionTitle || `赛事 #${record.competitionId}`}</Typography.Text>
          <Typography.Text type="secondary">{record.stageName || `阶段 #${record.stageId}`}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '报名编号',
      dataIndex: 'registrationNo',
      render: (value, record) => value || `#${record.registrationId}`,
    },
    {
      title: '结果',
      dataIndex: 'decision',
      render: (value: string) => {
        const config = decisionLabels[value] || { color: 'default', text: value };
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    { title: '得分', dataIndex: 'aggregateScore', render: (value) => value ?? '-' },
    { title: '名次', dataIndex: 'rankNo', render: (value) => value ? `第 ${value} 名` : '-' },
    { title: '发布时间', dataIndex: 'publishedAt', render: (value) => value || '-' },
    {
      title: '申诉',
      fixed: 'right',
      width: 160,
      render: (_, record) => {
        const appeal = appealByResult.get(
          `${record.publicationId}:${record.registrationId}`,
        );
        if (appeal) {
          const config = appealStatusLabels[appeal.status]
            || { color: 'default', text: appeal.status };
          return <Tag color={config.color}>{config.text}</Tag>;
        }
        return (
          <Button
            type="link"
            icon={<FormOutlined />}
            onClick={() => {
              form.resetFields();
              setAppealTarget(record);
            }}
          >
            提交申诉
          </Button>
        );
      },
    },
  ], [appealByResult, form]);

  const submitAppeal = async () => {
    if (!appealTarget) return;
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await submitReviewAppeal(
        appealTarget.publicationId,
        appealTarget.registrationId,
        values.reason,
      );
      message.success('申诉已提交，原始评分与结果快照已保留');
      setAppealTarget(undefined);
      form.resetFields();
      await load();
    } catch (error) {
      showErrorMessage(error, '申诉提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ManagementPage title="评审结果与申诉">
      <ManagementPageBody>
        <Spin spinning={loading}>
          <Card title="已发布结果" extra={<Button onClick={() => void load()}>刷新</Button>}>
            <DataTable<ReviewPublishedResult>
              rowKey={(record) => `${record.publicationId}:${record.registrationId}`}
              isMobile={responsive.isMobile}
              pagination={false}
              scroll={{ x: 900 }}
              dataSource={results}
              columns={reviewResultColumns}
            />
          </Card>

          <Card title="我的申诉记录">
            <DataTable<ReviewAppeal>
              rowKey="id"
              isMobile={responsive.isMobile}
              pagination={false}
              scroll={{ x: 900 }}
              dataSource={appeals}
              columns={reviewAppealColumns}
            />
          </Card>
        </Spin>
      </ManagementPageBody>

      <Modal
        title="提交评审结果申诉"
        open={Boolean(appealTarget)}
        confirmLoading={submitting}
        okText="确认提交"
        cancelText="取消"
        onOk={() => void submitAppeal()}
        onCancel={() => setAppealTarget(undefined)}
      >
        <Alert
          showIcon
          type="warning"
          icon={<FileSearchOutlined />}
          message="请清楚说明需要复核的材料、评分项和事实依据"
          style={{ marginBottom: 16 }}
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            label="申诉理由"
            rules={[
              { required: true, whitespace: true, message: '请输入申诉理由' },
              { max: 4000, message: '申诉理由不能超过 4000 个字符' },
            ]}
          >
            <Input.TextArea rows={7} showCount maxLength={4000} />
          </Form.Item>
        </Form>
      </Modal>
    </ManagementPage>
  );
};

export default CompetitionReviewResultsPage;
