import {
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileProtectOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { Alert, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Segmented, Select, Space, Steps, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { DataTable } from '@/features/table/DataTable';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import {
  archiveCertificateTemplate,
  createCertificateTemplate,
  downloadCompetitionWorkspaceCertificate,
  downloadCertificate,
  duplicateCertificateTemplate,
  generateCompetitionWorkspaceCertificatesFromAwards,
  generateCertificatesFromAwards,
  grantCompetitionWorkspacePublishedAwards,
  grantPublishedAwards,
  listCompetitionWorkspaceAwardGrants,
  listCompetitionWorkspaceCertificateAwardSources,
  listCompetitionWorkspaceCertificateBatches,
  listCompetitionWorkspaceCertificates,
  listAwardGrants,
  listCertificateAwardSources,
  listCertificateTemplateVersions,
  listCertificateTemplates,
  listCertificates,
  publishCertificateTemplate,
  regenerateCompetitionWorkspaceCertificate,
  regenerateCertificate,
  revokeCompetitionWorkspaceCertificate,
  revokeCertificate,
  updateCertificateTemplate,
} from '@/services/certificates/api';
import type {
  CertificateBatchRecord,
  CertificateAwardGrant,
  CertificateAwardRule,
  CertificateAwardSource,
  CertificateRecord,
  CertificateTemplateRecord,
  CertificateTemplateVersionRecord,
} from '@/services/certificates/types';
import { message } from '@/theme/antdFeedbackBridge';
import { saveBlobAsFile } from '@/utils/download';
import { showErrorMessage } from '@/utils/errorMessage';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import {
  haveAwardGrantsChanged,
  selectableAwardGrantIds,
  summarizeAwardGrants,
  validateCertificateAwardRules,
} from './certificateAwardRules';
import {
  certificateWorkspaceSectionPath,
  type CertificateWorkspaceSection,
} from './certificateWorkspaceNavigation';
import './certificate.css';

const templateStatusColor: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  ARCHIVED: 'blue',
};

const certificateStatusColor: Record<string, string> = {
  GENERATING: 'processing',
  GENERATED: 'processing',
  ISSUED: 'green',
  FAILED: 'red',
  REVOKED: 'red',
  EXPIRED: 'orange',
};

const templateStatusText: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
};

const certificateStatusText: Record<string, string> = {
  GENERATING: '生成中',
  GENERATED: '已生成',
  ISSUED: '已签发',
  FAILED: '生成失败',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
};

const certificateBatchStatusText: Record<string, string> = {
  PENDING: '待生成',
  PROCESSING: '生成中',
  COMPLETED: '已完成',
  PARTIAL_FAILED: '部分失败',
  FAILED: '失败',
};

const certificateBatchStatusColor: Record<string, string> = {
  PENDING: 'default',
  PROCESSING: 'processing',
  COMPLETED: 'success',
  PARTIAL_FAILED: 'warning',
  FAILED: 'error',
};

const awardGrantStatusText: Record<string, string> = {
  GRANTED: '待制证',
  ISSUED: '已制证',
  REVOKED: '已取消',
};

const awardGrantStatusColor: Record<string, string> = {
  GRANTED: 'processing',
  ISSUED: 'success',
  REVOKED: 'default',
};

const sceneTypeText: Record<string, string> = {
  COMPETITION_AWARD: '赛事获奖',
  PARTICIPATION: '参与证明',
  CUSTOM: '自定义',
};

const CertificateWorkspaceNavigation = ({
  competitionUuid,
  active,
}: {
  competitionUuid: string;
  active: CertificateWorkspaceSection;
}) => (
  <Segmented<CertificateWorkspaceSection>
    className="competition-workspace-certificate-navigation"
    aria-label="证书功能"
    value={active}
    options={[
      { value: 'generate', label: '生成证书' },
      { value: 'batches', label: '生成批次' },
      { value: 'records', label: '证书记录' },
    ]}
    onChange={(section) => history.push(certificateWorkspaceSectionPath(competitionUuid, section))}
  />
);

const handleCertificateDownload = async (record: CertificateRecord, competitionUuid?: string) => {
  try {
    const blob = await (competitionUuid
      ? downloadCompetitionWorkspaceCertificate(competitionUuid, record.id)
      : downloadCertificate(record.id));
    saveBlobAsFile(blob, `${record.certificateNo}.png`);
  } catch (error) {
    showErrorMessage(error, '证书下载失败');
  }
};

const TemplateForm = ({ form: templateEditorForm }: { form: ReturnType<typeof Form.useForm>[0] }) => (
  <Form form={templateEditorForm} layout="vertical">
    <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
      <Input maxLength={128} placeholder="例如：2026 年度一等奖证书" />
    </Form.Item>
    <Form.Item name="templateCode" label="模板编码">
      <Input maxLength={64} placeholder="留空自动生成" />
    </Form.Item>
    <Form.Item name="sceneType" label="使用场景" initialValue="COMPETITION_AWARD" rules={[{ required: true }]}>
      <Select
        options={[
          { label: '赛事获奖', value: 'COMPETITION_AWARD' },
          { label: '参与证明', value: 'PARTICIPATION' },
          { label: '自定义', value: 'CUSTOM' },
        ]}
      />
    </Form.Item>
    <Form.Item name="description" label="模板说明">
      <Input.TextArea rows={4} maxLength={1000} showCount placeholder="说明模板适用的赛事、奖项或使用边界" />
    </Form.Item>
  </Form>
);

const certificateTemplateTableRequest = buildTableRequest<CertificateTemplateRecord>(async (params) =>
  listCertificateTemplates({
    keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
    sceneType: typeof params.sceneType === 'string' ? params.sceneType : undefined,
    status: typeof params.status === 'string' ? params.status : undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  }),
);

export const TemplatesManagementPage = () => {
  const actionRef = useRef<ActionType | null>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<CertificateTemplateRecord | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const closeDrawer = () => {
    setOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ sceneType: 'COMPETITION_AWARD' });
    setOpen(true);
  };

  const openEdit = useCallback((record: CertificateTemplateRecord) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  }, [form]);

  const saveTemplate = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateCertificateTemplate(editing.id, values);
      } else {
        await createCertificateTemplate(values);
      }
      message.success('证书模板已保存');
      closeDrawer();
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<ProColumns<CertificateTemplateRecord>[]>(
    () => [
      {
        title: '模板查询',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: { placeholder: '输入模板名称或模板编码' },
      },
      {
        title: '模板',
        dataIndex: 'templateName',
        search: false,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.templateName}</Typography.Text>
            <Typography.Text type="secondary" className="certificate-table-meta">
              {record.templateCode}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: '场景',
        dataIndex: 'sceneType',
        valueType: 'select',
        valueEnum: {
          COMPETITION_AWARD: { text: '赛事获奖' },
          PARTICIPATION: { text: '参与证明' },
          CUSTOM: { text: '自定义' },
        },
        width: 120,
        render: (_, record) => sceneTypeText[record.sceneType] || record.sceneType || '-',
      },
      {
        title: '版本',
        dataIndex: 'latestVersion',
        search: false,
        width: 88,
        render: (_, record) => `v${record.latestVersion || 1}`,
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          DRAFT: { text: '草稿' },
          PUBLISHED: { text: '已发布' },
          ARCHIVED: { text: '已归档' },
        },
        width: 112,
        render: (_, record) => <Tag color={templateStatusColor[record.status]}>{templateStatusText[record.status] || record.status}</Tag>,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        search: false,
        width: 176,
        render: (value) => value || '-',
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 248,
        align: 'right',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'designer',
                label: '设计',
                icon: <FileProtectOutlined />,
                permission: 'aiadc:certificate-template:update',
                onClick: () => history.push(`/certificates/templates/${record.id}/designer`),
              },
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                permission: 'aiadc:certificate-template:update',
                onClick: () => openEdit(record),
              },
              {
                key: 'publish',
                label: '发布',
                icon: <SendOutlined />,
                permission: 'aiadc:certificate-template:publish',
                onClick: async () => {
                  await publishCertificateTemplate(record.id);
                  message.success('模板版本已发布');
                  actionRef.current?.reload();
                },
              },
              {
                key: 'duplicate',
                label: '复制',
                icon: <CopyOutlined />,
                permission: 'aiadc:certificate-template:create',
                onClick: async () => {
                  await duplicateCertificateTemplate(record.id);
                  message.success('模板已复制');
                  actionRef.current?.reload();
                },
              },
              {
                key: 'archive',
                label: '归档',
                icon: <DeleteOutlined />,
                permission: 'aiadc:certificate-template:delete',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认归档该模板？',
                    content: `归档后「${record.templateName}」不能继续用于生成新证书。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await archiveCertificateTemplate(record.id);
                      message.success('模板已归档');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, openEdit, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="证书模板">
      <ManagementPageBody className="certificate-management-page">
        <ManagementTable<CertificateTemplateRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          request={certificateTemplateTableRequest}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'aiadc:certificate-template:create',
                value: (
                  <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    新建模板
                  </Button>
                ),
              },
            ])
          }
        />
      </ManagementPageBody>

      <ManagementDrawer
        title={editing ? '编辑证书模板' : '新建证书模板'}
        open={open}
        onClose={closeDrawer}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: closeDrawer },
          { key: 'save', label: '保存', type: 'primary', loading: saving, onClick: () => void saveTemplate() },
        ]}
      >
        <TemplateForm form={form} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

export const GenerateManagementPage = () => {
  const workspace = useOptionalCompetitionWorkspace();
  const workspaceUuid = workspace?.competitionUuid;
  const responsive = useResponsive();
  const [templates, setTemplates] = useState<CertificateTemplateRecord[]>([]);
  const [versions, setVersions] = useState<CertificateTemplateVersionRecord[]>([]);
  const [awardSources, setAwardSources] = useState<CertificateAwardSource[]>([]);
  const [result, setResult] = useState<CertificateRecord[]>([]);
  const [batchResult, setBatchResult] = useState<CertificateBatchRecord>();
  const [awardGrants, setAwardGrants] = useState<CertificateAwardGrant[]>([]);
  const [selectedGrantIds, setSelectedGrantIds] = useState<number[]>([]);
  const [awardSourcesLoading, setAwardSourcesLoading] = useState(true);
  const [awardGrantsLoading, setAwardGrantsLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [templateForm] = Form.useForm();
  const [awardForm] = Form.useForm();
  const watchedCompetitionId = Form.useWatch('competitionId', awardForm);
  const selectedCompetitionId = workspaceUuid ? awardSources[0]?.competitionId : watchedCompetitionId;
  const selectedStageId = Form.useWatch('stageId', awardForm);
  const selectedReviewBatchId = Form.useWatch('reviewBatchId', awardForm);

  useEffect(() => {
    void listCertificateTemplates({ status: 'PUBLISHED', pageSize: 100 }).then((res) => setTemplates(res.records || []));
    const sourcesRequest = workspaceUuid
      ? listCompetitionWorkspaceCertificateAwardSources(workspaceUuid)
      : listCertificateAwardSources();
    void sourcesRequest
      .then((sources) => setAwardSources(sources || []))
      .finally(() => setAwardSourcesLoading(false));
  }, [awardForm, workspaceUuid]);

  const competitionOptions = useMemo(
    () => Array.from(new Map(awardSources.map((source) => [
      source.competitionId,
      { label: source.competitionTitle, value: source.competitionId },
    ])).values()),
    [awardSources],
  );
  const stageOptions = useMemo(
    () => Array.from(new Map(
      awardSources
        .filter((source) => source.competitionId === selectedCompetitionId)
        .map((source) => [
          source.stageId,
          { label: source.stageName, value: source.stageId },
        ]),
    ).values()),
    [awardSources, selectedCompetitionId],
  );
  const batchOptions = useMemo(
    () => awardSources
      .filter((source) => source.competitionId === selectedCompetitionId && source.stageId === selectedStageId)
      .map((source) => ({
        label: `${source.batchName}（${source.batchNo}，${source.candidateCount} 个候选）`,
        value: source.reviewBatchId,
      })),
    [awardSources, selectedCompetitionId, selectedStageId],
  );
  const awardGrantSummary = useMemo(() => summarizeAwardGrants(awardGrants), [awardGrants]);

  const setLoadedAwardGrants = (grants: CertificateAwardGrant[]) => {
    setAwardGrants(grants);
    setSelectedGrantIds(selectableAwardGrantIds(grants));
  };

  const loadAwardGrants = async (reviewBatchId: number): Promise<CertificateAwardGrant[]> => {
    setAwardGrantsLoading(true);
    try {
      const grants = workspaceUuid
        ? await listCompetitionWorkspaceAwardGrants(workspaceUuid, reviewBatchId)
        : await listAwardGrants(reviewBatchId);
      setLoadedAwardGrants(grants);
      return grants;
    } finally {
      setAwardGrantsLoading(false);
    }
  };

  const refreshAwardSources = async () => {
    setAwardSources(await (workspaceUuid
      ? listCompetitionWorkspaceCertificateAwardSources(workspaceUuid)
      : listCertificateAwardSources()));
  };

  const loadVersions = async (templateId: number) => {
    templateForm.setFieldValue('templateVersionId', undefined);
    const list = await listCertificateTemplateVersions(templateId);
    const published = list.filter((item) => item.status === 'PUBLISHED');
    setVersions(published);
    if (published.length === 1) {
      templateForm.setFieldValue('templateVersionId', published[0].id);
    }
  };

  const confirmAwardGrants = async (
    reviewBatchId?: number,
    currentGrants: CertificateAwardGrant[] = awardGrants,
  ) => {
    const values = await awardForm.validateFields();
    const rules = values.awardRules as CertificateAwardRule[];
    const targetReviewBatchId = reviewBatchId ?? values.reviewBatchId;
    const existingIds = new Set(currentGrants.map((grant) => grant.id));
    setGenerating(true);
    setAwardGrantsLoading(true);
    try {
      const payload = {
        reviewBatchId: targetReviewBatchId,
        rules: rules.map((rule) => ({ ...rule, awardName: rule.awardName.trim() })),
      };
      const grants = await (workspaceUuid
        ? grantCompetitionWorkspacePublishedAwards(workspaceUuid, payload)
        : grantPublishedAwards(payload));
      const grantsChanged = haveAwardGrantsChanged(currentGrants, grants);
      setLoadedAwardGrants(grants);
      await refreshAwardSources();
      const addedCount = grants.filter((grant) => !existingIds.has(grant.id)).length;
      const summary = summarizeAwardGrants(grants);
      if (addedCount > 0) {
        message.success(`已新增 ${addedCount} 条授奖记录；待制证 ${summary.pending} 条`);
      } else if (grantsChanged) {
        message.success(`授奖规则已更新；待制证 ${summary.pending} 条，已制证 ${summary.issued} 条，已取消 ${summary.revoked} 条`);
      } else {
        message.info(`授奖规则已存在，未重复建档；待制证 ${summary.pending} 条，已制证 ${summary.issued} 条`);
      }
    } finally {
      setGenerating(false);
      setAwardGrantsLoading(false);
    }
  };

  const generateAwardBatch = async () => {
    const values = await templateForm.validateFields(['templateId', 'templateVersionId', 'batchName']);
    if (!selectedGrantIds.length) {
      message.warning('请选择尚未制证的授奖记录');
      return;
    }
    setGenerating(true);
    try {
      const payload = {
        batchName: values.batchName,
        templateId: values.templateId,
        templateVersionId: values.templateVersionId,
        grantIds: selectedGrantIds,
      };
      const response = await (workspaceUuid
        ? generateCompetitionWorkspaceCertificatesFromAwards(workspaceUuid, payload)
        : generateCertificatesFromAwards(payload));
      setResult(response.records || []);
      setBatchResult(response.batch);
      if (response.batch.failedCount > 0) {
        message.warning(`成功 ${response.batch.successCount} 张，失败 ${response.batch.failedCount} 张`);
      } else {
        message.success(`已从授奖记录生成 ${response.records.length} 张证书`);
      }
      const reviewBatchId = awardForm.getFieldValue('reviewBatchId');
      if (reviewBatchId) {
        await Promise.all([loadAwardGrants(reviewBatchId), refreshAwardSources()]);
      }
    } finally {
      setGenerating(false);
    }
  };

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspaceUuid)}
      title={workspaceUuid ? '证书生成' : '跨赛事证书生成'}
      extra={workspaceUuid ? <CertificateWorkspaceNavigation competitionUuid={workspaceUuid} active="generate" /> : undefined}
      showWorkspaceHeader={Boolean(workspaceUuid)}
      bodyClassName="certificate-generate-page"
      workspaceVariant="content"
    >
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={6}>
            <Card className="certificate-side-card">
              <Steps
                orientation={responsive.isMobile ? 'horizontal' : 'vertical'}
                current={result.length ? 3 : awardGrants.length ? 2 : 1}
                items={[
                  { title: '选择模板', content: '绑定发布版本' },
                  { title: '加载获奖数据', content: '按奖项规则自动匹配' },
                  { title: '预览确认', content: '核对获奖名单' },
                  { title: '生成结果', content: '下载或查验' },
                ]}
              />
            </Card>
          </Col>
          <Col xs={24} lg={18}>
            <Space orientation="vertical" size={16} className="certificate-workbench-stack">
              <Alert
                type="info"
                showIcon
                title="自动生成规则"
                description="选择已发布评审批次并设置奖项名次规则，系统会从评审发布结果自动匹配并加载获奖数据，无需手动录入或导入 CSV。生成时会绑定具体模板版本，后续模板修改不会影响已生成证书。"
              />
              <Card title="模板与批次" className="certificate-section-card">
                <Form form={templateForm} layout="vertical">
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="templateId" label="证书模板" rules={[{ required: true, message: '请选择证书模板' }]}>
                        <Select
                          showSearch
                          optionFilterProp="label"
                          options={templates.map((item) => ({ label: item.templateName, value: item.id }))}
                          onChange={(templateId) => void loadVersions(templateId)}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="templateVersionId" label="模板版本" rules={[{ required: true, message: '请选择模板版本' }]}>
                        <Select options={versions.map((item) => ({ label: `v${item.version}`, value: item.id }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="batchName" label="批次名称">
                        <Input placeholder="例如：2026 决赛获奖证书" />
                      </Form.Item>
                    </Col>
                  </Row>
                </Form>
              </Card>
              <Card title="自动授奖与制证" className="certificate-section-card">
                <Alert
                  type="info"
                  showIcon
                  title="评审发布结果是唯一数据源"
                  description="选定评审批次后，系统会自动加载已有授奖记录；首次使用会按下方奖项名次规则自动匹配获奖数据。每条发布结果只能生成一张有效证书。"
                  style={{ marginBottom: 16 }}
                />
                <Form
                  form={awardForm}
                  layout="vertical"
                  initialValues={{
                    awardRules: [
                      { awardName: '一等奖', minRank: 1, maxRank: 1 },
                      { awardName: '二等奖', minRank: 2, maxRank: 3 },
                      { awardName: '三等奖', minRank: 4, maxRank: 10 },
                    ],
                  }}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name="competitionId"
                        label="赛事"
                        hidden={Boolean(workspaceUuid)}
                        rules={workspaceUuid ? undefined : [{ required: true, message: '请选择赛事' }]}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          loading={awardSourcesLoading}
                          placeholder="选择已有发布结果的赛事"
                          options={competitionOptions}
                          onChange={() => {
                            awardForm.setFieldsValue({ stageId: undefined, reviewBatchId: undefined });
                            setLoadedAwardGrants([]);
                          }}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="stageId" label="阶段" rules={[{ required: true, message: '请选择阶段' }]}>
                        <Select
                          showSearch
                          optionFilterProp="label"
                          disabled={!selectedCompetitionId}
                          placeholder="选择赛事阶段"
                          options={stageOptions}
                          onChange={() => {
                            awardForm.setFieldValue('reviewBatchId', undefined);
                            setLoadedAwardGrants([]);
                          }}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="reviewBatchId" label="已发布评审批次" rules={[{ required: true, message: '请选择已发布评审批次' }]}>
                        <Select
                          showSearch
                          optionFilterProp="label"
                          disabled={!selectedStageId}
                          loading={awardSourcesLoading}
                          placeholder="选择评审批次"
                          options={batchOptions}
                          onChange={(reviewBatchId: number) => {
                            const source = awardSources.find((item) => item.reviewBatchId === reviewBatchId);
                            if (source && !templateForm.getFieldValue('batchName')) {
                              templateForm.setFieldValue('batchName', `${source.competitionTitle} - 获奖证书`);
                            }
                            void loadAwardGrants(reviewBatchId)
                              .then((grants) => {
                                if (!grants.length) {
                                  return confirmAwardGrants(reviewBatchId, grants);
                                }
                                return undefined;
                              })
                              .catch((error) => showErrorMessage(error, '获奖数据自动加载失败'));
                          }}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                  {!awardSourcesLoading && !awardSources.length && (
                    <Alert
                      type="warning"
                      showIcon
                      title="暂无可授奖的发布结果"
                      description="请先在评审管理中完成结果发布，再返回此处授奖。"
                      style={{ marginBottom: 16 }}
                    />
                  )}
                  <Typography.Title level={5}>奖项名次规则</Typography.Title>
                  <Form.List
                    name="awardRules"
                    rules={[{
                      validator: async (_, rules: CertificateAwardRule[]) => {
                        const error = validateCertificateAwardRules(rules);
                        if (error) {
                          throw new Error(error);
                        }
                      },
                    }]}
                  >
                    {(fields, { add, remove }, { errors }) => (
                      <>
                        {fields.map(({ key, ...field }) => (
                          <Row gutter={12} align="middle" key={key}>
                            <Col xs={24} md={9}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'awardName']}
                                label="奖项名称"
                                rules={[{ required: true, message: '请输入奖项名称' }]}
                              >
                                <Input maxLength={128} placeholder="例如：一等奖" />
                              </Form.Item>
                            </Col>
                            <Col xs={10} md={6}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'minRank']}
                                label="起始名次"
                                rules={[{ required: true, message: '请输入起始名次' }]}
                              >
                                <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} />
                              </Form.Item>
                            </Col>
                            <Col xs={10} md={6}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'maxRank']}
                                label="结束名次"
                                rules={[{ required: true, message: '请输入结束名次' }]}
                              >
                                <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} />
                              </Form.Item>
                            </Col>
                            <Col xs={4} md={3}>
                              <Button
                                aria-label="删除奖项规则"
                                danger
                                icon={<DeleteOutlined />}
                                disabled={fields.length === 1}
                                onClick={() => remove(field.name)}
                              />
                            </Col>
                          </Row>
                        ))}
                        <Form.ErrorList errors={errors} />
                        <Button
                          type="dashed"
                          icon={<PlusOutlined />}
                          onClick={() => add({ awardName: '', minRank: 1, maxRank: 1 })}
                          style={{ marginBottom: 16 }}
                        >
                          添加奖项规则
                        </Button>
                      </>
                    )}
                  </Form.List>
                  <Space wrap style={{ marginBottom: 16 }}>
                    <Button onClick={() => void confirmAwardGrants()} loading={generating}>重新应用奖项设置</Button>
                    <Button type="primary" onClick={() => void generateAwardBatch()} loading={generating}>
                      为已加载的获奖数据生成证书
                    </Button>
                  </Space>
                </Form>
                {selectedReviewBatchId && (
                  <Alert
                    type={awardGrantSummary.total === 0 ? 'info' : awardGrantSummary.pending > 0 ? 'warning' : 'success'}
                    showIcon
                    title={`授奖记录 ${awardGrantSummary.total} 条：待制证 ${awardGrantSummary.pending} 条，已制证 ${awardGrantSummary.issued} 条，已取消 ${awardGrantSummary.revoked} 条`}
                    description="重复应用相同规则不会重复建档；已制证记录不会被后续规则覆盖。"
                    style={{ marginBottom: 16 }}
                  />
                )}
                <DataTable<CertificateAwardGrant>
                  rowKey="id"
                  isMobile={responsive.isMobile}
                  size="small"
                  dataSource={awardGrants}
                  loading={awardGrantsLoading}
                  pagination={false}
                  scroll={{ x: 620 }}
                  rowSelection={{
                    selectedRowKeys: selectedGrantIds,
                    onChange: (keys) => setSelectedGrantIds(keys.map(Number)),
                    getCheckboxProps: (grant) => ({
                      disabled: grant.status !== 'GRANTED' || Boolean(grant.certificateRecordId),
                    }),
                  }}
                  columns={[
                    { title: '名次', dataIndex: 'rankNo', width: 72 },
                    { title: '获奖人', dataIndex: 'recipientName', width: 140 },
                    { title: '项目', dataIndex: 'projectName', ellipsis: true },
                    { title: '奖项', dataIndex: 'awardName', width: 140 },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      width: 100,
                      render: (value) => (
                        <Tag color={awardGrantStatusColor[value]}>
                          {awardGrantStatusText[value] || value}
                        </Tag>
                      ),
                    },
                  ]}
                />
              </Card>
              <Card title="生成结果" className="certificate-section-card">
                {batchResult?.failedCount ? (
                  <Alert
                    showIcon
                    type={batchResult.successCount ? 'warning' : 'error'}
                    title={`批次 ${batchResult.batchNo}：成功 ${batchResult.successCount}，失败 ${batchResult.failedCount}`}
                    description={batchResult.errorMessage || '证书生成未全部完成'}
                    style={{ marginBottom: 16 }}
                  />
                ) : null}
                <DataTable<CertificateRecord>
                  rowKey="id"
                  isMobile={responsive.isMobile}
                  size="middle"
                  dataSource={result}
                  pagination={false}
                  scroll={{ x: 860 }}
                  columns={[
                    { title: '证书编号', dataIndex: 'certificateNo', width: 180 },
                    { title: '获奖人/团队', dataIndex: 'recipientName', width: 160 },
                    { title: '奖项', dataIndex: 'awardName', width: 140, render: (value) => value || '-' },
                    { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={certificateStatusColor[value]}>{certificateStatusText[value] || value}</Tag> },
                    { title: '校验码', dataIndex: 'verificationCode', width: 120 },
                    {
                      title: '操作',
                      width: 180,
                      render: (_, record) => (
                        <Space>
                          <Button size="small" onClick={() => void handleCertificateDownload(record, workspaceUuid)} icon={<DownloadOutlined />}>
                            下载
                          </Button>
                          <Button size="small" onClick={() => navigator.clipboard.writeText(`${location.origin}/certificate/verify/${record.publicToken}`)}>
                            复制链接
                          </Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Card>
            </Space>
          </Col>
        </Row>
    </CompetitionWorkspacePageFrame>
  );
};

export const RecordsManagementPage = () => {
  const workspace = useOptionalCompetitionWorkspace();
  const workspaceUuid = workspace?.competitionUuid;
  const actionRef = useRef<ActionType | null>(null);
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const [detail, setDetail] = useState<CertificateRecord | null>(null);

  const columns = useMemo<ProColumns<CertificateRecord>[]>(
    () => [
      {
        title: '证书编号',
        dataIndex: 'certificateNo',
        width: 180,
        fieldProps: { placeholder: '输入证书编号' },
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{record.certificateNo}</Typography.Text>
            <Typography.Text type="secondary" className="certificate-table-meta">
              {record.verificationCode}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: '获奖人/团队',
        dataIndex: 'recipientName',
        width: 160,
        fieldProps: { placeholder: '输入姓名或团队' },
      },
      {
        title: '赛事',
        dataIndex: 'competitionTitle',
        search: false,
        ellipsis: true,
        render: (value) => value || '-',
      },
      {
        title: '项目',
        dataIndex: 'projectName',
        search: false,
        ellipsis: true,
        render: (value) => value || '-',
      },
      {
        title: '奖项',
        dataIndex: 'awardName',
        search: false,
        width: 140,
        render: (value) => value || '-',
      },
      {
        title: '模板',
        dataIndex: 'templateName',
        search: false,
        width: 160,
        render: (value) => value || '-',
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        valueEnum: {
          GENERATED: { text: '已生成' },
          ISSUED: { text: '已签发' },
          REVOKED: { text: '已撤销' },
          EXPIRED: { text: '已过期' },
        },
        width: 112,
        render: (_, record) => <Tag color={certificateStatusColor[record.status]}>{certificateStatusText[record.status] || record.status}</Tag>,
      },
      {
        title: '发证日期',
        dataIndex: 'issueDate',
        search: false,
        width: 120,
        render: (value) => value || '-',
      },
      {
        title: '创建时间',
        dataIndex: 'createdAt',
        search: false,
        width: 176,
        render: (value) => value || '-',
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 244,
        align: 'right',
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              {
                key: 'detail',
                label: '详情',
                icon: <EyeOutlined />,
                permission: 'aiadc:certificate:view',
                onClick: () => setDetail(record),
              },
              {
                key: 'download',
                label: '下载',
                icon: <DownloadOutlined />,
                permission: 'aiadc:certificate:download',
                onClick: () => handleCertificateDownload(record, workspaceUuid),
              },
              {
                key: 'copy',
                label: '复制链接',
                icon: <CopyOutlined />,
                permission: 'aiadc:certificate:view',
                onClick: () => {
                  navigator.clipboard.writeText(`${location.origin}/certificate/verify/${record.publicToken}`);
                  message.success('查询链接已复制');
                },
              },
              {
                key: 'regenerate',
                label: '重新生成',
                icon: <FileDoneOutlined />,
                permission: 'aiadc:certificate:regenerate',
                onClick: async () => {
                  await (workspaceUuid
                    ? regenerateCompetitionWorkspaceCertificate(workspaceUuid, record.id)
                    : regenerateCertificate(record.id));
                  message.success('证书已重新生成');
                  actionRef.current?.reload();
                },
              },
              {
                key: 'revoke',
                label: '撤销',
                icon: <DeleteOutlined />,
                permission: 'aiadc:certificate:revoke',
                danger: true,
                onClick: () => {
                  Modal.confirm({
                    title: '确认撤销该证书？',
                    content: `撤销后「${record.certificateNo}」公开查询会显示已撤销。`,
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      await (workspaceUuid
                        ? revokeCompetitionWorkspaceCertificate(workspaceUuid, record.id, '管理员撤销')
                        : revokeCertificate(record.id, '管理员撤销'));
                      message.success('证书已撤销');
                      actionRef.current?.reload();
                    },
                  });
                },
              },
            ])}
          />
        ),
      },
    ],
    [actionPermission, responsive.isDesktop, responsive.isMobile, workspaceUuid],
  );

  const tableRequest = useMemo(
    () => buildTableRequest<CertificateRecord>(async (params) => {
      const query = {
        certificateNo: typeof params.certificateNo === 'string' ? params.certificateNo : undefined,
        recipientName: typeof params.recipientName === 'string' ? params.recipientName : undefined,
        status: typeof params.status === 'string' ? params.status : undefined,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      };
      return workspaceUuid
        ? listCompetitionWorkspaceCertificates(workspaceUuid, query)
        : listCertificates(query);
    }),
    [workspaceUuid],
  );

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspaceUuid)}
      title={workspaceUuid ? '证书记录' : '全局证书记录'}
      extra={workspaceUuid ? <CertificateWorkspaceNavigation competitionUuid={workspaceUuid} active="records" /> : undefined}
      showWorkspaceHeader={Boolean(workspaceUuid)}
      bodyClassName="certificate-management-page"
      workspaceVariant="table"
    >
        <ManagementTable<CertificateRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          request={tableRequest}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      <ManagementDrawer title="证书详情" open={Boolean(detail)} onClose={() => setDetail(null)}>
        {detail ? (
          <Space orientation="vertical" size={16} className="certificate-detail">
            <Descriptions
              column={2}
              bordered
              items={[
                { key: 'no', label: '证书编号', children: detail.certificateNo },
                { key: 'code', label: '校验码', children: detail.verificationCode },
                { key: 'name', label: '获奖人/团队', children: detail.recipientName },
                { key: 'status', label: '状态', children: <Tag color={certificateStatusColor[detail.status]}>{certificateStatusText[detail.status] || detail.status}</Tag> },
                { key: 'competition', label: '赛事', children: detail.competitionTitle || '-' },
                { key: 'project', label: '项目', children: detail.projectName || '-' },
                { key: 'award', label: '奖项', children: detail.awardName || '-' },
                { key: 'date', label: '发证日期', children: detail.issueDate || '-' },
              ]}
            />
            {detail.certificateFileUrl ? <img className="certificate-detail__preview" src={normalizeUploadUrl(detail.certificateFileUrl)} alt="证书预览" /> : null}
            <Card size="small" title="渲染数据">
              <Input.TextArea rows={8} value={detail.dataJson || ''} readOnly />
            </Card>
          </Space>
        ) : null}
      </ManagementDrawer>
    </CompetitionWorkspacePageFrame>
  );
};

export const BatchesManagementPage = () => {
  const workspace = useOptionalCompetitionWorkspace();
  const workspaceUuid = workspace?.competitionUuid;
  const responsive = useResponsive();
  const actionRef = useRef<ActionType | null>(null);
  const tableRequest = useMemo(
    () => buildTableRequest<CertificateBatchRecord>(async (params) => {
      if (!workspaceUuid) {
        return { records: [], total: 0 };
      }
      return listCompetitionWorkspaceCertificateBatches(workspaceUuid, {
        status: typeof params.status === 'string' ? params.status : undefined,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      });
    }),
    [workspaceUuid],
  );
  const columns = useMemo<ProColumns<CertificateBatchRecord>[]>(() => [
    {
      title: '批次编号',
      dataIndex: 'batchNo',
      width: 180,
      search: false,
      render: (_, record) => <Typography.Text strong>{record.batchNo}</Typography.Text>,
    },
    {
      title: '批次名称',
      dataIndex: 'batchName',
      search: false,
      ellipsis: true,
      render: (value) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(certificateBatchStatusText).map(([status, text]) => [status, { text }]),
      ),
      width: 120,
      render: (_, record) => (
        <Tag color={certificateBatchStatusColor[record.status] || 'default'}>
          {certificateBatchStatusText[record.status] || record.status}
        </Tag>
      ),
    },
    { title: '总数', dataIndex: 'totalCount', search: false, width: 88 },
    { title: '成功', dataIndex: 'successCount', search: false, width: 88 },
    { title: '失败', dataIndex: 'failedCount', search: false, width: 88 },
    {
      title: '失败原因',
      dataIndex: 'errorMessage',
      search: false,
      ellipsis: true,
      render: (value) => value || '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', search: false, width: 176, render: (value) => value || '-' },
  ], []);

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspaceUuid)}
      title="证书批次"
      extra={workspaceUuid ? <CertificateWorkspaceNavigation competitionUuid={workspaceUuid} active="batches" /> : undefined}
      showWorkspaceHeader={Boolean(workspaceUuid)}
      bodyClassName="certificate-management-page"
      workspaceVariant="table"
    >
      {workspaceUuid ? (
        <ManagementTable<CertificateBatchRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          request={tableRequest}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      ) : <Alert showIcon type="warning" title="请从赛事工作空间进入证书批次。" />}
    </CompetitionWorkspacePageFrame>
  );
};
