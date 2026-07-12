import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import {
  applyCompetitionStagePromotionRule,
  listCompetitions,
  listCompetitionStages,
  listCompetitionStageReviewCandidates,
  saveCompetitionStageReviewDecision,
} from '@/services/competition/api';
import type {
  CompetitionRecord,
  CompetitionStageRecord,
  CompetitionStageReviewCandidateRecord,
} from '@/services/competition/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';

const decisionOptions = [
  { label: '待评审', value: 'PENDING' },
  { label: '晋级', value: 'ADVANCED' },
  { label: '未晋级', value: 'ELIMINATED' },
];

const CompetitionReviewPage = () => {
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [competitionId, setCompetitionId] = useState<number>();
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [stageId, setStageId] = useState<number>();
  const [records, setRecords] = useState<CompetitionStageReviewCandidateRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState<number>();
  const [applyingRule, setApplyingRule] = useState(false);

  useEffect(() => {
    void listCompetitions({ pageNo: 1, pageSize: 100 })
      .then((result) => {
        setCompetitions(result.records || []);
        setCompetitionId(result.records?.[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '赛事列表加载失败'));
  }, []);

  useEffect(() => {
    if (!competitionId) {
      setStages([]);
      setStageId(undefined);
      return;
    }
    void listCompetitionStages(competitionId)
      .then((result) => {
        const reviewStages = result.filter((stage) => stage.stageCode !== 'FINAL');
        setStages(reviewStages);
        setStageId(reviewStages[0]?.id);
      })
      .catch((error) => showErrorMessage(error, '评审阶段加载失败'));
  }, [competitionId]);

  const loadCandidates = useCallback(async () => {
    if (!stageId) {
      setRecords([]);
      return;
    }
    setLoading(true);
    try {
      setRecords(await listCompetitionStageReviewCandidates(stageId));
    } catch (error) {
      showErrorMessage(error, '评审名单加载失败');
    } finally {
      setLoading(false);
    }
  }, [stageId]);

  useEffect(() => {
    void loadCandidates();
  }, [loadCandidates]);

  const updateRecord = useCallback((registrationId: number, patch: Partial<CompetitionStageReviewCandidateRecord>) => {
    setRecords((current) => current.map((record) => record.registrationId === registrationId ? { ...record, ...patch } : record));
  }, []);

  const saveRecord = useCallback(async (record: CompetitionStageReviewCandidateRecord) => {
    if (!stageId) return;
    setSavingId(record.registrationId);
    try {
      const saved = await saveCompetitionStageReviewDecision(stageId, record.registrationId, {
        score: record.score,
        decision: record.decision,
        comment: record.reviewComment || undefined,
      });
      updateRecord(record.registrationId, saved);
      message.success(record.decision === 'ADVANCED' ? '晋级结果已发布，决赛材料权限已开放' : '评审结果已保存');
    } catch (error) {
      showErrorMessage(error, '评审结果保存失败');
    } finally {
      setSavingId(undefined);
    }
  }, [stageId, updateRecord]);

  const columns = useMemo<ColumnsType<CompetitionStageReviewCandidateRecord>>(() => [
    { title: '报名编号', dataIndex: 'registrationNo', width: 210, fixed: 'left' },
    { title: '团队', dataIndex: 'teamName', width: 180 },
    { title: '项目', dataIndex: 'projectTitle', width: 220 },
    {
      title: '材料', dataIndex: 'submittedAt', width: 150,
      render: (value) => value ? <Tag color="success">已提交</Tag> : <Tag>未提交</Tag>,
    },
    {
      title: '评分', dataIndex: 'score', width: 130,
      render: (_, record) => (
        <InputNumber
          min={0}
          max={100}
          precision={2}
          value={record.score ?? undefined}
          onChange={(value) => updateRecord(record.registrationId, { score: value })}
        />
      ),
    },
    {
      title: '评审结论', dataIndex: 'decision', width: 150,
      render: (_, record) => (
        <Select
          style={{ width: 120 }}
          options={decisionOptions}
          value={record.decision}
          onChange={(value) => updateRecord(record.registrationId, { decision: value })}
        />
      ),
    },
    {
      title: '评审意见', dataIndex: 'reviewComment', width: 240,
      render: (_, record) => (
        <Input
          value={record.reviewComment || ''}
          maxLength={1000}
          placeholder="选填"
          onChange={(event) => updateRecord(record.registrationId, { reviewComment: event.target.value })}
        />
      ),
    },
    {
      title: '操作', key: 'actions', width: 120, fixed: 'right',
      render: (_, record) => (
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={savingId === record.registrationId}
          onClick={() => void saveRecord(record)}
        >
          保存
        </Button>
      ),
    },
  ], [saveRecord, savingId, updateRecord]);

  const applyPromotionRule = async () => {
    if (!stageId) return;
    setApplyingRule(true);
    try {
      setRecords(await applyCompetitionStagePromotionRule(stageId));
      message.success('已按赛事配置生成晋级名单；晋级边界同分项已保留待人工确认');
    } catch (error) {
      showErrorMessage(error, '晋级名单生成失败');
    } finally {
      setApplyingRule(false);
    }
  };

  return (
    <ManagementPage title="评审与晋级">
      <ManagementPageBody>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            showIcon
            type="info"
            message="评审结果与决赛材料权限联动"
            description="只有结论保存为“晋级”的团队，才能在决赛材料开放时间内修改材料；变更结论会立即同步权限。"
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
              <Typography.Text>评审阶段</Typography.Text>
              <Select
                style={{ width: 220 }}
                value={stageId}
                options={stages.map((item) => ({ label: item.stageName, value: item.id }))}
                onChange={setStageId}
              />
              <Button icon={<ReloadOutlined />} onClick={() => void loadCandidates()}>刷新</Button>
              <Button type="primary" loading={applyingRule} onClick={() => void applyPromotionRule()}>按规则生成晋级名单</Button>
            </Space>
          </Card>
          <Table
            rowKey="registrationId"
            loading={loading}
            columns={columns}
            dataSource={records}
            scroll={{ x: 1400 }}
            pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
          />
        </Space>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default CompetitionReviewPage;
