import { request } from '@/services/common/request';
import type {
  CompetitionPaymentOrderRecord,
  CompetitionMaterialSubmissionRecord,
  CompetitionQueryParams,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionStageFormUpsertPayload,
  CompetitionStageFormRecord,
  CompetitionStageRecord,
  CompetitionStageUpsertPayload,
  CompetitionUpsertPayload,
  PageResponse,
  ProjectRecord,
  ProjectUpsertPayload,
} from './types';

const COMPETITION_API = '/v2/aiadc/competitions';

export const listCompetitions = (params: CompetitionQueryParams) =>
  request<PageResponse<CompetitionRecord>>(COMPETITION_API, {
    method: 'GET',
    params: { ...params },
  });

export const createCompetition = (data: CompetitionUpsertPayload) =>
  request<CompetitionRecord>(COMPETITION_API, {
    method: 'POST',
    data,
  });

export const updateCompetition = (id: number, data: CompetitionUpsertPayload) =>
  request<CompetitionRecord>(`${COMPETITION_API}/${id}`, {
    method: 'PUT',
    data,
  });

export const deleteCompetition = (id: number) =>
  request<boolean>(`${COMPETITION_API}/${id}`, {
    method: 'DELETE',
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

export const createRegistration = (data: { competitionId: number; teamId: number; projectId: number }) =>
  request<CompetitionRegistrationRecord>('/v2/aiadc/registrations', {
    method: 'POST',
    data,
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

export const createRegistrationPaymentOrder = (registrationId: number, data: { providerCode?: string } = {}) =>
  request<CompetitionPaymentOrderRecord>(`/v2/aiadc/registrations/${registrationId}/payment-order`, {
    method: 'POST',
    data,
  });
