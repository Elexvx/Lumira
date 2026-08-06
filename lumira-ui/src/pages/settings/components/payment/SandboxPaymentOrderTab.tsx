import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  QRCode,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  cancelPaymentOrder,
  createPaymentOrder,
  createSandboxPaymentOrder,
  getPaymentOrder,
  listManualPaymentOrders,
} from '@/services/payment/api';
import { message } from '@/theme/antdFeedbackBridge';
import { StandardDrawer } from '@/features/management/StandardDrawer';
import type { PaymentOrderRecord, PaymentProviderSettings } from '@/types/api';
import {
  normalizePaymentEnvironment,
  paymentEnvironmentDisplayName,
  paymentProviderDisplayName,
} from './paymentDisplay';
import {
  buildManualPaymentRequest,
  isManualPaymentOrderCancellable,
  isWechatNativeOrder,
  listManualPaymentProviders,
  listWechatManualScenes,
  resolveManualOrderEnvironment,
  resolveManualOrderScene,
} from './manualPaymentOrder';
import type { ManualPaymentFormValues } from './manualPaymentOrder';
import { formatPaymentOrderCreatedAt, sortPaymentOrdersNewestFirst } from './paymentOrderTime';
import { buildCleanSandboxOrderPath } from './sandboxPaymentReturnUrl';
import { databaseMessage } from '@/i18n/databaseMessage';
import { resolveRuntimeLocale } from '@/i18n/locale';

const t = databaseMessage;

const PAID_STATUSES = ['PAID', 'SUCCESS', 'SETTLED'];
const TERMINAL_STATUSES = [...PAID_STATUSES, 'FAILED', 'CANCELLED', 'CLOSED', 'EXPIRED'];
const isPaid = (status?: string | null) => PAID_STATUSES.includes(status || '');
const isPending = (status?: string | null) => !TERMINAL_STATUSES.includes(status || '');

const providerName = (providerCode?: string | null) => paymentProviderDisplayName(
  providerCode,
  providerCode,
);

const statusColor = (status?: string | null) => (
  isPaid(status) ? 'success' : isPending(status) ? 'processing' : 'default'
);

export const SandboxPaymentOrderTab = ({
  paymentSettings,
  canCreateOrders,
}: {
  paymentSettings: PaymentProviderSettings[];
  canCreateOrders: boolean;
}) => {
  const [form] = Form.useForm<ManualPaymentFormValues>();
  const selectedProviderCode = Form.useWatch('providerCode', form);
  const selectedScene = Form.useWatch('scene', form);
  const [orders, setOrders] = useState<PaymentOrderRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [activeOrder, setActiveOrder] = useState<PaymentOrderRecord>();
  const [detailOrder, setDetailOrder] = useState<PaymentOrderRecord>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [cancellingOrderNo, setCancellingOrderNo] = useState<string>();

  const providers = useMemo(() => listManualPaymentProviders(paymentSettings), [paymentSettings]);
  const selectedSettings = useMemo(
    () => providers.find((item) => item.providerCode === selectedProviderCode),
    [providers, selectedProviderCode],
  );
  const selectedEnvironment = normalizePaymentEnvironment(selectedSettings?.environment);
  const isProduction = selectedEnvironment === 'PRODUCTION';
  const wechatScenes = useMemo(() => listWechatManualScenes(selectedSettings), [selectedSettings]);
  const upsertOrder = useCallback((order: PaymentOrderRecord) => {
    setOrders((current) => sortPaymentOrdersNewestFirst([
      order,
      ...current.filter((item) => item.orderNo !== order.orderNo),
    ]));
  }, []);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listManualPaymentOrders({ pageNo: 1, pageSize: 50 });
      setOrders(sortPaymentOrdersNewestFirst(page.records));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const orderNo = searchParams.get('orderNo') || searchParams.get('out_trade_no');
    if (!orderNo) {
      return;
    }
    void getPaymentOrder(orderNo).then((order) => {
      setActiveOrder(order);
      upsertOrder(order);
    });
    const cleanPath = buildCleanSandboxOrderPath(window.location.href);
    if (`${window.location.pathname}${window.location.search}` !== cleanPath) {
      window.history.replaceState(window.history.state, '', cleanPath);
    }
  }, [upsertOrder]);

  useEffect(() => {
    if (!activeOrder?.orderNo || !isPending(activeOrder.status)) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void getPaymentOrder(activeOrder.orderNo).then((order) => {
        const wasPaid = isPaid(activeOrder.status);
        setActiveOrder(order);
        setDetailOrder((current) => current?.orderNo === order.orderNo ? order : current);
        upsertOrder(order);
        if (!wasPaid && isPaid(order.status)) {
          message.success(t('ui.settings.payment.sandboxpaymentorder.paymentReceivedAndSynchronized', { value1: providerName(order.providerCode) }));
        }
      });
    }, 3000);
    return () => window.clearInterval(timer);
  }, [activeOrder?.orderNo, activeOrder?.status, upsertOrder]);

  const openCreateDrawer = () => {
    const initial = providers[0];
    const scenes = listWechatManualScenes(initial);
    form.resetFields();
    form.setFieldsValue({
      providerCode: initial?.providerCode,
      amountYuan: 0.01,
      subject: t('ui.settings.payment.sandboxpaymentorder.lumiraManualPaymentVerification'),
      scene: initial?.providerCode === 'wechat_pay' ? scenes[0] : undefined,
      productionConfirmed: false,
    });
    setDrawerOpen(true);
  };

  const changeProvider = (providerCode: string) => {
    const settings = providers.find((item) => item.providerCode === providerCode);
    const scenes = listWechatManualScenes(settings);
    form.setFieldsValue({
      scene: providerCode === 'wechat_pay' ? scenes[0] : undefined,
      clientIp: undefined,
      openid: undefined,
      productionConfirmed: false,
    });
  };

  const submitOrder = async () => {
    let checkoutWindow: Window | null = null;
    try {
      const values = await form.validateFields();
      const settings = providers.find((item) => item.providerCode === values.providerCode);
      if (!settings) {
        message.error(t('ui.settings.payment.sandboxpaymentorder.theSelectedProviderIsUnavailable'));
        return;
      }
      const request = buildManualPaymentRequest({
        values,
        settings,
        origin: window.location.origin,
      });
      const nativeWechat = settings.providerCode === 'wechat_pay' && values.scene === 'NATIVE';
      if (!nativeWechat) {
        checkoutWindow = window.open('about:blank', '_blank');
      }
      setSubmitting(true);
      const environment = normalizePaymentEnvironment(settings.environment);
      const order = environment === 'SANDBOX'
        ? await createSandboxPaymentOrder(request)
        : await createPaymentOrder(request);
      setActiveOrder(order);
      upsertOrder(order);
      setDrawerOpen(false);

      if (!order.paymentUrl) {
        checkoutWindow?.close();
        message.error(t('ui.settings.payment.sandboxpaymentorder.theProviderDidNotReturnAPaymentUrl'));
        return;
      }
      if (nativeWechat) {
        checkoutWindow?.close();
        message.success(t('ui.settings.payment.sandboxpaymentorder.wechatPayOrderCreatedScanTheQrCode'));
        return;
      }
      if (checkoutWindow) {
        checkoutWindow.location.href = order.paymentUrl;
      } else {
        window.location.assign(order.paymentUrl);
      }
      message.success(t('ui.settings.payment.sandboxpaymentorder.paymentOrderCreatedAndCheckoutOpened'));
    } catch {
      checkoutWindow?.close();
    } finally {
      setSubmitting(false);
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

  const cancelOrder = useCallback(async (record: PaymentOrderRecord) => {
    setCancellingOrderNo(record.orderNo);
    try {
      const cancelled = await cancelPaymentOrder(record.orderNo);
      upsertOrder(cancelled);
      setActiveOrder((current) => current?.orderNo === cancelled.orderNo ? cancelled : current);
      setDetailOrder((current) => current?.orderNo === cancelled.orderNo ? cancelled : current);
      message.success(t('ui.settings.payment.sandboxpaymentorder.paymentOrderCancelled'));
    } finally {
      setCancellingOrderNo(undefined);
    }
  }, [upsertOrder]);

  const renderPaymentAction = (order: PaymentOrderRecord, compact = false) => {
    if (!order.paymentUrl || !isPending(order.status)) {
      return null;
    }
    if (isWechatNativeOrder(order)) {
      return (
        <Space direction="vertical" align="center" size={compact ? 8 : 12}>
          <QRCode value={order.paymentUrl} size={compact ? 152 : 196} />
          <Typography.Text type="secondary">
            {t('ui.settings.payment.sandboxpaymentorder.scanWithWechatToPay')}
          </Typography.Text>
        </Space>
      );
    }
    return (
      <Button type="primary" href={order.paymentUrl} target="_blank" rel="noreferrer">
        {t('ui.settings.payment.sandboxpaymentorder.continuePayment')}
      </Button>
    );
  };

  const columns = useMemo<ColumnsType<PaymentOrderRecord>>(() => [
    {
      title: t('ui.settings.payment.sandboxpaymentorder.orderNumber'),
      dataIndex: 'orderNo',
      width: 270,
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => void openOrderDetail(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.provider'),
      dataIndex: 'providerCode',
      width: 140,
      render: (value: string) => providerName(value),
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.environment'),
      width: 120,
      render: (_, record) => {
        const environment = resolveManualOrderEnvironment(record, paymentSettings);
        return (
          <Tag color={environment === 'PRODUCTION' ? 'red' : 'blue'}>
            {paymentEnvironmentDisplayName(environment)}
          </Tag>
        );
      },
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.scene'),
      width: 100,
      render: (_, record) => resolveManualOrderScene(record) || '-',
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.amount'),
      dataIndex: 'amountMinor',
      width: 130,
      render: (value: number, record) => `${(value / 100).toFixed(2)} ${record.currency || 'CNY'}`,
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.status'),
      dataIndex: 'status',
      width: 120,
      render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>,
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.createdAt'),
      dataIndex: 'createdAt',
      width: 190,
      render: (value: string, record) => formatPaymentOrderCreatedAt(
        record.orderNo,
        value,
        resolveRuntimeLocale(),
      ),
    },
    {
      title: t('ui.settings.payment.sandboxpaymentorder.actions'),
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        isManualPaymentOrderCancellable(record) && canCreateOrders ? (
          <Popconfirm
            title={t('ui.settings.payment.sandboxpaymentorder.cancelThisPaymentOrder')}
            description={t('ui.settings.payment.sandboxpaymentorder.theExistingCheckoutLinkOrQrCodeWill')}
            okText={t('ui.settings.payment.sandboxpaymentorder.cancelOrder')}
            cancelText={t('ui.settings.payment.sandboxpaymentorder.keepOrder')}
            onConfirm={() => cancelOrder(record)}
          >
            <Button
              danger
              type="link"
              loading={cancellingOrderNo === record.orderNo}
            >
              {t('ui.settings.payment.sandboxpaymentorder.cancel')}
            </Button>
          </Popconfirm>
        ) : '-'
      ),
    },
  ], [canCreateOrders, cancelOrder, cancellingOrderNo, openOrderDetail, paymentSettings]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {!canCreateOrders ? (
        <Alert showIcon type="warning" message={t('ui.settings.payment.sandboxpaymentorder.youCannotCreatePaymentOrders')} />
      ) : null}
      {providers.length === 0 ? (
        <Alert
          showIcon
          type="warning"
          message={t('ui.settings.payment.sandboxpaymentorder.noPaymentProviderIsReadyForManualVerification')}
          description={t('ui.settings.payment.sandboxpaymentorder.enableAndConfigureAlipayOrWechatPayFirst')}
        />
      ) : null}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {t('ui.settings.payment.sandboxpaymentorder.manualPaymentOrder')}
          </Typography.Title>
          <Typography.Text type="secondary">
            {t('ui.settings.payment.sandboxpaymentorder.createAMinimumValueOrderThroughAConfigured')}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void loadOrders()} loading={loading}>
            {t('ui.settings.payment.sandboxpaymentorder.refreshOrderHistory')}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateDrawer}
            disabled={!canCreateOrders || providers.length === 0}
          >
            {t('ui.settings.payment.sandboxpaymentorder.createOrder')}
          </Button>
        </Space>
      </div>

      {activeOrder && isPending(activeOrder.status) ? (
        <Alert
          showIcon
          type="info"
          message={t('ui.settings.payment.sandboxpaymentorder.orderIsAwaitingPayment', { value1: providerName(activeOrder.providerCode), orderNo: activeOrder.orderNo })}
          description={renderPaymentAction(activeOrder)}
        />
      ) : null}

      <Alert
        showIcon
        type="info"
        message={t('ui.settings.payment.sandboxpaymentorder.productionOrderHistory')}
        description={t('ui.settings.payment.sandboxpaymentorder.productionAndSandboxManualOrdersRemainAvailableAfter')}
      />

      <Table<PaymentOrderRecord>
        rowKey="orderNo"
        columns={columns}
        dataSource={orders}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
        scroll={{ x: 1170 }}
        locale={{ emptyText: t('ui.settings.payment.sandboxpaymentorder.noManualPaymentOrders') }}
      />

      <StandardDrawer
        title={t('ui.settings.payment.sandboxpaymentorder.transactionDetails')}
        open={Boolean(detailOrder)}
        onClose={() => setDetailOrder(undefined)}
        destroyOnClose
        extra={detailLoading ? <Typography.Text type="secondary">{t('ui.settings.payment.sandboxpaymentorder.refreshing')}</Typography.Text> : null}
      >
        {detailOrder ? (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.orderNumber')}>
              <Typography.Text copyable>{detailOrder.orderNo}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.provider')}>{providerName(detailOrder.providerCode)}</Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.environment.38aa5d01')}>
              {paymentEnvironmentDisplayName(
                resolveManualOrderEnvironment(detailOrder, paymentSettings),
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.paymentScene')}>
              {resolveManualOrderScene(detailOrder) || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.status.3dc9b722')}>
              <Tag color={statusColor(detailOrder.status)}>{detailOrder.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.amount.f456317d')}>
              {(detailOrder.amountMinor / 100).toFixed(2)} {detailOrder.currency || 'CNY'}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.subject')}>{detailOrder.subject}</Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.providerTransactionNumber')}>
              {detailOrder.providerOrderNo || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.createdAt')}>
              {formatPaymentOrderCreatedAt(
                detailOrder.orderNo,
                detailOrder.createdAt,
                resolveRuntimeLocale(),
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.paidAt')}>
              {detailOrder.paidAt?.replace('T', ' ') || '-'}
            </Descriptions.Item>
            {detailOrder.failureMessage ? (
              <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.failureReason')}>{detailOrder.failureMessage}</Descriptions.Item>
            ) : null}
            {detailOrder.paymentUrl && isPending(detailOrder.status) ? (
              <Descriptions.Item label={t('ui.settings.payment.sandboxpaymentorder.paymentAction')}>
                {renderPaymentAction(detailOrder, true)}
              </Descriptions.Item>
            ) : null}
          </Descriptions>
        ) : null}
      </StandardDrawer>

      <StandardDrawer
        title={t('ui.settings.payment.sandboxpaymentorder.createManualPaymentOrder')}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
        extra={(
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>{t('ui.settings.payment.sandboxpaymentorder.cancel.d94a8eaf')}</Button>
            <Button type="primary" loading={submitting} onClick={() => void submitOrder()}>
              {selectedProviderCode === 'wechat_pay' && selectedScene === 'NATIVE'
                ? t('ui.settings.payment.sandboxpaymentorder.createPaymentQrCode')
                : t('ui.settings.payment.sandboxpaymentorder.createAndOpenCheckout')}
            </Button>
          </Space>
        )}
      >
        <Form<ManualPaymentFormValues> form={form} layout="vertical">
          <Form.Item
            name="providerCode"
            label={t('ui.settings.payment.sandboxpaymentorder.paymentProvider')}
            rules={[{ required: true, message: t('ui.settings.payment.sandboxpaymentorder.selectAPaymentProvider') }]}
          >
            <Select
              onChange={changeProvider}
              options={providers.map((settings) => ({
                value: settings.providerCode,
                label: `${providerName(settings.providerCode)} · ${paymentEnvironmentDisplayName(settings.environment)}`,
              }))}
            />
          </Form.Item>

          {selectedProviderCode === 'wechat_pay' ? (
            <Form.Item
              name="scene"
              label={t('ui.settings.payment.sandboxpaymentorder.wechatPayScene')}
              rules={[{ required: true, message: t('ui.settings.payment.sandboxpaymentorder.selectAWechatPayScene') }]}
            >
              <Select options={wechatScenes.map((scene) => ({ value: scene, label: scene }))} />
            </Form.Item>
          ) : null}

          {selectedProviderCode === 'wechat_pay' && selectedScene === 'H5' ? (
            <Form.Item
              name="clientIp"
              label={t('ui.settings.payment.sandboxpaymentorder.payerClientIp')}
              rules={[
                { required: true, whitespace: true, message: t('ui.settings.payment.sandboxpaymentorder.enterThePayerPublicIp') },
              ]}
              extra={t('ui.settings.payment.sandboxpaymentorder.requiredByWechatH5UseThePayerDevice')}
            >
              <Input placeholder="203.0.113.10" />
            </Form.Item>
          ) : null}

          {selectedProviderCode === 'wechat_pay' && selectedScene === 'JSAPI' ? (
            <Form.Item
              name="openid"
              label="OpenID"
              rules={[
                { required: true, whitespace: true, message: t('ui.settings.payment.sandboxpaymentorder.enterTheUserOpenidForThisAppid') },
              ]}
            >
              <Input />
            </Form.Item>
          ) : null}

          <Form.Item
            name="subject"
            label={t('ui.settings.payment.sandboxpaymentorder.orderSubject')}
            rules={[{ required: true, whitespace: true, message: t('ui.settings.payment.sandboxpaymentorder.enterAnOrderSubject') }]}
          >
            <Input maxLength={256} />
          </Form.Item>
          <Form.Item
            name="amountYuan"
            label={t('ui.settings.payment.sandboxpaymentorder.orderAmountCny')}
            rules={[
              { required: true, message: t('ui.settings.payment.sandboxpaymentorder.enterAnOrderAmount') },
              { type: 'number', min: 0.01, message: t('ui.settings.payment.sandboxpaymentorder.amountMustBeGreaterThanZero') },
            ]}
          >
            <InputNumber min={0.01} precision={2} step={0.01} style={{ width: '100%' }} addonAfter="CNY" />
          </Form.Item>

          {isProduction ? (
            <Alert
              showIcon
              type="error"
              message={t('ui.settings.payment.sandboxpaymentorder.thisIsProductionAndCreatesARealPayment')}
              description={t('ui.settings.payment.sandboxpaymentorder.keepThe001VerificationAmountPayingCauses')}
              style={{ marginBottom: 20 }}
            />
          ) : (
            <Alert
              showIcon
              type="info"
              message={t('ui.settings.payment.sandboxpaymentorder.thisIsASandboxNoRealFundsAre')}
              style={{ marginBottom: 20 }}
            />
          )}

          {isProduction ? (
            <Form.Item
              name="productionConfirmed"
              valuePropName="checked"
              rules={[{
                validator: (_, checked) => checked
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('ui.settings.payment.sandboxpaymentorder.confirmTheRealTransactionWarning'))),
              }]}
            >
              <Checkbox>
                {t('ui.settings.payment.sandboxpaymentorder.iUnderstandThisIsAProductionOrderInvolving')}
              </Checkbox>
            </Form.Item>
          ) : null}
        </Form>
      </StandardDrawer>
    </Space>
  );
};
