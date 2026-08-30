import { formatMessage } from '@/i18n/formatMessage';
import { Form } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useLocation } from '@umijs/max';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import {
  applyBrandingRuntime,
  buildCopyrightText,
  DEFAULT_BRANDING_SETTINGS,
  getStoredBrandingSettings,
  normalizeBrandingSettings,
  persistBrandingSettings,
} from '@/branding/settings';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { LoginEncryptionKey, LoginResponse } from '@/types/api';
import type { LoginFormValues, LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { useLoginFlowRuntime } from './useLoginFlowRuntime';
import { request } from '@/services/common/request';
import type { AgreementSettings, BrandingSettings, LoginCapabilities } from '@/types/api';
import { loadSecuritySettings } from '@/auth/sessionSecurity';

const LOGIN_PAGE_PUBLIC_REFRESH_TIMEOUT_MS = 1500;
const LOGIN_ENCRYPTION_KEY_TIMEOUT_MS = 2500;
const LOGIN_PAGE_PUBLIC_BOOTSTRAP_TTL_MS = 30_000;
type ForcedPasswordChangeFormValues = {
  newPassword: string;
  confirmPassword: string;
};

type LoginCapabilitiesState = NonNullable<AppInitialState['loginCapabilities']>;

const DEFAULT_LOGIN_CAPABILITIES: LoginCapabilitiesState = {
  passwordLoginAvailable: true,
  smsLoginAvailable: false,
  emailLoginAvailable: false,
  wechatLoginAvailable: false,
  passkeyLoginAvailable: false,
  passkeyPasswordlessAvailable: false,
  registrationSmsVerificationRequired: false,
  registrationEmailVerificationRequired: false,
  loginModeOrder: ['passkey', 'sms', 'email', 'wechat', 'password'],
};

const getAvailableLoginModes = (capabilities: LoginCapabilitiesState): LoginMode[] => {
  const enabled: Record<LoginMode, boolean> = {
    passkey: Boolean(capabilities.passkeyLoginAvailable && capabilities.passkeyPasswordlessAvailable),
    sms: Boolean(capabilities.smsLoginAvailable),
    email: Boolean(capabilities.emailLoginAvailable),
    wechat: Boolean(capabilities.wechatLoginAvailable),
    password: Boolean(capabilities.passwordLoginAvailable),
  };
  const configuredOrder = capabilities.loginModeOrder?.filter((mode): mode is LoginMode =>
    mode === 'passkey' || mode === 'sms' || mode === 'email' || mode === 'wechat' || mode === 'password',
  ) || [];
  const defaultOrder: LoginMode[] = ['passkey', 'sms', 'email', 'wechat', 'password'];
  const order = [...configuredOrder, ...defaultOrder.filter((mode) => !configuredOrder.includes(mode))];
  const modes = order.filter((mode) => enabled[mode]);
  return modes.length ? modes : ['password'];
};

const defaultLoginMode = (capabilities: LoginCapabilitiesState): LoginMode => getAvailableLoginModes(capabilities)[0] || 'password';

export type LoginBootstrapFlow = {
  loginPageStyle: CSSProperties;
  brandingWebsiteName: string;
  brandingFooterItems: string[];
  agreementSettings: ReturnType<typeof normalizeAgreementSettings>;
  availableLoginModes: LoginMode[];
  activeLoginMode: LoginMode;
  setActiveLoginMode: (mode: LoginMode | ((current: LoginMode) => LoginMode)) => void;
  loginCapabilities: LoginCapabilitiesState;
  loginEncryptionKey: LoginEncryptionKey | null;
  loadLoginEncryptionKey: (forceRefresh?: boolean) => Promise<LoginEncryptionKey | null>;
  agreementPreviewOpen: boolean;
  setAgreementPreviewOpen: (open: boolean) => void;
  agreementPreviewKind: 'user' | 'privacy';
  openAgreementPreview: (kind: 'user' | 'privacy') => void;
  agreementPreviewTitle: string;
  agreementPreviewMarkdown: string;
};

const useLoginBootstrapFlow = (): LoginBootstrapFlow => {
  const { initialState, setInitialState } = useInitialStateModel();
  const [activeLoginMode, setActiveLoginMode] = useState<LoginMode>('password');
  const [agreementPreviewOpen, setAgreementPreviewOpen] = useState(false);
  const [agreementPreviewKind, setAgreementPreviewKind] = useState<'user' | 'privacy'>('user');
  const [loginEncryptionKey, setLoginEncryptionKey] = useState<LoginEncryptionKey | null>(null);
  const [, setLoginEncryptionLoading] = useState(false);
  const loginEncryptionLoadPromiseRef = useRef<Promise<LoginEncryptionKey | null> | null>(null);

  const brandingSettings = useMemo(
    () => normalizeBrandingSettings(initialState?.brandingSettings || getStoredBrandingSettings() || DEFAULT_BRANDING_SETTINGS),
    [initialState?.brandingSettings],
  );
  const brandingSettingsRef = useRef(brandingSettings);
  const agreementSettings = useMemo(() => normalizeAgreementSettings(initialState?.agreementSettings || DEFAULT_AGREEMENT_SETTINGS), [initialState?.agreementSettings]);
  const loginCapabilities: LoginCapabilitiesState = initialState?.loginCapabilities || DEFAULT_LOGIN_CAPABILITIES;
  const availableLoginModes = useMemo(() => getAvailableLoginModes(loginCapabilities), [loginCapabilities]);
  const hasFreshPublicBootstrapSnapshot = useMemo(() => {
    const loadedAt = initialState?.publicBootstrapLoadedAt;
    return typeof loadedAt === 'number' && Date.now() - loadedAt < LOGIN_PAGE_PUBLIC_BOOTSTRAP_TTL_MS;
  }, [initialState?.publicBootstrapLoadedAt]);

  useEffect(() => {
    brandingSettingsRef.current = brandingSettings;
  }, [brandingSettings]);

  useEffect(() => {
    if (initialState?.loginCapabilities) {
      return;
    }

    let disposed = false;

    void request<LoginCapabilities>('/v1/public/login-capabilities', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: LOGIN_PAGE_PUBLIC_REFRESH_TIMEOUT_MS,
    })
      .then((capabilities) => {
        if (disposed) {
          return;
        }

        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                loginCapabilities: {
                  ...DEFAULT_LOGIN_CAPABILITIES,
                  ...capabilities,
                },
              }
            : prev,
        );
      })
      .catch(() => {
        // Keep the bootstrap snapshot values when the public capability endpoint is temporarily unavailable.
      });

    return () => {
      disposed = true;
    };
  }, [initialState?.loginCapabilities, setInitialState]);

  useEffect(() => {
    if (initialState?.currentUser || hasFreshPublicBootstrapSnapshot) {
      return;
    }

    // The complete guest bootstrap owns publicBootstrapLoadedAt. These slice refreshes run
    // concurrently after an SPA logout and must not mark the others as stale or dispose them.
    let disposed = false;

    void request<AgreementSettings>('/v1/public/agreement-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: LOGIN_PAGE_PUBLIC_REFRESH_TIMEOUT_MS,
    })
      .then((settings) => {
        if (disposed) {
          return;
        }
        const normalizedAgreement = normalizeAgreementSettings(settings);
        setInitialState((prev: AppInitialState | undefined) => {
          if (!prev) {
            return prev;
          }
          const currentAgreement = normalizeAgreementSettings(prev.agreementSettings || DEFAULT_AGREEMENT_SETTINGS);
          if (
            currentAgreement.userAgreementMarkdown === normalizedAgreement.userAgreementMarkdown &&
            currentAgreement.privacyAgreementMarkdown === normalizedAgreement.privacyAgreementMarkdown
          ) {
            return prev;
          }
          return {
            ...prev,
            agreementSettings: normalizedAgreement,
          };
        });
      })
      .catch(() => {
        // Keep the bootstrap snapshot values when the public agreement endpoint is temporarily unavailable.
      });

    return () => {
      disposed = true;
    };
  }, [hasFreshPublicBootstrapSnapshot, initialState?.currentUser, setInitialState]);

  useEffect(() => {
    if (initialState?.currentUser || hasFreshPublicBootstrapSnapshot) {
      return;
    }

    let disposed = false;

    void loadSecuritySettings({
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: 3000,
    })
      .then((securitySettings) => {
        if (disposed) {
          return;
        }

        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                securitySettings,
              }
            : prev,
        );
      })
      .catch(() => {
        // Keep the current login-page snapshot when the public security endpoint is temporarily unavailable.
      });

    return () => {
      disposed = true;
    };
  }, [hasFreshPublicBootstrapSnapshot, initialState?.currentUser, setInitialState]);

  useEffect(() => {
    if (initialState?.currentUser) {
      return;
    }

    if (hasFreshPublicBootstrapSnapshot) {
      applyBrandingRuntime(brandingSettingsRef.current);
      return;
    }

    let disposed = false;

    void request<BrandingSettings>('/v1/public/branding-settings', {
      method: 'GET',
      skipAuth: true,
      silent: true,
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      timeoutMs: LOGIN_PAGE_PUBLIC_REFRESH_TIMEOUT_MS,
    })
      .then((settings) => {
        if (disposed) {
          return;
        }
        const normalizedBranding = normalizeBrandingSettings(settings);
        persistBrandingSettings(normalizedBranding);
        applyBrandingRuntime(normalizedBranding);
        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                brandingSettings: normalizedBranding,
                brandingRevision:
                  prev.brandingSettings?.websiteName === normalizedBranding.websiteName &&
                  prev.brandingSettings?.websiteLogoUrl === normalizedBranding.websiteLogoUrl &&
                  prev.brandingSettings?.websiteFaviconUrl === normalizedBranding.websiteFaviconUrl
                    ? prev.brandingRevision
                    : (prev.brandingRevision ?? 0) + 1,
              }
            : prev,
        );
      })
      .catch(() => {
        applyBrandingRuntime(brandingSettingsRef.current);
      });

    return () => {
      disposed = true;
    };
  }, [hasFreshPublicBootstrapSnapshot, initialState?.currentUser, setInitialState]);

  const loginPageStyle = useMemo(
    () =>
      ({
        '--saas-login-background-image': brandingSettings.loginBackgroundUrl
          ? `url("${brandingSettings.loginBackgroundUrl.replace(/"/g, '\\"')}")`
          : undefined,
      }) as CSSProperties,
    [brandingSettings.loginBackgroundUrl],
  );
  const brandingFooterItems = useMemo(
    () =>
      [
        brandingSettings.footerIcp,
        brandingSettings.footerPoliceBeian,
        brandingSettings.footerCopyright || buildCopyrightText(brandingSettings),
      ].filter((item): item is string => Boolean(item?.trim())),
    [brandingSettings],
  );

  useEffect(() => {
    setActiveLoginMode((current) => (availableLoginModes.includes(current) ? current : defaultLoginMode(loginCapabilities)));
  }, [availableLoginModes, loginCapabilities]);

  const openAgreementPreview = useCallback((kind: 'user' | 'privacy') => {
    setAgreementPreviewKind(kind);
    setAgreementPreviewOpen(true);
  }, []);

  const agreementPreviewTitle = useMemo(
    () =>
      agreementPreviewKind === 'user'
        ? formatMessage({ id: 'page.login.agreement.user', defaultMessage: 'User Agreement' })
        : formatMessage({ id: 'page.login.agreement.privacy', defaultMessage: 'Privacy Policy' }),
    [agreementPreviewKind],
  );
  const agreementPreviewMarkdown = useMemo(
    () => (agreementPreviewKind === 'user' ? agreementSettings.userAgreementMarkdown : agreementSettings.privacyAgreementMarkdown),
    [agreementPreviewKind, agreementSettings.privacyAgreementMarkdown, agreementSettings.userAgreementMarkdown],
  );
  const loadLoginEncryptionKey = useCallback(async (forceRefresh = false) => {
    if (loginEncryptionKey && !forceRefresh) {
      return loginEncryptionKey;
    }

    if (forceRefresh) {
      loginEncryptionLoadPromiseRef.current = null;
      setLoginEncryptionKey(null);
    }

    if (!loginEncryptionLoadPromiseRef.current) {
      setLoginEncryptionLoading(true);
      const cacheBuster = Date.now();
      loginEncryptionLoadPromiseRef.current = request<LoginEncryptionKey>('/v2/auth/login-encryption-key', {
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
        skipAuth: true,
        method: 'GET',
        params: { _t: cacheBuster },
        headers: { 'Cache-Control': 'no-cache' },
        timeoutMs: LOGIN_ENCRYPTION_KEY_TIMEOUT_MS,
      })
        .catch(() =>
          request<LoginEncryptionKey>('/v1/auth/login-encryption-key', {
            autoRedirectOnUnauthorized: false,
            allowUnauthorizedWithoutRedirect: true,
            silent: true,
            skipAuth: true,
            method: 'GET',
            params: { _t: Date.now() },
            headers: { 'Cache-Control': 'no-cache' },
            timeoutMs: LOGIN_ENCRYPTION_KEY_TIMEOUT_MS,
          }),
        )
        .then((key) => {
          setLoginEncryptionKey(key);
          return key;
        })
        .catch(() => null)
        .finally(() => {
          setLoginEncryptionLoading(false);
          loginEncryptionLoadPromiseRef.current = null;
        });
    }

    return loginEncryptionLoadPromiseRef.current;
  }, [loginEncryptionKey]);

  useEffect(() => {
    void loadLoginEncryptionKey();
  }, [loadLoginEncryptionKey]);

  return {
    loginPageStyle,
    brandingWebsiteName: brandingSettings.websiteName,
    brandingFooterItems,
    agreementSettings,
    availableLoginModes,
    activeLoginMode,
    setActiveLoginMode,
    loginCapabilities,
    loginEncryptionKey,
    loadLoginEncryptionKey,
    agreementPreviewOpen,
    setAgreementPreviewOpen,
    agreementPreviewKind,
    openAgreementPreview,
    agreementPreviewTitle,
    agreementPreviewMarkdown,
  };
};

export const useLoginFlow = () => {
  const [submitting, setSubmitting] = useState(false);
  const [passkeySubmitting, setPasskeySubmitting] = useState(false);
  const [pendingSecondFactorLogin, setPendingSecondFactorLogin] = useState<LoginResponse | null>(null);
  const [pendingPasswordChangeLogin, setPendingPasswordChangeLogin] = useState<LoginResponse | null>(null);
  const [restoredPasswordChangeRequired, setRestoredPasswordChangeRequired] = useState(false);
  const [passwordChangeSubmitting, setPasswordChangeSubmitting] = useState(false);
  const [registrationSuggestion, setRegistrationSuggestion] = useState<{ mobile: string; nonce: number } | null>(null);
  const [loginForm] = Form.useForm<LoginFormValues>();
  const [forcedPasswordChangeForm] = Form.useForm<ForcedPasswordChangeFormValues>();

  const resetSecondFactorFlow = useCallback(() => {
    setPendingSecondFactorLogin(null);
    loginForm.setFieldsValue({ verificationCode: undefined });
  }, [loginForm]);

  const flowState = {
    submitting,
    setSubmitting,
    passkeySubmitting,
    setPasskeySubmitting,
    pendingSecondFactorLogin,
    setPendingSecondFactorLogin,
    pendingPasswordChangeLogin,
    setPendingPasswordChangeLogin,
    restoredPasswordChangeRequired,
    setRestoredPasswordChangeRequired,
    passwordChangeSubmitting,
    setPasswordChangeSubmitting,
    loginForm,
    forcedPasswordChangeForm,
    resetSecondFactorFlow,
    registrationSuggestion,
    setRegistrationSuggestion,
  };
  const { initialState, setInitialState } = useInitialStateModel();
  const location = useLocation();
  const bootstrapFlow = useLoginBootstrapFlow();

  return useLoginFlowRuntime({
    flowState,
    bootstrapFlow,
    initialState,
    locationSearch: location.search,
    locationPathname: location.pathname,
    setInitialState,
  });
};
