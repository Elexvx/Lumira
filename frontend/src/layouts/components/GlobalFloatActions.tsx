import { QrcodeOutlined, ReloadOutlined, VerticalAlignTopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useIntl, useLocation } from '@umijs/max';
import { Empty, FloatButton, Popover, Typography } from 'antd';
import { isLoggedIn } from '@/auth/session';
import { DEFAULT_FLOATING_WINDOW_SETTINGS, normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import { useResponsive } from '@/hooks/useResponsive';
import { systemService } from '@/services/system';
import './GlobalFloatActions.css';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


export const GlobalFloatActions = () => {
  const intl = useIntl();
  const { pathname } = useLocation();
  const { isMobile } = useResponsive();
  const floatingSettingsQuery = useQuery({
    queryKey: ['floating-window-settings'],
    queryFn: () => systemService.floatingWindowSettings(API_OPTS.SILENT_NO_REDIRECT),
    enabled: isLoggedIn(),
    staleTime: 5 * 60 * 1000,
  });
  const floatingSettings = normalizeFloatingWindowSettings(floatingSettingsQuery.data || DEFAULT_FLOATING_WINDOW_SETTINGS);
  const showApiDocsQr = floatingSettings.apiDocsQrEnabled;
  const isAssistantPage = pathname === '/ai/assistant' || pathname.startsWith('/ai/share/');

  if (!isLoggedIn()) {
    return null;
  }

  return (
    <FloatButton.Group
      className="saas-global-float-actions"
      shape="square"
      style={{
        right: isAssistantPage ? (isMobile ? 12 : 16) : isMobile ? 16 : 32,
        bottom: isAssistantPage ? (isMobile ? 24 : 56) : isMobile ? 24 : 40,
      }}
    >
      {showApiDocsQr ? (
        <Popover
          overlayClassName="saas-global-float-actions__qr-popover"
          placement="left"
          trigger={['hover', 'click']}
          content={
            <div className="saas-global-float-actions__qr-card">
              <Typography.Text className="saas-global-float-actions__qr-title" type="secondary">
                {floatingSettings.apiDocsQrTitle}
              </Typography.Text>
              <div className="saas-global-float-actions__qr-image-wrap">
                {floatingSettings.apiDocsQrImageUrl ? (
                  <img className="saas-global-float-actions__qr-image" src={floatingSettings.apiDocsQrImageUrl} alt={floatingSettings.apiDocsQrTitle} />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请在个性化设置上传二维码" />
                )}
              </div>
            </div>
          }
        >
          <FloatButton
            icon={<QrcodeOutlined />}
            tooltip={intl.formatMessage({ id: 'global.float.qrCode', defaultMessage: '二维码' })}
            aria-label={intl.formatMessage({ id: 'global.float.qrCode', defaultMessage: '二维码' })}
          />
        </Popover>
      ) : null}
      <FloatButton
        icon={<ReloadOutlined />}
        tooltip={intl.formatMessage({ id: 'global.float.refresh', defaultMessage: '刷新页面' })}
        aria-label={intl.formatMessage({ id: 'global.float.refresh', defaultMessage: '刷新页面' })}
        onClick={() => window.location.reload()}
      />
      <FloatButton.BackTop
        icon={<VerticalAlignTopOutlined />}
        tooltip={intl.formatMessage({ id: 'global.float.backTop', defaultMessage: '回到顶部' })}
        aria-label={intl.formatMessage({ id: 'global.float.backTop', defaultMessage: '回到顶部' })}
        visibilityHeight={0}
      />
    </FloatButton.Group>
  );
};
