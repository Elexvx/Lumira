import type { ReviewInvitation } from '@/services/review/types';

export const mergeReviewInvitationStatus = (
  current: ReviewInvitation | undefined,
  status: ReviewInvitation,
): ReviewInvitation => {
  const currentExpirySecond = current?.qrExpiresAt?.replace(/\.\d+$/, '');
  const statusExpirySecond = status.qrExpiresAt?.replace(/\.\d+$/, '');
  if (
    status.checkinStatus !== 'CHECKED_IN'
    && current?.qrValue
    && currentExpirySecond === statusExpirySecond
  ) {
    return { ...status, qrValue: current.qrValue };
  }
  return status;
};
