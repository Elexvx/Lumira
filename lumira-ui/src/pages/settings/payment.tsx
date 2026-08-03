import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { usePaymentManagement } from './components/payment/hooks/usePaymentManagement';
import { SandboxPaymentOrderTab } from './components/payment/SandboxPaymentOrderTab';

import { Button, Dropdown, Tabs } from 'antd';
import { DownOutlined, PlusOutlined } from '@ant-design/icons';
import { paymentProviderDisplayName } from './components/payment/paymentDisplay';
import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const resolveDrawerTitle = (providerCode?: string | null) => {
  if (!providerCode) {
    return t('ui.settings.payment.paymentSettings');
  }
  return t('ui.settings.payment.configuration').replace(
    '{provider}',
    paymentProviderDisplayName(providerCode, providerCode),
  );
};

const SystemPaymentPage = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const canUpdateSettings = actionPermission.can('payment:config:update');
  const canTestSettings = actionPermission.can('payment:config:test');
  const canCreatePaymentOrder = actionPermission.can('payment:order:create');
  const { tablePack, drawerPack } = usePaymentManagement({
    canUpdateSettings,
    canTestSettings,
    isMobile: responsive.isMobile,
  });

  return (
    <ManagementPage title={t('ui.settings.payment.paymentSettings')}>
      <ManagementPageBody>
        <Tabs
          defaultActiveKey={new URLSearchParams(window.location.search).get('tab') || 'settings'}
          destroyInactiveTabPane
          items={[
            {
              key: 'settings',
              label: t('ui.settings.payment.paymentSettings'),
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
                        {t('ui.settings.payment.add')} <DownOutlined />
                      </Button>
                    </Dropdown>,
                  ]}
                />
              ),
            },
            {
              key: 'sandbox-orders',
              label: t('ui.settings.payment.manualPaymentOrder'),
              children: (
                <SandboxPaymentOrderTab
                  paymentSettings={tablePack.paymentSettingsData}
                  canCreateOrders={canCreatePaymentOrder}
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
