import { Button, Popconfirm, Space, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { usePaymentConfigDrawer, type PaymentProviderCode } from './usePaymentConfigDrawer';
import type { PaymentProviderSettings } from '@/types/api';

import { requestPaymentApi } from '../paymentAuthenticatedRequest';
import { paymentConnectivityStatusDisplayName } from '../paymentMessage';
import { paymentEnvironmentDisplayName, paymentProviderDisplayName } from '../paymentDisplay';
import { PAYMENT_PROVIDER_SETTINGS_QUERY_KEY } from '../paymentQueryKeys';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

type PaymentRow = PaymentProviderSettings & {
  key: string;
};

const PAYMENT_PROVIDER_ORDER: PaymentProviderCode[] = ['alipay', 'wechat_pay', 'stripe', 'paypal'];

const resolveStatusColor = (enabled: boolean, configured: boolean) => {
  if (!configured) {
    return 'warning';
  }
  return enabled ? 'success' : 'default';
};

const buildProviderStatusText = (enabled: boolean, configured: boolean) => {
  if (!configured) {
    return t('ui.settings.payment.usepayment.notConfigured');
  }
  return enabled ? t('ui.settings.payment.usepayment.enabled') : t('ui.settings.payment.usepayment.disabled');
};

export type UsePaymentManagementParams = {
  canUpdateSettings: boolean;
  canTestSettings: boolean;
  isMobile: boolean;
};

export const usePaymentManagement = ({ canUpdateSettings, canTestSettings, isMobile }: UsePaymentManagementParams) => {
  const paymentSettingsQuery = useQuery({
    queryKey: PAYMENT_PROVIDER_SETTINGS_QUERY_KEY,
    queryFn: async () =>
      requestPaymentApi<PaymentProviderSettings[]>('/v1/payment/providers', {
        method: 'GET',
      }),
  });

  const { openConfigDrawer, handleTestProvider, drawerProps } = usePaymentConfigDrawer({
    canUpdateSettings,
    canTestSettings,
    paymentSettingsData: paymentSettingsQuery.data,
    onRefetch: paymentSettingsQuery.refetch,
  });

  const paymentRows = useMemo<PaymentRow[]>(
    () => (paymentSettingsQuery.data || [])
      .filter((item) => item.persisted)
      .map((item) => ({
        ...item,
        key: item.providerCode,
      })),
    [paymentSettingsQuery.data],
  );

  const addPaymentProviderItems = useMemo<MenuProps['items']>(() => {
    const persistedProviderCodes = new Set((paymentSettingsQuery.data || []).filter((item) => item.persisted).map((item) => item.providerCode));
    return PAYMENT_PROVIDER_ORDER
      .filter((providerCode) => !persistedProviderCodes.has(providerCode))
      .map((providerCode) => ({
        key: providerCode,
        label: paymentProviderDisplayName(providerCode, providerCode),
        onClick: () => openConfigDrawer(providerCode),
      }));
  }, [openConfigDrawer, paymentSettingsQuery.data]);

  const paymentColumns = useMemo<ProColumns<PaymentRow>[]>(
    () => [
      {
        title: t('ui.settings.payment.usepayment.paymentPlatform'),
        dataIndex: 'providerName',
        width: 180,
        render: (_, record) => (
          <Typography.Text strong style={{ whiteSpace: 'nowrap' }}>
            {paymentProviderDisplayName(record.providerCode, record.providerName)}
          </Typography.Text>
        ),
      },
      {
        title: t('ui.settings.payment.usepayment.environment'),
        dataIndex: 'environment',
        width: 120,
        render: (_, record) => <Tag>{paymentEnvironmentDisplayName(record.environment)}</Tag>,
      },
      {
        title: t('ui.settings.payment.usepayment.status'),
        width: 120,
        render: (_, record) => <Tag color={resolveStatusColor(record.enabled, record.configured)}>{buildProviderStatusText(record.enabled, record.configured)}</Tag>,
      },
      {
        title: t('ui.settings.payment.usepayment.configurationCompleteness'),
        width: 140,
        render: (_, record) => (
          <Tag color={record.configured ? 'success' : 'warning'}>
            {record.configured ? t('ui.settings.payment.usepayment.complete') : t('ui.settings.payment.usepayment.incomplete')}
          </Tag>
        ),
      },
      {
        title: t('ui.settings.payment.usepayment.connectivity'),
        width: 180,
        render: (_, record) => (
          <Tag
            color={record.lastTestSuccess === true ? 'success' : record.lastTestSuccess === false ? 'error' : 'default'}
            style={{ whiteSpace: 'nowrap' }}
          >
            {paymentConnectivityStatusDisplayName(record.lastTestSuccess)}
          </Tag>
        ),
      },
      {
        title: t('ui.settings.payment.usepayment.actions'),
        valueType: 'option',
        fixed: 'right',
        width: 180,
        render: (_, record) => (
          <Space className="saas-table-action-bar" size={8} wrap={false}>
            <Button
              type="link"
              size="small"
              onClick={() => openConfigDrawer(record.providerCode as PaymentProviderCode)}
              disabled={!canUpdateSettings}
            >
              {t('ui.settings.payment.usepayment.configure')}
            </Button>
            <Popconfirm
              title={t('ui.settings.payment.usepayment.testPaymentConnectivity')}
              description={t('ui.settings.payment.usepayment.theTestWillRunUsingTheCurrentlySaved')}
              okText={t('ui.settings.payment.usepayment.confirm')}
              cancelText={t('ui.settings.payment.usepayment.cancel')}
              onConfirm={() => void handleTestProvider(record.providerCode as PaymentProviderCode)}
            >
              <Button type="link" size="small" disabled={!record.configured || !canTestSettings}>{t('ui.settings.payment.usepayment.test')}</Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [canTestSettings, canUpdateSettings, handleTestProvider, openConfigDrawer],
  );

  return {
    tablePack: {
      paymentRows,
      paymentColumns,
      paymentLoading: paymentSettingsQuery.isLoading,
      paymentSettingsData: paymentSettingsQuery.data || [],
      onRefresh: paymentSettingsQuery.refetch,
      isMobile,
      toolbarProps: {
        addPaymentProviderItems,
        canUpdateSettings,
      },
    },
    drawerPack: {
      drawerProps,
    },
  };
};
