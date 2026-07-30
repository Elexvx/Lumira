export type PageResponse<T> = {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
};

export type CertificateTemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type CertificateRecordStatus = 'GENERATING' | 'GENERATED' | 'ISSUED' | 'FAILED' | 'REVOKED' | 'EXPIRED';

export type CertificateTemplateRecord = {
  id: number;
  templateCode: string;
  templateName: string;
  templateType: string;
  sceneType: string;
  description?: string;
  latestVersion: number;
  status: CertificateTemplateStatus;
  createdAt?: string;
  updatedAt?: string;
};

export type CertificateTemplateVersionRecord = {
  id: number;
  templateId: number;
  version: number;
  backgroundFileId?: number;
  backgroundUrl?: string;
  pageWidth: number;
  pageHeight: number;
  orientation: 'LANDSCAPE' | 'PORTRAIT';
  unit: 'PX' | 'MM';
  dpi: number;
  canvasJson: string;
  variableSchemaJson?: string;
  status: CertificateTemplateStatus;
};

export type CertificateCanvasElement = {
  id: string;
  type: 'text' | 'qrcode';
  fieldKey?: string;
  text?: string;
  x: number;
  y: number;
  width: number;
  height: number;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: string;
  color?: string;
  textAlign?: 'left' | 'center' | 'right';
  placeholder?: string;
};

export type CertificateCanvas = {
  page: {
    width: number;
    height: number;
    dpi: number;
    orientation: 'LANDSCAPE' | 'PORTRAIT';
  };
  elements: CertificateCanvasElement[];
};

export type CertificateDataPayload = {
  recipientName: string;
  recipientType?: string;
  competitionTitle?: string;
  projectName?: string;
  teamName?: string;
  awardName?: string;
  issueDate?: string;
  expireDate?: string;
  data?: Record<string, unknown>;
};

export type CertificateGeneratePayload = {
  batchName?: string;
  templateId: number;
  templateVersionId: number;
  competitionId?: number;
  stageId?: number;
  sourceType: 'MANUAL' | 'IMPORT' | 'AWARD_RESULT';
  records: CertificateDataPayload[];
};

export type CertificateAwardGrant = {
  id: number;
  publicationId: number;
  publicationVersion: number;
  reviewBatchId: number;
  competitionId: number;
  stageId: number;
  candidateId: number;
  registrationId: number;
  projectId: number;
  teamId: number;
  userId: number;
  recipientName: string;
  competitionTitle: string;
  projectName?: string;
  teamName?: string;
  awardName: string;
  rankNo?: number;
  decision: string;
  status: 'GRANTED' | 'ISSUED' | 'REVOKED';
  certificateRecordId?: number;
  grantedAt?: string;
};

export type CertificateAwardSource = {
  reviewBatchId: number;
  batchNo: string;
  batchName: string;
  competitionId: number;
  competitionTitle: string;
  stageId: number;
  stageName: string;
  candidateCount: number;
  publicationVersion: number;
  publishedAt?: string;
  grantCount: number;
  issuedCount: number;
};

export type CertificateAwardRule = {
  awardName: string;
  minRank: number;
  maxRank: number;
};

export type AwardGrantPayload = {
  reviewBatchId: number;
  rules: CertificateAwardRule[];
};

export type AwardCertificateGeneratePayload = {
  batchName?: string;
  templateId: number;
  templateVersionId: number;
  grantIds: number[];
};

export type CertificateBatchRecord = {
  id: number;
  batchNo: string;
  batchName?: string;
  templateId: number;
  templateVersionId: number;
  sourceRefId?: number;
  totalCount: number;
  successCount: number;
  failedCount: number;
  status: string;
  errorMessage?: string;
  createdAt?: string;
};

export type CertificateRecord = {
  id: number;
  certificateNo: string;
  verificationCode: string;
  publicToken: string;
  batchId?: number;
  templateId: number;
  templateVersionId: number;
  templateName?: string;
  registrationId?: number;
  projectId?: number;
  teamId?: number;
  userId?: number;
  recipientName: string;
  recipientType: string;
  competitionTitle?: string;
  projectName?: string;
  teamName?: string;
  awardName?: string;
  issueDate?: string;
  expireDate?: string;
  dataJson?: string;
  certificateFileUrl?: string;
  status: CertificateRecordStatus;
  revokedReason?: string;
  revokedAt?: string;
  createdAt?: string;
};

export type CertificateGenerateResult = {
  batch: CertificateBatchRecord;
  records: CertificateRecord[];
};

export type CertificatePublicVerifyResult = {
  result: 'VALID' | 'REVOKED' | 'EXPIRED' | 'NOT_FOUND' | 'INVALID_CODE';
  certificateNo?: string;
  recipientName?: string;
  competitionTitle?: string;
  projectName?: string;
  awardName?: string;
  issueDate?: string;
  organizer?: string;
  status?: string;
  certificateFileUrl?: string;
  safeData?: Record<string, unknown>;
};
