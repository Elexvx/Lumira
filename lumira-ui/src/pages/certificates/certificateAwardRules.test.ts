import { describe, expect, it } from 'vitest';
import type { CertificateAwardGrant } from '@/services/certificates/types';
import {
  haveAwardGrantsChanged,
  selectableAwardGrantIds,
  summarizeAwardGrants,
  validateCertificateAwardRules,
} from './certificateAwardRules';

describe('certificate award rules', () => {
  it('accepts non-overlapping award tiers', () => {
    expect(validateCertificateAwardRules([
      { awardName: '一等奖', minRank: 1, maxRank: 1 },
      { awardName: '二等奖', minRank: 2, maxRank: 3 },
      { awardName: '三等奖', minRank: 4, maxRank: 10 },
    ])).toBeUndefined();
  });

  it('rejects overlapping and inverted rank ranges', () => {
    expect(validateCertificateAwardRules([
      { awardName: '一等奖', minRank: 1, maxRank: 3 },
      { awardName: '二等奖', minRank: 3, maxRank: 5 },
    ])).toContain('不能重叠');
    expect(validateCertificateAwardRules([
      { awardName: '一等奖', minRank: 2, maxRank: 1 },
    ])).toContain('范围无效');
  });

  it('only selects grants that have not been issued', () => {
    const grants = [
      { id: 1, status: 'GRANTED' },
      { id: 2, status: 'ISSUED', certificateRecordId: 20 },
      { id: 3, status: 'GRANTED', certificateRecordId: 30 },
    ] as CertificateAwardGrant[];

    expect(selectableAwardGrantIds(grants)).toEqual([1]);
    expect(summarizeAwardGrants(grants)).toEqual({
      total: 3,
      pending: 1,
      issued: 2,
      revoked: 0,
    });
  });

  it('distinguishes an idempotent replay from a reconciled grant state', () => {
    const granted = [{ id: 1, awardName: '一等奖', rankNo: 1, status: 'GRANTED' }] as CertificateAwardGrant[];
    const unchanged = [{ ...granted[0] }] as CertificateAwardGrant[];
    const revoked = [{ ...granted[0], status: 'REVOKED' }] as CertificateAwardGrant[];

    expect(haveAwardGrantsChanged(granted, unchanged)).toBe(false);
    expect(haveAwardGrantsChanged(granted, revoked)).toBe(true);
    expect(haveAwardGrantsChanged(granted, [...granted, { id: 2 } as CertificateAwardGrant])).toBe(true);
  });
});
