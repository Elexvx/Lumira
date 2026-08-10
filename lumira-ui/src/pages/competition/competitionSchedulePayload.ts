import type { Dayjs } from 'dayjs';

export type CompetitionTimeMode = 'CONFIRMED' | 'TBD';

export type CompetitionScheduleFormItem = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  materialRange?: [Dayjs, Dayjs] | [string, string];
  reviewRange?: [Dayjs, Dayjs] | [string, string];
};

export type CompetitionJsonSchedule = {
  timeMode?: CompetitionTimeMode;
  title?: string;
  materialStart?: string;
  materialEnd?: string;
  reviewStart?: string;
  reviewEnd?: string;
};

export type CompetitionTimelineSnapshot = {
  scheduleJson?: string | null;
  registrationStart?: string | null;
  registrationEnd?: string | null;
  competitionStart?: string | null;
  competitionEnd?: string | null;
};

const normalizeText = (value?: unknown) => {
  if (value === undefined || value === null) {
    return undefined;
  }
  const normalized = String(value).trim();
  return normalized || undefined;
};

const formatRangeValue = (value?: Dayjs | string) => {
  if (!value) {
    return undefined;
  }
  return typeof value === 'string' ? normalizeText(value) : value.format('YYYY-MM-DD HH:mm');
};

const hasCurrentScheduleWindow = (schedule: CompetitionJsonSchedule) => Boolean(
  schedule.materialStart
    && schedule.materialEnd
    && schedule.reviewStart
    && schedule.reviewEnd,
);

export const sanitizeCompetitionSchedules = (
  schedules?: CompetitionScheduleFormItem[],
): CompetitionJsonSchedule[] => {
  const normalized = (schedules || [])
    .map((item) => {
      if (item.timeMode !== 'CONFIRMED') {
        return { timeMode: 'TBD' as const };
      }
      const [materialStartValue, materialEndValue] = item.materialRange || [];
      const [reviewStartValue, reviewEndValue] = item.reviewRange || [];
      return {
        timeMode: 'CONFIRMED' as const,
        title: normalizeText(item.title),
        materialStart: formatRangeValue(materialStartValue),
        materialEnd: formatRangeValue(materialEndValue),
        reviewStart: formatRangeValue(reviewStartValue),
        reviewEnd: formatRangeValue(reviewEndValue),
      };
    })
    .filter((item) => (
      item.timeMode === 'TBD'
        || item.title
        || item.materialStart
        || item.materialEnd
        || item.reviewStart
        || item.reviewEnd
    ));
  const confirmedSchedules = normalized.filter((item) => item.timeMode === 'CONFIRMED');
  if (confirmedSchedules.length) {
    return confirmedSchedules;
  }
  return normalized.some((item) => item.timeMode === 'TBD') ? [{ timeMode: 'TBD' }] : [];
};

const earliest = (values: string[]) => values.reduce(
  (result, value) => (value.localeCompare(result) < 0 ? value : result),
);

const latest = (values: string[]) => values.reduce(
  (result, value) => (value.localeCompare(result) > 0 ? value : result),
);

export const deriveCompetitionOverallWindow = (
  schedules: CompetitionJsonSchedule[],
  fallbackStart?: string | null,
  fallbackEnd?: string | null,
) => {
  if (schedules.length > 0 && schedules.every((item) => item.timeMode === 'TBD')) {
    return { competitionStart: 'TBD', competitionEnd: undefined };
  }

  const confirmedSchedules = schedules.filter((item) => item.timeMode === 'CONFIRMED');
  if (confirmedSchedules.length > 0 && confirmedSchedules.every(hasCurrentScheduleWindow)) {
    return {
      competitionStart: earliest(confirmedSchedules.map((item) => item.materialStart!)),
      competitionEnd: latest(confirmedSchedules.map((item) => item.reviewEnd!)),
    };
  }

  return {
    competitionStart: normalizeText(fallbackStart) || 'TBD',
    competitionEnd: normalizeText(fallbackEnd),
  };
};

export const preserveCompetitionTimelineSnapshot = (
  snapshot: CompetitionTimelineSnapshot,
) => ({
  scheduleJson: snapshot.scheduleJson ?? undefined,
  registrationStart: snapshot.registrationStart ?? undefined,
  registrationEnd: snapshot.registrationEnd ?? undefined,
  competitionStart: snapshot.competitionStart ?? 'TBD',
  competitionEnd: snapshot.competitionEnd ?? undefined,
});
