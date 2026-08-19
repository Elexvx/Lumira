import { Button, Result, Space, Typography } from 'antd';
import { history, useLocation, useModel } from '@umijs/max';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { databaseMessage } from '@/i18n/databaseMessage';
import { request } from '@/services/common/request';
import { buildRegistrationDraftStorageKey } from './utils/registrationDraftStorageKey';
import {
  clearLocalRegistrationDraft,
  isRegistrationDraftForRegistration,
  readLocalRegistrationDraft,
  type RegistrationDraftEnvelope,
} from './utils/registrationDraftPersistence';

const t = databaseMessage;

const PaymentResultPage = () => {
  const { initialState } = useModel('@@initialState');
  const location = useLocation();
  const registrationId = parsePaymentResultRegistrationId(location.search);
  const registrationDraftStorageKey = useMemo(
    () => buildRegistrationDraftStorageKey(initialState?.currentUser?.userId),
    [initialState?.currentUser?.userId],
  );
  const clearedSuccessfulDraftRef = useRef(false);
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
      if (showError) showErrorMessage(error, t('ui.competition.paymentresult.failedToLoadThePaymentResult'));
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

  useEffect(() => {
    const currentUserId = initialState?.currentUser?.userId;
    if (!successful || registrationId == null || currentUserId == null || clearedSuccessfulDraftRef.current) {
      return;
    }
    clearedSuccessfulDraftRef.current = true;
    const localDraft = readLocalRegistrationDraft<unknown>(currentUserId);
    if (isRegistrationDraftForRegistration(localDraft, registrationId)) {
      clearLocalRegistrationDraft(currentUserId);
    }
    void request<RegistrationDraftEnvelope<unknown> | null>(`/v2/user-drafts/${registrationDraftStorageKey}`, {
      method: 'GET',
      silent: true,
    }).then((cloudDraft) => {
      if (!isRegistrationDraftForRegistration(cloudDraft, registrationId)) {
        return undefined;
      }
      return request<void>(`/v2/user-drafts/${registrationDraftStorageKey}`, {
        method: 'DELETE',
        silent: true,
      });
    }).catch(() => undefined);
  }, [initialState?.currentUser?.userId, registrationDraftStorageKey, registrationId, successful]);

  const failed = loadError || isPaymentOrderFailed(paymentOrder?.status);
  const resultStatus = successful ? 'success' : failed ? 'error' : 'info';
  const title = successful
    ? t('ui.competition.paymentresult.paymentSuccessfulRegistrationConfirmed')
    : failed
      ? t('ui.competition.paymentresult.paymentIncomplete')
      : t('ui.competition.paymentresult.confirmingPaymentResult');
  const subTitle = successful
    ? t('ui.competition.paymentresult.registrationNo', { value1: registration?.registrationNo || registrationId, value2: registration?.participantNo ? `，参赛编号：${registration.participantNo}` : '' })
    : failed
      ? t('ui.competition.paymentresult.noValidSuccessfulPaymentWasFoundReturnTo')
      : t('ui.competition.paymentresult.registrationNoThePaymentCallbackMayBeDelayed', { value1: registration?.registrationNo || registrationId || '-' });

  return (
    <ManagementPage title={t('ui.competition.paymentresult.paymentResult')}>
      <ManagementPageBody>
        <Result
          status={resultStatus}
          title={loading ? t('ui.competition.paymentresult.checkingPaymentResult') : title}
          subTitle={subTitle}
          extra={
            <Space wrap>
              {!successful ? <Button type="primary" loading={loading} onClick={() => void refresh(true)}>{t('ui.competition.paymentresult.refreshResult')}</Button> : null}
              <Button onClick={() => history.push('/competitions/register')}>{t('ui.competition.paymentresult.backToRegistrations')}</Button>
            </Space>
          }
        >
          {!successful && !failed
            ? <Typography.Text type="secondary">{t('ui.competition.paymentresult.doNotCreateDuplicateOrdersYouMaySafely')}</Typography.Text>
            : null}
        </Result>
      </ManagementPageBody>
    </ManagementPage>
  );
};

export default PaymentResultPage;
