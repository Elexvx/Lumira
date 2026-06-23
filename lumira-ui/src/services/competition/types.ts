export type CompetitionLocale = 'zh' | 'en';
export type CompetitionStatus = 'draft' | 'published' | 'archived';

export interface CompetitionRecord {
  id: number;
  tenantId?: number;
  code: string;
  locale: CompetitionLocale;
  title: string;
  category: string;
  level?: string | null;
  organizer?: string | null;
  registrationStart?: string | null;
  registrationEnd?: string | null;
  competitionStart: string;
  competitionEnd?: string | null;
  location: string;
  description?: string | null;
  imageUrl?: string | null;
  tags?: string | null;
  status: CompetitionStatus;
  featured: boolean;
  sort: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CompetitionUpsertPayload {
  code?: string;
  locale: CompetitionLocale;
  title: string;
  category: string;
  level?: string;
  organizer?: string;
  registrationStart?: string;
  registrationEnd?: string;
  competitionStart: string;
  competitionEnd?: string;
  location: string;
  description?: string;
  imageUrl?: string;
  tags?: string;
  status: CompetitionStatus;
  featured?: boolean;
  sort?: number;
}

export interface CompetitionQueryParams {
  keyword?: string;
  category?: string;
  locale?: CompetitionLocale;
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
