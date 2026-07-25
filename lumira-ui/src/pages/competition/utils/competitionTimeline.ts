import dayjs, { type Dayjs } from 'dayjs';

export type CompetitionTimelineRange = [Dayjs, Dayjs] | [string, string];

const toValidDayjs = (value?: Dayjs | string) => {
  if (!value) {
    return undefined;
  }
  const parsed = dayjs.isDayjs(value) ? value : dayjs(value);
  return parsed.isValid() ? parsed : undefined;
};

const getCompleteRange = (range?: CompetitionTimelineRange) => {
  if (!Array.isArray(range) || range.length !== 2) {
    return undefined;
  }
  const start = toValidDayjs(range[0]);
  const end = toValidDayjs(range[1]);
  return start && end ? [start, end] as const : undefined;
};

export const isScheduleAtOrAfterRegistrationEnd = (
  scheduleRange?: CompetitionTimelineRange,
  registrationRange?: CompetitionTimelineRange,
) => {
  const scheduleBounds = getCompleteRange(scheduleRange);
  const registrationBounds = getCompleteRange(registrationRange);
  if (!scheduleBounds || !registrationBounds) {
    return false;
  }
  return !scheduleBounds[0].isBefore(registrationBounds[1]);
};
