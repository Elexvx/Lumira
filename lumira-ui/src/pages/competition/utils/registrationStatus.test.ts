import { describe, expect, it } from 'vitest';
import {
  getRegistrationStatusLabel,
  registrationStatusValueEnum,
} from './registrationStatus';

describe('registration status presentation', () => {
  it('labels an unpaid registration as pending payment', () => {
    expect(getRegistrationStatusLabel('PENDING_PAYMENT')).toBe('待付款');
    expect(registrationStatusValueEnum.PENDING_PAYMENT.text).toBe('待付款');
  });

  it('normalizes backend statuses and preserves unknown values', () => {
    expect(getRegistrationStatusLabel(' paid ')).toBe('已支付');
    expect(getRegistrationStatusLabel(undefined, 'DRAFT')).toBe('草稿');
    expect(getRegistrationStatusLabel('AWAITING_REVIEW')).toBe('AWAITING_REVIEW');
  });
});
