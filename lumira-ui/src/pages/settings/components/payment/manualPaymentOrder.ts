import type {
  PaymentCreateOrderRequest,
  PaymentOrderRecord,
  PaymentProviderSettings,
} from '@/types/api';
import { normalizePaymentEnvironment } from './paymentDisplay';

export type ManualPaymentScene = 'NATIVE' | 'H5' | 'JSAPI';

export type ManualPaymentFormValues = {
  providerCode: string;
  amountYuan: number;
  subject: string;
  scene?: ManualPaymentScene;
  clientIp?: string;
  openid?: string;
  productionConfirmed?: boolean;
};

export const MANUAL_PAYMENT_PROVIDER_CODES = ['alipay', 'wechat_pay', 'builtin_mock'] as const;

export const listManualPaymentProviders = (settings: PaymentProviderSettings[]) =>
  settings.filter((item) => (
    MANUAL_PAYMENT_PROVIDER_CODES.includes(item.providerCode as (typeof MANUAL_PAYMENT_PROVIDER_CODES)[number])
    && item.enabled
    && item.configured
  ));

export const listWechatManualScenes = (settings?: PaymentProviderSettings): ManualPaymentScene[] => {
  const enabled = settings?.enabledScenes?.map((scene) => scene.trim().toUpperCase()) || [];
  const supported = enabled.length > 0 ? enabled : ['NATIVE'];
  return supported.filter((scene): scene is ManualPaymentScene => (
    scene === 'NATIVE' || scene === 'H5' || scene === 'JSAPI'
  ));
};

export const buildManualPaymentOrderNo = (
  providerCode: string,
  environment: string,
  timestamp = Date.now(),
  randomSuffix = Math.random().toString(36).slice(2, 8).toUpperCase(),
) => {
  const provider = providerCode === 'wechat_pay' ? 'WX' : providerCode === 'alipay' ? 'ALI' : 'PAY';
  const env = normalizePaymentEnvironment(environment) === 'SANDBOX' ? 'S' : 'P';
  return `MAN-${provider}-${env}-${timestamp}-${randomSuffix}`;
};

export const buildManualPaymentRequest = ({
  values,
  settings,
  origin,
  timestamp,
  randomSuffix,
}: {
  values: ManualPaymentFormValues;
  settings: PaymentProviderSettings;
  origin: string;
  timestamp?: number;
  randomSuffix?: string;
}): PaymentCreateOrderRequest => {
  const environment = normalizePaymentEnvironment(settings.environment);
  const orderNo = buildManualPaymentOrderNo(
    settings.providerCode,
    environment,
    timestamp,
    randomSuffix,
  );
  const scene = settings.providerCode === 'wechat_pay' ? (values.scene || 'NATIVE') : undefined;
  const metadata: Record<string, unknown> = {
    bizType: 'manual_payment_verification',
    paymentEnvironment: environment,
  };
  if (scene) {
    metadata.paymentScene = scene;
  }
  if (scene === 'H5') {
    metadata.h5Type = 'Wap';
  }
  if (scene === 'JSAPI' && values.openid?.trim()) {
    metadata.openid = values.openid.trim();
  }

  return {
    providerCode: settings.providerCode,
    orderNo,
    subject: values.subject.trim(),
    amountMinor: Math.round(values.amountYuan * 100),
    currency: settings.currency?.trim().toUpperCase() || 'CNY',
    clientIp: scene === 'H5' ? values.clientIp?.trim() : undefined,
    returnUrl: `${origin}/settings/payment?tab=sandbox-orders&orderNo=${encodeURIComponent(orderNo)}`,
    metadata,
    idempotencyKey: orderNo,
  };
};

const requestMetadata = (order: PaymentOrderRecord): Record<string, unknown> => {
  const metadata = order.metadata?.metadata;
  return metadata && typeof metadata === 'object' && !Array.isArray(metadata)
    ? metadata as Record<string, unknown>
    : {};
};

export const resolveManualOrderEnvironment = (
  order: PaymentOrderRecord,
  settings: PaymentProviderSettings[],
) => {
  const stored = requestMetadata(order).paymentEnvironment;
  if (typeof stored === 'string' && stored.trim()) {
    return normalizePaymentEnvironment(stored);
  }
  const providerSettings = settings.find((item) => item.providerCode === order.providerCode);
  return normalizePaymentEnvironment(providerSettings?.environment) || '-';
};

export const resolveManualOrderScene = (order: PaymentOrderRecord) => {
  const stored = requestMetadata(order).paymentScene;
  return typeof stored === 'string' && stored.trim() ? stored.trim().toUpperCase() : undefined;
};

export const isWechatNativeOrder = (order?: PaymentOrderRecord) => (
  order?.providerCode === 'wechat_pay'
  && (resolveManualOrderScene(order) || 'NATIVE') === 'NATIVE'
);

export const isManualPaymentOrderCancellable = (order?: PaymentOrderRecord) => (
  order?.status === 'CREATED' || order?.status === 'PENDING'
);
