import { request, requestFile } from '@/services/common/request';
import type {
  CertificateGeneratePayload,
  CertificateGenerateResult,
  CertificateBatchRecord,
  CertificateAwardGrant,
  CertificateAwardRule,
  CertificateAwardSource,
  AwardGrantPayload,
  AwardCertificateGeneratePayload,
  CertificatePublicVerifyResult,
  CertificateRecord,
  CertificateTemplateRecord,
  CertificateTemplateVersionRecord,
  PageResponse,
} from './types';

const API = '/v2/aiadc';
const workspaceApi = (competitionUuid: string) => `${API}/competitions/${encodeURIComponent(competitionUuid)}`;

export const listCertificateTemplates = (params: Record<string, unknown> = {}) =>
  request<PageResponse<CertificateTemplateRecord>>(`${API}/certificate-templates`, { method: 'GET', params });

export const getCertificateTemplate = (id: number) =>
  request<CertificateTemplateRecord>(`${API}/certificate-templates/${id}`, { method: 'GET' });

export const createCertificateTemplate = (data: Partial<CertificateTemplateRecord>) =>
  request<CertificateTemplateRecord>(`${API}/certificate-templates`, { method: 'POST', data });

export const updateCertificateTemplate = (id: number, data: Partial<CertificateTemplateRecord>) =>
  request<CertificateTemplateRecord>(`${API}/certificate-templates/${id}`, { method: 'PUT', data });

export const publishCertificateTemplate = (id: number) =>
  request<CertificateTemplateVersionRecord>(`${API}/certificate-templates/${id}/publish`, { method: 'POST' });

export const duplicateCertificateTemplate = (id: number) =>
  request<CertificateTemplateRecord>(`${API}/certificate-templates/${id}/duplicate`, { method: 'POST' });

export const archiveCertificateTemplate = (id: number) =>
  request<CertificateTemplateRecord>(`${API}/certificate-templates/${id}/archive`, { method: 'POST' });

export const listCertificateTemplateVersions = (templateId: number) =>
  request<CertificateTemplateVersionRecord[]>(`${API}/certificate-templates/${templateId}/versions`, { method: 'GET' });

export const getCertificateTemplateVersion = (versionId: number) =>
  request<CertificateTemplateVersionRecord>(`${API}/certificate-template-versions/${versionId}`, { method: 'GET' });

export const saveCertificateCanvas = (versionId: number, data: Record<string, unknown>) =>
  request<CertificateTemplateVersionRecord>(`${API}/certificate-template-versions/${versionId}/canvas`, { method: 'PUT', data });

export const uploadCertificateBackground = (versionId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return request<CertificateTemplateVersionRecord>(`${API}/certificate-template-versions/${versionId}/background`, {
    method: 'POST',
    data: formData,
  });
};

export const generateCertificates = (data: CertificateGeneratePayload) =>
  request<CertificateGenerateResult>(`${API}/certificate-batches`, { method: 'POST', data });

export const listCertificateAwardSources = () =>
  request<CertificateAwardSource[]>(`${API}/certificate-award-sources`, { method: 'GET' });

export const grantPublishedAwards = (data: AwardGrantPayload) =>
  request<CertificateAwardGrant[]>(`${API}/certificate-awards/grant`, { method: 'POST', data });

export const listAwardGrants = (reviewBatchId: number) =>
  request<CertificateAwardGrant[]>(`${API}/certificate-awards`, {
    method: 'GET',
    params: { reviewBatchId },
  });

export const generateCertificatesFromAwards = (data: AwardCertificateGeneratePayload) =>
  request<CertificateGenerateResult>(`${API}/certificate-batches/from-awards`, { method: 'POST', data });

export const listCertificates = (params: Record<string, unknown> = {}) =>
  request<PageResponse<CertificateRecord>>(`${API}/certificates`, { method: 'GET', params });

export const listMyCertificates = () =>
  request<CertificateRecord[]>(`${API}/certificates/mine`, { method: 'GET' });

export const getCertificate = (id: number) =>
  request<CertificateRecord>(`${API}/certificates/${id}`, { method: 'GET' });

export const downloadCertificate = (id: number) =>
  requestFile(`${API}/certificates/${id}/download`, { method: 'GET' });

export const downloadMyCertificate = (id: number) =>
  requestFile(`${API}/certificates/mine/${id}/download`, { method: 'GET' });

export const regenerateCertificate = (id: number) =>
  request<CertificateRecord>(`${API}/certificates/${id}/regenerate`, { method: 'POST' });

export const revokeCertificate = (id: number, reason?: string) =>
  request<CertificateRecord>(`${API}/certificates/${id}/revoke`, { method: 'POST', data: { reason } });

export const listCompetitionWorkspaceCertificateAwardSources = (competitionUuid: string) =>
  request<CertificateAwardSource[]>(`${workspaceApi(competitionUuid)}/certificate-award-sources`, { method: 'GET' });

export const listCompetitionWorkspaceCertificateAwardRules = (competitionUuid: string) =>
  request<CertificateAwardRule[]>(`${workspaceApi(competitionUuid)}/certificate-award-rules`, { method: 'GET' });

export const grantCompetitionWorkspacePublishedAwards = (competitionUuid: string, data: AwardGrantPayload) =>
  request<CertificateAwardGrant[]>(`${workspaceApi(competitionUuid)}/certificate-awards/grant`, { method: 'POST', data });

export const listCompetitionWorkspaceAwardGrants = (competitionUuid: string, reviewBatchId: number) =>
  request<CertificateAwardGrant[]>(`${workspaceApi(competitionUuid)}/certificate-awards`, {
    method: 'GET',
    params: { reviewBatchId },
  });

export const generateCompetitionWorkspaceCertificates = (competitionUuid: string, data: CertificateGeneratePayload) =>
  request<CertificateGenerateResult>(`${workspaceApi(competitionUuid)}/certificate-batches`, { method: 'POST', data });

export const generateCompetitionWorkspaceCertificatesFromAwards = (
  competitionUuid: string,
  data: AwardCertificateGeneratePayload,
) => request<CertificateGenerateResult>(`${workspaceApi(competitionUuid)}/certificate-batches/from-awards`, { method: 'POST', data });

export const listCompetitionWorkspaceCertificateBatches = (competitionUuid: string, params: Record<string, unknown> = {}) =>
  request<PageResponse<CertificateBatchRecord>>(`${workspaceApi(competitionUuid)}/certificate-batches`, { method: 'GET', params });

export const getCompetitionWorkspaceCertificateBatch = (competitionUuid: string, id: number) =>
  request<CertificateBatchRecord>(`${workspaceApi(competitionUuid)}/certificate-batches/${id}`, { method: 'GET' });

export const listCompetitionWorkspaceCertificates = (competitionUuid: string, params: Record<string, unknown> = {}) =>
  request<PageResponse<CertificateRecord>>(`${workspaceApi(competitionUuid)}/certificates`, { method: 'GET', params });

export const getCompetitionWorkspaceCertificate = (competitionUuid: string, id: number) =>
  request<CertificateRecord>(`${workspaceApi(competitionUuid)}/certificates/${id}`, { method: 'GET' });

export const downloadCompetitionWorkspaceCertificate = (competitionUuid: string, id: number) =>
  requestFile(`${workspaceApi(competitionUuid)}/certificates/${id}/download`, { method: 'GET' });

export const regenerateCompetitionWorkspaceCertificate = (competitionUuid: string, id: number) =>
  request<CertificateRecord>(`${workspaceApi(competitionUuid)}/certificates/${id}/regenerate`, { method: 'POST' });

export const revokeCompetitionWorkspaceCertificate = (competitionUuid: string, id: number, reason?: string) =>
  request<CertificateRecord>(`${workspaceApi(competitionUuid)}/certificates/${id}/revoke`, {
    method: 'POST',
    data: { reason },
  });

export const verifyCertificateByNo = (certificateNo: string, verificationCode: string) =>
  request<CertificatePublicVerifyResult>('/public/certificates/verify', {
    method: 'GET',
    params: { certificateNo, verificationCode },
  });

export const verifyCertificateByToken = (publicToken: string) =>
  request<CertificatePublicVerifyResult>(`/public/certificates/verify/${publicToken}`, { method: 'GET' });
