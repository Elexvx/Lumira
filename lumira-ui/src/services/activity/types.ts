export type ActivityLocale = 'zh' | 'en';
export type ActivityStatus = 'draft' | 'published';
export type ActivityBadgeTone = 'blue' | 'gold' | 'silver' | 'bronze' | 'slate' | 'dark';

export interface ActivityRecord {
  id: number;
  code: string;
  locale: string;
  title: string;
  subtitle?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  iconKey?: string | null;
  sort: number;
  status: ActivityStatus;
  tags?: string | null;
  ctaLabel?: string | null;
  ctaHref?: string | null;
  badgeText?: string | null;
  badgeTone?: ActivityBadgeTone | null;
  activityDate: string;
  activityTime: string;
  location: string;
  featured: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ActivityUpsertPayload {
  code?: string;
  locale: string;
  title: string;
  subtitle?: string;
  description?: string;
  imageUrl?: string;
  iconKey?: string;
  sort?: number;
  status: ActivityStatus;
  tags?: string;
  ctaLabel?: string;
  ctaHref?: string;
  badgeText?: string;
  badgeTone?: ActivityBadgeTone;
  activityDate: string;
  activityTime: string;
  location: string;
  featured?: boolean;
}

export interface ActivityQueryParams {
  keyword?: string;
  locale?: string;
  status?: ActivityStatus;
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
