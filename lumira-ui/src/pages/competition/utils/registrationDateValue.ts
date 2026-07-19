import dayjs, { type Dayjs } from 'dayjs';

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
