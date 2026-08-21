import type { MockSmsDelivery } from '@/types/api';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  modalInfo: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
}));

vi.mock('@/theme/antdFeedbackBridge', () => ({
  modal: { info: mocks.modalInfo },
  message: { success: mocks.messageSuccess, error: mocks.messageError },
}));

import {
  adaptVerificationChallengeResponse,
  copyMockSmsVerificationCode,
  presentMockSmsDelivery,
  resolveMockSmsCode,
} from './challengeResponseAdapter';

const delivery: MockSmsDelivery = {
  providerCode: 'builtin_mock_sms',
  phoneNumbers: '138****8000',
  signName: 'Lumira调试',
  templateCode: 'SMS_DEBUG_VERIFICATION',
  templateParam: '{"code":"123456"}',
  resultCode: 'OK',
  resultMessage: 'Mock SMS accepted',
  requestId: 'mock-request-1',
  bizId: 'mock-biz-1',
};

describe('verification challenge response adapter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('extracts only a string code from TemplateParam', () => {
    expect(resolveMockSmsCode(delivery)).toBe('123456');
    expect(resolveMockSmsCode({ ...delivery, templateParam: '{"code":123456}' })).toBe('');
    expect(resolveMockSmsCode({ ...delivery, templateParam: 'invalid-json' })).toBe('');
  });

  it('adapts an ordinary verification challenge response', () => {
    adaptVerificationChallengeResponse({ challengeId: 'challenge-1', mockSmsDelivery: delivery });
    expect(mocks.modalInfo).toHaveBeenCalledOnce();
  });

  it('adapts second-factor options already returned by login', () => {
    adaptVerificationChallengeResponse({
      accessToken: '',
      secondFactorOptions: [{ factorCode: 'sms', mockSmsDelivery: delivery }],
    });
    expect(mocks.modalInfo).toHaveBeenCalledOnce();
  });

  it('ignores responses without mock delivery and non-mock providers', () => {
    adaptVerificationChallengeResponse({ challengeId: 'challenge-2' });
    presentMockSmsDelivery({ ...delivery, providerCode: 'aliyun' } as unknown as MockSmsDelivery);
    expect(mocks.modalInfo).not.toHaveBeenCalled();
  });

  it('copies the verification code and reports success', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    await copyMockSmsVerificationCode('123456');

    expect(writeText).toHaveBeenCalledWith('123456');
    expect(mocks.messageSuccess).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });
});
