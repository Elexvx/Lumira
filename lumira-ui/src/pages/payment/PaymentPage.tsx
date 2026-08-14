import { EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Descriptions, Space, Tag, Typography } from 'antd';
import { useLocation } from '@umijs/max';
import { useMemo, useRef, useState } from 'react';
import { useOptionalCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementTable } from '@/features/management/ManagementTable';
import { StandardDrawer } from '@/features/management/StandardDrawer';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import {
  getRegistrationStatusLabel,
  registrationStatusValueEnum,
} from '@/pages/competition/utils/registrationStatus';
import { listRegistrationPayments } from '@/services/payment/api';
import { listCompetitionWorkspacePayments } from '@/services/competition/api';
import type { RegistrationPaymentRecord } from '@/services/payment/types';
import './PaymentPage.css';

const paymentStatusValueEnum = {
  PENDING: { text: '待付款' },
  PAID: { text: '已支付' },
  SUCCESS: { text: '支付成功' },
  SETTLED: { text: '已结算' },
  CONFIRMED: { text: '已确认' },
  REFUNDING: { text: '退款中' },
  REFUNDED: { text: '已退款' },
  FAILED: { text: '支付失败' },
  CANCELLED: { text: '已取消' },
  EXPIRED: { text: '已超时' },
  CLOSED: { text: '已关闭' },
  PENDING_PAYMENT: { text: '待生成订单' },
};

const providerValueEnum = {
  manual: { text: '线下确认' },
  alipay: { text: '支付宝' },
  wechat_pay: { text: '微信支付' },
  stripe: { text: 'Stripe' },
  paypal: { text: 'PayPal' },
  builtin_mock: { text: '内置模拟支付' },
};

const paymentStatusColor: Record<string, string> = {
  PENDING: 'processing',
  PENDING_PAYMENT: 'warning',
  PAID: 'success',
  SUCCESS: 'success',
  SETTLED: 'success',
  CONFIRMED: 'success',
  REFUNDING: 'warning',
  REFUNDED: 'blue',
  FAILED: 'error',
  CANCELLED: 'default',
  EXPIRED: 'default',
  CLOSED: 'default',
};

const registrationStatusColor: Record<string, string> = {
  DRAFT: 'default',
  PENDING_PAYMENT: 'warning',
  PAID: 'success',
  CONFIRMED: 'success',
  CANCELLED: 'default',
};

const paymentPageRegistrationStatusValueEnum = Object.fromEntries(
  Object.keys(registrationStatusColor).map((status) => [status, registrationStatusValueEnum[status]]),
);

const statusText = (value?: string | null, valueEnum?: Record<string, { text: string }>) => (value ? valueEnum?.[value]?.text || value : '-');

const formatAmount = (amountMinor?: number | null, currency?: string | null) => {
  const amount = Number(amountMinor || 0) / 100;
  return `${amount.toFixed(2)} ${currency || 'CNY'}`;
};

const formatTime = (value?: string | null) => (value ? value.replace('T', ' ') : '-');

const renderPaymentStatus = (status?: string | null) => {
  const normalized = status || 'PENDING_PAYMENT';
  return <Tag color={paymentStatusColor[normalized] || 'default'}>{statusText(normalized, paymentStatusValueEnum)}</Tag>;
};

const renderRegistrationStatus = (status?: string | null) => {
  const normalized = status || 'DRAFT';
  return <Tag color={registrationStatusColor[normalized] || 'default'}>{getRegistrationStatusLabel(normalized, 'DRAFT')}</Tag>;
};

const PaymentDetailDrawer = ({
  record,
  onClose,
}: {
  record?: RegistrationPaymentRecord;
  onClose: () => void;
}) => {
  const responsive = useResponsive();

  return (
    <StandardDrawer
      className="payment-detail-drawer"
      title="支付记录详情"
      open={Boolean(record)}
      onClose={onClose}
      destroyOnHidden
    >
      {record ? (
        <Descriptions className="payment-detail-descriptions" column={responsive.isMobile ? 1 : 2} bordered size="small">
        <Descriptions.Item label="报名编号">{record.registrationNo}</Descriptions.Item>
        <Descriptions.Item label="参赛编号">{record.participantNo || '-'}</Descriptions.Item>
        <Descriptions.Item label="订单号">{record.orderNo || '-'}</Descriptions.Item>
        <Descriptions.Item label="渠道订单号">{record.providerOrderNo || '-'}</Descriptions.Item>
        <Descriptions.Item label="支付渠道">{statusText(record.providerCode, providerValueEnum)}</Descriptions.Item>
        <Descriptions.Item label="支付状态">{renderPaymentStatus(record.paymentStatus)}</Descriptions.Item>
        <Descriptions.Item label="报名状态">{renderRegistrationStatus(record.registrationStatus)}</Descriptions.Item>
        <Descriptions.Item label="应付金额">{formatAmount(record.amountMinor ?? record.payableAmountMinor, record.currency)}</Descriptions.Item>
        <Descriptions.Item label="赛事">{record.competitionTitle || record.competitionCode || '-'}</Descriptions.Item>
        <Descriptions.Item label="团队">{record.teamName || `团队 ${record.teamId}`}</Descriptions.Item>
        <Descriptions.Item label="项目">{record.projectTitle || `项目 ${record.projectId}`}</Descriptions.Item>
        <Descriptions.Item label="团队人数">{record.memberCount ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="下单时间">{formatTime(record.orderCreatedAt)}</Descriptions.Item>
        <Descriptions.Item label="支付时间">{formatTime(record.paidAt)}</Descriptions.Item>
        <Descriptions.Item label="报名时间">{formatTime(record.registrationCreatedAt)}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{formatTime(record.updatedAt)}</Descriptions.Item>
        <Descriptions.Item label="失败原因" span={2}>
          {record.failureMessage || record.failureCode || '-'}
        </Descriptions.Item>
        </Descriptions>
      ) : null}
    </StandardDrawer>
  );
};

const PaymentPage = () => {
  const location = useLocation();
  const workspace = useOptionalCompetitionWorkspace();
  const responsive = useResponsive();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [detailRecord, setDetailRecord] = useState<RegistrationPaymentRecord>();
  const isStatusQuery = location.pathname === '/payments/status';
  const workspaceUuid = workspace?.competitionUuid;
  const workspaceTitle = workspace?.workspace?.title;

  const tableRequest = useMemo(
    () => buildTableRequest<RegistrationPaymentRecord>(async (params) => {
      const query = {
        keyword: typeof params.keyword === 'string' ? params.keyword : undefined,
        paymentStatus: typeof params.paymentStatus === 'string' ? params.paymentStatus : undefined,
        registrationStatus: typeof params.registrationStatus === 'string' ? params.registrationStatus : undefined,
        providerCode: typeof params.providerCode === 'string' ? params.providerCode : undefined,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      };
      return workspaceUuid
        ? listCompetitionWorkspacePayments(workspaceUuid, query)
        : listRegistrationPayments(query);
    }),
    [workspaceUuid],
  );

  const columns = useMemo<ProColumns<RegistrationPaymentRecord>[]>(
    () => [
      {
        title: '订单号',
        dataIndex: 'keyword',
        width: 210,
        fieldProps: {
          placeholder: '报名号/订单号/赛事/团队/项目',
        },
        render: (_, record) => (
          <Space className="payment-record-title" orientation="vertical" size={0}>
            <Typography.Text strong ellipsis={{ tooltip: record.orderNo || undefined }}>
              {record.orderNo || '-'}
            </Typography.Text>
            <Typography.Text type="secondary" ellipsis={{ tooltip: record.registrationNo }}>
              报名：{record.registrationNo}
            </Typography.Text>
            {record.participantNo ? <Tag color="blue">{record.participantNo}</Tag> : null}
          </Space>
        ),
      },
      {
        title: '支付状态',
        dataIndex: 'paymentStatus',
        valueType: 'select',
        valueEnum: paymentStatusValueEnum,
        width: 96,
        render: (_, record) => renderPaymentStatus(record.paymentStatus),
      },
      {
        title: '报名状态',
        dataIndex: 'registrationStatus',
        valueType: 'select',
        valueEnum: paymentPageRegistrationStatusValueEnum,
        width: 96,
        render: (_, record) => renderRegistrationStatus(record.registrationStatus),
      },
      {
        title: '支付渠道',
        dataIndex: 'providerCode',
        valueType: 'select',
        valueEnum: providerValueEnum,
        width: 104,
        responsive: ['lg', 'xl', 'xxl'],
        render: (_, record) => statusText(record.providerCode, providerValueEnum),
      },
      {
        title: '赛事',
        dataIndex: 'competitionTitle',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.competitionTitle || record.competitionCode || workspaceTitle || '-',
      },
      {
        title: '团队',
        dataIndex: 'teamName',
        search: false,
        width: 128,
        ellipsis: true,
        responsive: ['md', 'lg', 'xl', 'xxl'],
        render: (_, record) => record.teamName || `团队 ${record.teamId}`,
      },
      {
        title: '项目',
        dataIndex: 'projectTitle',
        search: false,
        width: 128,
        ellipsis: true,
        responsive: ['xl', 'xxl'],
        render: (_, record) => record.projectTitle || `项目 ${record.projectId}`,
      },
      {
        title: '金额',
        dataIndex: 'amountMinor',
        search: false,
        width: 104,
        render: (_, record) => formatAmount(record.amountMinor ?? record.payableAmountMinor, record.currency),
      },
      {
        title: '下单时间',
        dataIndex: 'orderCreatedAt',
        search: false,
        width: 168,
        responsive: ['xxl'],
        render: (value) => formatTime(typeof value === 'string' ? value : undefined),
      },
      {
        title: '支付时间',
        dataIndex: 'paidAt',
        search: false,
        width: 168,
        responsive: ['xxl'],
        render: (value) => formatTime(typeof value === 'string' ? value : undefined),
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 80,
        align: 'right',
        render: (_, record) => (
          <Button type="text" icon={<EyeOutlined />} onClick={() => setDetailRecord(record)}>
            详情
          </Button>
        ),
      },
    ],
    [responsive.isDesktop, workspaceTitle],
  );

  const tableColumns = useMemo(
    () => (isStatusQuery ? columns.filter((column) => column.valueType !== 'option') : columns),
    [columns, isStatusQuery],
  );

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace={Boolean(workspaceUuid)}
      title={workspaceUuid ? '支付' : isStatusQuery ? '支付状态查询' : '全局支付流水'}
      showWorkspaceHeader={Boolean(workspaceUuid)}
      workspaceVariant="table"
    >
      <ManagementTable<RegistrationPaymentRecord>
          actionRef={actionRef}
          rowKey="registrationId"
          columns={tableColumns}
          containerResponsive
          isMobile={responsive.isMobile}
          scroll={{ x: 960 }}
          tableLayout="fixed"
          request={tableRequest}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() => [
            <Button key="refresh" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
              刷新
            </Button>,
          ]}
      />
      <PaymentDetailDrawer record={detailRecord} onClose={() => setDetailRecord(undefined)} />
    </CompetitionWorkspacePageFrame>
  );
};

export default PaymentPage;
