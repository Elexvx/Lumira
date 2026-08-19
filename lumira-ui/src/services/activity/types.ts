export type ActivityLocale = 'zh' | 'en';
export type ActivityStatus = 'draft' | 'published';
export type ActivityRegistrationFieldType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'DATE'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'MOBILE'
  | 'EMAIL';

export interface ActivityRegistrationField {
  fieldKey: string;
  label: string;
  fieldType: ActivityRegistrationFieldType;
  placeholder?: string | null;
  description?: string | null;
  required: boolean;
  options?: string[];
}

export type ActivityRegistrationValue = string | number | string[] | null;

export interface ActivityRegistrationAnswer {
  fieldKey: string;
  label: string;
  fieldType: ActivityRegistrationFieldType;
  value: ActivityRegistrationValue;
}

export interface ActivityRecord {
  id: number;
  code: string;
  locale: string;
  title: string;
  subtitle?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  sort: number;
  status: ActivityStatus;
  tags?: string | null;
  ctaLabel?: string | null;
  ctaHref?: string | null;
  activityDate: string;
  activityTime: string;
  location: string;
  featured: boolean;
  registrationFields: ActivityRegistrationField[];
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface PublicActivityRecord {
  id: number;
  locale: string;
  title: string;
  subtitle?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  tags?: string | null;
  ctaLabel?: string | null;
  ctaHref?: string | null;
  activityDate: string;
  activityTime: string;
  location: string;
  featured: boolean;
  registrationFields: ActivityRegistrationField[];
}

export interface ActivityUpsertPayload {
  code?: string;
  locale: string;
  title: string;
  subtitle?: string;
  description?: string;
  imageUrl?: string;
  sort?: number;
  status: ActivityStatus;
  tags?: string;
  ctaLabel?: string;
  ctaHref?: string;
  activityDate: string;
  activityTime: string;
  location: string;
  featured?: boolean;
  registrationFields?: ActivityRegistrationField[];
}

export interface ActivityQueryParams {
  keyword?: string;
  locale?: string;
  status?: ActivityStatus;
  featured?: boolean;
  pageNo?: number;
  pageSize?: number;
}

export interface ActivityRegistrationPayload {
  activityId: number;
  answers: Record<string, ActivityRegistrationValue>;
  name?: string;
  mobile?: string;
  email?: string;
  organization?: string;
  position?: string;
  remark?: string;
}

export interface ActivityRegistrationRecord extends Omit<ActivityRegistrationPayload, 'answers'> {
  id: number;
  applicationNo: string;
  activityTitle: string;
  status: 'SUBMITTED';
  submittedAt: string;
  ownerUserId?: number | null;
  ownerUsername?: string | null;
  answers: ActivityRegistrationAnswer[];
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  hasMore?: boolean;
}
