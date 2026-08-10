import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import {
  deriveCompetitionOverallWindow,
  preserveCompetitionTimelineSnapshot,
  sanitizeCompetitionSchedules,
} from './competitionSchedulePayload';

describe('competition schedule payload', () => {
  it('writes only material and review windows for a complete current schedule', () => {
    const schedules = sanitizeCompetitionSchedules([{
      timeMode: 'CONFIRMED',
      title: '初赛',
      materialRange: [dayjs('2026-08-01 09:00'), dayjs('2026-08-02 09:00')],
      reviewRange: [dayjs('2026-08-05 09:00'), dayjs('2026-08-06 09:00')],
    }]);

    expect(schedules).toEqual([{
      timeMode: 'CONFIRMED',
      title: '初赛',
      materialStart: '2026-08-01 09:00',
      materialEnd: '2026-08-02 09:00',
      reviewStart: '2026-08-05 09:00',
      reviewEnd: '2026-08-06 09:00',
    }]);
  });

  it('derives the overall window from the earliest material start and latest review end', () => {
    const result = deriveCompetitionOverallWindow([
      {
        timeMode: 'CONFIRMED',
        materialStart: '2026-09-01 09:00',
        materialEnd: '2026-09-02 09:00',
        reviewStart: '2026-09-03 09:00',
        reviewEnd: '2026-09-04 09:00',
      },
      {
        timeMode: 'CONFIRMED',
        materialStart: '2026-08-01 09:00',
        materialEnd: '2026-08-02 09:00',
        reviewStart: '2026-10-03 09:00',
        reviewEnd: '2026-10-04 09:00',
      },
    ]);

    expect(result).toEqual({
      competitionStart: '2026-08-01 09:00',
      competitionEnd: '2026-10-04 09:00',
    });
  });

  it('preserves an existing timeline snapshot byte-for-byte for non-time settings', () => {
    expect(preserveCompetitionTimelineSnapshot({
      scheduleJson: ' [{"timeMode":"CONFIRMED","stored":"opaque"}] ',
      registrationStart: '2026-07-01T09:00:30',
      registrationEnd: '2026-10-31T18:00:45',
      competitionStart: '2026-07-01T09:00:30',
      competitionEnd: '2026-11-01T09:00:15',
    })).toEqual({
      scheduleJson: ' [{"timeMode":"CONFIRMED","stored":"opaque"}] ',
      registrationStart: '2026-07-01T09:00:30',
      registrationEnd: '2026-10-31T18:00:45',
      competitionStart: '2026-07-01T09:00:30',
      competitionEnd: '2026-11-01T09:00:15',
    });
  });

  it('does not invent schedule JSON for an empty confirmed row', () => {
    expect(sanitizeCompetitionSchedules([{ timeMode: 'CONFIRMED' }])).toEqual([]);
    expect(deriveCompetitionOverallWindow([], '2026-07-01 09:00', undefined)).toEqual({
      competitionStart: '2026-07-01 09:00',
      competitionEnd: undefined,
    });
  });

  it('keeps partially entered current rows available for draft autosave', () => {
    expect(sanitizeCompetitionSchedules([{
      timeMode: 'CONFIRMED',
      title: '初赛',
      materialRange: ['2026-08-01 09:00', '2026-08-02 09:00'],
    }])).toEqual([{
      timeMode: 'CONFIRMED',
      title: '初赛',
      materialStart: '2026-08-01 09:00',
      materialEnd: '2026-08-02 09:00',
      reviewStart: undefined,
      reviewEnd: undefined,
    }]);
  });
});
