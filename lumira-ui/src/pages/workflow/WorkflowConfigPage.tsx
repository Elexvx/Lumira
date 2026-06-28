import { BranchesOutlined, CheckCircleOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import {
  Background,
  Controls,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useEffect, useMemo, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { getWorkflowDefinition, publishWorkflow, saveWorkflowDraft } from '@/services/workflow/api';
import type { WorkflowBusinessType, WorkflowDefinitionPayload, WorkflowNode, WorkflowNodeType } from '@/services/workflow/types';
import { message } from '@/theme/antdFeedbackBridge';
import { showErrorMessage } from '@/utils/errorMessage';
import './WorkflowPage.css';

type FlowNodeData = Record<string, unknown> & WorkflowNode & { label: string };
type FlowNode = Node<FlowNodeData>;
type FlowEdge = Edge<Record<string, unknown>>;

const businessOptions: Array<{ label: string; value: WorkflowBusinessType }> = [
  { label: '专家申请', value: 'EXPERT_APPLICATION' },
  { label: '赛事审批', value: 'COMPETITION_APPROVAL' },
];

const nodeTypeOptions: Array<{ label: string; value: WorkflowNodeType }> = [
  { label: '开始', value: 'START' },
  { label: '审批', value: 'APPROVAL' },
  { label: '条件', value: 'CONDITION' },
  { label: '结束', value: 'END' },
];

const defaultNodes: FlowNode[] = [
  { id: 'start', position: { x: 80, y: 140 }, data: { label: '开始', nodeKey: 'start', nodeType: 'START', name: '开始' } },
  {
    id: 'review',
    position: { x: 340, y: 140 },
    data: {
      label: '管理员审批',
      nodeKey: 'review',
      nodeType: 'APPROVAL',
      name: '管理员审批',
      assignmentType: 'ROLE',
      approverRoleIds: [1001],
      approverUserIds: [],
      approvalMode: 'ALL',
    },
  },
  { id: 'end', position: { x: 620, y: 140 }, data: { label: '结束', nodeKey: 'end', nodeType: 'END', name: '结束' } },
];

const defaultEdges: FlowEdge[] = [
  { id: 'start-review', source: 'start', target: 'review' },
  { id: 'review-end', source: 'review', target: 'end' },
];

const parseIds = (value?: string) =>
  (value || '')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0);

const stringifyIds = (values?: number[]) => (values || []).join(',');

const toFlowNodes = (nodes: WorkflowNode[]): FlowNode[] =>
  nodes.map((node) => ({
    id: node.nodeKey,
    position: { x: node.x ?? 120, y: node.y ?? 120 },
    data: { ...node, label: node.name },
  }));

const toFlowEdges = (edges: WorkflowDefinitionPayload['edges']): FlowEdge[] =>
  edges.map((edge) => ({
    id: edge.edgeKey,
    source: edge.sourceNodeKey,
    target: edge.targetNodeKey,
    label: edge.conditionExpression || undefined,
    data: { ...edge },
  }));

const WorkflowConfigPage = () => {
  const [businessType, setBusinessType] = useState<WorkflowBusinessType>('EXPERT_APPLICATION');
  const [definitionName, setDefinitionName] = useState('专家申请审批');
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(defaultNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(defaultEdges);
  const [selectedNodeId, setSelectedNodeId] = useState<string>('review');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<WorkflowNode & { approverUserIdsText?: string; approverRoleIdsText?: string }>();

  const selectedNode = useMemo(() => nodes.find((node) => node.id === selectedNodeId), [nodes, selectedNodeId]);

  useEffect(() => {
    setLoading(true);
    getWorkflowDefinition(businessType)
      .then((definition) => {
        setDefinitionName(definition.name);
        setNodes(toFlowNodes(definition.nodes || defaultNodes.map((node) => node.data)));
        setEdges(toFlowEdges(definition.edges || []));
        setSelectedNodeId((definition.nodes?.find((node) => node.nodeType === 'APPROVAL')?.nodeKey) || 'review');
      })
      .catch(() => {
        setDefinitionName(businessType === 'EXPERT_APPLICATION' ? '专家申请审批' : '赛事审批');
        setNodes(defaultNodes);
        setEdges(defaultEdges);
        setSelectedNodeId('review');
      })
      .finally(() => setLoading(false));
  }, [businessType, setEdges, setNodes]);

  useEffect(() => {
    if (!selectedNode) {
      form.resetFields();
      return;
    }
    form.setFieldsValue({
      ...selectedNode.data,
      approverUserIdsText: stringifyIds(selectedNode.data.approverUserIds),
      approverRoleIdsText: stringifyIds(selectedNode.data.approverRoleIds),
    });
  }, [form, selectedNode]);

  const updateSelectedNode = () => {
    if (!selectedNode) {
      return;
    }
    const values = form.getFieldsValue();
    setNodes((items) =>
      items.map((node) =>
        node.id === selectedNode.id
          ? {
              ...node,
              id: values.nodeKey || node.id,
              data: {
                ...node.data,
                ...values,
                label: values.name || node.data.label,
                approverUserIds: parseIds(values.approverUserIdsText),
                approverRoleIds: parseIds(values.approverRoleIdsText),
              },
            }
          : node,
      ),
    );
    setSelectedNodeId(values.nodeKey || selectedNode.id);
  };

  const addNode = (nodeType: WorkflowNodeType) => {
    const nodeKey = `${nodeType.toLowerCase()}_${Date.now().toString(36)}`;
    const name = nodeType === 'APPROVAL' ? '审批节点' : nodeType === 'CONDITION' ? '条件节点' : nodeType === 'END' ? '结束' : '开始';
    setNodes((items) => [
      ...items,
      {
        id: nodeKey,
        position: { x: 220 + items.length * 40, y: 260 },
        data: {
          label: name,
          nodeKey,
          nodeType,
          name,
          assignmentType: nodeType === 'APPROVAL' ? 'ROLE' : undefined,
          approverRoleIds: nodeType === 'APPROVAL' ? [1001] : [],
          approverUserIds: [],
          approvalMode: 'ALL',
        },
      },
    ]);
    setSelectedNodeId(nodeKey);
  };

  const onConnect = (connection: Connection) => {
    setEdges((items) =>
      addEdge(
        {
          ...connection,
          id: `${connection.source}-${connection.target}-${Date.now().toString(36)}`,
        },
        items,
      ),
    );
  };

  const buildPayload = (): WorkflowDefinitionPayload => ({
    name: definitionName,
    nodes: nodes.map((node) => ({
      ...node.data,
      nodeKey: node.id,
      name: node.data.name || node.data.label,
      x: Math.round(node.position.x),
      y: Math.round(node.position.y),
      config: node.data.config || {},
    })),
    edges: edges.map((edge, index) => ({
      edgeKey: edge.id,
      sourceNodeKey: edge.source,
      targetNodeKey: edge.target,
      conditionExpression: typeof edge.label === 'string' ? edge.label : undefined,
      sortOrder: index + 1,
      config: (edge.data as Record<string, unknown>) || {},
    })),
  });

  const save = async (publish = false) => {
    setSaving(true);
    try {
      await saveWorkflowDraft(businessType, buildPayload());
      if (publish) {
        await publishWorkflow(businessType);
        message.success('流程已发布');
      } else {
        message.success('草稿已保存');
      }
    } catch (error) {
      showErrorMessage(error, '流程保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ManagementPage
      title="审批配置"
      extra={
        <Space>
          <Select value={businessType} options={businessOptions} onChange={setBusinessType} style={{ width: 180 }} />
          <Button icon={<SaveOutlined />} loading={saving} onClick={() => void save(false)}>
            保存草稿
          </Button>
          <Button type="primary" icon={<CheckCircleOutlined />} loading={saving} onClick={() => void save(true)}>
            发布
          </Button>
        </Space>
      }
    >
      <ManagementPageBody className="workflow-page">
        <div className="workflow-designer">
          <aside className="workflow-sidebar">
            <Typography.Title level={5}>节点</Typography.Title>
            <Space direction="vertical" className="workflow-sidebar__stack">
              {nodeTypeOptions.map((item) => (
                <Button key={item.value} icon={<PlusOutlined />} onClick={() => addNode(item.value)}>
                  {item.label}
                </Button>
              ))}
            </Space>
          </aside>
          <main className="workflow-canvas">
            <Input value={definitionName} onChange={(event) => setDefinitionName(event.target.value)} className="workflow-title-input" />
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, node) => setSelectedNodeId(node.id)}
              fitView
              nodesDraggable
              elementsSelectable
            >
              <Background />
              <Controls />
            </ReactFlow>
          </main>
          <aside className="workflow-properties">
            <Typography.Title level={5}>
              <BranchesOutlined /> 属性
            </Typography.Title>
            {selectedNode ? (
              <Form form={form} layout="vertical" onValuesChange={updateSelectedNode}>
                <Form.Item name="nodeKey" label="节点标识">
                  <Input />
                </Form.Item>
                <Form.Item name="name" label="名称">
                  <Input />
                </Form.Item>
                <Form.Item name="nodeType" label="类型">
                  <Select options={nodeTypeOptions} />
                </Form.Item>
                <Form.Item name="approvalMode" label="审批模式">
                  <Select
                    options={[
                      { label: '会签，全部通过', value: 'ALL' },
                      { label: '或签，任一通过', value: 'ANY' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="assignmentType" label="审批人来源">
                  <Select
                    allowClear
                    options={[
                      { label: '指定用户', value: 'USER' },
                      { label: '指定角色', value: 'ROLE' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name="approverUserIdsText" label="用户 ID">
                  <Input placeholder="多个用英文逗号分隔" />
                </Form.Item>
                <Form.Item name="approverRoleIdsText" label="角色 ID">
                  <Input placeholder="例如 1001" />
                </Form.Item>
                <Form.Item label="坐标">
                  <Space.Compact>
                    <InputNumber value={Math.round(selectedNode.position.x)} disabled />
                    <InputNumber value={Math.round(selectedNode.position.y)} disabled />
                  </Space.Compact>
                </Form.Item>
              </Form>
            ) : (
              <Typography.Text type="secondary">请选择节点</Typography.Text>
            )}
          </aside>
        </div>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default WorkflowConfigPage;
