import { describe, expect, it } from 'vitest';
import {
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

describe('buildRegistrationQuerySignature', () => {
  it('changes only when an applied filter changes', () => {
    const current = buildRegistrationQuerySignature({
      status: 'CONFIRMED',
      keyword: 'alpha',
    });

    expect(buildRegistrationQuerySignature({
      status: 'CONFIRMED',
      keyword: 'alpha',
    })).toBe(current);
    expect(buildRegistrationQuerySignature({
      status: 'PAID',
      keyword: 'alpha',
    })).not.toBe(current);
  });
});
