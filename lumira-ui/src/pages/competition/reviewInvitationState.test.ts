import { describe, expect, it } from 'vitest';
import type { ReviewInvitation } from '@/services/review/types';
import { mergeReviewInvitationStatus } from './reviewInvitationState';

const invitation = (overrides: Partial<ReviewInvitation> = {}): ReviewInvitation => ({
  invitationId: 1,
  batchId: 2,
  batchName: 'Initial review',
  expertId: 3,
  expertName: 'Expert',
  status: 'QR_ISSUED',
  checkinStatus: 'WAITING',
  qrExpiresAt: '2026-08-20T12:05:00',
  ...overrides,
});

describe('mergeReviewInvitationStatus', () => {
  it('preserves the one-time QR value while polling the same QR issuance', () => {
    const current = invitation({ qrValue: 'one-time-qr', qrExpiresAt: '2026-08-20T12:05:00.812345' });
    const status = invitation({ qrValue: null });

    expect(mergeReviewInvitationStatus(current, status).qrValue).toBe('one-time-qr');
  });

  it('drops a stale QR value when another issuance replaces it', () => {
    const current = invitation({ qrValue: 'stale-qr' });
    const status = invitation({ qrValue: null, qrExpiresAt: '2026-08-20T12:10:00' });

    expect(mergeReviewInvitationStatus(current, status).qrValue).toBeNull();
  });

  it('does not retain a QR value after check-in', () => {
    const current = invitation({ qrValue: 'used-qr' });
    const status = invitation({
      status: 'CHECKED_IN',
      checkinStatus: 'CHECKED_IN',
      qrValue: null,
      checkedInAt: '2026-08-20T12:01:00',
    });

    expect(mergeReviewInvitationStatus(current, status).qrValue).toBeNull();
  });
});
