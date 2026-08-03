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
import { Alert, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Select, Space, Steps, Table, Tag, Typography, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { TableActionBar } from '@/features/table/TableActionBar';
import { useResponsive } from '@/hooks/useResponsive';
import {
  archiveCertificateTemplate,
  createCertificateTemplate,
  downloadCertificate,
  duplicateCertificateTemplate,
  generateCertificatesFromAwards,
  generateCertificates,
  grantPublishedAwards,
  listAwardGrants,
  listCertificateAwardSources,
  listCertificateTemplateVersions,
  listCertificateTemplates,
  listCertificates,
  publishCertificateTemplate,
  regenerateCertificate,
  revokeCertificate,
  updateCertificateTemplate,
} from '@/services/certificates/api';
import type {
  CertificateBatchRecord,
  CertificateAwardGrant,
  CertificateAwardRule,
  CertificateAwardSource,
  CertificateDataPayload,
  CertificateGeneratePayload,
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

const handleCertificateDownload = async (record: CertificateRecord) => {
  try {
    const blob = await downloadCertificate(record.id);
    saveBlobAsFile(blob, `${record.certificateNo}.png`);
  } catch (error) {
    showErrorMessage(error, '证书下载失败');
  }
};

const csvToRows = (text: string): CertificateDataPayload[] => {
  const [headerLine, ...lines] = text.split(/\r?\n/).filter(Boolean);
  if (!headerLine) {
    return [];
  }

  const headers = headerLine.split(',').map((item) => item.trim());
  return lines
    .map((line) => {
      const values = line.split(',');
      const data: Record<string, string> = {};
      headers.forEach((header, index) => {
        data[header] = values[index]?.trim() || '';
      });
      return {
        recipientName: data.recipientName || data.name || data.姓名 || data.获奖人 || data.团队 || '',
        competitionTitle: data.competitionTitle || data.赛事 || data.赛事名称 || '',
        projectName: data.projectName || data.项目 || data.项目名称 || '',
        teamName: data.teamName || data.团队 || data.团队名称 || '',
        awardName: data.awardName || data.奖项 || data.奖项名称 || '',
        issueDate: data.issueDate || data.日期 || data.发证日期 || undefined,
        recipientType: 'CUSTOM',
        data,
      };
    })
    .filter((item) => item.recipientName);
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
          <Space direction="vertical" size={0}>
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
          request={async (params) => {
            const response = await listCertificateTemplates({
              keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
              sceneType: typeof params.sceneType === 'string' ? params.sceneType : undefined,
              status: typeof params.status === 'string' ? params.status : undefined,
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return { data: response.records, total: response.total, success: true };
          }}
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
  const [manualForm] = Form.useForm();
  const [awardForm] = Form.useForm();
  const selectedCompetitionId = Form.useWatch('competitionId', awardForm);
  const selectedStageId = Form.useWatch('stageId', awardForm);
  const selectedReviewBatchId = Form.useWatch('reviewBatchId', awardForm);

  useEffect(() => {
    void listCertificateTemplates({ status: 'PUBLISHED', pageSize: 100 }).then((res) => setTemplates(res.records || []));
    void listCertificateAwardSources()
      .then((sources) => setAwardSources(sources || []))
      .finally(() => setAwardSourcesLoading(false));
  }, []);

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

  const loadAwardGrants = async (reviewBatchId: number) => {
    setAwardGrantsLoading(true);
    try {
      setLoadedAwardGrants(await listAwardGrants(reviewBatchId));
    } finally {
      setAwardGrantsLoading(false);
    }
  };

  const refreshAwardSources = async () => {
    setAwardSources(await listCertificateAwardSources());
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

  const buildSinglePayload = async (): Promise<CertificateGeneratePayload> => {
    const [templateValues, values] = await Promise.all([
      templateForm.validateFields(),
      manualForm.validateFields(),
    ]);
    return {
      batchName: templateValues.batchName,
      templateId: templateValues.templateId,
      templateVersionId: templateValues.templateVersionId,
      sourceType: 'MANUAL',
      records: [
        {
          recipientName: values.recipientName,
          recipientType: 'CUSTOM',
          competitionTitle: values.competitionTitle,
          projectName: values.projectName,
          teamName: values.teamName,
          awardName: values.awardName,
          issueDate: values.issueDate,
        },
      ],
    };
  };

  const submit = async () => {
    setGenerating(true);
    try {
      const response = await generateCertificates(await buildSinglePayload());
      setResult(response.records || []);
      setBatchResult(response.batch);
      if (response.batch.failedCount > 0) {
        message.error(response.batch.errorMessage || '证书生成失败，请查看批次结果');
      } else {
        message.success('证书已生成');
      }
    } finally {
      setGenerating(false);
    }
  };

  const confirmAwardGrants = async () => {
    const values = await awardForm.validateFields();
    const rules = values.awardRules as CertificateAwardRule[];
    const existingIds = new Set(awardGrants.map((grant) => grant.id));
    setGenerating(true);
    try {
      const grants = await grantPublishedAwards({
        reviewBatchId: values.reviewBatchId,
        rules: rules.map((rule) => ({ ...rule, awardName: rule.awardName.trim() })),
      });
      const grantsChanged = haveAwardGrantsChanged(awardGrants, grants);
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
      const response = await generateCertificatesFromAwards({
        batchName: values.batchName,
        templateId: values.templateId,
        templateVersionId: values.templateVersionId,
        grantIds: selectedGrantIds,
      });
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

  const csvUpload: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      const values = await templateForm.validateFields(['templateId', 'templateVersionId']);
      const rows = csvToRows(await file.text());
      if (!rows.length) {
        message.warning('CSV 中未识别到可生成的证书数据');
        return false;
      }
      setGenerating(true);
      try {
        const response = await generateCertificates({
          batchName: file.name,
          templateId: values.templateId,
          templateVersionId: values.templateVersionId,
          sourceType: 'IMPORT',
          records: rows,
        });
        setResult(response.records || []);
        setBatchResult(response.batch);
        if (response.batch.failedCount > 0) {
          message.warning(
            `成功 ${response.batch.successCount} 张，失败 ${response.batch.failedCount} 张：${response.batch.errorMessage || '请查看批次结果'}`,
          );
        } else {
          message.success(`已导入生成 ${response.records.length} 张证书`);
        }
      } finally {
        setGenerating(false);
      }
      return false;
    },
  };

  return (
    <ManagementPage title="证书生成">
      <ManagementPageBody className="certificate-generate-page">
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={6}>
            <Card className="certificate-side-card">
              <Steps
                direction={responsive.isMobile ? 'horizontal' : 'vertical'}
                current={result.length ? 3 : 1}
                items={[
                  { title: '选择模板', description: '绑定发布版本' },
                  { title: '录入数据', description: '手动或 CSV' },
                  { title: '预览确认', description: '核对变量字段' },
                  { title: '生成结果', description: '下载或查验' },
                ]}
              />
            </Card>
          </Col>
          <Col xs={24} lg={18}>
            <Space direction="vertical" size={16} className="certificate-workbench-stack">
              <Alert
                type="info"
                showIcon
                message="生成规则"
                description="第一阶段支持手动录入单张证书和 CSV 批量导入。生成时会绑定具体模板版本，后续模板修改不会影响已生成证书。"
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
              <Card title="评审结果授奖与制证" className="certificate-section-card">
                <Alert
                  type="info"
                  showIcon
                  message="先确认授奖，再从授奖记录生成证书；每条发布结果只能生成一张有效证书。"
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
                      <Form.Item name="competitionId" label="赛事" rules={[{ required: true, message: '请选择赛事' }]}>
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
                            void loadAwardGrants(reviewBatchId);
                          }}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                  {!awardSourcesLoading && !awardSources.length && (
                    <Alert
                      type="warning"
                      showIcon
                      message="暂无可授奖的发布结果"
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
                        {fields.map((field) => (
                          <Row gutter={12} align="middle" key={field.key}>
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
                    <Button onClick={() => void confirmAwardGrants()} loading={generating}>应用规则并加载授奖记录</Button>
                    <Button type="primary" onClick={() => void generateAwardBatch()} loading={generating}>
                      为所选授奖记录生成证书
                    </Button>
                  </Space>
                </Form>
                {selectedReviewBatchId && (
                  <Alert
                    type={awardGrantSummary.total === 0 ? 'info' : awardGrantSummary.pending > 0 ? 'warning' : 'success'}
                    showIcon
                    message={`授奖记录 ${awardGrantSummary.total} 条：待制证 ${awardGrantSummary.pending} 条，已制证 ${awardGrantSummary.issued} 条，已取消 ${awardGrantSummary.revoked} 条`}
                    description="重复应用相同规则不会重复建档；已制证记录不会被后续规则覆盖。"
                    style={{ marginBottom: 16 }}
                  />
                )}
                <Table<CertificateAwardGrant>
                  rowKey="id"
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
              <Card
                title="证书数据"
                extra={
                  <Upload {...csvUpload}>
                    <Button icon={<FileDoneOutlined />} loading={generating}>
                      导入 CSV 生成
                    </Button>
                  </Upload>
                }
                className="certificate-section-card"
              >
                <Form form={manualForm} layout="vertical">
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="recipientName" label="获奖人/团队" rules={[{ required: true, message: '请输入获奖人或团队名称' }]}>
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="competitionTitle" label="赛事名称">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="awardName" label="奖项">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="projectName" label="项目名称">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="teamName" label="团队名称">
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="issueDate" label="发证日期">
                        <Input placeholder="YYYY-MM-DD" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Space>
                    <Button type="primary" icon={<SendOutlined />} loading={generating} onClick={() => void submit()}>
                      生成单张证书
                    </Button>
                    <Button onClick={() => manualForm.resetFields()}>清空数据</Button>
                  </Space>
                </Form>
              </Card>
              <Card title="生成结果" className="certificate-section-card">
                {batchResult?.failedCount ? (
                  <Alert
                    showIcon
                    type={batchResult.successCount ? 'warning' : 'error'}
                    message={`批次 ${batchResult.batchNo}：成功 ${batchResult.successCount}，失败 ${batchResult.failedCount}`}
                    description={batchResult.errorMessage || '证书生成未全部完成'}
                    style={{ marginBottom: 16 }}
                  />
                ) : null}
                <Table<CertificateRecord>
                  rowKey="id"
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
                          <Button size="small" onClick={() => void handleCertificateDownload(record)} icon={<DownloadOutlined />}>
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
      </ManagementPageBody>
    </ManagementPage>
  );
};

export const RecordsManagementPage = () => {
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
          <Space direction="vertical" size={0}>
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
                onClick: () => handleCertificateDownload(record),
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
                  await regenerateCertificate(record.id);
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
                      await revokeCertificate(record.id, '管理员撤销');
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
    [actionPermission, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="证书记录">
      <ManagementPageBody className="certificate-management-page">
        <ManagementTable<CertificateRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          request={async (params) => {
            const response = await listCertificates({
              certificateNo: typeof params.certificateNo === 'string' ? params.certificateNo : undefined,
              recipientName: typeof params.recipientName === 'string' ? params.recipientName : undefined,
              status: typeof params.status === 'string' ? params.status : undefined,
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return { data: response.records, total: response.total, success: true };
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </ManagementPageBody>
      <ManagementDrawer title="证书详情" open={Boolean(detail)} onClose={() => setDetail(null)} width={760}>
        {detail ? (
          <Space direction="vertical" size={16} className="certificate-detail">
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
    </ManagementPage>
  );
};
