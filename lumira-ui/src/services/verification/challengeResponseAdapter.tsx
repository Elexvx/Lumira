import { formatMessage } from '@/i18n/formatMessage';
import { registerRequestSuccessAdapter } from '@/services/common/requestSuccessAdapters';
import { message, modal } from '@/theme/antdFeedbackBridge';
import type { MockSmsDelivery } from '@/types/api';
import { Alert, Button, Descriptions, Space, Typography } from 'antd';

type VerificationChallengeResponse = {
  mockSmsDelivery?: MockSmsDelivery | null;
  secondFactorOptions?: Array<{ mockSmsDelivery?: MockSmsDelivery | null }> | null;
};

export const resolveMockSmsCode = (delivery?: MockSmsDelivery | null) => {
  if (!delivery?.templateParam) {
    return '';
  }
  try {
    const parameters = JSON.parse(delivery.templateParam) as { code?: unknown };
    return typeof parameters.code === 'string' ? parameters.code : '';
  } catch {
    return '';
  }
};

export const copyMockSmsVerificationCode = async (code: string) => {
  if (!code || typeof navigator === 'undefined' || !navigator.clipboard) {
    message.error(formatMessage({
      id: 'mockSms.modal.copyFailed',
      defaultMessage: '复制失败，请手动复制验证码',
    }));
    return;
  }
  try {
    await navigator.clipboard.writeText(code);
    message.success(formatMessage({
      id: 'mockSms.modal.copySuccess',
      defaultMessage: '验证码已复制',
    }));
  } catch {
    message.error(formatMessage({
      id: 'mockSms.modal.copyFailed',
      defaultMessage: '复制失败，请手动复制验证码',
    }));
  }
};

export const presentMockSmsDelivery = (delivery?: MockSmsDelivery | null) => {
  if (!delivery || delivery.providerCode !== 'builtin_mock_sms') {
    return;
  }
  const code = resolveMockSmsCode(delivery);
  modal.info({
    title: formatMessage({ id: 'mockSms.modal.title', defaultMessage: '模拟短信验证码' }),
    width: 560,
    okText: formatMessage({ id: 'mockSms.modal.close', defaultMessage: '关闭' }),
    content: (
      <Space orientation="vertical" size="middle" style={{ width: '100%', marginTop: 12 }}>
        <Alert
          type="warning"
          showIcon
          message={formatMessage({
            id: 'mockSms.modal.debugOnly',
            defaultMessage: '仅用于本地调试，不会发送真实短信',
          })}
        />
        <Space orientation="vertical" size="small" style={{ width: '100%', alignItems: 'center' }}>
          <Typography.Text type="secondary">
            {formatMessage({ id: 'mockSms.modal.code', defaultMessage: '验证码' })}
          </Typography.Text>
          <Typography.Title level={2} copyable={{ text: code }} style={{ margin: 0, letterSpacing: 8 }}>
            {code || '-'}
          </Typography.Title>
          <Button type="primary" disabled={!code} onClick={() => void copyMockSmsVerificationCode(code)}>
            {formatMessage({ id: 'mockSms.modal.copy', defaultMessage: '复制验证码' })}
          </Button>
        </Space>
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="PhoneNumbers">{delivery.phoneNumbers || '-'}</Descriptions.Item>
          <Descriptions.Item label="SignName">{delivery.signName || '-'}</Descriptions.Item>
          <Descriptions.Item label="TemplateCode">{delivery.templateCode || '-'}</Descriptions.Item>
          <Descriptions.Item label="TemplateParam">
            <Typography.Text code>{delivery.templateParam || '-'}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="RequestId">{delivery.requestId || '-'}</Descriptions.Item>
          <Descriptions.Item label="BizId">{delivery.bizId || '-'}</Descriptions.Item>
        </Descriptions>
      </Space>
    ),
  });
};

export const adaptVerificationChallengeResponse = (data: unknown) => {
  if (!data || typeof data !== 'object') {
    return;
  }
  const response = data as VerificationChallengeResponse;
  presentMockSmsDelivery(response.mockSmsDelivery);
  response.secondFactorOptions?.forEach((option) => presentMockSmsDelivery(option.mockSmsDelivery));
};

registerRequestSuccessAdapter(adaptVerificationChallengeResponse, 'verification-challenge-response');
