import { request } from '@/services/common/request';
import type { CompetitionQueryParams, CompetitionRecord, CompetitionUpsertPayload, PageResponse } from './types';

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
