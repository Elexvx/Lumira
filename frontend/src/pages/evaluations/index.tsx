import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Drawer, Empty, Form, Input, InputNumber, Select, Space, Table, Tabs, message } from 'antd';
import { useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useRefetchAll } from '@/hooks/useRefetchAll';
import { evaluationService, type EvaluationTemplatePayload } from '@/services/evaluation';
import type { EvaluationInstanceRecord, EvaluationScoreTaskRecord, EvaluationTemplateRecord } from '@/types/api';
import { renderStatusTag } from '@/utils/statusTag';
import { buildTemplateEnabledColumn } from '@/utils/templateColumns';

const defaultDimensions = [{ dimensionName: '交付质量', weight: 40, maxScore: 100, sortOrder: 0 }];
const defaultGradeRules = [
  { gradeCode: 'A', gradeName: '优秀', minScore: 90, maxScore: 100 },
  { gradeCode: 'B', gradeName: '良好', minScore: 80, maxScore: 89 },
  { gradeCode: 'C', gradeName: '合格', minScore: 60, maxScore: 79 },
];

const EvaluationsPage = () => {
  const [templateOpen, setTemplateOpen] = useState(false);
  const [instanceOpen, setInstanceOpen] = useState(false);
  const [activeTemplate, setActiveTemplate] = useState<EvaluationTemplateRecord | null>(null);
  const [templateForm] = Form.useForm<EvaluationTemplatePayload>();
  const [instanceForm] = Form.useForm();
  const templates = useQuery({ queryKey: ['evaluation-templates'], queryFn: () => evaluationService.templates({ pageNo: 1, pageSize: 100 }) });
  const projects = useQuery({ queryKey: ['evaluation-projects'], queryFn: () => evaluationService.instances({ objectType: 'PROJECT_SCORE', pageNo: 1, pageSize: 100 }) });
  const employees = useQuery({ queryKey: ['evaluation-employees'], queryFn: () => evaluationService.instances({ objectType: 'EMPLOYEE_RATING', pageNo: 1, pageSize: 100 }) });
  const pending = useQuery({ queryKey: ['evaluation-pending'], queryFn: () => evaluationService.myPendingTasks({ pageNo: 1, pageSize: 100 }, { autoRedirectOnUnauthorized: false }) });
  const all = useQuery({ queryKey: ['evaluation-all'], queryFn: () => evaluationService.instances({ pageNo: 1, pageSize: 100 }) });

  const reload = useRefetchAll([templates, projects, employees, pending, all]);
  const enabledTemplates = (templates.data?.records || []).filter((item) => item.enabled);
  const templateOptions = enabledTemplates.map((item) => ({ label: `${item.templateName} (${item.objectType})`, value: item.id }));

  const openTemplate = (record?: EvaluationTemplateRecord) => {
    setActiveTemplate(record || null);
    templateForm.setFieldsValue(record ? { ...record, dimensions: record.dimensions || defaultDimensions, gradeRules: record.gradeRules || defaultGradeRules } : { objectType: 'PROJECT_SCORE', dimensions: defaultDimensions, gradeRules: defaultGradeRules });
    setTemplateOpen(true);
  };

  const saveTemplate = async () => {
    const values = await templateForm.validateFields();
    let savedTemplate: EvaluationTemplateRecord;
    if (activeTemplate) {
      savedTemplate = await evaluationService.updateTemplate(activeTemplate.id, values);
    } else {
      savedTemplate = await evaluationService.createTemplate(values);
    }
    message.success('评分模板已保存');
    setTemplateOpen(false);
    if (instanceOpen && savedTemplate.enabled) {
      instanceForm.setFieldsValue({ templateId: savedTemplate.id });
    }
    reload();
  };

  const createInstance = async () => {
    const values = await instanceForm.validateFields();
    await evaluationService.createInstance({ ...values, scorerUserIds: String(values.scorerUserIds).split(',').map((item) => Number(item.trim())).filter(Boolean) });
    message.success('评审已发起');
    setInstanceOpen(false);
    reload();
  };

  const instanceColumns = [
    { title: '对象', dataIndex: 'objectTitle' },
    { title: '类型', dataIndex: 'objectType', width: 160 },
    { title: '状态', dataIndex: 'status', width: 120, render: (value?: string | null) => renderStatusTag(value) },
    { title: '最终分', dataIndex: 'finalScore', width: 100 },
    { title: '等级', dataIndex: 'finalGrade', width: 100 },
    { title: '创建时间', dataIndex: 'createTime', width: 190 },
  ];

  return (
    <PageContainer title="评审中心" extra={<Button type="primary" onClick={() => setInstanceOpen(true)}>发起评审</Button>}>
      <ProCard variant="outlined">
        <Tabs
          items={[
            { key: 'project', label: '项目打分', children: <Table<EvaluationInstanceRecord> rowKey="id" loading={projects.isLoading} dataSource={projects.data?.records || []} columns={instanceColumns} pagination={false} /> },
            { key: 'employee', label: '员工评级', children: <Table<EvaluationInstanceRecord> rowKey="id" loading={employees.isLoading} dataSource={employees.data?.records || []} columns={instanceColumns} pagination={false} /> },
            {
              key: 'pending',
              label: `待评分 (${pending.data?.total || 0})`,
              children: (
                <Table<EvaluationScoreTaskRecord>
                  rowKey="id"
                  loading={pending.isLoading}
                  dataSource={pending.data?.records || []}
                  pagination={false}
                  columns={[
                    { title: '任务ID', dataIndex: 'id', width: 100 },
                    { title: '实例ID', dataIndex: 'instanceId', width: 100 },
                    { title: '状态', dataIndex: 'status', render: (value?: string | null) => renderStatusTag(value, { PENDING: { color: 'orange', text: value || '-' } }) },
                    { title: '操作', render: (_, record) => <Button size="small" type="primary" onClick={async () => { await evaluationService.submitScore(record.id, { details: [{ dimensionId: 1, score: 80 }], comment: '默认评分' }); message.success('已提交评分'); reload(); }}>快速提交</Button> },
                  ]}
                  locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无评分待办" /> }}
                />
              ),
            },
            {
              key: 'templates',
              label: '评分模板',
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Button type="primary" onClick={() => openTemplate()}>新建模板</Button>
                  <Table<EvaluationTemplateRecord>
                    rowKey="id"
                    loading={templates.isLoading}
                    dataSource={templates.data?.records || []}
                    pagination={false}
                    columns={[
                      { title: '模板名称', dataIndex: 'templateName' },
                      { title: '对象类型', dataIndex: 'objectType' },
                      buildTemplateEnabledColumn<EvaluationTemplateRecord>(evaluationService.updateTemplateEnabled, reload),
                      { title: '操作', render: (_, record) => <Button size="small" onClick={() => openTemplate(record)}>编辑</Button> },
                    ]}
                  />
                </Space>
              ),
            },
            {
              key: 'archive',
              label: '复核归档',
              children: (
                <Table<EvaluationInstanceRecord>
                  rowKey="id"
                  loading={all.isLoading}
                  dataSource={all.data?.records || []}
                  columns={[
                    ...instanceColumns,
                    {
                      title: '操作',
                      render: (_, record) => (
                        <Space>
                          <Button size="small" onClick={async () => { await evaluationService.review(record.id, { finalScore: record.finalScore || 80, finalGrade: record.finalGrade || 'B', comment: '复核确认' }); message.success('已复核'); reload(); }}>复核</Button>
                          <Button size="small" type="primary" onClick={async () => { await evaluationService.archive(record.id, { comment: '归档' }); message.success('已归档'); reload(); }}>归档</Button>
                        </Space>
                      ),
                    },
                  ]}
                  pagination={false}
                />
              ),
            },
          ]}
        />
      </ProCard>

      <Drawer title={activeTemplate ? '编辑评分模板' : '新建评分模板'} open={templateOpen} onClose={() => setTemplateOpen(false)} width={STANDARD_DRAWER_WIDTH} extra={<Button type="primary" onClick={saveTemplate}>保存</Button>}>
        <Form form={templateForm} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="objectType" label="对象类型" rules={[{ required: true }]}><Select options={[{ label: '项目打分', value: 'PROJECT_SCORE' }, { label: '员工评级', value: 'EMPLOYEE_RATING' }]} /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item>
          <Form.List name="dimensions">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item {...field} name={[field.name, 'dimensionName']} rules={[{ required: true }]}><Input placeholder="维度" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'weight']} rules={[{ required: true }]}><InputNumber placeholder="权重" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'maxScore']} rules={[{ required: true }]}><InputNumber placeholder="满分" /></Form.Item>
                    <Button onClick={() => remove(field.name)}>删除</Button>
                  </Space>
                ))}
                <Button onClick={() => add({ dimensionName: `维度${fields.length + 1}`, weight: 10, maxScore: 100 })}>添加维度</Button>
              </Space>
            )}
          </Form.List>
          <Form.List name="gradeRules">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%', marginTop: 16 }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item {...field} name={[field.name, 'gradeCode']} rules={[{ required: true }]}><Input placeholder="等级编码" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'gradeName']} rules={[{ required: true }]}><Input placeholder="等级名称" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'minScore']} rules={[{ required: true }]}><InputNumber placeholder="最低分" /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'maxScore']} rules={[{ required: true }]}><InputNumber placeholder="最高分" /></Form.Item>
                    <Button onClick={() => remove(field.name)}>删除</Button>
                  </Space>
                ))}
                <Button onClick={() => add({ gradeCode: 'D', gradeName: '待定', minScore: 0, maxScore: 59 })}>添加等级</Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Drawer>

      <Drawer title="发起评审" open={instanceOpen} onClose={() => setInstanceOpen(false)} width={STANDARD_DRAWER_WIDTH} extra={<Button type="primary" onClick={createInstance}>提交</Button>}>
        <Form form={instanceForm} layout="vertical">
          {templateOptions.length === 0 ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="暂无可用评分模板"
              description="请先新建并启用评分模板，然后再发起评审。"
              action={<Button size="small" type="primary" onClick={() => openTemplate()}>新建模板</Button>}
            />
          ) : null}
          <Form.Item name="templateId" label="评分模板" rules={[{ required: true }]}>
            <Select
              loading={templates.isLoading}
              options={templateOptions}
              placeholder="请选择评分模板"
              notFoundContent={(
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无可用模板"
                >
                  <Button type="primary" onClick={() => openTemplate()}>新建模板</Button>
                </Empty>
              )}
              dropdownRender={(menu) => (
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  {menu}
                  <Button type="link" onClick={() => openTemplate()} style={{ paddingInline: 8 }}>
                    新建评分模板
                  </Button>
                </Space>
              )}
            />
          </Form.Item>
          <Form.Item name="objectId" label="对象ID"><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="objectTitle" label="对象名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="scorerUserIds" label="评分人用户ID" rules={[{ required: true }]}><Input placeholder="多个用户用英文逗号分隔，例如 1001,1002" /></Form.Item>
          <Form.Item name="reviewerUserId" label="复核人用户ID"><InputNumber style={{ width: '100%' }} /></Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default EvaluationsPage;
