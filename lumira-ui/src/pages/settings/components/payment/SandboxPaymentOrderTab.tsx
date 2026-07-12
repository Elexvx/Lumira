import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Drawer, Form, Input, InputNumber, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import {
  createSandboxPaymentOrder,
  getPaymentOrder,
  listSandboxPaymentOrders,
} from '@/services/payment/api';
import { message } from '@/theme/antdFeedbackBridge';
import type { PaymentOrderRecord, PaymentProviderSettings } from '@/types/api';
import { normalizePaymentEnvironment } from './paymentDisplay';
import { formatPaymentOrderCreatedAt, sortPaymentOrdersNewestFirst } from './paymentOrderTime';
import { buildCleanSandboxOrderPath } from './sandboxPaymentReturnUrl';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type AlipaySandboxFormValues = {
  amountYuan: number;
  subject: string;
};

const isPaid = (status?: string | null) => ['PAID', 'SUCCESS', 'SETTLED'].includes(status || '');

export const SandboxPaymentOrderTab = ({
  paymentSettings,
  canCreateOrders,
}: {
  paymentSettings: PaymentProviderSettings[];
  canCreateOrders: boolean;
}) => {
  const [alipayForm] = Form.useForm<AlipaySandboxFormValues>();
  const [orders, setOrders] = useState<PaymentOrderRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [alipayDrawerOpen, setAlipayDrawerOpen] = useState(false);
  const [alipaySubmitting, setAlipaySubmitting] = useState(false);
  const [activeOrder, setActiveOrder] = useState<PaymentOrderRecord>();
  const [detailOrder, setDetailOrder] = useState<PaymentOrderRecord>();
  const [detailLoading, setDetailLoading] = useState(false);

  const alipaySandboxReady = useMemo(() => paymentSettings.some((settings) =>
    settings.providerCode === 'alipay'
      && settings.enabled
      && settings.configured
      && normalizePaymentEnvironment(settings.environment) === 'SANDBOX'), [paymentSettings]);

  const upsertOrder = useCallback((order: PaymentOrderRecord) => {
    setOrders((current) => sortPaymentOrdersNewestFirst([
      order,
      ...current.filter((item) => item.orderNo !== order.orderNo),
    ]));
  }, []);

  const loadOrders = useCallback(async (nextPageNo: number, nextPageSize: number) => {
    setLoading(true);
    try {
      const page = await listSandboxPaymentOrders({ pageNo: nextPageNo, pageSize: nextPageSize });
      setOrders(sortPaymentOrdersNewestFirst(page.records));
      setPageNo(Number(page.pageNo));
      setPageSize(Number(page.pageSize));
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders(pageNo, pageSize);
  }, [loadOrders, pageNo, pageSize]);

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const orderNo = searchParams.get('orderNo') || searchParams.get('out_trade_no');
    if (orderNo) {
      void getPaymentOrder(orderNo).then((order) => {
        setActiveOrder(order);
        upsertOrder(order);
      });
      const cleanPath = buildCleanSandboxOrderPath(window.location.href);
      if (`${window.location.pathname}${window.location.search}` !== cleanPath) {
        window.history.replaceState(window.history.state, '', cleanPath);
      }
    }
  }, [upsertOrder]);

  useEffect(() => {
    if (!activeOrder?.orderNo || activeOrder.status !== 'PENDING') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void getPaymentOrder(activeOrder.orderNo).then((order) => {
        setActiveOrder(order);
        upsertOrder(order);
        if (isPaid(order.status)) {
          message.success(t('支付宝沙箱收款成功，订单状态已同步', 'Alipay sandbox payment received and synchronized'));
        }
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [activeOrder?.orderNo, activeOrder?.status, upsertOrder]);

  const openAlipayDrawer = () => {
    alipayForm.setFieldsValue({
      amountYuan: 0.01,
      subject: t('Lumira 支付宝沙箱收款验证', 'Lumira Alipay sandbox receipt verification'),
    });
    setAlipayDrawerOpen(true);
  };

  const submitAlipaySandbox = async () => {
    let checkoutWindow: Window | null = null;
    try {
      const values = await alipayForm.validateFields();
      checkoutWindow = window.open('about:blank', '_blank');
      setAlipaySubmitting(true);
      const orderNo = `SBX-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
      const returnUrl = `${window.location.origin}/settings/payment?tab=sandbox-orders&orderNo=${encodeURIComponent(orderNo)}`;
      const order = await createSandboxPaymentOrder({
        providerCode: 'alipay',
        orderNo,
        subject: values.subject.trim(),
        amountMinor: Math.round(values.amountYuan * 100),
        currency: 'CNY',
        returnUrl,
        metadata: { bizType: 'alipay_sandbox_receipt_verification' },
        idempotencyKey: orderNo,
      });
      setActiveOrder(order);
      upsertOrder(order);
      setAlipayDrawerOpen(false);
      if (!order.paymentUrl) {
        checkoutWindow?.close();
        throw new Error(t('支付宝未返回收银台地址', 'Alipay checkout URL was not returned'));
      }
      if (checkoutWindow) {
        checkoutWindow.location.href = order.paymentUrl;
      } else {
        window.location.assign(order.paymentUrl);
      }
      message.success(t(
        '支付宝沙箱订单已生成，请在新窗口使用沙箱买家账号付款',
        'Sandbox order created. Pay with the sandbox buyer account in the new window.',
      ));
    } catch (error) {
      checkoutWindow?.close();
      throw error;
    } finally {
      setAlipaySubmitting(false);
    }
  };

  const openOrderDetail = useCallback(async (record: PaymentOrderRecord) => {
    setDetailOrder(record);
    setDetailLoading(true);
    try {
      const order = await getPaymentOrder(record.orderNo);
      setDetailOrder(order);
      setActiveOrder(order);
      upsertOrder(order);
    } finally {
      setDetailLoading(false);
    }
  }, [upsertOrder]);

  const columns = useMemo<ColumnsType<PaymentOrderRecord>>(() => [
    {
      title: t('订单号', 'Order number'),
      dataIndex: 'orderNo',
      width: 250,
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => void openOrderDetail(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: t('订单标题', 'Subject'),
      dataIndex: 'subject',
      width: 280,
    },
    {
      title: t('订单价格', 'Amount'),
      dataIndex: 'amountMinor',
      width: 150,
      render: (value: number, record) => `${(value / 100).toFixed(2)} ${record.currency || 'CNY'}`,
    },
    {
      title: t('环境', 'Environment'),
      width: 160,
      render: () => <Tag color="blue">{t('支付宝云沙箱', 'Alipay cloud sandbox')}</Tag>,
    },
    {
      title: t('状态', 'Status'),
      dataIndex: 'status',
      width: 130,
      render: (value: string) => <Tag color={isPaid(value) ? 'success' : 'warning'}>{value}</Tag>,
    },
    {
      title: t('生成时间', 'Created at'),
      dataIndex: 'createdAt',
      width: 190,
      render: (value: string, record) => formatPaymentOrderCreatedAt(
        record.orderNo,
        value,
        isEnglishLocale() ? 'en-US' : 'zh-CN',
      ),
    },
  ], [openOrderDetail]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {!canCreateOrders ? (
        <Alert showIcon type="warning" message={t('当前账号没有生成支付订单的权限', 'You cannot create payment orders')} />
      ) : null}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {t('支付宝云沙箱订单验证', 'Alipay cloud sandbox order verification')}
          </Typography.Title>
          <Typography.Text type="secondary">
            {t('创建支付宝官方沙箱订单并验证收款结果', 'Create official Alipay sandbox orders and verify payment results')}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void loadOrders(pageNo, pageSize)} loading={loading}>
            {t('刷新', 'Refresh')}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openAlipayDrawer}
            disabled={!canCreateOrders || !alipaySandboxReady}
          >
            {t('创建支付宝沙箱订单', 'Create Alipay sandbox order')}
          </Button>
        </Space>
      </div>

      <Table<PaymentOrderRecord>
        rowKey="orderNo"
        columns={columns}
        dataSource={orders}
        loading={loading}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (count, range) => t(
            `第 ${range[0]}-${range[1]} 条/共 ${count} 条`,
            `${range[0]}-${range[1]} of ${count}`,
          ),
          onChange: (nextPageNo, nextPageSize) => {
            if (nextPageSize !== pageSize) {
              setPageNo(1);
              setPageSize(nextPageSize);
              return;
            }
            setPageNo(nextPageNo);
          },
        }}
        scroll={{ x: 1160 }}
        locale={{ emptyText: t('暂无支付宝云沙箱订单', 'No Alipay cloud sandbox orders') }}
      />

      <Drawer
        title={t('交易详情', 'Transaction details')}
        width={560}
        open={Boolean(detailOrder)}
        onClose={() => setDetailOrder(undefined)}
        destroyOnClose
        extra={detailLoading ? <Typography.Text type="secondary">{t('刷新中…', 'Refreshing…')}</Typography.Text> : null}
      >
        {detailOrder ? (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('订单编号', 'Order number')}>
              <Typography.Text copyable>{detailOrder.orderNo}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('交易环境', 'Environment')}>
              {t('支付宝云沙箱', 'Alipay cloud sandbox')}
            </Descriptions.Item>
            <Descriptions.Item label={t('交易状态', 'Status')}>
              <Tag color={isPaid(detailOrder.status) ? 'success' : 'warning'}>{detailOrder.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('交易金额', 'Amount')}>
              {(detailOrder.amountMinor / 100).toFixed(2)} {detailOrder.currency || 'CNY'}
            </Descriptions.Item>
            <Descriptions.Item label={t('订单标题', 'Subject')}>{detailOrder.subject}</Descriptions.Item>
            <Descriptions.Item label={t('支付宝交易号', 'Alipay transaction number')}>
              {detailOrder.providerOrderNo || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('创建时间', 'Created at')}>
              {formatPaymentOrderCreatedAt(
                detailOrder.orderNo,
                detailOrder.createdAt,
                isEnglishLocale() ? 'en-US' : 'zh-CN',
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('支付时间', 'Paid at')}>
              {detailOrder.paidAt?.replace('T', ' ') || '-'}
            </Descriptions.Item>
            {detailOrder.failureMessage ? (
              <Descriptions.Item label={t('失败原因', 'Failure reason')}>
                {detailOrder.failureMessage}
              </Descriptions.Item>
            ) : null}
            {detailOrder.paymentUrl && detailOrder.status === 'PENDING' ? (
              <Descriptions.Item label={t('操作', 'Action')}>
                <Button type="primary" href={detailOrder.paymentUrl} target="_blank">
                  {t('继续付款', 'Continue payment')}
                </Button>
              </Descriptions.Item>
            ) : null}
          </Descriptions>
        ) : null}
      </Drawer>

      <Drawer
        title={t('创建支付宝云沙箱订单', 'Create Alipay cloud sandbox order')}
        width={480}
        open={alipayDrawerOpen}
        onClose={() => setAlipayDrawerOpen(false)}
        destroyOnClose
        extra={(
          <Space>
            <Button onClick={() => setAlipayDrawerOpen(false)}>{t('取消', 'Cancel')}</Button>
            <Button type="primary" loading={alipaySubmitting} onClick={() => void submitAlipaySandbox()}>
              {t('生成并打开收银台', 'Create and open checkout')}
            </Button>
          </Space>
        )}
      >
        <Alert
          showIcon
          type="warning"
          message={t('仅用于支付宝官方沙箱，不会产生真实资金扣款', 'Official Alipay sandbox only; no real funds are charged')}
          style={{ marginBottom: 20 }}
        />
        <Form<AlipaySandboxFormValues> form={alipayForm} layout="vertical">
          <Form.Item
            name="subject"
            label={t('订单标题', 'Order subject')}
            rules={[{ required: true, whitespace: true, message: t('请输入订单标题', 'Enter an order subject') }]}
          >
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item
            name="amountYuan"
            label={t('订单金额（元）', 'Order amount (CNY)')}
            rules={[
              { required: true, message: t('请输入订单金额', 'Enter an order amount') },
              { type: 'number', min: 0.01, message: t('订单金额必须大于 0', 'Amount must be greater than zero') },
            ]}
          >
            <InputNumber min={0.01} precision={2} step={1} style={{ width: '100%' }} addonAfter="CNY" />
          </Form.Item>
        </Form>
      </Drawer>
    </Space>
  );
};
