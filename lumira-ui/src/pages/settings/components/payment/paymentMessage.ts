import { databaseMessage } from '@/i18n/databaseMessage';

const PAYMENT_MESSAGE_KEYS: Record<string, string> = {
  'Payment provider is disabled': 'payment.message.providerDisabled',
  'Payment provider is not configured': 'payment.message.providerNotConfigured',
  'Payment provider test failed': 'payment.message.providerTestFailed',
  'Payment provider is ready': 'payment.message.providerReady',
  'Payment connectivity test passed': 'payment.message.connectivityPassed',
};

const PAYMENT_MESSAGE_PREFIX_KEYS: Array<[string, string]> = [
  ['Missing required payment fields: ', 'payment.message.missingFields'],
  ['Payment connectivity test failed: ', 'payment.message.connectivityFailedWithReason'],
];

export const localizePaymentMessage = (value: string | null | undefined) => {
  if (!value) {
    return value || '';
  }

  const messageKey = PAYMENT_MESSAGE_KEYS[value];
  if (messageKey) {
    return databaseMessage(messageKey);
  }

  const prefixMessage = PAYMENT_MESSAGE_PREFIX_KEYS.find(([prefix]) => value.startsWith(prefix));
  if (!prefixMessage) {
    return value;
  }

  const [prefix, prefixKey] = prefixMessage;
  return databaseMessage(prefixKey, { reason: value.slice(prefix.length) });
};

export const paymentConnectivityStatusDisplayName = (lastTestSuccess: boolean | null | undefined) => {
  if (lastTestSuccess === true) {
    return databaseMessage('payment.connectivity.available');
  }
  if (lastTestSuccess === false) {
    return databaseMessage('payment.connectivity.unavailable');
  }
  return databaseMessage('payment.connectivity.notTested');
};

export const paymentConnectivityStatusDisplayName = (lastTestSuccess: boolean | null | undefined, english: boolean) => {
  if (lastTestSuccess === true) {
    return english ? 'Available' : '可用';
  }
  if (lastTestSuccess === false) {
    return english ? 'Unavailable' : '不可用';
  }
  return english ? 'Not tested' : '未测试';
};
