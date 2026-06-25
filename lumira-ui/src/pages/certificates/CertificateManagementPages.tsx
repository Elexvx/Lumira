import {
  AuditOutlined,
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
import { Alert, Button, Card, Col, Descriptions, Form, Input, Modal, Row, Select, Space, Steps, Table, Tag, Typography, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
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
  duplicateCertificateTemplate,
  generateCertificates,
  listCertificateTemplateVersions,
  listCertificateTemplates,
  listCertificates,
  publishCertificateTemplate,
  regenerateCertificate,
  revokeCertificate,
  updateCertificateTemplate,
} from '@/services/certificates/api';
import type {
  CertificateDataPayload,
  CertificateGeneratePayload,
  CertificateRecord,
  CertificateTemplateRecord,
  CertificateTemplateVersionRecord,
} from '@/services/certificates/types';
import { message } from '@/theme/antdFeedbackBridge';
import { normalizeUploadUrl } from '@/utils/uploadUrl';
import './certificate.css';

const templateStatusColor: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  ARCHIVED: 'blue',
};

const certificateStatusColor: Record<string, string> = {
  GENERATED: 'processing',
  ISSUED: 'green',
  REVOKED: 'red',
  EXPIRED: 'orange',
};

const templateStatusText: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
};

const certificateStatusText: Record<string, string> = {
  GENERATED: '已生成',
  ISSUED: '已签发',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
};

const sceneTypeText: Record<string, string> = {
  COMPETITION_AWARD: '赛事获奖',
  PARTICIPATION: '参与证明',
  CUSTOM: '自定义',
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

const TemplateForm = ({ form }: { form: ReturnType<typeof Form.useForm>[0] }) => (
  <Form form={form} layout="vertical">
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

  const openEdit = (record: CertificateTemplateRecord) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

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
    [actionPermission, responsive.isDesktop, responsive.isMobile],
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
  const [result, setResult] = useState<CertificateRecord[]>([]);
  const [generating, setGenerating] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    void listCertificateTemplates({ status: 'PUBLISHED', pageSize: 100 }).then((res) => setTemplates(res.records || []));
  }, []);

  const loadVersions = async (templateId: number) => {
    form.setFieldValue('templateVersionId', undefined);
    const list = await listCertificateTemplateVersions(templateId);
    const published = list.filter((item) => item.status === 'PUBLISHED');
    setVersions(published);
    if (published.length === 1) {
      form.setFieldValue('templateVersionId', published[0].id);
    }
  };

  const buildSinglePayload = async (): Promise<CertificateGeneratePayload> => {
    const values = await form.validateFields();
    return {
      batchName: values.batchName,
      templateId: values.templateId,
      templateVersionId: values.templateVersionId,
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
      message.success('证书已生成');
    } finally {
      setGenerating(false);
    }
  };

  const csvUpload: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      const values = await form.validateFields(['templateId', 'templateVersionId']);
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
        message.success(`已导入生成 ${response.records.length} 张证书`);
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
                <Form form={form} layout="vertical">
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
                <Form form={form} layout="vertical">
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
                    <Button onClick={() => form.resetFields()}>清空数据</Button>
                  </Space>
                </Form>
              </Card>
              <Card title="生成结果" className="certificate-section-card">
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
                          <Button size="small" href={`/api/v2/aiadc/certificates/${record.id}/download`} icon={<DownloadOutlined />}>
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
                onClick: () => window.open(`/api/v2/aiadc/certificates/${record.id}/download`, '_blank'),
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
