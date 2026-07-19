import dayjs, { type Dayjs } from 'dayjs';

const REGISTRATION_YEAR_FIELD_KEYS = new Set(['enrollmentdate', 'graduationdate']);

export const isRegistrationYearField = (itemKey?: string | null) => (
  REGISTRATION_YEAR_FIELD_KEYS.has((itemKey || '').replace(/[^a-z0-9]/gi, '').toLowerCase())
);

export const normalizeRegistrationDateValue = (value: unknown): Dayjs | undefined => {
  if (dayjs.isDayjs(value)) {
    return value.isValid() ? value : undefined;
  }
  if (typeof value !== 'string' || !value.trim()) {
    return undefined;
  }
  const parsed = dayjs(value);
  if (parsed.isValid()) {
    return parsed;
  }
  const dottedDate = dayjs(value.replace(/^(\d{4})\.(\d{1,2})\.(\d{1,2})/, '$1-$2-$3'));
  return dottedDate.isValid() ? dottedDate : undefined;
};

export const formatRegistrationYearValue = (value: unknown): string | undefined => (
  normalizeRegistrationDateValue(value)?.format('YYYY')
);
