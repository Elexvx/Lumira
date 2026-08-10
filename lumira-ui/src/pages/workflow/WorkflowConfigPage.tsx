import { BranchesOutlined, CheckCircleOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Spin, Tag, Typography } from 'antd';
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
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ErrorCode } from '@/enums/errorCode';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { getWorkflowDefinition, publishWorkflow, saveWorkflowDraft } from '@/services/workflow/api';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import type {
  WorkflowBusinessType,
  WorkflowDefinition,
  WorkflowDefinitionPayload,
  WorkflowNode,
  WorkflowNodeType,
} from '@/services/workflow/types';
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

const defaultDefinitionNames: Record<WorkflowBusinessType, string> = {
  EXPERT_APPLICATION: '专家申请审批',
  COMPETITION_APPROVAL: '赛事审批流程',
};

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

const createDefaultNodes = (): FlowNode[] => defaultNodes.map((node) => ({
  ...node,
  position: { ...node.position },
  data: {
    ...node.data,
    approverRoleIds: [...(node.data.approverRoleIds || [])],
    approverUserIds: [...(node.data.approverUserIds || [])],
  },
}));

const createDefaultEdges = (): FlowEdge[] => defaultEdges.map((edge) => ({
  ...edge,
  data: edge.data ? { ...edge.data } : undefined,
}));

const isMissingDefinition = (error: unknown) =>
  error instanceof ApiRequestError
  && (error.httpStatus === 404 || error.code === ErrorCode.NOT_FOUND);

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
    data: { ...(edge.config || {}) },
  }));

const WorkflowConfigPage = () => {
  const [businessType, setBusinessType] = useState<WorkflowBusinessType>('EXPERT_APPLICATION');
  const [definitionName, setDefinitionName] = useState('专家申请审批');
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(defaultNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(defaultEdges);
  const [selectedNodeId, setSelectedNodeId] = useState<string>('review');
  const [definitionStatus, setDefinitionStatus] = useState<'DRAFT' | 'ACTIVE'>('DRAFT');
  const [definitionPersisted, setDefinitionPersisted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createBusinessType, setCreateBusinessType] = useState<WorkflowBusinessType>('COMPETITION_APPROVAL');
  const [createName, setCreateName] = useState(defaultDefinitionNames.COMPETITION_APPROVAL);
  const activeBusinessType = useRef<WorkflowBusinessType>('EXPERT_APPLICATION');
  const definitionLoadGeneration = useRef(0);
  const skipNextBusinessLoad = useRef<WorkflowBusinessType | null>(null);
  const [form] = Form.useForm<WorkflowNode & { approverUserIdsText?: string; approverRoleIdsText?: string }>();

  const selectedNode = useMemo(() => nodes.find((node) => node.id === selectedNodeId), [nodes, selectedNodeId]);

  const applyDefinition = useCallback((definition: WorkflowDefinition) => {
    setDefinitionName(definition.name);
    setNodes(toFlowNodes(definition.nodes || []));
    setEdges(toFlowEdges(definition.edges || []));
    setSelectedNodeId((definition.nodes?.find((node) => node.nodeType === 'APPROVAL')?.nodeKey) || definition.nodes?.[0]?.nodeKey || 'review');
    setDefinitionStatus(definition.status);
    setDefinitionPersisted(true);
  }, [setEdges, setNodes]);

  const applyNewDraft = useCallback((name: string) => {
    setDefinitionName(name);
    setNodes(createDefaultNodes());
    setEdges(createDefaultEdges());
    setSelectedNodeId('review');
    setDefinitionStatus('DRAFT');
    setDefinitionPersisted(false);
  }, [setEdges, setNodes]);

  const activateBusinessType = (nextBusinessType: WorkflowBusinessType, skipLoad = false) => {
    definitionLoadGeneration.current += 1;
    activeBusinessType.current = nextBusinessType;
    if (skipLoad) {
      setLoading(false);
    }
    if (nextBusinessType === businessType) {
      return;
    }
    skipNextBusinessLoad.current = skipLoad ? nextBusinessType : null;
    setBusinessType(nextBusinessType);
  };

  useEffect(() => {
    activeBusinessType.current = businessType;
    if (skipNextBusinessLoad.current === businessType) {
      skipNextBusinessLoad.current = null;
      return;
    }
    const loadGeneration = ++definitionLoadGeneration.current;
    setLoading(true);
    getWorkflowDefinition(businessType)
      .then((definition) => {
        if (loadGeneration === definitionLoadGeneration.current) {
          applyDefinition(definition);
        }
      })
      .catch((error) => {
        if (loadGeneration !== definitionLoadGeneration.current) {
          return;
        }
        if (isMissingDefinition(error)) {
          applyNewDraft(defaultDefinitionNames[businessType]);
          return;
        }
        showErrorMessage(error, '工作流加载失败');
      })
      .finally(() => {
        if (loadGeneration === definitionLoadGeneration.current) {
          setLoading(false);
        }
      });
    return () => {
      if (loadGeneration === definitionLoadGeneration.current) {
        definitionLoadGeneration.current += 1;
      }
    };
  }, [applyDefinition, applyNewDraft, businessType]);

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

  const openCreateWorkflow = () => {
    const nextBusinessType = businessOptions.find((item) => item.value !== businessType)?.value || businessType;
    setCreateBusinessType(nextBusinessType);
    setCreateName(defaultDefinitionNames[nextBusinessType]);
    setCreateOpen(true);
  };

  const createWorkflow = async () => {
    const normalizedName = createName.trim();
    if (!normalizedName) {
      message.warning('请输入工作流名称');
      return;
    }

    setCreating(true);
    try {
      const existingDefinition = await getWorkflowDefinition(createBusinessType);
      activateBusinessType(existingDefinition.businessType, true);
      applyDefinition(existingDefinition);
      setCreateOpen(false);
      message.info('该业务场景已有工作流，已打开现有配置');
    } catch (error) {
      if (!isMissingDefinition(error)) {
        showErrorMessage(error, '工作流创建前检查失败');
        return;
      }

      activateBusinessType(createBusinessType, true);
      applyNewDraft(normalizedName);
      setCreateOpen(false);
      message.success('已新建工作流草稿，请完成配置后保存');
    } finally {
      setCreating(false);
    }
  };

  const save = async (publish = false) => {
    setSaving(true);
    try {
      const saveBusinessType = activeBusinessType.current;
      let savedDefinition = await saveWorkflowDraft(saveBusinessType, buildPayload());
      if (publish) {
        savedDefinition = await publishWorkflow(saveBusinessType);
        message.success('流程已发布');
      } else {
        message.success('草稿已保存');
      }
      applyDefinition(savedDefinition);
    } catch (error) {
      showErrorMessage(error, '流程保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <ManagementPage
        title="工作流配置"
        extra={
          <Space>
            <Button icon={<PlusOutlined />} onClick={openCreateWorkflow}>
              新建工作流
            </Button>
            <Select value={businessType} options={businessOptions} onChange={(value) => activateBusinessType(value)} style={{ width: 180 }} />
            <Tag color={definitionPersisted ? (definitionStatus === 'ACTIVE' ? 'green' : 'gold') : 'default'}>
              {definitionPersisted ? (definitionStatus === 'ACTIVE' ? '已发布' : '草稿') : '未保存'}
            </Tag>
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
          <Alert
            className="workflow-guidance"
            type="info"
            showIcon
            message="在系统设置中统一配置审批工作流"
            description="先选择业务场景并配置节点、审批人和流转关系；保存草稿后可发布。审批人员在“审批中心 / 我的审批”处理运行中的任务。"
          />
          <Spin spinning={loading}>
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
          </Spin>
        </ManagementPageBody>
      </ManagementPage>
      <Modal
        title="新建工作流"
        open={createOpen}
        okText="创建草稿"
        cancelText="取消"
        confirmLoading={creating}
        onOk={() => void createWorkflow()}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Space direction="vertical" size="middle" className="workflow-create-form">
          <div>
            <Typography.Text strong>业务场景</Typography.Text>
            <Select
              className="workflow-create-form__control"
              value={createBusinessType}
              options={businessOptions}
              onChange={(value) => {
                setCreateBusinessType(value);
                setCreateName(defaultDefinitionNames[value]);
              }}
            />
          </div>
          <div>
            <Typography.Text strong>工作流名称</Typography.Text>
            <Input
              className="workflow-create-form__control"
              value={createName}
              maxLength={128}
              placeholder="请输入工作流名称"
              onChange={(event) => setCreateName(event.target.value)}
            />
          </div>
          <Typography.Text type="secondary">
            创建后先保存为草稿；发布前不会影响当前业务审批。
          </Typography.Text>
        </Space>
      </Modal>
    </>
  );
};

export default WorkflowConfigPage;
