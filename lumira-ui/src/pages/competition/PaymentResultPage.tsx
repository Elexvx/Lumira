import { Button, Result, Space, Typography } from 'antd';
import { history, useLocation } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { getRegistration, getRegistrationPaymentStatus } from '@/services/competition/api';
import type { CompetitionPaymentOrderRecord, CompetitionRegistrationRecord } from '@/services/competition/types';
import { showErrorMessage } from '@/utils/errorMessage';
import {
  createCleanPaymentResultSearch,
  isPaymentOrderFailed,
  isRegistrationPaymentSuccessful,
  parsePaymentResultRegistrationId,
} from './utils/registrationCheckout';

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
      if (showError) showErrorMessage(error, '支付结果加载失败');
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
  const title = successful ? '付款成功，报名已确认' : failed ? '付款未完成' : '正在确认付款结果';
  const subTitle = successful
    ? `报名编号：${registration?.registrationNo || registrationId}${registration?.participantNo ? `，参赛编号：${registration.participantNo}` : ''}`
    : failed
      ? '未查询到有效的支付成功结果。你可以返回报名记录后重新支付。'
      : `报名编号：${registration?.registrationNo || registrationId || '-'}。支付回调可能稍有延迟，本页会自动刷新。`;

  return (
    <ManagementPage title="支付结果">
      <ManagementPageBody>
        <Result
          status={resultStatus}
          title={loading ? '正在查询付款结果…' : title}
          subTitle={subTitle}
          extra={
            <Space wrap>
              {successful ? <Button type="primary" onClick={() => history.push('/')}>返回首页</Button> : null}
              {!successful ? <Button type="primary" loading={loading} onClick={() => void refresh(true)}>刷新结果</Button> : null}
              <Button onClick={() => history.push('/competitions/register')}>返回报名记录</Button>
            </Space>
          }
        >
          {!successful && !failed ? <Typography.Text type="secondary">请勿重复创建订单；确认结果前可以安全刷新页面。</Typography.Text> : null}
        </Result>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default PaymentResultPage;
