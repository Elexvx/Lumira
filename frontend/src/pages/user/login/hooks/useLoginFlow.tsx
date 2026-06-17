import { formatMessage } from '@umijs/max';
import { Form } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useLocation } from '@umijs/max';
import { DEFAULT_AGREEMENT_SETTINGS, normalizeAgreementSettings } from '@/agreement/settings';
import { DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import type { AppInitialState } from '@/app';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { LoginEncryptionKey, LoginResponse } from '@/types/api';
import { showErrorMessage } from '@/utils/errorMessage';
import type { LoginFormValues, LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { useLoginFlowRuntime } from './useLoginFlowRuntime';
import { request } from '@/services/common/request';
import type { LoginCapabilities } from '@/types/api';

const INITIAL_PASSWORD = '123456';
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
  loginModeOrder: ['passkey', 'sms', 'email', 'password'],
};

const getAvailableLoginModes = (capabilities: LoginCapabilitiesState): LoginMode[] => {
  const enabled: Record<LoginMode, boolean> = {
    passkey: Boolean(capabilities.passkeyLoginAvailable && capabilities.passkeyPasswordlessAvailable),
    sms: Boolean(capabilities.smsLoginAvailable),
    email: Boolean(capabilities.emailLoginAvailable),
    password: Boolean(capabilities.passwordLoginAvailable),
  };
  const configuredOrder = capabilities.loginModeOrder?.filter((mode): mode is LoginMode =>
    mode === 'passkey' || mode === 'sms' || mode === 'email' || mode === 'password',
  ) || [];
  const defaultOrder: LoginMode[] = ['passkey', 'sms', 'email', 'password'];
  const order = [...configuredOrder, ...defaultOrder.filter((mode) => !configuredOrder.includes(mode))];
  const modes = order.filter((mode) => enabled[mode]);
  return modes.length ? modes : ['password'];
};

const defaultLoginMode = (capabilities: LoginCapabilitiesState): LoginMode => getAvailableLoginModes(capabilities)[0] || 'password';

export type LoginBootstrapFlow = {
  loginPageStyle: CSSProperties;
  brandingWebsiteName: string;
  agreementSettings: ReturnType<typeof normalizeAgreementSettings>;
  availableLoginModes: LoginMode[];
  activeLoginMode: LoginMode;
  setActiveLoginMode: (mode: LoginMode | ((current: LoginMode) => LoginMode)) => void;
  loginCapabilities: LoginCapabilitiesState;
  loginEncryptionKey: LoginEncryptionKey | null;
  loadLoginEncryptionKey: () => Promise<LoginEncryptionKey | null>;
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

  const brandingSettings = useMemo(() => normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS), [initialState?.brandingSettings]);
  const agreementSettings = useMemo(() => normalizeAgreementSettings(initialState?.agreementSettings || DEFAULT_AGREEMENT_SETTINGS), [initialState?.agreementSettings]);
  const loginCapabilities: LoginCapabilitiesState = initialState?.loginCapabilities || DEFAULT_LOGIN_CAPABILITIES;
  const availableLoginModes = useMemo(() => getAvailableLoginModes(loginCapabilities), [loginCapabilities]);

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

  const loginPageStyle = useMemo(
    () =>
      ({
        '--saas-login-background-image': brandingSettings.loginBackgroundUrl
          ? `url("${brandingSettings.loginBackgroundUrl.replace(/"/g, '\\"')}")`
          : undefined,
      }) as CSSProperties,
    [brandingSettings.loginBackgroundUrl],
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
  const loadLoginEncryptionKey = useCallback(async () => {
    if (loginEncryptionKey) {
      return loginEncryptionKey;
    }

    if (!loginEncryptionLoadPromiseRef.current) {
      setLoginEncryptionLoading(true);
      loginEncryptionLoadPromiseRef.current = request<LoginEncryptionKey>('/v2/auth/login-encryption-key', {
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
        skipAuth: true,
        method: 'GET',
      })
        .catch(() =>
          request<LoginEncryptionKey>('/v1/auth/login-encryption-key', {
            autoRedirectOnUnauthorized: false,
            allowUnauthorizedWithoutRedirect: true,
            silent: true,
            skipAuth: true,
            method: 'GET',
          }),
        )
        .then((key) => {
          setLoginEncryptionKey(key);
          return key;
        })
        .catch((error) => {
          showErrorMessage(error, formatMessage({ id: 'page.login.error.loginEncryption', defaultMessage: 'Failed to load login encryption info, please refresh and try again' }));
          return null;
        })
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
  const [pendingPasswordChangeCurrentPassword, setPendingPasswordChangeCurrentPassword] = useState(INITIAL_PASSWORD);
  const [passwordChangeSubmitting, setPasswordChangeSubmitting] = useState(false);
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
    pendingPasswordChangeCurrentPassword,
    setPendingPasswordChangeCurrentPassword,
    passwordChangeSubmitting,
    setPasswordChangeSubmitting,
    loginForm,
    forcedPasswordChangeForm,
    resetSecondFactorFlow,
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
