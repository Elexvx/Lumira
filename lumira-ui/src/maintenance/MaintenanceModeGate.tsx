import { ClockCircleOutlined, MoreOutlined } from '@ant-design/icons';
import { FloatButton, Result } from 'antd';
import { history } from '@umijs/max';
import type { ReactNode } from 'react';
import { useSyncExternalStore } from 'react';
import {
  getBrandingSettingsSnapshot,
  subscribeBrandingSettings,
} from '@/branding/settings';
import { getStoredCurrentUser, subscribeSessionState } from '@/auth/sessionState';
import { isLoggedIn, performLogout } from '@/auth/sessionLifecycle';
import { databaseMessage } from '@/i18n/databaseMessage';
import {
  MAINTENANCE_ADMIN_TARGET,
  MAINTENANCE_LOGIN_PATH,
  shouldShowMaintenancePage,
} from './maintenanceMode';
import { formatMaintenanceCountdown, useMaintenanceCountdown } from './maintenanceCountdown';
import './MaintenanceModeGate.css';

const subscribeLocation = (listener: () => void) => history.listen(listener);

const getLocationSnapshot = () =>
  `${history.location.pathname}${history.location.search || ''}${history.location.hash || ''}`;

const buildAdminLoginTarget = () =>
  `${MAINTENANCE_LOGIN_PATH}?redirect=${encodeURIComponent(MAINTENANCE_ADMIN_TARGET)}`;

const maintenanceLoginEntryLabel = () =>
  databaseMessage('ui.settings.personalization.maintenance.loginEntry');

export const MaintenanceModeGate = ({ children }: { children: ReactNode }) => {
  const brandingSettings = useSyncExternalStore(
    subscribeBrandingSettings,
    getBrandingSettingsSnapshot,
    getBrandingSettingsSnapshot,
  );
  const locationSnapshot = useSyncExternalStore(
    subscribeLocation,
    getLocationSnapshot,
    () => '/',
  );
  const pathname = locationSnapshot.split(/[?#]/, 1)[0] || '/';
  const currentUser = useSyncExternalStore(
    subscribeSessionState,
    getStoredCurrentUser,
    () => null,
  );
  const maintenanceRemainingSeconds = useMaintenanceCountdown(brandingSettings.maintenanceEndAt);

  if (
    !shouldShowMaintenancePage({
      brandingSettings,
      pathname,
      search: history.location.search || '',
      currentUser,
    })
  ) {
    return <>{children}</>;
  }

  const openAdminLogin = () => {
    const loginTarget = buildAdminLoginTarget();
    if (isLoggedIn()) {
      void performLogout({
        reason: 'user_initiated',
        loginTarget,
      });
      return;
    }
    history.push(loginTarget);
  };

  return (
    <main className="maintenance-mode" role="main">
      <section className="maintenance-mode__panel" aria-labelledby="maintenance-mode-title">
        <div className="maintenance-mode__brand">
          {brandingSettings.websiteLogoUrl ? (
            <img
              className="maintenance-mode__logo"
              src={brandingSettings.websiteLogoUrl}
              alt={brandingSettings.websiteName}
            />
          ) : (
            <span className="maintenance-mode__brand-name">{brandingSettings.websiteName}</span>
          )}
        </div>
        <Result
          className="maintenance-mode__result"
          status="info"
          title={<span id="maintenance-mode-title">{brandingSettings.maintenanceTitle}</span>}
          subTitle={
            <div className="maintenance-mode__subtitle">
              <span>{brandingSettings.maintenanceMessage}</span>
              {maintenanceRemainingSeconds !== null ? (
                <span
                  className="maintenance-mode__countdown"
                  role="timer"
                  aria-live="polite"
                  aria-label={brandingSettings.maintenanceTitle}
                >
                  <ClockCircleOutlined aria-hidden="true" />
                  <span>{formatMaintenanceCountdown(maintenanceRemainingSeconds)}</span>
                </span>
              ) : null}
            </div>
          }
        />
      </section>
      <FloatButton
        className="maintenance-mode__admin-entry"
        type="primary"
        shape="circle"
        icon={<MoreOutlined />}
        aria-label={maintenanceLoginEntryLabel()}
        tooltip={maintenanceLoginEntryLabel()}
        onClick={openAdminLogin}
      />
    </main>
  );
};
