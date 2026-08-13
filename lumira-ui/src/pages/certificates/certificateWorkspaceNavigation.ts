export type CertificateWorkspaceSection = 'generate' | 'batches' | 'records';

export const certificateWorkspaceSectionPath = (
  competitionUuid: string,
  section: CertificateWorkspaceSection,
) => `/competitions/${encodeURIComponent(competitionUuid)}/certificates/${section}`;
