import { request } from '@/services/common/request';
import type {
  PageResponse,
  WorkflowActionLog,
  WorkflowBusinessType,
  WorkflowDefinition,
  WorkflowDefinitionPayload,
  WorkflowTask,
} from './types';

const WORKFLOW_API = '/v2/workflows';

export const getWorkflowDefinition = (businessType: WorkflowBusinessType) =>
  request<WorkflowDefinition>(`${WORKFLOW_API}/definitions/${businessType}`, {
    method: 'GET',
  });

export const saveWorkflowDraft = (businessType: WorkflowBusinessType, data: WorkflowDefinitionPayload) =>
  request<WorkflowDefinition>(`${WORKFLOW_API}/definitions/${businessType}/draft`, {
    method: 'PUT',
    data,
  });

export const publishWorkflow = (businessType: WorkflowBusinessType) =>
  request<WorkflowDefinition>(`${WORKFLOW_API}/definitions/${businessType}/publish`, {
    method: 'POST',
  });

export const listMyWorkflowTasks = (params: { status?: string; pageNo?: number; pageSize?: number }) =>
  request<PageResponse<WorkflowTask>>(`${WORKFLOW_API}/tasks/my`, {
    method: 'GET',
    params,
  });

export const approveWorkflowTask = (taskId: number, comment?: string) =>
  request<boolean>(`${WORKFLOW_API}/tasks/${taskId}/approve`, {
    method: 'POST',
    data: { comment },
  });

export const rejectWorkflowTask = (taskId: number, comment?: string) =>
  request<boolean>(`${WORKFLOW_API}/tasks/${taskId}/reject`, {
    method: 'POST',
    data: { comment },
  });

export const listWorkflowLogs = (instanceId: number) =>
  request<WorkflowActionLog[]>(`${WORKFLOW_API}/instances/${instanceId}/logs`, {
    method: 'GET',
  });
