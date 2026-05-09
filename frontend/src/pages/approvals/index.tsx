import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Empty, Form, Input, InputNumber, Select, Space, Switch, Table, Tabs, Tag, message } from 'antd';
import { useState } from 'react';
import { approvalService, type ApprovalTemplatePayload } from '@/services/approval';
import type { ApprovalInstanceRecord, ApprovalTaskRecord, ApprovalTemplateRecord } from '@/types/api';

const defaultNodes = [{ nodeName: '一级审批', sortOrder: 0, approvalPolicy: 'ANY_ONE', approverType: 'USER' as const, approverId: 1001 }];

const ApprovalsPage = () => {
  const [templateOpen, setTemplateOpen] = useState(false);
  const [submitOpen, setSubmitOpen] = useState(false);
  const [activeTemplate, setActiveTemplate] = useState<ApprovalTemplateRecord | null>(null);
  const [templateForm] = Form.useForm<ApprovalTemplatePayload>();
  const [submitForm] = Form.useForm();
  const templates = useQuery({ queryKey: ['approval-templates'], queryFn: () => approvalService.templates({ pageNo: 1, pageSize: 100 }) });
  const pending = useQuery({ queryKey: ['approval-pending'], queryFn: () => approvalService.myPendingTasks({ pageNo: 1, pageSize: 100 }, { autoRedirectOnUnauthorized: false }) });
  const submitted = useQuery({ queryKey: ['approval-submitted'], queryFn: () => approvalService.instances({ scope: 'submitted', pageNo: 1, pageSize: 100 }) });
  const all = useQuery({ queryKey: ['approval-all'], queryFn: () => approvalService.instances({ pageNo: 1, pageSize: 100 }) });

  const reload = () => {
    void templates.refetch(); void pending.refetch(); void submitted.refetch(); void all.refetch();
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

  const submitApproval = async () => {
    await approvalService.createInstance(await submitForm.validateFields());
    message.success('审批已提交');
    setSubmitOpen(false);
    reload();
  };

  const instanceColumns = [
    { title: '标题', dataIndex: 'businessTitle' },
    { title: '业务类型', dataIndex: 'businessType', width: 160 },
    { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <Tag>{value}</Tag> },
    { title: '发起人', dataIndex: 'applicantName', width: 120 },
    { title: '创建时间', dataIndex: 'createTime', width: 190 },
  ];

  return (
    <PageContainer title="审批中心" extra={<Button type="primary" onClick={() => setSubmitOpen(true)}>发起审批</Button>}>
      <ProCard variant="outlined">
        <Tabs
          items={[
            {
              key: 'pending',
              label: `我的待办 (${pending.data?.total || 0})`,
              children: (
                <Table<ApprovalTaskRecord>
                  rowKey="id"
                  loading={pending.isLoading}
                  dataSource={pending.data?.records || []}
                  pagination={false}
                  columns={[
                    { title: '任务ID', dataIndex: 'id', width: 100 },
                    { title: '实例ID', dataIndex: 'instanceId', width: 100 },
                    { title: '状态', dataIndex: 'status', render: (value) => <Tag color="orange">{value}</Tag> },
                    {
                      title: '操作',
                      width: 180,
                      render: (_, record) => (
                        <Space>
                          <Button size="small" type="primary" onClick={async () => { await approvalService.approve(record.id, '同意'); message.success('已通过'); reload(); }}>通过</Button>
                          <Button size="small" danger onClick={async () => { await approvalService.reject(record.id, '驳回'); message.success('已驳回'); reload(); }}>驳回</Button>
                        </Space>
                      ),
                    },
                  ]}
                  locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审批待办" /> }}
                />
              ),
            },
            { key: 'submitted', label: '我发起的', children: <Table<ApprovalInstanceRecord> rowKey="id" loading={submitted.isLoading} dataSource={submitted.data?.records || []} columns={instanceColumns} pagination={false} /> },
            { key: 'handled', label: '已处理', children: <Table<ApprovalInstanceRecord> rowKey="id" loading={all.isLoading} dataSource={(all.data?.records || []).filter((item) => item.status !== 'PENDING')} columns={instanceColumns} pagination={false} /> },
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
                      { title: '启用', dataIndex: 'enabled', render: (value, record) => <Switch checked={value} onChange={(checked) => approvalService.updateTemplateEnabled(record.id, checked).then(reload)} /> },
                      { title: '操作', render: (_, record) => <Button size="small" onClick={() => openTemplate(record)}>编辑</Button> },
                    ]}
                  />
                </Space>
              ),
            },
          ]}
        />
      </ProCard>

      <Drawer title={activeTemplate ? '编辑审批模板' : '新建审批模板'} open={templateOpen} onClose={() => setTemplateOpen(false)} width={720} extra={<Button type="primary" onClick={saveTemplate}>保存</Button>}>
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

      <Drawer title="发起审批" open={submitOpen} onClose={() => setSubmitOpen(false)} width={520} extra={<Button type="primary" onClick={submitApproval}>提交</Button>}>
        <Form form={submitForm} layout="vertical">
          <Form.Item name="businessType" label="业务类型" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="businessId" label="业务ID"><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="businessTitle" label="审批标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="summary" label="摘要"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default ApprovalsPage;
