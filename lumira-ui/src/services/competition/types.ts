export type CompetitionLocale = 'zh' | 'en';
export type CompetitionStatus = 'draft' | 'published' | 'archived';
export type CompetitionFeeMode = 'TEAM' | 'MEMBER';

export interface CompetitionRecord {
  id: number;
  uuid?: string;
  competitionNo?: string;
  code: string;
  locale: string;
  title: string;
  shortName?: string | null;
  category: string;
  level?: string | null;
  competitionLevel?: string | null;
  organizer?: string | null;
  organizersJson?: string | null;
  registrationStart?: string | null;
  registrationEnd?: string | null;
  competitionStart: string;
  competitionEnd?: string | null;
  location: string;
  participationScope?: string | null;
  participationRequirement?: string | null;
  scheduleJson?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  contactName?: string | null;
  contactQrCodeUrl?: string | null;
  homepageContent?: string | null;
  tags?: string | null;
  status: CompetitionStatus;
  feeMode?: CompetitionFeeMode | null;
  entryFeeMinor?: number | null;
  currency?: string | null;
  featured: boolean;
  sort: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type CompetitionConfigItemType =
  | 'AGREEMENT'
  | 'CONSENT'
  | 'REGISTRATION_FIELD'
  | 'TEAM_FIELD'
  | 'MEMBER_FIELD'
  | 'PROJECT_FIELD'
  | 'TEAM_SETTINGS'
  | 'PAYMENT_SETTINGS'
  | 'REQUIRED_FILE'
  | 'STAGE_MATERIAL'
  | 'TIMELINE';

export interface CompetitionConfigSet {
  id: number;
  competitionUuid: string;
  version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CompetitionConfigItem {
  id?: number;
  competitionUuid?: string;
  configSetId?: number;
  itemType: CompetitionConfigItemType;
  itemKey: string;
  title: string;
  contentJson?: string | null;
  contentText?: string | null;
  sortOrder?: number;
  requiredFlag?: boolean;
  enabled?: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CompetitionSettingsRecord {
  competition: CompetitionRecord;
  activeConfigSet: CompetitionConfigSet;
  documents: CompetitionConfigItem[];
  fields: CompetitionConfigItem[];
  files: CompetitionConfigItem[];
  stageMaterials: CompetitionConfigItem[];
  timeline: CompetitionConfigItem[];
  payments: CompetitionConfigItem[];
}

export interface CompetitionUpsertPayload {
  code?: string;
  locale: string;
  title: string;
  shortName?: string;
  category: string;
  level?: string;
  competitionLevel?: string;
  organizer?: string;
  organizersJson?: string;
  registrationStart?: string;
  registrationEnd?: string;
  competitionStart: string;
  competitionEnd?: string;
  location: string;
  participationScope?: string;
  participationRequirement?: string;
  scheduleJson?: string;
  description?: string;
  imageUrl?: string;
  contactName?: string;
  contactQrCodeUrl?: string;
  homepageContent?: string;
  tags?: string;
  status: CompetitionStatus;
  feeMode?: CompetitionFeeMode;
  entryFeeMinor?: number;
  currency?: string;
  featured?: boolean;
  sort?: number;
}

export interface CompetitionQueryParams {
  keyword?: string;
  category?: string;
  locale?: string;
  status?: CompetitionStatus;
  featured?: boolean;
  pageNo?: number;
  pageSize?: number;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
}

export type ProjectStatus = 'draft' | 'published';
export type ProjectRating = 'all' | 'excellent' | 'popular' | 'new';

export interface ProjectRecord {
  id: number;
  code: string;
  locale?: string;
  title: string;
  category: string;
  description?: string | null;
  imageUrl?: string | null;
  ownerName?: string | null;
  rating?: ProjectRating | string;
  sort?: number;
  status?: ProjectStatus | string;
  tags?: string | null;
  ctaLabel?: string | null;
  ctaHref?: string | null;
  featured?: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ProjectUpsertPayload {
  code: string;
  locale?: string;
  title: string;
  category: string;
  description?: string;
  imageUrl?: string;
  ownerName?: string;
  rating?: ProjectRating;
  status?: ProjectStatus;
  sort?: number;
  tags?: string;
  ctaLabel?: string;
  ctaHref?: string;
  featured?: boolean;
}

export interface CompetitionRegistrationRecord {
  id: number;
  registrationNo: string;
  competitionId: number;
  teamId: number;
  projectId: number;
  ownerUserId?: number;
  status: string;
  feeMode: CompetitionFeeMode;
  entryFeeMinor: number;
  memberCount: number;
  payableAmountMinor: number;
  currency: string;
  paymentOrderNo?: string | null;
  participantNo?: string | null;
  teamSnapshotJson?: string | null;
  projectSnapshotJson?: string | null;
  memberSnapshotJson?: string | null;
  collectionSchemaSnapshotJson?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CompetitionStageRecord {
  id: number;
  competitionId: number;
  stageCode: string;
  stageName: string;
  status: string;
  sort: number;
  materialSubmitStart?: string | null;
  materialSubmitEnd?: string | null;
  reviewStart?: string | null;
  reviewEnd?: string | null;
  promotionRuleType?: string | null;
  promotionRuleValue?: number | null;
  promotionTiePolicy?: string | null;
  materialEditable?: boolean | null;
  materialAccessReason?: string | null;
}

export interface CompetitionStageUpsertPayload {
  stageCode: 'PRELIMINARY' | 'FINAL';
  stageName: string;
  status?: string;
  sort?: number;
  materialSubmitStart?: string | null;
  materialSubmitEnd?: string | null;
  reviewStart?: string | null;
  reviewEnd?: string | null;
  promotionRuleType?: string | null;
  promotionRuleValue?: number | null;
  promotionTiePolicy?: string | null;
}

export interface CompetitionStageReviewCandidateRecord {
  registrationId: number;
  registrationNo: string;
  competitionId: number;
  stageId: number;
  teamName: string;
  projectTitle: string;
  score?: number | null;
  decision: 'PENDING' | 'ADVANCED' | 'ELIMINATED';
  reviewComment?: string | null;
  publishedAt?: string | null;
  submittedAt?: string | null;
}

export interface CompetitionStageReviewDecisionPayload {
  score?: number | null;
  decision: 'PENDING' | 'ADVANCED' | 'ELIMINATED';
  comment?: string;
}

export interface CompetitionStageFormRecord {
  id: number;
  competitionId: number;
  stageId: number;
  formName: string;
  formSchemaJson: string;
  version: number;
  status: string;
}

export interface CompetitionStageFormUpsertPayload {
  formName: string;
  formSchemaJson: string;
  version?: number;
  status?: string;
}

export interface CompetitionPaymentOrderRecord {
  orderNo?: string | null;
  amountMinor: number;
  currency: string;
  status: string;
  paymentUrl?: string | null;
}

export interface CompetitionPaymentOptionRecord {
  providerCode: string;
  displayName: string;
  paymentScene: string;
  sortOrder?: number;
}

export interface CompetitionMaterialValueRecord {
  id: number;
  submissionId: number;
  fieldKey: string;
  fieldType: string;
  textValue?: string | null;
  fileId?: number | null;
  jsonValue?: string | null;
}

export interface CompetitionMaterialSubmissionRecord {
  id: number;
  registrationId: number;
  competitionId: number;
  stageId: number;
  formVersion: number;
  submitterUserId: number;
  status: string;
  submittedAt?: string | null;
  lockedAt?: string | null;
  values: CompetitionMaterialValueRecord[];
}
