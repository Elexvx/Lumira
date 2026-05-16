import { PageContainer, ProCard, StepsForm } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Timeline,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useRefetchAll } from '@/hooks/useRefetchAll';
import { approvalService, type ApprovalInstancePayload, type ApprovalTemplatePayload } from '@/services/approval';
import type { ApprovalInstanceRecord, ApprovalTaskRecord, ApprovalTemplateRecord } from '@/types/api';
import { renderStatusTag } from '@/utils/statusTag';
import { buildTemplateEnabledColumn } from '@/utils/templateColumns';

const defaultNodes = [{ nodeName: '一级审批', sortOrder: 0, approvalPolicy: 'ANY_ONE', approverType: 'USER' as const, approverId: 1001 }];

const statusMeta: Record<string, { color: string; text: string }> = {
  PENDING: { color: 'processing', text: '审批中' },
  APPROVED: { color: 'success', text: '已通过' },
  REJECTED: { color: 'error', text: '已驳回' },
  CANCELLED: { color: 'default', text: '已撤回' },
};

const actionText: Record<string, string> = {
  SUBMIT: '提交审批',
  APPROVE: '审批通过',
  REJECT: '审批驳回',
  CANCEL: '撤回审批',
  FINISH: '审批完成',
};

const ApprovalsPage = () => {
  const [activeTab, setActiveTab] = useState('submitted');
  const [templateOpen, setTemplateOpen] = useState(false);
  const [submitOpen, setSubmitOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailInstanceId, setDetailInstanceId] = useState<number>();
  const [detailTaskId, setDetailTaskId] = useState<number>();
  const [approvalDraft, setApprovalDraft] = useState<Partial<ApprovalInstancePayload> & { templateId?: number; templateName?: string }>({});
  const [activeTemplate, setActiveTemplate] = useState<ApprovalTemplateRecord | null>(null);
  const [templateForm] = Form.useForm<ApprovalTemplatePayload>();
  const [handleForm] = Form.useForm<{ comment?: string }>();

  const templates = useQuery({ queryKey: ['approval-templates'], queryFn: () => approvalService.templates({ pageNo: 1, pageSize: 100 }) });
  const pending = useQuery({
    queryKey: ['approval-pending'],
    queryFn: () => approvalService.myPendingTasks({ pageNo: 1, pageSize: 100 }, { autoRedirectOnUnauthorized: false }),
  });
  const submitted = useQuery({ queryKey: ['approval-submitted'], queryFn: () => approvalService.instances({ scope: 'submitted', pageNo: 1, pageSize: 100 }) });
  const detail = useQuery({
    queryKey: ['approval-detail', detailInstanceId],
    queryFn: () => approvalService.instance(detailInstanceId as number),
    enabled: detailOpen && Boolean(detailInstanceId),
  });

  const enabledTemplates = useMemo(() => (templates.data?.records || []).filter((item) => item.enabled), [templates.data?.records]);
  const selectedTemplate = useMemo(
    () => enabledTemplates.find((item) => item.id === approvalDraft.templateId),
    [approvalDraft.templateId, enabledTemplates],
  );
  const pendingTask = useMemo(
    () => (detail.data?.tasks || []).find((item) => item.id === detailTaskId && item.status === 'PENDING'),
    [detail.data?.tasks, detailTaskId],
  );

  const refetchList = useRefetchAll([templates, pending, submitted]);
  const reload = () => {
    refetchList();
    if (detailInstanceId) {
      void detail.refetch();
    }
  };

  const openTemplate = (record?: ApprovalTemplateRecord) => {
    setActiveTemplate(record || null);
    templateForm.setFieldsValue(record ? { ...record, nodes: record.nodes?.length ? record.nodes : defaultNodes } : { nodes: defaultNodes });
    setTemplateOpen(true);
  };

  const saveTemplate = async () => {
    const values = await templateForm.validateFields();
    if (activeTemplate) {
      await approvalService.updateTemplate(activeTemplate.id, values);
    } else {
      await approvalService.createTemplate(values);
    }
    message.success('审批模板已保存');
    setTemplateOpen(false);
    reload();
  };

  const openSubmit = () => {
    setApprovalDraft({});
    setSubmitOpen(true);
  };

  const submitApproval = async () => {
    const payload: ApprovalInstancePayload = {
      businessType: approvalDraft.businessType || '',
      businessId: approvalDraft.businessId,
      businessTitle: approvalDraft.businessTitle || '',
      summary: approvalDraft.summary,
      payloadJson: approvalDraft.payloadJson,
    };
    await approvalService.createInstance(payload);
    message.success('审批已提交');
    setSubmitOpen(false);
    setActiveTab('submitted');
    reload();
  };

  const openDetail = (instanceId: number, taskId?: number) => {
    setDetailInstanceId(instanceId);
    setDetailTaskId(taskId);
    handleForm.resetFields();
    setDetailOpen(true);
  };

  const handleApproval = async (action: 'approve' | 'reject') => {
    if (!detailTaskId) {
      return;
    }
    const values = await handleForm.validateFields();
    if (action === 'approve') {
      await approvalService.approve(detailTaskId, values.comment);
      message.success('已通过审批');
    } else {
      await approvalService.reject(detailTaskId, values.comment);
      message.success('已驳回审批');
    }
    setDetailOpen(false);
    reload();
  };

  const cancelInstance = async (record: ApprovalInstanceRecord) => {
    await approvalService.cancel(record.id);
    message.success('审批已撤回');
    reload();
  };

  const instanceColumns = [
    { title: '审批标题', dataIndex: 'businessTitle' },
    { title: '业务类型', dataIndex: 'businessType', width: 160 },
    { title: '状态', dataIndex: 'status', width: 120, render: (value?: string | null) => renderStatusTag(value, statusMeta) },
    { title: '创建时间', dataIndex: 'createTime', width: 190 },
    {
      title: '操作',
      width: 160,
      render: (_: unknown, record: ApprovalInstanceRecord) => (
        <Space>
          <Button size="small" onClick={() => openDetail(record.id)}>查看</Button>
          {record.status === 'PENDING' ? (
            <Button size="small" danger onClick={() => cancelInstance(record)}>
              撤回
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="审批中心" extra={<Button type="primary" onClick={openSubmit}>新增审批</Button>}>
      <ProCard variant="outlined">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'submitted',
              label: `我发起的审批 (${submitted.data?.total || 0})`,
              children: (
                <Table<ApprovalInstanceRecord>
                  rowKey="id"
                  loading={submitted.isLoading}
                  dataSource={submitted.data?.records || []}
                  columns={instanceColumns}
                  pagination={false}
                  locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已提交审批" /> }}
                />
              ),
            },
            {
              key: 'pending',
              label: `待我审批 (${pending.data?.total || 0})`,
              children: (
                <Table<ApprovalTaskRecord>
                  rowKey="id"
                  loading={pending.isLoading}
                  dataSource={pending.data?.records || []}
                  pagination={false}
                  columns={[
                    { title: '审批标题', dataIndex: 'businessTitle', render: (value) => value || '-' },
                    { title: '业务类型', dataIndex: 'businessType', width: 160, render: (value) => value || '-' },
                    { title: '状态', dataIndex: 'status', width: 120, render: (value?: string | null) => renderStatusTag(value, statusMeta) },
                    { title: '创建时间', dataIndex: 'createTime', width: 190 },
                    {
                      title: '操作',
                      width: 120,
                      render: (_, record) => (
                        <Button size="small" type="primary" onClick={() => openDetail(record.instanceId, record.id)}>
                          查看/处理
                        </Button>
                      ),
                    },
                  ]}
                  locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批待办" /> }}
                />
              ),
            },
            {
              key: 'templates',
              label: '审批模板',
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Button type="primary" onClick={() => openTemplate()}>新建模板</Button>
                  <Table<ApprovalTemplateRecord>
                    rowKey="id"
                    loading={templates.isLoading}
                    dataSource={templates.data?.records || []}
                    pagination={false}
                    columns={[
                      { title: '模板名称', dataIndex: 'templateName' },
                      { title: '业务类型', dataIndex: 'businessType' },
                      buildTemplateEnabledColumn<ApprovalTemplateRecord>(approvalService.updateTemplateEnabled, reload),
                      { title: '操作', render: (_, record) => <Button size="small" onClick={() => openTemplate(record)}>编辑</Button> },
                    ]}
                  />
                </Space>
              ),
            },
          ]}
        />
      </ProCard>

      <Drawer title={activeTemplate ? '编辑审批模板' : '新建审批模板'} open={templateOpen} onClose={() => setTemplateOpen(false)} width={STANDARD_DRAWER_WIDTH} extra={<Button type="primary" onClick={saveTemplate}>保存</Button>}>
        <Form form={templateForm} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="businessType" label="业务类型" rules={[{ required: true }]}><Input placeholder="例如 PROJECT_CHANGE" /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item>
          <Form.List name="nodes">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item {...field} name={[field.name, 'nodeName']} rules={[{ required: true }]}><Input placeholder="节点名称" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'approverType']} rules={[{ required: true }]}><Select style={{ width: 140 }} options={[{ label: '用户', value: 'USER' }, { label: '角色', value: 'ROLE' }, { label: '部门', value: 'DEPARTMENT' }]} /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'approverId']} rules={[{ required: true }]}><InputNumber placeholder="审批人ID" /></Form.Item>
                    <Button onClick={() => remove(field.name)}>删除</Button>
                  </Space>
                ))}
                <Button onClick={() => add({ ...defaultNodes[0], nodeName: `审批节点${fields.length + 1}` })}>添加节点</Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Drawer>

      <Drawer title="新增审批" open={submitOpen} onClose={() => setSubmitOpen(false)} width={STANDARD_DRAWER_WIDTH} destroyOnHidden>
        <StepsForm
          formProps={{ layout: 'vertical' }}
          stepsProps={{ responsive: false }}
          onFinish={submitApproval}
          stepsFormRender={(formDom, submitterDom) => (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              {formDom}
              {submitterDom}
            </Space>
          )}
        >
          <StepsForm.StepForm
            name="template"
            title="选择模板"
            onFinish={async (values) => {
              const template = enabledTemplates.find((item) => item.id === values.templateId);
              setApprovalDraft((prev) => ({
                ...prev,
                templateId: template?.id,
                templateName: template?.templateName,
                businessType: template?.businessType,
              }));
              return true;
            }}
          >
            <Form.Item name="templateId" label="审批模板" rules={[{ required: true, message: '请选择审批模板' }]}>
              <Select
                loading={templates.isLoading}
                placeholder="请选择已启用的审批模板"
                options={enabledTemplates.map((item) => ({ label: `${item.templateName} (${item.businessType})`, value: item.id }))}
                notFoundContent="暂无已启用审批模板"
              />
            </Form.Item>
          </StepsForm.StepForm>
          <StepsForm.StepForm
            name="content"
            title="填写事项"
            onFinish={async (values) => {
              setApprovalDraft((prev) => ({ ...prev, ...values }));
              return true;
            }}
          >
            <Form.Item name="businessId" label="业务ID"><InputNumber style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="businessTitle" label="审批标题" rules={[{ required: true, message: '请输入审批标题' }]}><Input /></Form.Item>
            <Form.Item name="summary" label="摘要"><Input.TextArea rows={3} /></Form.Item>
            <Form.Item name="payloadJson" label="补充信息"><Input.TextArea rows={4} placeholder="可填写 JSON 或纯文本补充说明" /></Form.Item>
          </StepsForm.StepForm>
          <StepsForm.StepForm name="confirm" title="确认提交">
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="审批模板">{approvalDraft.templateName || selectedTemplate?.templateName || '-'}</Descriptions.Item>
              <Descriptions.Item label="业务类型">{approvalDraft.businessType || selectedTemplate?.businessType || '-'}</Descriptions.Item>
              <Descriptions.Item label="业务ID">{approvalDraft.businessId || '-'}</Descriptions.Item>
              <Descriptions.Item label="审批标题">{approvalDraft.businessTitle || '-'}</Descriptions.Item>
              <Descriptions.Item label="摘要">{approvalDraft.summary || '-'}</Descriptions.Item>
              <Descriptions.Item label="补充信息">{approvalDraft.payloadJson || '-'}</Descriptions.Item>
            </Descriptions>
          </StepsForm.StepForm>
        </StepsForm>
      </Drawer>

      <Drawer title="审批详情" open={detailOpen} onClose={() => setDetailOpen(false)} width={STANDARD_DRAWER_WIDTH} destroyOnHidden>
        {detail.isLoading ? (
          <ProCard loading />
        ) : detail.data ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="审批标题">{detail.data.businessTitle}</Descriptions.Item>
              <Descriptions.Item label="业务类型">{detail.data.businessType}</Descriptions.Item>
              <Descriptions.Item label="业务ID">{detail.data.businessId || '-'}</Descriptions.Item>
              <Descriptions.Item label="发起人">{detail.data.applicantName || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{renderStatusTag(detail.data.status, statusMeta)}</Descriptions.Item>
              <Descriptions.Item label="摘要">{detail.data.summary || '-'}</Descriptions.Item>
              <Descriptions.Item label="补充信息">{detail.data.payloadJson || '-'}</Descriptions.Item>
            </Descriptions>
            <ProCard title="流转记录" variant="outlined">
              {(detail.data.records || []).length ? (
                <Timeline
                  items={(detail.data.records || []).map((item) => ({
                    children: (
                      <Space direction="vertical" size={2}>
                        <Typography.Text>{`${actionText[item.action] || item.action} · ${item.operatorName || '-'}`}</Typography.Text>
                        {item.comment ? <Typography.Text type="secondary">{item.comment}</Typography.Text> : null}
                        <Typography.Text type="secondary">{item.createTime || '-'}</Typography.Text>
                      </Space>
                    ),
                  }))}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无流转记录" />
              )}
            </ProCard>
            {pendingTask ? (
              <ProCard title="审批处理" variant="outlined">
                <Form form={handleForm} layout="vertical">
                  <Form.Item name="comment" label="审批意见"><Input.TextArea rows={3} /></Form.Item>
                  <Space>
                    <Button type="primary" onClick={() => handleApproval('approve')}>通过</Button>
                    <Button danger onClick={() => handleApproval('reject')}>驳回</Button>
                  </Space>
                </Form>
              </ProCard>
            ) : null}
          </Space>
        ) : (
          <Empty description="审批详情不存在或无权查看" />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default ApprovalsPage;
