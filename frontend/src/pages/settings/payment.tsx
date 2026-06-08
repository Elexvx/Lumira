import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useResponsive } from '@/hooks/useResponsive';
import { usePaymentManagement } from './components/payment/hooks/usePaymentManagement';

const PAYMENT_PROVIDER_TITLES: Record<string, string> = {
  alipay: '支付宝',
  wechat_pay: '微信支付',
  stripe: 'Stripe',
  paypal: 'PayPal',
};

const resolveDrawerTitle = (providerCode?: string | null) => {
  if (!providerCode) {
    return '支付设置';
  }
  return `${PAYMENT_PROVIDER_TITLES[providerCode] || providerCode} 配置`;
};

const SystemPaymentPage = () => {
  const responsive = useResponsive();
  const actionPermission = useActionPermission();
  const canManageSettings = actionPermission.can('payment:config:update') || actionPermission.can('payment:config:test');
  const { tablePack, drawerPack } = usePaymentManagement({
    canManageSettings,
    isMobile: responsive.isMobile,
  });

  return (
    <ManagementPage title="支付设置">
      <ManagementPageBody>
        <ManagementTable
          columns={tablePack.paymentColumns}
          isMobile={tablePack.isMobile}
          loading={tablePack.paymentLoading}
          dataSource={tablePack.paymentRows}
          rowKey="key"
          pagination={false}
          search={false}
          onRefresh={tablePack.onRefresh}
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
