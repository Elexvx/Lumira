import {
  FileTextOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  VerticalAlignTopOutlined,
} from '@ant-design/icons';
import { history, useAccess, useIntl } from '@umijs/max';
import { FloatButton } from 'antd';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { isLoggedIn } from '@/auth/session';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import './GlobalFloatActions.css';

export const GlobalFloatActions = () => {
  const { initialState } = useInitialStateModel();
  const access = useAccess();
  const intl = useIntl();
  const { isMobile } = useResponsive();
  const brandingSettings = normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS);
  const helpLink = brandingSettings.helpLinkEnabled ? resolveExternalLink(brandingSettings.helpLinkUrl) : '';
  const canVisitApiDocs = Boolean((access as Record<string, unknown>).canVisitSystemMonitoringDocs);
  const visibleActionCount = Number(Boolean(helpLink)) + Number(canVisitApiDocs) + 2;

  if (!isLoggedIn() || visibleActionCount <= 1) {
    return null;
  }

  return (
    <FloatButton.Group
      className="saas-global-float-actions"
      shape="square"
      style={{
        right: isMobile ? 16 : 32,
        bottom: isMobile ? 24 : 40,
      }}
    >
      {helpLink ? (
        <FloatButton
          icon={<QuestionCircleOutlined />}
          tooltip={intl.formatMessage({ id: 'global.float.help', defaultMessage: '帮助中心' })}
          aria-label={intl.formatMessage({ id: 'global.float.help', defaultMessage: '帮助中心' })}
          onClick={() => openExternalLink(helpLink)}
        />
      ) : null}
      {canVisitApiDocs ? (
        <FloatButton
          icon={<FileTextOutlined />}
          tooltip={intl.formatMessage({ id: 'global.float.apiDocs', defaultMessage: '接口文档' })}
          aria-label={intl.formatMessage({ id: 'global.float.apiDocs', defaultMessage: '接口文档' })}
          onClick={() => history.push('/settings/api-docs')}
        />
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
        visibilityHeight={120}
      />
    </FloatButton.Group>
  );
};

const resolveExternalLink = (value?: string | null) => {
  const trimmed = value?.trim() || '';
  if (!trimmed) {
    return '';
  }
  if (/^(https?:|mailto:|tel:)/i.test(trimmed) || trimmed.startsWith('/') || trimmed.startsWith('#')) {
    return trimmed;
  }
  if (/^[a-zA-Z][a-zA-Z\d+.-]*:/.test(trimmed)) {
    return '';
  }
  return `https://${trimmed}`;
};

const openExternalLink = (url?: string) => {
  if (!url || typeof window === 'undefined') {
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
};
