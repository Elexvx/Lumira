export type WorkflowBusinessType = 'EXPERT_APPLICATION' | 'COMPETITION_APPROVAL';
export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CONDITION' | 'END';
export type WorkflowApprovalMode = 'ALL' | 'ANY';

export interface WorkflowNode {
  id?: number;
  nodeKey: string;
  nodeType: WorkflowNodeType;
  name: string;
  x?: number;
  y?: number;
  assignmentType?: 'USER' | 'ROLE' | null;
  approverUserIds?: number[];
  approverRoleIds?: number[];
  approvalMode?: WorkflowApprovalMode;
  config?: Record<string, unknown>;
}

export interface WorkflowEdge {
  id?: number;
  edgeKey: string;
  sourceNodeKey: string;
  targetNodeKey: string;
  conditionExpression?: string | null;
  sortOrder?: number;
  config?: Record<string, unknown>;
}

export interface WorkflowDefinition {
  id?: number;
  businessType: WorkflowBusinessType;
  name: string;
  status: 'DRAFT' | 'ACTIVE';
  versionNo?: number;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkflowDefinitionPayload {
  name: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

export interface WorkflowTask {
  id: number;
  instanceId: number;
  businessType: WorkflowBusinessType;
  businessId: number;
  businessUuid?: string | null;
  businessTitle?: string | null;
  nodeKey: string;
  nodeName: string;
  status: string;
  approverUserId?: number | null;
  approverRoleId?: number | null;
  createdAt?: string | null;
  completedAt?: string | null;
}

export interface WorkflowActionLog {
  id: number;
  instanceId: number;
  taskId?: number | null;
  actionType: string;
  nodeKey?: string | null;
  nodeName?: string | null;
  operatorUsername?: string | null;
  comment?: string | null;
  createdAt?: string | null;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
}
