import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getLocale } from '@umijs/max';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { normalizeLocale } from '@/i18n/locale';
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

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const CompetitionReviewPage = () => {
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [competitionId, setCompetitionId] = useState<number>();
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [stageId, setStageId] = useState<number>();
  const [records, setRecords] = useState<CompetitionStageReviewCandidateRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState<number>();
  const [applyingRule, setApplyingRule] = useState(false);
  const decisionOptions = [
    { label: t('待评审', 'Pending review'), value: 'PENDING' },
    { label: t('晋级', 'Advanced'), value: 'ADVANCED' },
    { label: t('未晋级', 'Eliminated'), value: 'ELIMINATED' },
  ];

  useEffect(() => {
    void listCompetitions({ pageNo: 1, pageSize: 100 })
      .then((result) => {
        setCompetitions(result.records || []);
        setCompetitionId(result.records?.[0]?.id);
      })
      .catch((error) => showErrorMessage(error, t('赛事列表加载失败', 'Failed to load competitions')));
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
      .catch((error) => showErrorMessage(error, t('评审阶段加载失败', 'Failed to load review stages')));
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
      showErrorMessage(error, t('评审名单加载失败', 'Failed to load review candidates'));
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
      message.success(record.decision === 'ADVANCED'
        ? t('晋级结果已发布，决赛材料权限已开放', 'Advancement published; final-stage material access is now available')
        : t('评审结果已保存', 'Review result saved'));
    } catch (error) {
      showErrorMessage(error, t('评审结果保存失败', 'Failed to save the review result'));
    } finally {
      setSavingId(undefined);
    }
  }, [stageId, updateRecord]);

  const columns = useMemo<ColumnsType<CompetitionStageReviewCandidateRecord>>(() => [
    { title: t('报名编号', 'Registration No.'), dataIndex: 'registrationNo', width: 210, fixed: 'left' },
    { title: t('团队', 'Team'), dataIndex: 'teamName', width: 180 },
    { title: t('项目', 'Project'), dataIndex: 'projectTitle', width: 220 },
    {
      title: t('材料', 'Materials'), dataIndex: 'submittedAt', width: 150,
      render: (value) => value ? <Tag color="success">{t('已提交', 'Submitted')}</Tag> : <Tag>{t('未提交', 'Not submitted')}</Tag>,
    },
    {
      title: t('评分', 'Score'), dataIndex: 'score', width: 130,
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
      title: t('评审结论', 'Decision'), dataIndex: 'decision', width: 150,
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
      title: t('评审意见', 'Comments'), dataIndex: 'reviewComment', width: 240,
      render: (_, record) => (
        <Input
          value={record.reviewComment || ''}
          maxLength={1000}
          placeholder={t('选填', 'Optional')}
          onChange={(event) => updateRecord(record.registrationId, { reviewComment: event.target.value })}
        />
      ),
    },
    {
      title: t('操作', 'Actions'), key: 'actions', width: 120, fixed: 'right',
      render: (_, record) => (
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={savingId === record.registrationId}
          onClick={() => void saveRecord(record)}
        >
          {t('保存', 'Save')}
        </Button>
      ),
    },
  ], [decisionOptions, saveRecord, savingId, updateRecord]);

  const applyPromotionRule = async () => {
    if (!stageId) return;
    setApplyingRule(true);
    try {
      setRecords(await applyCompetitionStagePromotionRule(stageId));
      message.success(t(
        '已按赛事配置生成晋级名单；晋级边界同分项已保留待人工确认',
        'The advancement list was generated from the competition rules; ties at the cutoff remain for manual confirmation',
      ));
    } catch (error) {
      showErrorMessage(error, t('晋级名单生成失败', 'Failed to generate the advancement list'));
    } finally {
      setApplyingRule(false);
    }
  };

  return (
    <ManagementPage title={t('评审与晋级', 'Review & Advancement')}>
      <ManagementPageBody>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            showIcon
            type="info"
            message={t('评审结果与决赛材料权限联动', 'Review results control final-stage material access')}
            description={t(
              '只有结论保存为“晋级”的团队，才能在决赛材料开放时间内修改材料；变更结论会立即同步权限。',
              'Only teams saved as “Advanced” can edit materials during the final-stage submission window. Permission changes take effect immediately.',
            )}
          />
          <Card>
            <Space wrap>
              <Typography.Text>{t('赛事', 'Competition')}</Typography.Text>
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 300 }}
                value={competitionId}
                options={competitions.map((item) => ({ label: item.title, value: item.id }))}
                onChange={setCompetitionId}
              />
              <Typography.Text>{t('评审阶段', 'Review stage')}</Typography.Text>
              <Select
                style={{ width: 220 }}
                value={stageId}
                options={stages.map((item) => ({ label: item.stageName, value: item.id }))}
                onChange={setStageId}
              />
              <Button icon={<ReloadOutlined />} onClick={() => void loadCandidates()}>{t('刷新', 'Refresh')}</Button>
              <Button type="primary" loading={applyingRule} onClick={() => void applyPromotionRule()}>{t('按规则生成晋级名单', 'Generate advancement list')}</Button>
            </Space>
          </Card>
          <Table
            rowKey="registrationId"
            loading={loading}
            columns={columns}
            dataSource={records}
            scroll={{ x: 1400 }}
            pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => t(`共 ${total} 条`, `${total} items`) }}
          />
        </Space>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default CompetitionReviewPage;
