import { ToolOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { ReactNode } from 'react';
import { useSyncExternalStore } from 'react';
import {
  getBrandingSettingsSnapshot,
  subscribeBrandingSettings,
} from '@/branding/settings';
import { getStoredCurrentUser } from '@/auth/sessionState';
import { isLoggedIn, performLogout } from '@/auth/sessionLifecycle';
import {
  MAINTENANCE_ADMIN_TARGET,
  MAINTENANCE_LOGIN_PATH,
  shouldShowMaintenancePage,
} from './maintenanceMode';
import './MaintenanceModeGate.css';

const subscribeLocation = (listener: () => void) => history.listen(listener);

const getLocationSnapshot = () =>
  `${history.location.pathname}${history.location.search || ''}${history.location.hash || ''}`;

const buildAdminLoginTarget = () =>
  `${MAINTENANCE_LOGIN_PATH}?redirect=${encodeURIComponent(MAINTENANCE_ADMIN_TARGET)}`;

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
  const currentUser = getStoredCurrentUser();

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
      <div className="maintenance-mode__ambient maintenance-mode__ambient--one" aria-hidden="true" />
      <div className="maintenance-mode__ambient maintenance-mode__ambient--two" aria-hidden="true" />
      <section className="maintenance-mode__content" aria-labelledby="maintenance-mode-title">
        {brandingSettings.websiteLogoUrl ? (
          <img
            className="maintenance-mode__logo"
            src={brandingSettings.websiteLogoUrl}
            alt={brandingSettings.websiteName}
          />
        ) : (
          <div className="maintenance-mode__brand">{brandingSettings.websiteName}</div>
        )}
        <div className="maintenance-mode__icon" aria-hidden="true">
          <ToolOutlined />
        </div>
        <h1 id="maintenance-mode-title">
          {brandingSettings.maintenanceTitle}
        </h1>
        <p>{brandingSettings.maintenanceMessage}</p>
        <div className="maintenance-mode__pulse" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>
      <button
        className="maintenance-mode__admin-entry"
        type="button"
        aria-label="管理员登录"
        title="管理员登录"
        onClick={openAdminLogin}
      >
        <span aria-hidden="true" />
      </button>
    </main>
  );
};
