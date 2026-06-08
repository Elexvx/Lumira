import dayjs from 'dayjs';
import { Button, Popconfirm, Space, Tag, Typography } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { request } from '@/services/common/request';
import { API_OPTS } from '@/utils/errorMessage';
import { usePaymentConfigDrawer, type PaymentProviderCode } from './usePaymentConfigDrawer';
import type { PaymentProviderSettings } from '@/types/api';

type PaymentRow = PaymentProviderSettings & {
  key: string;
};
const resolveStatusColor = (enabled: boolean, configured: boolean) => {
  if (!configured) {
    return 'warning';
  }
  return enabled ? 'success' : 'default';
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '未测试';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value;
};

const buildProviderStatusText = (enabled: boolean, configured: boolean) => {
  if (!configured) {
    return '待配置';
  }
  return enabled ? '已启用' : '已停用';
};

export type UsePaymentManagementParams = {
  canManageSettings: boolean;
  isMobile: boolean;
};

export const usePaymentManagement = ({ canManageSettings, isMobile }: UsePaymentManagementParams) => {
  const paymentSettingsQuery = useQuery({
    queryKey: ['payment-provider-settings'],
    queryFn: async () =>
      request<PaymentProviderSettings[]>('/v1/payment/providers', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });

  const { openConfigDrawer, handleTestProvider, drawerProps } = usePaymentConfigDrawer({
    canManageSettings,
    paymentSettingsData: paymentSettingsQuery.data,
    onRefetch: paymentSettingsQuery.refetch,
  });

  const paymentRows = useMemo<PaymentRow[]>(
    () => (paymentSettingsQuery.data || []).map((item) => ({
      ...item,
      key: item.providerCode,
    })),
    [paymentSettingsQuery.data],
  );

  const paymentColumns = useMemo<ProColumns<PaymentRow>[]>(
    () => [
      {
        title: '支付平台',
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
        title: '环境',
        dataIndex: 'environment',
        width: 120,
        render: (_, record) => <Tag>{record.environment}</Tag>,
      },
      {
        title: '状态',
        width: 120,
        render: (_, record) => <Tag color={resolveStatusColor(record.enabled, record.configured)}>{buildProviderStatusText(record.enabled, record.configured)}</Tag>,
      },
      {
        title: '配置完整度',
        width: 160,
        render: (_, record) => (
          <Space size={8}>
            <Tag color={record.configured ? 'success' : 'warning'}>{record.configured ? '已完成' : '待完成'}</Tag>
            <Typography.Text type="secondary">{record.configuredFields?.length || 0} 项</Typography.Text>
          </Space>
        ),
      },
      {
        title: '最近测试',
        width: 240,
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Typography.Text>{formatDateTime(record.lastTestedAt)}</Typography.Text>
            <Typography.Text type={record.lastTestSuccess === false ? 'danger' : 'secondary'}>
              {record.lastTestMessage || '暂无测试记录'}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: 'right',
        width: 180,
        render: (_, record) => (
          <Space size={8} wrap>
            <Button
              size="small"
              onClick={() => openConfigDrawer(record.providerCode as PaymentProviderCode)}
              disabled={!canManageSettings}
            >
              配置
            </Button>
            <Popconfirm
              title="测试支付连通性"
              description="将按照当前已保存的支付配置发起连通性测试。"
              okText="确认"
              cancelText="取消"
              onConfirm={() => void handleTestProvider(record.providerCode as PaymentProviderCode)}
            >
              <Button size="small" disabled={!record.configured || !canManageSettings}>测试</Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [canManageSettings, handleTestProvider, openConfigDrawer],
  );

  return {
    tablePack: {
      paymentRows,
      paymentColumns,
      paymentLoading: paymentSettingsQuery.isLoading,
      onRefresh: paymentSettingsQuery.refetch,
      isMobile,
    },
    drawerPack: {
      drawerProps,
    },
  };
};
