import { request, type RequestOptions } from '@/services/common/request';
import type {
  CompetitionPaymentOrderRecord,
  CompetitionMaterialSubmissionRecord,
  CompetitionQueryParams,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionRegistrationExportStartRecord,
  CompetitionRegistrationExportTaskRecord,
  CompetitionConfigItem,
  CompetitionConfigSet,
  CompetitionSettingsRecord,
  CompetitionStageFormUpsertPayload,
  CompetitionStageFormRecord,
  CompetitionStageRecord,
  CompetitionStageUpsertPayload,
  CompetitionStageReviewCandidateRecord,
  CompetitionStageReviewDecisionPayload,
  CompetitionUpsertPayload,
  CompetitionWorkspaceQueryParams,
  CompetitionWorkspacePaymentQuery,
  CompetitionWorkspaceRegistrationQuery,
  CompetitionWorkspaceExportRequest,
  CompetitionWorkspaceRecord,
  CompetitionAuditRecord,
  PageResponse,
  ProjectRecord,
  ProjectUpsertPayload,
} from './types';
import type { RegistrationPaymentRecord } from '@/services/payment/types';

const COMPETITION_API = '/v2/aiadc/competitions';
const COMPETITION_WORKSPACE_API = '/v2/aiadc/competition-workspaces';
const competitionWorkspaceApi = (competitionUuid: string) =>
  `${COMPETITION_API}/${encodeURIComponent(competitionUuid)}`;

export const listCompetitionWorkspaces = (params: CompetitionWorkspaceQueryParams = {}, options: RequestOptions = {}) =>
  request<PageResponse<CompetitionWorkspaceRecord>>(COMPETITION_WORKSPACE_API, {
    ...options,
    method: 'GET',
    params: { ...params },
  });

export const getCompetitionWorkspace = (competitionUuid: string, options: RequestOptions = {}) =>
  request<CompetitionWorkspaceRecord>(`${COMPETITION_API}/${encodeURIComponent(competitionUuid)}/workspace`, {
    ...options,
    method: 'GET',
  });

export const listCompetitionWorkspaceRegistrations = (
  competitionUuid: string,
  params: CompetitionWorkspaceRegistrationQuery = {},
  options: RequestOptions = {},
) => request<PageResponse<CompetitionRegistrationRecord>>(`${competitionWorkspaceApi(competitionUuid)}/registrations`, {
  ...options,
  method: 'GET',
  params: { ...params },
});

export const getCompetitionWorkspaceRegistration = (
  competitionUuid: string,
  registrationId: number,
  options: RequestOptions = {},
) => request<CompetitionRegistrationRecord>(`${competitionWorkspaceApi(competitionUuid)}/registrations/${registrationId}`, {
  ...options,
  method: 'GET',
});

export const listCompetitionWorkspaceRegistrationMaterials = (
  competitionUuid: string,
  registrationId: number,
  options: RequestOptions = {},
) => request<CompetitionMaterialSubmissionRecord[]>(`${competitionWorkspaceApi(competitionUuid)}/registrations/${registrationId}/materials`, {
  ...options,
  method: 'GET',
});

export const listCompetitionWorkspaceStages = (
  competitionUuid: string,
  options: RequestOptions = {},
) => request<CompetitionStageRecord[]>(`${competitionWorkspaceApi(competitionUuid)}/workspace/stages`, {
  ...options,
  method: 'GET',
});

export const listCompetitionWorkspacePayments = (
  competitionUuid: string,
  params: CompetitionWorkspacePaymentQuery = {},
  options: RequestOptions = {},
) => request<PageResponse<RegistrationPaymentRecord>>(`${competitionWorkspaceApi(competitionUuid)}/payments`, {
  ...options,
  method: 'GET',
  params: { ...params },
});

export const startCompetitionWorkspaceRegistrationExport = (
  competitionUuid: string,
  data: CompetitionWorkspaceExportRequest,
) => request<CompetitionRegistrationExportStartRecord>(`${competitionWorkspaceApi(competitionUuid)}/registration-exports`, {
  method: 'POST',
  data,
});

export const startCompetitionWorkspaceMaterialPackage = (
  competitionUuid: string,
  data: CompetitionWorkspaceExportRequest,
) => request<CompetitionRegistrationExportStartRecord>(`${competitionWorkspaceApi(competitionUuid)}/registration-exports/materials-package`, {
  method: 'POST',
  data,
});

export const getCompetitionWorkspaceExportTask = (competitionUuid: string, taskId: number) =>
  request<CompetitionRegistrationExportTaskRecord>(`${competitionWorkspaceApi(competitionUuid)}/registration-exports/${taskId}`, {
    method: 'GET',
    silent: true,
  });

export const listCompetitionWorkspaceAudit = (
  competitionUuid: string,
  params: { module?: string; action?: string; pageNo?: number; pageSize?: number } = {},
) => request<PageResponse<CompetitionAuditRecord>>(`${competitionWorkspaceApi(competitionUuid)}/audit`, {
  method: 'GET',
  params,
});

export const listCompetitions = (params: CompetitionQueryParams) =>
  request<PageResponse<CompetitionRecord>>(COMPETITION_API, {
    method: 'GET',
    params: { ...params },
  });

export const getCompetition = (id: number, options: RequestOptions = {}) =>
  request<CompetitionRecord>(`${COMPETITION_API}/${id}`, {
    ...options,
    method: 'GET',
  });

export const createCompetition = (data: CompetitionUpsertPayload) =>
  request<CompetitionRecord>(COMPETITION_API, {
    method: 'POST',
    data,
  });

export const createCompetitionDraft = (data: CompetitionUpsertPayload) =>
  request<CompetitionRecord>(`${COMPETITION_API}/drafts`, {
    method: 'POST',
    data,
  });

export const updateCompetitionDraft = (id: number, data: CompetitionUpsertPayload, options: RequestOptions = {}) =>
  request<CompetitionRecord>(`${COMPETITION_API}/drafts/${id}`, {
    ...options,
    method: 'PUT',
    data,
  });

export const updateCompetition = (id: number, data: CompetitionUpsertPayload, options: RequestOptions = {}) =>
  request<CompetitionRecord>(`${COMPETITION_API}/${id}`, {
    ...options,
    method: 'PUT',
    data,
  });

export const deleteCompetition = (id: number) =>
  request<boolean>(`${COMPETITION_API}/${id}`, {
    method: 'DELETE',
  });

export const getCompetitionSettings = (competitionUuid: string) =>
  request<CompetitionSettingsRecord>(`${COMPETITION_API}/${competitionUuid}/settings`);

export const saveCompetitionSettingsModule = (competitionUuid: string, module: string, items: CompetitionConfigItem[], options: RequestOptions = {}) =>
  request<CompetitionSettingsRecord>(`${COMPETITION_API}/${competitionUuid}/settings/${module}`, {
    ...options,
    method: 'PUT',
    data: { items },
  });

export const publishCompetitionSettings = (competitionUuid: string) =>
  request<CompetitionConfigSet>(`${COMPETITION_API}/${competitionUuid}/settings/publish`, {
    method: 'POST',
  });

export const listProjects = (params: Record<string, unknown> = {}) =>
  request<PageResponse<ProjectRecord>>('/v2/aiadc/projects', {
    method: 'GET',
    params,
  });

export const createProject = (data: ProjectUpsertPayload) =>
  request<ProjectRecord>('/v2/aiadc/projects', {
    method: 'POST',
    data: { locale: 'zh', rating: 'popular', status: 'draft', sort: 100, ...data },
  });

export const updateProject = (id: number, data: ProjectUpsertPayload) =>
  request<ProjectRecord>(`/v2/aiadc/projects/${id}`, {
    method: 'PUT',
    data: { locale: 'zh', rating: 'popular', status: 'draft', sort: 100, ...data },
  });

export const deleteProject = (id: number) =>
  request<boolean>(`/v2/aiadc/projects/${id}`, {
    method: 'DELETE',
  });

export type RegistrationSnapshotMemberPayload = {
  participantType?: 'STUDENT' | 'TEACHER';
  memberName?: string;
  employeeNo?: string;
  departmentName?: string;
  role?: string;
  remark?: string;
  extraValues?: Record<string, unknown>;
};

export type RegistrationSnapshotTeamPayload = {
  teamName?: string;
  teamType?: string;
  avatarUrl?: string;
  description?: string;
  extraValues?: Record<string, unknown>;
};

export type RegistrationProjectSnapshotPayload = {
  title?: string;
  category?: string;
  description?: string;
  imageUrl?: string;
  extraValues?: Record<string, unknown>;
};

export type RegistrationUpsertPayload = {
  competitionId: number;
  teamId?: number;
  projectId?: number;
  registrationExtraValues?: Record<string, unknown>;
  teamSnapshot?: RegistrationSnapshotTeamPayload;
  projectSnapshot?: RegistrationProjectSnapshotPayload;
  members?: RegistrationSnapshotMemberPayload[];
};

export type RegistrationConfirmPayload = {
  registration: RegistrationUpsertPayload;
  project?: {
    title: string;
    category?: string;
    description?: string;
    imageUrl?: string;
  };
  materials?: {
    stageId: number;
    values: Array<{ fieldKey: string; fieldType: string; textValue?: string; fileId?: number; jsonValue?: string }>;
  };
};

export const createRegistration = (data: RegistrationUpsertPayload) =>
  request<CompetitionRegistrationRecord>('/v2/aiadc/registrations', {
    method: 'POST',
    data,
  });

export const confirmRegistration = (data: RegistrationConfirmPayload) =>
  request<CompetitionRegistrationRecord>('/v2/aiadc/registrations/confirm', {
    method: 'POST',
    data,
  });

export const reconfirmRegistration = (id: number, data: RegistrationConfirmPayload) =>
  request<CompetitionRegistrationRecord>(`/v2/aiadc/registrations/${id}/confirm`, {
    method: 'PUT',
    data,
  });

export const listRegistrations = (params: {
  pageNo?: number;
  pageSize?: number;
  competitionId?: number;
  status?: string;
  keyword?: string;
  includeSnapshots?: boolean;
} = {}) =>
  request<PageResponse<CompetitionRegistrationRecord>>('/v2/aiadc/registrations', {
    method: 'GET',
    params,
  });

export const getRegistration = (id: number) => request<CompetitionRegistrationRecord>(`/v2/aiadc/registrations/${id}`);

export const updateRegistration = (id: number, data: RegistrationUpsertPayload) =>
  request<CompetitionRegistrationRecord>(`/v2/aiadc/registrations/${id}`, {
    method: 'PUT',
    data,
  });

export const deleteRegistration = (id: number) =>
  request<boolean>(`/v2/aiadc/registrations/${id}`, {
    method: 'DELETE',
  });

export const listCompetitionStages = (competitionId: number) =>
  request<CompetitionStageRecord[]>(`/v2/aiadc/competitions/${competitionId}/stages`);

export const createCompetitionStage = (competitionId: number, data: CompetitionStageUpsertPayload) =>
  request<CompetitionStageRecord>(`/v2/aiadc/competitions/${competitionId}/stages`, {
    method: 'POST',
    data,
  });

export const getCompetitionStageForm = (stageId: number) =>
  request<CompetitionStageFormRecord>(`/v2/aiadc/stages/${stageId}/form`);

export const upsertCompetitionStageForm = (stageId: number, data: CompetitionStageFormUpsertPayload) =>
  request<CompetitionStageFormRecord>(`/v2/aiadc/stages/${stageId}/form`, {
    method: 'PUT',
    data,
  });

export const submitRegistrationMaterials = (
  registrationId: number,
  data: { stageId: number; values: Array<{ fieldKey: string; fieldType: string; textValue?: string; fileId?: number; jsonValue?: string }> },
) =>
  request<CompetitionRegistrationRecord>(`/v2/aiadc/registrations/${registrationId}/materials`, {
    method: 'POST',
    data,
  });

export const listRegistrationMaterials = (registrationId: number) =>
  request<CompetitionMaterialSubmissionRecord[]>(`/v2/aiadc/registrations/${registrationId}/materials`);

export const createRegistrationPaymentOrder = (
  registrationId: number,
  data: { providerCode?: string; clientType?: string; returnUrl?: string } = {},
) =>
  request<CompetitionPaymentOrderRecord>(`/v2/aiadc/registrations/${registrationId}/payment-order`, {
    method: 'POST',
    data,
  });

export const updateCompetitionStage = (stageId: number, data: CompetitionStageUpsertPayload) =>
  request<CompetitionStageRecord>(`/v2/aiadc/stages/${stageId}`, {
    method: 'PUT',
    data,
  });

export const listCompetitionStageReviewCandidates = (stageId: number) =>
  request<CompetitionStageReviewCandidateRecord[]>(`/v2/aiadc/stages/${stageId}/review-candidates`);

export const saveCompetitionStageReviewDecision = (
  stageId: number,
  registrationId: number,
  data: CompetitionStageReviewDecisionPayload,
) => request<CompetitionStageReviewCandidateRecord>(`/v2/aiadc/stages/${stageId}/review-candidates/${registrationId}`, {
  method: 'PUT',
  data,
});

export const applyCompetitionStagePromotionRule = (stageId: number) =>
  request<CompetitionStageReviewCandidateRecord[]>(`/v2/aiadc/stages/${stageId}/apply-promotion-rule`, {
    method: 'POST',
  });

export const listRegistrationPaymentOptions = (registrationId: number, clientType: 'DESKTOP' | 'MOBILE' | 'WECHAT') =>
  request<import('./types').CompetitionPaymentOptionRecord[]>(`/v2/aiadc/registrations/${registrationId}/payment-options`, {
    method: 'GET',
    params: { clientType },
  });

export const getRegistrationPaymentStatus = (registrationId: number) =>
  request<CompetitionPaymentOrderRecord>(`/v2/aiadc/registrations/${registrationId}/payment-status`, {
    method: 'GET',
  });
