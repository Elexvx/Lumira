import { describe, expect, it } from 'vitest';
import {
  buildCompetitionRegistrationExportRequest,
  buildRegistrationQuerySignature,
  resolveRegistrationExportScope,
} from './registrationExportScope';

describe('resolveRegistrationExportScope', () => {
  it('disables actions until a competition with results is loaded', () => {
    expect(resolveRegistrationExportScope({
      hasCompetition: false,
      filteredCount: 0,
      selectedCount: 0,
    })).toEqual({
      mode: 'filtered',
      count: 0,
      disabled: true,
      exportLabel: '仅导出筛选报名记录',
      materialPackageLabel: '导出筛选完整材料',
    });
  });

  it('uses the filtered result count when no team is selected', () => {
    expect(resolveRegistrationExportScope({
      hasCompetition: true,
      filteredCount: 23,
      selectedCount: 0,
    })).toEqual({
      mode: 'filtered',
      count: 23,
      disabled: false,
      exportLabel: '仅导出筛选报名记录（23）',
      materialPackageLabel: '导出筛选完整材料（23）',
    });
  });

  it('lets selected teams override the filtered result scope', () => {
    expect(resolveRegistrationExportScope({
      hasCompetition: true,
      filteredCount: 23,
      selectedCount: 3,
    })).toEqual({
      mode: 'selected',
      count: 3,
      disabled: false,
      exportLabel: '仅导出所选报名记录（3）',
      materialPackageLabel: '导出所选完整材料（3）',
    });
  });
});

describe('buildCompetitionRegistrationExportRequest', () => {
  it('uses current filters when nothing is selected', () => {
    expect(buildCompetitionRegistrationExportRequest({
      competitionId: 88,
      status: 'CONFIRMED',
      keyword: 'alpha',
    }, [])).toEqual({
      competitionId: 88,
      status: 'CONFIRMED',
      keyword: 'alpha',
    });
  });

  it('uses only unique selected ids when a selection exists', () => {
    expect(buildCompetitionRegistrationExportRequest({
      competitionId: 88,
      status: 'CONFIRMED',
      keyword: 'alpha',
    }, [101, 101, 102, -1])).toEqual({
      competitionId: 88,
      registrationIds: [101, 102],
    });
  });

  it('does not build an export request without a competition', () => {
    expect(buildCompetitionRegistrationExportRequest({}, [101])).toBeUndefined();
  });
});

describe('buildRegistrationQuerySignature', () => {
  it('changes only when an applied filter changes', () => {
    const current = buildRegistrationQuerySignature({
      competitionId: 88,
      status: 'CONFIRMED',
      keyword: 'alpha',
    });

    expect(buildRegistrationQuerySignature({
      competitionId: 88,
      status: 'CONFIRMED',
      keyword: 'alpha',
    })).toBe(current);
    expect(buildRegistrationQuerySignature({
      competitionId: 88,
      status: 'PAID',
      keyword: 'alpha',
    })).not.toBe(current);
  });
});
