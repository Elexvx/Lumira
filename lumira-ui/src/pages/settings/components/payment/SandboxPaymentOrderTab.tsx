import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  QRCode,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getLocale } from '@umijs/max';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { normalizeLocale } from '@/i18n/locale';
import {
  createPaymentOrder,
  createSandboxPaymentOrder,
  getPaymentOrder,
  listSandboxPaymentOrders,
} from '@/services/payment/api';
import { message } from '@/theme/antdFeedbackBridge';
import type { PaymentOrderRecord, PaymentProviderSettings } from '@/types/api';
import {
  normalizePaymentEnvironment,
  paymentEnvironmentDisplayName,
  paymentProviderDisplayName,
} from './paymentDisplay';
import {
  buildManualPaymentRequest,
  isWechatNativeOrder,
  listManualPaymentProviders,
  listWechatManualScenes,
  resolveManualOrderEnvironment,
  resolveManualOrderScene,
} from './manualPaymentOrder';
import type { ManualPaymentFormValues } from './manualPaymentOrder';
import { formatPaymentOrderCreatedAt, sortPaymentOrdersNewestFirst } from './paymentOrderTime';
import { buildCleanSandboxOrderPath } from './sandboxPaymentReturnUrl';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const PAID_STATUSES = ['PAID', 'SUCCESS', 'SETTLED'];
const TERMINAL_STATUSES = [...PAID_STATUSES, 'FAILED', 'CANCELLED', 'CLOSED', 'EXPIRED'];
const isPaid = (status?: string | null) => PAID_STATUSES.includes(status || '');
const isPending = (status?: string | null) => !TERMINAL_STATUSES.includes(status || '');

const providerName = (providerCode?: string | null) => paymentProviderDisplayName(
  providerCode,
  providerCode,
  isEnglishLocale(),
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

  const providers = useMemo(() => listManualPaymentProviders(paymentSettings), [paymentSettings]);
  const selectedSettings = useMemo(
    () => providers.find((item) => item.providerCode === selectedProviderCode),
    [providers, selectedProviderCode],
  );
  const selectedEnvironment = normalizePaymentEnvironment(selectedSettings?.environment);
  const isProduction = selectedEnvironment === 'PRODUCTION';
  const wechatScenes = useMemo(() => listWechatManualScenes(selectedSettings), [selectedSettings]);
  const hasSandboxProvider = providers.some(
    (item) => normalizePaymentEnvironment(item.environment) === 'SANDBOX',
  );

  const upsertOrder = useCallback((order: PaymentOrderRecord) => {
    setOrders((current) => sortPaymentOrdersNewestFirst([
      order,
      ...current.filter((item) => item.orderNo !== order.orderNo),
    ]));
  }, []);

  const loadOrders = useCallback(async () => {
    if (!hasSandboxProvider) {
      return;
    }
    setLoading(true);
    try {
      const page = await listSandboxPaymentOrders({ pageNo: 1, pageSize: 50 });
      setOrders((current) => sortPaymentOrdersNewestFirst([
        ...current.filter((item) => (
          resolveManualOrderEnvironment(item, paymentSettings) !== 'SANDBOX'
        )),
        ...page.records,
      ]));
    } finally {
      setLoading(false);
    }
  }, [hasSandboxProvider, paymentSettings]);

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
          message.success(t(
            `${providerName(order.providerCode)}收款成功，订单状态已同步`,
            `${providerName(order.providerCode)} payment received and synchronized`,
          ));
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
      subject: t('Lumira 手动支付接口验收', 'Lumira manual payment verification'),
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
        message.error(t('所选支付接口未启用或配置不完整', 'The selected provider is unavailable'));
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
        message.error(t('支付接口未返回付款地址', 'The provider did not return a payment URL'));
        return;
      }
      if (nativeWechat) {
        checkoutWindow?.close();
        message.success(t('微信支付订单已生成，请扫描二维码付款', 'WeChat Pay order created. Scan the QR code to pay.'));
        return;
      }
      if (checkoutWindow) {
        checkoutWindow.location.href = order.paymentUrl;
      } else {
        window.location.assign(order.paymentUrl);
      }
      message.success(t('支付订单已生成，已打开付款页面', 'Payment order created and checkout opened'));
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

  const renderPaymentAction = (order: PaymentOrderRecord, compact = false) => {
    if (!order.paymentUrl || !isPending(order.status)) {
      return null;
    }
    if (isWechatNativeOrder(order)) {
      return (
        <Space direction="vertical" align="center" size={compact ? 8 : 12}>
          <QRCode value={order.paymentUrl} size={compact ? 152 : 196} />
          <Typography.Text type="secondary">
            {t('请使用微信扫描二维码付款', 'Scan with WeChat to pay')}
          </Typography.Text>
        </Space>
      );
    }
    return (
      <Button type="primary" href={order.paymentUrl} target="_blank" rel="noreferrer">
        {t('继续付款', 'Continue payment')}
      </Button>
    );
  };

  const columns = useMemo<ColumnsType<PaymentOrderRecord>>(() => [
    {
      title: t('订单号', 'Order number'),
      dataIndex: 'orderNo',
      width: 270,
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => void openOrderDetail(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: t('支付接口', 'Provider'),
      dataIndex: 'providerCode',
      width: 140,
      render: (value: string) => providerName(value),
    },
    {
      title: t('环境', 'Environment'),
      width: 120,
      render: (_, record) => {
        const environment = resolveManualOrderEnvironment(record, paymentSettings);
        return (
          <Tag color={environment === 'PRODUCTION' ? 'red' : 'blue'}>
            {paymentEnvironmentDisplayName(environment, isEnglishLocale())}
          </Tag>
        );
      },
    },
    {
      title: t('场景', 'Scene'),
      width: 100,
      render: (_, record) => resolveManualOrderScene(record) || '-',
    },
    {
      title: t('金额', 'Amount'),
      dataIndex: 'amountMinor',
      width: 130,
      render: (value: number, record) => `${(value / 100).toFixed(2)} ${record.currency || 'CNY'}`,
    },
    {
      title: t('状态', 'Status'),
      dataIndex: 'status',
      width: 120,
      render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>,
    },
    {
      title: t('创建时间', 'Created at'),
      dataIndex: 'createdAt',
      width: 190,
      render: (value: string, record) => formatPaymentOrderCreatedAt(
        record.orderNo,
        value,
        isEnglishLocale() ? 'en-US' : 'zh-CN',
      ),
    },
  ], [openOrderDetail, paymentSettings]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {!canCreateOrders ? (
        <Alert showIcon type="warning" message={t('当前账号没有生成支付订单的权限', 'You cannot create payment orders')} />
      ) : null}
      {providers.length === 0 ? (
        <Alert
          showIcon
          type="warning"
          message={t('暂无可手动验收的支付接口', 'No payment provider is ready for manual verification')}
          description={t('请先启用并完整配置支付宝或微信支付。', 'Enable and configure Alipay or WeChat Pay first.')}
        />
      ) : null}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {t('手动生成支付订单', 'Manual payment order')}
          </Typography.Title>
          <Typography.Text type="secondary">
            {t('选择已配置的支付接口，创建最低金额订单并验证付款及回调状态。', 'Create a minimum-value order through a configured provider and verify payment callbacks.')}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void loadOrders()} loading={loading} disabled={!hasSandboxProvider}>
            {t('刷新沙箱记录', 'Refresh sandbox history')}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateDrawer}
            disabled={!canCreateOrders || providers.length === 0}
          >
            {t('创建订单', 'Create order')}
          </Button>
        </Space>
      </div>

      {activeOrder && isPending(activeOrder.status) ? (
        <Alert
          showIcon
          type="info"
          message={t(
            `${providerName(activeOrder.providerCode)}订单 ${activeOrder.orderNo} 正在等待付款`,
            `${providerName(activeOrder.providerCode)} order ${activeOrder.orderNo} is awaiting payment`,
          )}
          description={renderPaymentAction(activeOrder)}
        />
      ) : null}

      <Alert
        showIcon
        type="info"
        message={t('生产订单记录说明', 'Production order history')}
        description={t(
          '本页会持续显示本次会话创建的生产订单；刷新页面后可通过付款返回地址恢复当前订单。沙箱历史可直接刷新。',
          'Production orders created in this session remain visible here. The payment return URL restores the current order after reload; sandbox history can be refreshed.',
        )}
      />

      <Table<PaymentOrderRecord>
        rowKey="orderNo"
        columns={columns}
        dataSource={orders}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50] }}
        scroll={{ x: 1170 }}
        locale={{ emptyText: t('暂无手动支付订单', 'No manual payment orders') }}
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
            <Descriptions.Item label={t('订单号', 'Order number')}>
              <Typography.Text copyable>{detailOrder.orderNo}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('支付接口', 'Provider')}>{providerName(detailOrder.providerCode)}</Descriptions.Item>
            <Descriptions.Item label={t('交易环境', 'Environment')}>
              {paymentEnvironmentDisplayName(
                resolveManualOrderEnvironment(detailOrder, paymentSettings),
                isEnglishLocale(),
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('支付场景', 'Payment scene')}>
              {resolveManualOrderScene(detailOrder) || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('交易状态', 'Status')}>
              <Tag color={statusColor(detailOrder.status)}>{detailOrder.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('交易金额', 'Amount')}>
              {(detailOrder.amountMinor / 100).toFixed(2)} {detailOrder.currency || 'CNY'}
            </Descriptions.Item>
            <Descriptions.Item label={t('订单标题', 'Subject')}>{detailOrder.subject}</Descriptions.Item>
            <Descriptions.Item label={t('渠道交易号', 'Provider transaction number')}>
              {detailOrder.providerOrderNo || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('创建时间', 'Created at')}>
              {formatPaymentOrderCreatedAt(
                detailOrder.orderNo,
                detailOrder.createdAt,
                isEnglishLocale() ? 'en-US' : 'zh-CN',
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('付款时间', 'Paid at')}>
              {detailOrder.paidAt?.replace('T', ' ') || '-'}
            </Descriptions.Item>
            {detailOrder.failureMessage ? (
              <Descriptions.Item label={t('失败原因', 'Failure reason')}>{detailOrder.failureMessage}</Descriptions.Item>
            ) : null}
            {detailOrder.paymentUrl && isPending(detailOrder.status) ? (
              <Descriptions.Item label={t('付款操作', 'Payment action')}>
                {renderPaymentAction(detailOrder, true)}
              </Descriptions.Item>
            ) : null}
          </Descriptions>
        ) : null}
      </Drawer>

      <Drawer
        title={t('创建手动支付订单', 'Create manual payment order')}
        width={500}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
        extra={(
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>{t('取消', 'Cancel')}</Button>
            <Button type="primary" loading={submitting} onClick={() => void submitOrder()}>
              {selectedProviderCode === 'wechat_pay' && selectedScene === 'NATIVE'
                ? t('生成付款二维码', 'Create payment QR code')
                : t('生成并打开收银台', 'Create and open checkout')}
            </Button>
          </Space>
        )}
      >
        <Form<ManualPaymentFormValues> form={form} layout="vertical">
          <Form.Item
            name="providerCode"
            label={t('支付接口', 'Payment provider')}
            rules={[{ required: true, message: t('请选择支付接口', 'Select a payment provider') }]}
          >
            <Select
              onChange={changeProvider}
              options={providers.map((settings) => ({
                value: settings.providerCode,
                label: `${providerName(settings.providerCode)} · ${paymentEnvironmentDisplayName(settings.environment, isEnglishLocale())}`,
              }))}
            />
          </Form.Item>

          {selectedProviderCode === 'wechat_pay' ? (
            <Form.Item
              name="scene"
              label={t('微信支付场景', 'WeChat Pay scene')}
              rules={[{ required: true, message: t('请选择微信支付场景', 'Select a WeChat Pay scene') }]}
            >
              <Select options={wechatScenes.map((scene) => ({ value: scene, label: scene }))} />
            </Form.Item>
          ) : null}

          {selectedProviderCode === 'wechat_pay' && selectedScene === 'H5' ? (
            <Form.Item
              name="clientIp"
              label={t('付款客户端 IP', 'Payer client IP')}
              rules={[
                { required: true, whitespace: true, message: t('请输入付款客户端公网 IP', 'Enter the payer public IP') },
              ]}
              extra={t('微信 H5 下单必填，应填写实际发起付款设备的公网 IP。', 'Required by WeChat H5; use the payer device public IP.')}
            >
              <Input placeholder="203.0.113.10" />
            </Form.Item>
          ) : null}

          {selectedProviderCode === 'wechat_pay' && selectedScene === 'JSAPI' ? (
            <Form.Item
              name="openid"
              label="OpenID"
              rules={[
                { required: true, whitespace: true, message: t('请输入当前 AppID 下的用户 OpenID', 'Enter the user OpenID for this AppID') },
              ]}
            >
              <Input />
            </Form.Item>
          ) : null}

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
            <InputNumber min={0.01} precision={2} step={0.01} style={{ width: '100%' }} addonAfter="CNY" />
          </Form.Item>

          {isProduction ? (
            <Alert
              showIcon
              type="error"
              message={t('这是生产环境，将创建真实支付订单', 'This is production and creates a real payment order')}
              description={t('请保持 0.01 元验收金额；付款后会产生真实资金交易和支付回调。', 'Keep the ¥0.01 verification amount. Paying causes a real funds transfer and callback.')}
              style={{ marginBottom: 20 }}
            />
          ) : (
            <Alert
              showIcon
              type="info"
              message={t('当前为沙箱环境，不会扣除真实资金', 'This is a sandbox; no real funds are charged')}
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
                  : Promise.reject(new Error(t('请确认真实交易风险', 'Confirm the real-transaction warning'))),
              }]}
            >
              <Checkbox>
                {t('我确认这是生产订单，付款将产生真实资金交易', 'I understand this is a production order involving real funds')}
              </Checkbox>
            </Form.Item>
          ) : null}
        </Form>
      </Drawer>
    </Space>
  );
};
