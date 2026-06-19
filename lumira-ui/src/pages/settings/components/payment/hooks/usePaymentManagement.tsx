import dayjs from 'dayjs';
import { Button, Popconfirm, Space, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { usePaymentConfigDrawer, type PaymentProviderCode } from './usePaymentConfigDrawer';
import type { PaymentProviderSettings } from '@/types/api';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type PaymentRow = PaymentProviderSettings & {
  key: string;
};

const PAYMENT_PROVIDER_ORDER: PaymentProviderCode[] = ['alipay', 'wechat_pay', 'stripe', 'paypal'];

const PAYMENT_PROVIDER_TITLES: Record<PaymentProviderCode, string> = {
  alipay: t('支付宝', 'Alipay'),
  wechat_pay: t('微信支付', 'WeChat Pay'),
  stripe: 'Stripe',
  paypal: 'PayPal',
};
const resolveStatusColor = (enabled: boolean, configured: boolean) => {
  if (!configured) {
    return 'warning';
  }
  return enabled ? 'success' : 'default';
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return t('未测试', 'Not tested');
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value;
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
      request<PaymentProviderSettings[]>('/v1/payment/providers', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
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
        label: PAYMENT_PROVIDER_TITLES[providerCode],
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
          <Space direction="vertical" size={2}>
            <Typography.Text strong>{record.providerName}</Typography.Text>
            <Typography.Text type="secondary">{record.providerCode}</Typography.Text>
          </Space>
        ),
      },
      {
        title: t('环境', 'Environment'),
        dataIndex: 'environment',
        width: 120,
        render: (_, record) => <Tag>{record.environment}</Tag>,
      },
      {
        title: t('状态', 'Status'),
        width: 120,
        render: (_, record) => <Tag color={resolveStatusColor(record.enabled, record.configured)}>{buildProviderStatusText(record.enabled, record.configured)}</Tag>,
      },
      {
        title: t('配置完整度', 'Configuration completeness'),
        width: 160,
        render: (_, record) => (
          <Space size={8}>
            <Tag color={record.configured ? 'success' : 'warning'}>{record.configured ? t('已完成', 'Complete') : t('待完成', 'Incomplete')}</Tag>
            <Typography.Text type="secondary">
              {record.configuredFields?.length || 0}
              {t('项', 'items')}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: t('最近测试', 'Latest test'),
        width: 240,
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Typography.Text>{formatDateTime(record.lastTestedAt)}</Typography.Text>
            <Typography.Text type={record.lastTestSuccess === false ? 'danger' : 'secondary'}>
              {record.lastTestMessage || t('暂无测试记录', 'No test history')}
            </Typography.Text>
          </Space>
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
