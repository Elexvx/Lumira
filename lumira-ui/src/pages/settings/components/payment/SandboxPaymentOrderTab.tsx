import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Form, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { request } from '@/services/common/request';
import { createSandboxSimulationOrder, listSandboxSimulationOrders } from '@/services/payment/api';
import { message } from '@/theme/antdFeedbackBridge';
import type { PagedResult, PaymentProviderSettings, SandboxSimulationOrderRecord, UserRecord } from '@/types/api';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type SimulationFormValues = {
  targetUserId: number;
  amountYuan: number;
};

const accountName = (record: SandboxSimulationOrderRecord) =>
  record.realName || record.nickname || record.username || `#${record.targetUserId}`;

const userLabel = (user: UserRecord) => {
  const name = user.realName || user.nickname || user.username;
  const contact = user.mobile || user.email;
  return contact ? `${name} · ${contact}` : `${name} · ${user.username}`;
};

export const SandboxPaymentOrderTab = ({
  paymentSettings: _paymentSettings,
  canCreateOrders,
}: {
  paymentSettings: PaymentProviderSettings[];
  canCreateOrders: boolean;
}) => {
  const [form] = Form.useForm<SimulationFormValues>();
  const [orders, setOrders] = useState<SandboxSimulationOrderRecord[]>([]);
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [userLoading, setUserLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      setOrders(await listSandboxSimulationOrders());
    } finally {
      setLoading(false);
    }
  }, []);

  const loadUsers = useCallback(async (keyword?: string) => {
    setUserLoading(true);
    try {
      const result = await request<PagedResult<UserRecord>>('/v1/system/users', {
        method: 'GET',
        params: {
          current: 1,
          pageSize: 20,
          keyword: keyword?.trim() || undefined,
          status: 'ENABLED',
          _t: Date.now(),
        },
      });
      setUsers(result.records || []);
    } finally {
      setUserLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const openDrawer = () => {
    form.resetFields();
    form.setFieldValue('amountYuan', 0.01);
    setDrawerOpen(true);
    void loadUsers();
  };

  const submit = async () => {
    try {
      setSubmitting(true);
      const values = await form.validateFields();
      await createSandboxSimulationOrder({
        targetUserId: values.targetUserId,
        amountMinor: Math.round(values.amountYuan * 100),
      });
      message.success(t('模拟订单已生成，全程未调用云端支付平台', 'Simulation order created without calling a cloud payment provider'));
      setDrawerOpen(false);
      await loadOrders();
    } finally {
      setSubmitting(false);
    }
  };

  const columns = useMemo<ColumnsType<SandboxSimulationOrderRecord>>(() => [
    {
      title: t('订单号', 'Order number'),
      dataIndex: 'orderNo',
      width: 250,
      render: (value: string) => <Typography.Text copyable>{value}</Typography.Text>,
    },
    {
      title: t('账户', 'Account'),
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{accountName(record)}</Typography.Text>
          <Typography.Text type="secondary">{record.username || `ID ${record.targetUserId}`}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('订单价格', 'Amount'),
      dataIndex: 'amountMinor',
      width: 150,
      render: (value: number, record) => `${(value / 100).toFixed(2)} ${record.currency || 'CNY'}`,
    },
    {
      title: t('模式', 'Mode'),
      width: 150,
      render: (_, record) => (
        <Tag color={record.localOnly && !record.cloudRequestSent ? 'green' : 'red'}>
          {record.localOnly && !record.cloudRequestSent ? t('本地沙箱', 'Local sandbox') : t('异常', 'Invalid')}
        </Tag>
      ),
    },
    {
      title: t('状态', 'Status'),
      dataIndex: 'status',
      width: 130,
      render: () => <Tag color="processing">{t('已模拟', 'Simulated')}</Tag>,
    },
    {
      title: t('生成时间', 'Created at'),
      dataIndex: 'createdAt',
      width: 190,
      render: (value: string) => value ? value.replace('T', ' ') : '-',
    },
  ], []);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        showIcon
        type="info"
        message={t('本页面仅生成本地沙箱模拟订单', 'This page creates local sandbox simulations only')}
        description={t(
          '不会读取正式支付配置，不会调用支付平台 SDK、网关或任何云端下单接口。',
          'Production payment settings, provider SDKs, gateways, and cloud order APIs are never used.',
        )}
      />
      {!canCreateOrders ? (
        <Alert showIcon type="warning" message={t('当前账号没有生成模拟订单的权限', 'You cannot create simulation orders')} />
      ) : null}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>{t('模拟订单', 'Simulation orders')}</Typography.Title>
          <Typography.Text type="secondary">{t('最近生成的 100 条本地沙箱记录', 'The latest 100 local sandbox records')}</Typography.Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void loadOrders()} loading={loading}>
            {t('刷新', 'Refresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openDrawer} disabled={!canCreateOrders}>
            {t('生成模拟订单', 'Create simulation order')}
          </Button>
        </Space>
      </div>
      <Table<SandboxSimulationOrderRecord>
        rowKey="orderNo"
        columns={columns}
        dataSource={orders}
        loading={loading}
        pagination={false}
        scroll={{ x: 1090 }}
        locale={{ emptyText: t('暂无模拟订单', 'No simulation orders') }}
      />
      <Drawer
        title={t('生成模拟订单', 'Create simulation order')}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
        extra={(
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>{t('取消', 'Cancel')}</Button>
            <Button type="primary" loading={submitting} onClick={() => void submit()}>{t('生成订单', 'Create order')}</Button>
          </Space>
        )}
      >
        <Alert
          showIcon
          type="success"
          message={t('本地模拟，不会产生真实扣款', 'Local simulation with no real charge')}
          style={{ marginBottom: 20 }}
        />
        <Form<SimulationFormValues> form={form} layout="vertical">
          <Form.Item
            name="targetUserId"
            label={t('选择账户', 'Select account')}
            rules={[{ required: true, message: t('请选择需要生成订单的账户', 'Select an account') }]}
          >
            <Select
              showSearch
              filterOption={false}
              loading={userLoading}
              placeholder={t('搜索用户名、姓名、手机号或邮箱', 'Search username, name, mobile, or email')}
              options={users.map((user) => ({ label: userLabel(user), value: user.id }))}
              onSearch={(value) => void loadUsers(value)}
              notFoundContent={userLoading ? t('加载中…', 'Loading…') : t('未找到启用账户', 'No enabled accounts found')}
            />
          </Form.Item>
          <Form.Item
            name="amountYuan"
            label={t('订单价格（元）', 'Order amount (CNY)')}
            rules={[
              { required: true, message: t('请输入订单价格', 'Enter an order amount') },
              { type: 'number', min: 0.01, message: t('订单价格必须大于 0', 'Amount must be greater than zero') },
            ]}
          >
            <InputNumber min={0.01} precision={2} step={1} style={{ width: '100%' }} addonAfter="CNY" />
          </Form.Item>
        </Form>
      </Drawer>
    </Space>
  );
};
