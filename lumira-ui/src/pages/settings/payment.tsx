import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { usePaymentManagement } from './components/payment/hooks/usePaymentManagement';
import { SandboxPaymentOrderTab } from './components/payment/SandboxPaymentOrderTab';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { Button, Dropdown, Tabs } from 'antd';
import { DownOutlined, PlusOutlined } from '@ant-design/icons';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const PAYMENT_PROVIDER_TITLES: Record<string, string> = {
  alipay: t('支付宝', 'Alipay'),
  wechat_pay: t('微信支付', 'WeChat Pay'),
  stripe: 'Stripe',
  paypal: 'PayPal',
};

const resolveDrawerTitle = (providerCode?: string | null) => {
  if (!providerCode) {
    return t('支付设置', 'Payment settings');
  }
  return t('{provider} 配置', '{provider} configuration').replace('{provider}', PAYMENT_PROVIDER_TITLES[providerCode] || providerCode);
};

const SystemPaymentPage = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const canUpdateSettings = actionPermission.can('payment:config:update');
  const canTestSettings = actionPermission.can('payment:config:test');
  const canCreateSandboxOrder = actionPermission.can('payment:order:create');
  const { tablePack, drawerPack } = usePaymentManagement({
    canUpdateSettings,
    canTestSettings,
    isMobile: responsive.isMobile,
  });

  return (
    <ManagementPage title={t('支付设置', 'Payment settings')}>
      <ManagementPageBody>
        <Tabs
          defaultActiveKey="settings"
          destroyInactiveTabPane
          items={[
            {
              key: 'settings',
              label: t('支付设置', 'Payment settings'),
              children: (
                <ManagementTable
                  columns={tablePack.paymentColumns}
                  isMobile={tablePack.isMobile}
                  loading={tablePack.paymentLoading}
                  dataSource={tablePack.paymentRows}
                  rowKey="key"
                  pagination={false}
                  search={false}
                  onRefresh={tablePack.onRefresh}
                  toolBarRender={() => [
                    <Dropdown
                      key="payment-add"
                      trigger={['click']}
                      menu={{ items: tablePack.toolbarProps.addPaymentProviderItems }}
                      placement="bottomRight"
                    >
                      <Button type="primary" disabled={!tablePack.toolbarProps.canUpdateSettings || !tablePack.toolbarProps.addPaymentProviderItems?.length} icon={<PlusOutlined />}>
                        {t('添加', 'Add')} <DownOutlined />
                      </Button>
                    </Dropdown>,
                  ]}
                />
              ),
            },
            {
              key: 'sandbox-orders',
              label: t('手动生成支付订单', 'Manual payment order'),
              children: (
                <SandboxPaymentOrderTab
                  paymentSettings={tablePack.paymentSettingsData}
                  canCreateOrders={canCreateSandboxOrder}
                />
              ),
            },
          ]}
        />
      </ManagementPageBody>
      <ManagementDrawer
        open={drawerPack.drawerProps.open}
        onClose={drawerPack.drawerProps.onClose}
        title={resolveDrawerTitle(drawerPack.drawerProps.configDrawerMode)}
        footerActions={drawerPack.drawerProps.footerActions}
      >
        {drawerPack.drawerProps.renderConfigDrawerContent()}
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default SystemPaymentPage;
