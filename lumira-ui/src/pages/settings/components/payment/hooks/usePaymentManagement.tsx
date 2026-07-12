import { Button, Popconfirm, Space, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { usePaymentConfigDrawer, type PaymentProviderCode } from './usePaymentConfigDrawer';
import type { PaymentProviderSettings } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { requestPaymentApi } from '../paymentAuthenticatedRequest';
import { paymentConnectivityStatusDisplayName } from '../paymentMessage';
import { paymentEnvironmentDisplayName, paymentProviderDisplayName } from '../paymentDisplay';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
    return t('待配置', 'Not configured');
  }
  return enabled ? t('已启用', 'Enabled') : t('已停用', 'Disabled');
};

export type UsePaymentManagementParams = {
  canUpdateSettings: boolean;
  canTestSettings: boolean;
  isMobile: boolean;
};

export const usePaymentManagement = ({ canUpdateSettings, canTestSettings, isMobile }: UsePaymentManagementParams) => {
  const paymentSettingsQuery = useQuery({
    queryKey: ['payment-provider-settings'],
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
        label: paymentProviderDisplayName(providerCode, providerCode, isEnglishLocale()),
        onClick: () => openConfigDrawer(providerCode),
      }));
  }, [openConfigDrawer, paymentSettingsQuery.data]);

  const paymentColumns = useMemo<ProColumns<PaymentRow>[]>(
    () => [
      {
        title: t('支付平台', 'Payment platform'),
        dataIndex: 'providerName',
        width: 180,
        render: (_, record) => (
          <Typography.Text strong style={{ whiteSpace: 'nowrap' }}>
            {paymentProviderDisplayName(record.providerCode, record.providerName, isEnglishLocale())}
          </Typography.Text>
        ),
      },
      {
        title: t('环境', 'Environment'),
        dataIndex: 'environment',
        width: 120,
        render: (_, record) => <Tag>{paymentEnvironmentDisplayName(record.environment, isEnglishLocale())}</Tag>,
      },
      {
        title: t('状态', 'Status'),
        width: 120,
        render: (_, record) => <Tag color={resolveStatusColor(record.enabled, record.configured)}>{buildProviderStatusText(record.enabled, record.configured)}</Tag>,
      },
      {
        title: t('配置完整度', 'Configuration completeness'),
        width: 140,
        render: (_, record) => (
          <Tag color={record.configured ? 'success' : 'warning'}>
            {record.configured ? t('已完成', 'Complete') : t('待完成', 'Incomplete')}
          </Tag>
        ),
      },
      {
        title: t('连通状态', 'Connectivity'),
        width: 180,
        render: (_, record) => (
          <Tag
            color={record.lastTestSuccess === true ? 'success' : record.lastTestSuccess === false ? 'error' : 'default'}
            style={{ whiteSpace: 'nowrap' }}
          >
            {paymentConnectivityStatusDisplayName(record.lastTestSuccess, isEnglishLocale())}
          </Tag>
        ),
      },
      {
        title: t('操作', 'Actions'),
        valueType: 'option',
        fixed: 'right',
        width: 180,
        render: (_, record) => (
          <Space size={8} wrap>
            <Button
              size="small"
              onClick={() => openConfigDrawer(record.providerCode as PaymentProviderCode)}
              disabled={!canUpdateSettings}
            >
              {t('配置', 'Configure')}
            </Button>
            <Popconfirm
              title={t('测试支付连通性', 'Test payment connectivity')}
              description={t('将按照当前已保存的支付配置发起连通性测试。', 'The test will run using the currently saved payment settings.')}
              okText={t('确认', 'Confirm')}
              cancelText={t('取消', 'Cancel')}
              onConfirm={() => void handleTestProvider(record.providerCode as PaymentProviderCode)}
            >
              <Button size="small" disabled={!record.configured || !canTestSettings}>{t('测试', 'Test')}</Button>
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
