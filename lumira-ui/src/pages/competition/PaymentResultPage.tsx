import { Button, Result, Space, Typography } from 'antd';
import { getLocale, history, useLocation } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { normalizeLocale } from '@/i18n/locale';
import { getRegistration, getRegistrationPaymentStatus } from '@/services/competition/api';
import type { CompetitionPaymentOrderRecord, CompetitionRegistrationRecord } from '@/services/competition/types';
import { showErrorMessage } from '@/utils/errorMessage';
import {
  createCleanPaymentResultSearch,
  isPaymentOrderFailed,
  isRegistrationPaymentSuccessful,
  parsePaymentResultRegistrationId,
} from './utils/registrationCheckout';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const PaymentResultPage = () => {
  const location = useLocation();
  const registrationId = parsePaymentResultRegistrationId(location.search);
  const [registration, setRegistration] = useState<CompetitionRegistrationRecord>();
  const [paymentOrder, setPaymentOrder] = useState<CompetitionPaymentOrderRecord>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const refresh = useCallback(async (showError = false) => {
    if (!registrationId) {
      setLoadError(true);
      setLoading(false);
      return;
    }
    try {
      const latestRegistration = await getRegistration(registrationId);
      setRegistration(latestRegistration);
      setLoadError(false);
      if (latestRegistration.payableAmountMinor > 0 && !isRegistrationPaymentSuccessful(latestRegistration.status)) {
        try {
          setPaymentOrder(await getRegistrationPaymentStatus(registrationId));
        } catch {
          setPaymentOrder(undefined);
        }
      }
    } catch (error) {
      setLoadError(true);
      if (showError) showErrorMessage(error, t('支付结果加载失败', 'Failed to load the payment result'));
    } finally {
      setLoading(false);
    }
  }, [registrationId]);

  useEffect(() => {
    if (!registrationId) return;
    const cleanSearch = createCleanPaymentResultSearch(registrationId);
    if (location.search !== cleanSearch) {
      history.replace({ pathname: location.pathname, search: cleanSearch });
    }
  }, [location.pathname, location.search, registrationId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!registrationId || isRegistrationPaymentSuccessful(registration?.status) || isPaymentOrderFailed(paymentOrder?.status)) {
      return;
    }
    const timer = window.setInterval(() => void refresh(), 3000);
    return () => window.clearInterval(timer);
  }, [paymentOrder?.status, refresh, registration?.status, registrationId]);

  const successful = isRegistrationPaymentSuccessful(registration?.status);
  const failed = loadError || isPaymentOrderFailed(paymentOrder?.status);
  const resultStatus = successful ? 'success' : failed ? 'error' : 'info';
  const title = successful
    ? t('付款成功，报名已确认', 'Payment successful; registration confirmed')
    : failed
      ? t('付款未完成', 'Payment incomplete')
      : t('正在确认付款结果', 'Confirming payment result');
  const subTitle = successful
    ? t(
      `报名编号：${registration?.registrationNo || registrationId}${registration?.participantNo ? `，参赛编号：${registration.participantNo}` : ''}`,
      `Registration No.: ${registration?.registrationNo || registrationId}${registration?.participantNo ? `; Participant No.: ${registration.participantNo}` : ''}`,
    )
    : failed
      ? t(
        '未查询到有效的支付成功结果。你可以返回报名记录后重新支付。',
        'No valid successful payment was found. Return to your registrations to try again.',
      )
      : t(
        `报名编号：${registration?.registrationNo || registrationId || '-'}。支付回调可能稍有延迟，本页会自动刷新。`,
        `Registration No.: ${registration?.registrationNo || registrationId || '-'}. The payment callback may be delayed; this page refreshes automatically.`,
      );

  return (
    <ManagementPage title={t('支付结果', 'Payment result')}>
      <ManagementPageBody>
        <Result
          status={resultStatus}
          title={loading ? t('正在查询付款结果…', 'Checking payment result…') : title}
          subTitle={subTitle}
          extra={
            <Space wrap>
              {successful ? <Button type="primary" onClick={() => history.push('/')}>{t('返回首页', 'Back to home')}</Button> : null}
              {!successful ? <Button type="primary" loading={loading} onClick={() => void refresh(true)}>{t('刷新结果', 'Refresh result')}</Button> : null}
              <Button onClick={() => history.push('/competitions/register')}>{t('返回报名记录', 'Back to registrations')}</Button>
            </Space>
          }
        >
          {!successful && !failed
            ? <Typography.Text type="secondary">{t('请勿重复创建订单；确认结果前可以安全刷新页面。', 'Do not create duplicate orders. You may safely refresh while confirmation is pending.')}</Typography.Text>
            : null}
        </Result>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default PaymentResultPage;
