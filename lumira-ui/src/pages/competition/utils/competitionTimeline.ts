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

export const isChronologicalTimeRange = (range?: CompetitionTimelineRange) => {
  const bounds = getCompleteRange(range);
  return Boolean(bounds && bounds[1].isAfter(bounds[0]));
};

export const isTimeRangeAtOrAfterPreviousEnd = (
  range?: CompetitionTimelineRange,
  previousRange?: CompetitionTimelineRange,
) => {
  const bounds = getCompleteRange(range);
  const previousBounds = getCompleteRange(previousRange);
  if (!bounds || !previousBounds) {
    return false;
  }
  return !bounds[0].isBefore(previousBounds[1]);
};

export const isScheduleAtOrAfterRegistrationEnd = (
  scheduleRange?: CompetitionTimelineRange,
  registrationRange?: CompetitionTimelineRange,
) => isTimeRangeAtOrAfterPreviousEnd(scheduleRange, registrationRange);
