import type { AppInitialState } from '@/app';
import { formatMessage, history } from '@umijs/max';
import { message } from '@/theme/antdFeedbackBridge';
import type { FormInstance } from 'antd';
import type { FormProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import { flushSync } from 'react-dom';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';
import { clearAuthSession, isLoggedIn } from '@/auth/sessionLifecycle';
import { normalizeAuthenticatedMenuTree } from '@/auth/authenticatedMenuTree';
import { createLoginSessionBroadcastListener, resolveAuthorizedLoginRedirectTarget, resolveLoginPageRuntimeRedirectTarget } from '@/auth/loginRedirect';
import { isPasskeySupported, toAuthenticationPayload, toPublicKeyRequestOptions } from '@/auth/passkey';
import { DEFAULT_SECURITY_SETTINGS } from '@/auth/securitySettingsTypes';
import { normalizeSecuritySettings } from '@/auth/securitySettingsNormalize';
import { persistSecuritySettings } from '@/auth/securitySettingsStorage';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import type { LoginCodeChallenge, LoginEncryptionKey } from '@/types/api';
import type { CaptchaChallenge, CaptchaType, LoginResponse, RuntimeAppearanceSettings, SecuritySettings } from '@/types/api';
import type { LoginFormValues, LoginMode } from '@/pages/user/login/components/LoginFormFields';
import { showErrorMessage } from '@/utils/errorMessage';
import type { LoginBootstrapFlow } from './useLoginFlow';
import { initializeAfterLogin } from '@/auth/sessionBootstrap';
import { persistBrandingSettings, DEFAULT_BRANDING_SETTINGS, normalizeBrandingSettings } from '@/branding/settings';
import { persistWatermarkSettings } from '@/watermark/settingsStorage';
import { DEFAULT_WATERMARK_SETTINGS } from '@/watermark/settingsTypes';
import { normalizeWatermarkSettings } from '@/watermark/settingsNormalize';
import { DEFAULT_FLOATING_WINDOW_SETTINGS, normalizeFloatingWindowSettings } from '@/floatingWindow/settings';
import { restoreSession } from '@/auth/sessionBootstrap';
import { request, type RequestOptions } from '@/services/common/request';
import type { MenuNode, PasskeyOptions, PluginAvailability } from '@/types/api';
import type { BrandingSettings, FloatingWindowSettings, WatermarkSettings } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { isLoginPasswordPayloadError, resolveLoginErrorFeedback, shouldFallbackToLegacyPasswordLogin } from '@/pages/user/login/utils/loginErrorFeedback';
import { consumeWechatOAuthCallback } from '@/pages/user/login/utils/wechatOAuthCallback';

export type LoginInputKind = 'account' | 'mobile' | 'email' | 'verificationCode';

type KeyEventLike = {
  altKey?: boolean;
  ctrlKey?: boolean;
  isComposing?: boolean;
  key?: string;
  keyCode?: number;
  metaKey?: boolean;
};

type PasteEventLike = {
  clipboardData?: {
    getData: (type: string) => string;
  };
};

const UNSAFE_ACCOUNT_CHARS_PATTERN = /[^A-Za-z0-9@._-]/g;
const MOBILE_UNSAFE_CHARS_PATTERN = /\D/g;
const VERIFICATION_CODE_UNSAFE_CHARS_PATTERN = /[^A-Za-z0-9]/g;

const coerceInputValue = (value: unknown) => String(value ?? '');

export const sanitizeLoginInputValue = (value: unknown, kind: LoginInputKind) => {
  const text = coerceInputValue(value);

  if (kind === 'mobile') {
    return text.replace(MOBILE_UNSAFE_CHARS_PATTERN, '').slice(0, 11);
  }

  if (kind === 'verificationCode') {
    return text.replace(VERIFICATION_CODE_UNSAFE_CHARS_PATTERN, '').slice(0, 12);
  }

  return text.replace(UNSAFE_ACCOUNT_CHARS_PATTERN, '').slice(0, 128);
};

export const getLoginInputValueFromEvent =
  (kind: LoginInputKind) =>
  (event: { target?: { value?: unknown } } | string | number | null | undefined) => {
    if (typeof event === 'string' || typeof event === 'number') {
      return sanitizeLoginInputValue(event, kind);
    }

    return sanitizeLoginInputValue(event?.target?.value, kind);
  };

export const shouldBlockLoginInputKey = (kind: LoginInputKind, event: KeyEventLike) => {
  if (event.isComposing || event.keyCode === 229) {
    return kind === 'mobile' || kind === 'verificationCode';
  }

  if (event.ctrlKey || event.altKey || event.metaKey) {
    return false;
  }

  if (!event.key || event.key.length !== 1) {
    return false;
  }

  return sanitizeLoginInputValue(event.key, kind) !== event.key;
};

export const shouldBlockLoginInputPaste = (kind: LoginInputKind, event: PasteEventLike) => {
  const text = event.clipboardData?.getData('text') ?? '';
  return sanitizeLoginInputValue(text, kind) !== text;
};

export const rejectUnsafeLoginInput = async (_: unknown, value: unknown, kind: LoginInputKind, message: string) => {
  if (coerceInputValue(value) !== sanitizeLoginInputValue(value, kind)) {
    throw new Error(message);
  }
};

type ForcedPasswordChangeFormValues = {
  newPassword: string;
  confirmPassword: string;
};

const INITIAL_PASSWORD = '123456';
type CodeLoginMode = 'sms' | 'email';

const POST_LOGIN_MENU_TIMEOUT_MS = 2500;
const POST_LOGIN_OPTIONAL_TIMEOUT_MS = 1200;
const WECHAT_CONTACT_BIND_REQUIRED_KEY = 'lumira_wechat_contact_bind_required';

const markWechatContactBindRequired = () => {
  window.sessionStorage.setItem(WECHAT_CONTACT_BIND_REQUIRED_KEY, '1');
};

const clearWechatContactBindRequired = () => {
  window.sessionStorage.removeItem(WECHAT_CONTACT_BIND_REQUIRED_KEY);
};

type PluginBootstrapResponse = {
  menuTree?: MenuNode[];
  availablePlugins?: PluginAvailability[];
};

const TEXT_ENCODER = new TextEncoder();
const KEY_CACHE = new Map<string, Promise<CryptoKey>>();

const normalizeRuntimeAppearanceSettingsResponse = (settings?: RuntimeAppearanceSettings) => ({
  brandingSettings: normalizeBrandingSettings(settings?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
  watermarkSettings: normalizeWatermarkSettings(settings?.watermarkSettings || DEFAULT_WATERMARK_SETTINGS),
  floatingWindowSettings: normalizeFloatingWindowSettings(settings?.floatingWindowSettings || DEFAULT_FLOATING_WINDOW_SETTINGS),
});

const loadRuntimeAppearanceSettings = async (): Promise<RuntimeAppearanceSettings> => {
  const response = await request<RuntimeAppearanceSettings>('/v2/platform/runtime-appearance-settings', {
    method: 'GET',
    autoRedirectOnUnauthorized: false,
    allowUnauthorizedWithoutRedirect: true,
    silent: true,
    timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
  }).catch(() =>
    request<RuntimeAppearanceSettings>('/v1/system/runtime-appearance-settings', {
      method: 'GET',
      autoRedirectOnUnauthorized: false,
      allowUnauthorizedWithoutRedirect: true,
      silent: true,
      timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
    }),
  );

  return response;
};

const loadPluginBootstrap = async (timeoutMs = POST_LOGIN_MENU_TIMEOUT_MS): Promise<PluginBootstrapResponse> => {
  const pluginRequestOptions: RequestOptions = {
    method: 'GET',
    autoRedirectOnUnauthorized: false,
    allowUnauthorizedWithoutRedirect: true,
    silent: true,
    timeoutMs,
  };

  try {
    const bootstrap = await request<PluginBootstrapResponse>('/v2/plugins/current/bootstrap', pluginRequestOptions);
    return bootstrap;
  } catch {
    // Fall through to legacy bootstrap and menu split endpoints.
  }

  try {
    const bootstrap = await request<PluginBootstrapResponse>('/v1/plugins/current/bootstrap', {
      ...pluginRequestOptions,
    });
    return bootstrap;
  } catch {
    // Fall through to legacy split endpoints during migration.
  }

  try {
    const [menuTree, availablePlugins] = await Promise.all([
      request<MenuNode[]>('/v1/plugins/current/menus', {
        ...pluginRequestOptions,
      }),
      request<PluginAvailability[]>('/v1/plugins/current/available', {
        ...pluginRequestOptions,
      }),
    ]);
    return { menuTree, availablePlugins };
  } catch {
    return {};
  }
};

const base64ToArrayBuffer = (base64: string) => {
  const binary = window.atob(base64.replace(/\s+/g, ''));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
};

const arrayBufferToBase64 = (buffer: ArrayBuffer) => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let index = 0; index < bytes.length; index += 1) {
    binary += String.fromCharCode(bytes[index]);
  }
  return window.btoa(binary);
};

const importLoginPublicKey = async (key: LoginEncryptionKey) => {
  if (!window.crypto?.subtle) {
    throw new Error('This browser does not support login encryption. Please upgrade your browser and try again.');
  }

  const cacheKey = key.keyId || key.publicKey;
  const cached = KEY_CACHE.get(cacheKey);
  if (cached) {
    return cached;
  }

  const promise = window.crypto.subtle.importKey(
    'spki',
    base64ToArrayBuffer(key.publicKey),
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256',
    },
    false,
    ['encrypt'],
  );
  KEY_CACHE.set(cacheKey, promise);
  return promise;
};

const encryptLoginPassword = async (password: string, key: LoginEncryptionKey) => {
  const publicKey = await importLoginPublicKey(key);
  const encrypted = await window.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, TEXT_ENCODER.encode(password));
  return arrayBufferToBase64(encrypted);
};

const canEncryptLoginPassword = () => Boolean(window.crypto?.subtle);

const buildPasswordLoginPayload = async (password: string, key: LoginEncryptionKey | null) => {
  if (key && canEncryptLoginPassword()) {
    return {
      password: await encryptLoginPassword(password, key),
      headers: undefined,
    };
  }
  return {
    password,
    headers: {
      'X-Login-Password-Plaintext': 'true',
    },
  };
};

const isRecoverableLoginEncryptionError = (error: unknown) => {
  if (!(error instanceof ApiRequestError)) {
    return false;
  }
  return isLoginPasswordPayloadError(error);
};

export type LoginFlowState = {
  submitting: boolean;
  setSubmitting: Dispatch<SetStateAction<boolean>>;
  passkeySubmitting: boolean;
  setPasskeySubmitting: Dispatch<SetStateAction<boolean>>;
  pendingSecondFactorLogin: import('@/types/api').LoginResponse | null;
  setPendingSecondFactorLogin: Dispatch<SetStateAction<import('@/types/api').LoginResponse | null>>;
  pendingPasswordChangeLogin: import('@/types/api').LoginResponse | null;
  setPendingPasswordChangeLogin: Dispatch<SetStateAction<import('@/types/api').LoginResponse | null>>;
  restoredPasswordChangeRequired: boolean;
  setRestoredPasswordChangeRequired: Dispatch<SetStateAction<boolean>>;
  pendingPasswordChangeCurrentPassword: string;
  setPendingPasswordChangeCurrentPassword: Dispatch<SetStateAction<string>>;
  passwordChangeSubmitting: boolean;
  setPasswordChangeSubmitting: Dispatch<SetStateAction<boolean>>;
  loginForm: FormInstance<import('@/pages/user/login/components/LoginFormFields').LoginFormValues>;
  forcedPasswordChangeForm: FormInstance<ForcedPasswordChangeFormValues>;
  resetSecondFactorFlow: () => void;
};

type UseLoginFlowInteractionsParams = {
  flowState: LoginFlowState;
  bootstrapFlow: LoginBootstrapFlow;
  initialState: AppInitialState | undefined;
  locationSearch: string;
  setInitialState: (updater: (prev: AppInitialState | undefined) => AppInitialState | undefined) => void;
  suppressLoginBroadcastRedirectRef: MutableRefObject<boolean>;
};

const useLoginFlowInteractions = ({
  flowState,
  bootstrapFlow,
  initialState,
  locationSearch,
  setInitialState,
  suppressLoginBroadcastRedirectRef,
}: UseLoginFlowInteractionsParams) => {
  const pendingSecondFactorOption = flowState.pendingSecondFactorLogin?.secondFactorOptions?.[0] || null;
  const currentUserRequiresPasswordChange = Boolean(isLoggedIn() && initialState?.currentUser?.requiresPasswordChange);
  const restoredPasswordChangeOpen = flowState.restoredPasswordChangeRequired && currentUserRequiresPasswordChange;
  const forcedPasswordChangeOpen =
    Boolean(flowState.pendingPasswordChangeLogin) ||
    restoredPasswordChangeOpen;
  const completeSuccessfulLogin = useCallback(
    async (loginResponse: LoginResponse, remember?: boolean) => {
      suppressLoginBroadcastRedirectRef.current = true;
      let sessionResult: Awaited<ReturnType<typeof initializeAfterLogin>>;
      try {
        sessionResult = await initializeAfterLogin(loginResponse, { remember });
      } catch (error) {
        suppressLoginBroadcastRedirectRef.current = false;
        throw error;
      }
      const pluginBootstrapPromise = sessionResult.menuTree !== undefined && sessionResult.availablePlugins !== undefined
        ? null
        : loadPluginBootstrap().catch(() => ({
            menuTree: initialState?.menuTree || [],
            availablePlugins: initialState?.availablePlugins || [],
          }));
      const bootstrapRuntimeAppearanceSettings = sessionResult.runtimeAppearanceSettings
        ? normalizeRuntimeAppearanceSettingsResponse(sessionResult.runtimeAppearanceSettings)
        : null;
      const baseAppearanceResources = {
        brandingSettings: bootstrapRuntimeAppearanceSettings?.brandingSettings
          || normalizeBrandingSettings(initialState?.brandingSettings || DEFAULT_BRANDING_SETTINGS),
        watermarkSettings: bootstrapRuntimeAppearanceSettings?.watermarkSettings
          || initialState?.watermarkSettings
          || DEFAULT_WATERMARK_SETTINGS,
        floatingWindowSettings: bootstrapRuntimeAppearanceSettings?.floatingWindowSettings
          || initialState?.floatingWindowSettings
          || DEFAULT_FLOATING_WINDOW_SETTINGS,
      };
      const runtimeAppearancePromise = bootstrapRuntimeAppearanceSettings
        ? null
        : loadRuntimeAppearanceSettings()
            .then((appearanceSettings) => ({
              brandingSettings: normalizeBrandingSettings(appearanceSettings.brandingSettings || baseAppearanceResources.brandingSettings),
              watermarkSettings: normalizeWatermarkSettings(appearanceSettings.watermarkSettings || baseAppearanceResources.watermarkSettings),
              floatingWindowSettings: normalizeFloatingWindowSettings(
                appearanceSettings.floatingWindowSettings || baseAppearanceResources.floatingWindowSettings,
              ),
            }))
            .catch(async () => {
              const [brandingResult, watermarkResult, floatingWindowResult] = await Promise.allSettled([
                request<BrandingSettings>('/v2/platform/branding-settings', {
                  method: 'GET',
                  autoRedirectOnUnauthorized: false,
                  allowUnauthorizedWithoutRedirect: true,
                  silent: true,
                  timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
                }).catch(() =>
                  request<BrandingSettings>('/v1/system/branding-settings', {
                    method: 'GET',
                    autoRedirectOnUnauthorized: false,
                    allowUnauthorizedWithoutRedirect: true,
                    silent: true,
                    timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
                  }),
                ),
                request<WatermarkSettings>('/v1/system/watermark-settings', {
                  method: 'GET',
                  autoRedirectOnUnauthorized: false,
                  allowUnauthorizedWithoutRedirect: true,
                  silent: true,
                  timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
                }),
                request<FloatingWindowSettings>('/v1/system/floating-window-settings', {
                  method: 'GET',
                  autoRedirectOnUnauthorized: false,
                  allowUnauthorizedWithoutRedirect: true,
                  silent: true,
                  timeoutMs: POST_LOGIN_OPTIONAL_TIMEOUT_MS,
                }),
              ]);
              return {
                brandingSettings: normalizeBrandingSettings(
                  brandingResult.status === 'fulfilled' ? brandingResult.value : baseAppearanceResources.brandingSettings,
                ),
                watermarkSettings: normalizeWatermarkSettings(
                  watermarkResult.status === 'fulfilled' ? watermarkResult.value : baseAppearanceResources.watermarkSettings,
                ),
                floatingWindowSettings: normalizeFloatingWindowSettings(
                  floatingWindowResult.status === 'fulfilled'
                    ? floatingWindowResult.value
                    : baseAppearanceResources.floatingWindowSettings,
                ),
              };
            });
      const resources = {
        menuTree: normalizeAuthenticatedMenuTree(
          sessionResult.menuTree || initialState?.menuTree || [],
          sessionResult.currentUser,
        ),
        availablePlugins: sessionResult.availablePlugins || initialState?.availablePlugins || [],
        ...baseAppearanceResources,
      };

      persistBrandingSettings(resources.brandingSettings);
      persistWatermarkSettings(resources.watermarkSettings);
      flushSync(() => {
        setInitialState((prev: AppInitialState | undefined) => ({
          ...prev,
          currentUser: sessionResult.currentUser,
          menuTree: resources.menuTree,
          menuVersion: (prev?.menuVersion ?? 0) + 1,
          availablePlugins: resources.availablePlugins,
          securitySettings: sessionResult.securitySettings,
          brandingSettings: resources.brandingSettings,
          watermarkSettings: resources.watermarkSettings,
          floatingWindowSettings: resources.floatingWindowSettings,
          agreementSettings: prev?.agreementSettings || bootstrapFlow.agreementSettings,
          loginCapabilities: prev?.loginCapabilities || bootstrapFlow.loginCapabilities,
        }));
      });
      const redirectTarget = resolveAuthorizedLoginRedirectTarget(locationSearch, sessionResult.currentUser, resources.menuTree);
      message.success(formatMessage({ id: 'page.login.success.loggedIn', defaultMessage: '登录成功，正在进入系统' }));
      history.replace(redirectTarget);
      window.setTimeout(() => {
        suppressLoginBroadcastRedirectRef.current = false;
      }, 0);

      if (pluginBootstrapPromise) {
        void pluginBootstrapPromise.then((pluginBootstrap) => {
          const nextMenuTree = normalizeAuthenticatedMenuTree(
            pluginBootstrap.menuTree || resources.menuTree,
            sessionResult.currentUser,
          );
          const nextAvailablePlugins = pluginBootstrap.availablePlugins || resources.availablePlugins;
          setInitialState((prev: AppInitialState | undefined) =>
            prev
              ? {
                  ...prev,
                  menuTree: nextMenuTree,
                  menuVersion: (prev.menuVersion ?? 0) + 1,
                  availablePlugins: nextAvailablePlugins,
                }
              : prev,
          );

          if (redirectTarget === '/403') {
            const recoveredRedirectTarget = resolveAuthorizedLoginRedirectTarget(locationSearch, sessionResult.currentUser, nextMenuTree);
            if (recoveredRedirectTarget !== redirectTarget) {
              history.replace(recoveredRedirectTarget);
            }
          }
        });
      }

      if (runtimeAppearancePromise) {
        void runtimeAppearancePromise.then(({ brandingSettings: nextBrandingSettings, watermarkSettings: nextWatermarkSettings, floatingWindowSettings }) => {
          persistBrandingSettings(nextBrandingSettings);
          persistWatermarkSettings(nextWatermarkSettings);
          setInitialState((prev: AppInitialState | undefined) =>
            prev
              ? {
                  ...prev,
                  brandingSettings: nextBrandingSettings,
                  watermarkSettings: nextWatermarkSettings,
                  floatingWindowSettings,
                }
              : prev,
          );
        });
      }
    },
    [
      bootstrapFlow.agreementSettings,
      bootstrapFlow.loginCapabilities,
      initialState?.availablePlugins,
      initialState?.brandingSettings,
      initialState?.floatingWindowSettings,
      initialState?.menuTree,
      initialState?.watermarkSettings,
      locationSearch,
      setInitialState,
      suppressLoginBroadcastRedirectRef,
    ],
  );
  const startForcedPasswordChange = useCallback(
    async (loginResponse: LoginResponse, currentPassword: string) => {
      suppressLoginBroadcastRedirectRef.current = true;
      flowState.setPendingPasswordChangeLogin(loginResponse);
      flowState.setPendingPasswordChangeCurrentPassword(currentPassword || '');
      flowState.forcedPasswordChangeForm.resetFields();
      await initializeAfterLogin(loginResponse, { remember: Boolean(flowState.loginForm.getFieldValue('remember')) });
      message.warning(formatMessage({ id: 'page.login.initialPasswordChange.required', defaultMessage: '当前账号仍在使用初始密码，请先修改密码' }));
    },
    [flowState, suppressLoginBroadcastRedirectRef],
  );
  const loginAfterPasswordChange = useCallback(
    async (account: string, newPassword: string) => {
      const encryptionKey: LoginEncryptionKey | null = bootstrapFlow.loginEncryptionKey || (await bootstrapFlow.loadLoginEncryptionKey());
      if (!encryptionKey && canEncryptLoginPassword()) {
        return null;
      }

      const submitLogin = async (key: LoginEncryptionKey | null) => {
        const loginPassword = await buildPasswordLoginPayload(newPassword, key);
        const loginPayload = {
          account,
          username: account,
          password: loginPassword.password,
        };

        return request<LoginResponse>('/v2/auth/login', {
          method: 'POST',
          headers: loginPassword.headers,
          data: loginPayload,
          skipAuth: true,
          silent: true,
          allowDuplicate: true,
        }).catch(() =>
          request<LoginResponse>('/v1/auth/login', {
            method: 'POST',
            headers: loginPassword.headers,
            data: loginPayload,
            skipAuth: true,
            silent: true,
            allowDuplicate: true,
          }),
        );
      };

      try {
        return await submitLogin(encryptionKey);
      } catch (error) {
        if (!encryptionKey || !canEncryptLoginPassword() || !isRecoverableLoginEncryptionError(error)) {
          throw error;
        }

        const refreshedKey = await bootstrapFlow.loadLoginEncryptionKey(true);
        return submitLogin(refreshedKey);
      }
    },
    [bootstrapFlow],
  );
  const handleForcedPasswordChange = useCallback(
    async (values: ForcedPasswordChangeFormValues) => {
      if (!flowState.pendingPasswordChangeLogin && !restoredPasswordChangeOpen) {
        return;
      }
      flowState.setPasswordChangeSubmitting(true);
      const pendingLoginResponse = flowState.pendingPasswordChangeLogin;
      try {
        await request<boolean>('/v1/profile/password', {
          method: 'PUT',
          data: {
            currentPassword: flowState.pendingPasswordChangeCurrentPassword,
            newPassword: values.newPassword,
            confirmPassword: values.confirmPassword,
          },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(formatMessage({ id: 'page.login.initialPasswordChange.success', defaultMessage: '密码已修改，请使用新密码登录' }));
        const account =
          pendingLoginResponse?.user?.username ||
          initialState?.currentUser?.username ||
          flowState.loginForm.getFieldValue('passwordAccount') ||
          '';
        if (account) {
          const reloginResponse = await loginAfterPasswordChange(account, values.newPassword).catch(() => null);
          if (reloginResponse?.accessToken) {
            flowState.setPendingPasswordChangeLogin(null);
            flowState.setRestoredPasswordChangeRequired(false);
            flowState.setPendingPasswordChangeCurrentPassword('');
            flowState.forcedPasswordChangeForm.resetFields();
            suppressLoginBroadcastRedirectRef.current = false;
            await completeSuccessfulLogin(
              {
                ...reloginResponse,
                requiresPasswordChange: false,
              },
              Boolean(flowState.loginForm.getFieldValue('remember')),
            );
            return;
          }
        }

        clearAuthSession();
        flowState.setPendingPasswordChangeLogin(null);
        flowState.setRestoredPasswordChangeRequired(false);
        flowState.setPendingPasswordChangeCurrentPassword('');
        flowState.forcedPasswordChangeForm.resetFields();
        suppressLoginBroadcastRedirectRef.current = false;
        message.info(formatMessage({ id: 'page.login.initialPasswordChange.success', defaultMessage: '密码已修改，请使用新密码登录' }));
      } catch (error) {
        message.error(
          error instanceof Error && error.message
            ? error.message
            : formatMessage({ id: 'page.login.initialPasswordChange.failed', defaultMessage: '密码修改失败，请检查后重试' }),
        );
        return;
      } finally {
        flowState.setPasswordChangeSubmitting(false);
      }
    },
    [
      completeSuccessfulLogin,
      flowState,
      initialState?.currentUser?.username,
      loginAfterPasswordChange,
      restoredPasswordChangeOpen,
      suppressLoginBroadcastRedirectRef,
    ],
  );

  const authInteractions = useLoginFlowAuthInteractions({
    flowState,
    bootstrapFlow,
    initialState,
    pendingSecondFactorOption,
    completeSuccessfulLogin,
    startForcedPasswordChange,
  });

  return {
    postLoginPack: {
      completeSuccessfulLogin,
      pendingSecondFactorOption,
      forcedPasswordChangeOpen,
      startForcedPasswordChange,
      handleForcedPasswordChange,
    },
    authPack: authInteractions,
  };
};

const preloadImage = (imageUrl: string) =>
  new Promise<void>((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('验证码图片加载失败'));
    image.src = imageUrl;
  });

const loadCaptchaChallenge = async (captchaType: CaptchaType, options: RequestOptions = {}): Promise<CaptchaChallenge> => {
  const challenge = await request<CaptchaChallenge>('/v1/public/captcha/challenge', {
    method: 'GET',
    skipAuth: true,
    silent: true,
    params: { captchaType },
    ...options,
  });
  if (challenge?.imageUrl) {
    try {
      await preloadImage(challenge.imageUrl);
    } catch {
      // Keep the challenge usable even if the image prefetch fails.
    }
  }
  return challenge;
};

interface CaptchaRefreshControllerDeps {
  getCaptchaEnabled: () => boolean;
  getCaptchaType: () => CaptchaType;
  loadChallenge: (captchaType: CaptchaType) => Promise<CaptchaChallenge>;
  setCaptchaChallenge: (challenge: CaptchaChallenge | null) => void;
  setCaptchaLoading: (loading: boolean) => void;
  setCaptchaImageLoadFailed: (failed: boolean) => void;
  onRefreshFailure: () => void;
}

interface CaptchaRefreshController {
  refresh: () => Promise<CaptchaChallenge | null>;
  invalidate: () => void;
}

const createCaptchaRefreshController = (deps: CaptchaRefreshControllerDeps): CaptchaRefreshController => {
  let requestSeq = 0;

  const refresh = async (): Promise<CaptchaChallenge | null> => {
    if (!deps.getCaptchaEnabled()) {
      deps.setCaptchaChallenge(null);
      deps.setCaptchaImageLoadFailed(false);
      deps.setCaptchaLoading(false);
      return null;
    }

    const seq = ++requestSeq;
    deps.setCaptchaLoading(true);
    deps.setCaptchaImageLoadFailed(false);

    try {
      const challenge = await deps.loadChallenge(deps.getCaptchaType());
      if (seq !== requestSeq) {
        return null;
      }
      deps.setCaptchaImageLoadFailed(false);
      deps.setCaptchaChallenge(challenge);
      return challenge;
    } catch {
      if (seq === requestSeq) {
        deps.onRefreshFailure();
      }
      return null;
    } finally {
      if (seq === requestSeq) {
        deps.setCaptchaLoading(false);
      }
    }
  };

  const invalidate = () => {
    requestSeq += 1;
  };

  return { refresh, invalidate };
};

const useLoginSecurityFlow = ({ initialSecuritySettings }: { initialSecuritySettings?: SecuritySettings }) => {
  const [securitySettings, setSecuritySettings] = useState(normalizeSecuritySettings(initialSecuritySettings || DEFAULT_SECURITY_SETTINGS));
  const [captchaChallenge, setCaptchaChallenge] = useState<CaptchaChallenge | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaImageLoadFailed, setCaptchaImageLoadFailed] = useState(false);
  const securitySettingsRef = useRef(securitySettings);
  const loadedCaptchaTypeRef = useRef<CaptchaType | null>(null);
  const captchaRefreshControllerRef = useRef(
    createCaptchaRefreshController({
      getCaptchaEnabled: () => securitySettingsRef.current.captchaEnabled,
      getCaptchaType: () => securitySettingsRef.current.captchaType,
      loadChallenge: (captchaType) =>
        loadCaptchaChallenge(captchaType, {
          autoRedirectOnUnauthorized: false,
          silent: true,
          skipAuth: true,
        }),
      setCaptchaChallenge,
      setCaptchaLoading,
      setCaptchaImageLoadFailed,
      onRefreshFailure: () =>
        message.warning(formatMessage({ id: 'page.login.error.refreshCaptcha', defaultMessage: 'Captcha refresh failed, please try again later' })),
    }),
  );

  useEffect(() => {
    const normalizedSecuritySettings = normalizeSecuritySettings(initialSecuritySettings || DEFAULT_SECURITY_SETTINGS);
    persistSecuritySettings(normalizedSecuritySettings);
    setSecuritySettings(normalizedSecuritySettings);
  }, [initialSecuritySettings]);

  useEffect(() => {
    securitySettingsRef.current = securitySettings;
  }, [securitySettings]);

  const refreshCaptcha = useCallback(async () => {
    const challenge = await captchaRefreshControllerRef.current.refresh();
    if (challenge?.captchaType) {
      loadedCaptchaTypeRef.current = challenge.captchaType;
    }
    return challenge;
  }, []);

  useEffect(() => {
    if (!securitySettings.captchaEnabled) {
      loadedCaptchaTypeRef.current = null;
      setCaptchaChallenge(null);
      setCaptchaImageLoadFailed(false);
      setCaptchaLoading(false);
      captchaRefreshControllerRef.current.invalidate();
      return;
    }

    if (securitySettings.captchaType === 'SLIDER') {
      loadedCaptchaTypeRef.current = null;
      captchaRefreshControllerRef.current.invalidate();
      setCaptchaImageLoadFailed(false);
      setCaptchaLoading(false);
      return;
    }

    if (loadedCaptchaTypeRef.current !== securitySettings.captchaType) {
      void refreshCaptcha();
    }
  }, [refreshCaptcha, securitySettings.captchaEnabled, securitySettings.captchaType]);

  return {
    securitySettings,
    captchaChallenge,
    captchaLoading,
    captchaImageLoadFailed,
    refreshCaptcha,
    setCaptchaChallenge,
    setCaptchaImageLoadFailed,
  };
};

type UseLoginFlowAuthInteractionsParams = {
  flowState: LoginFlowState;
  bootstrapFlow: LoginBootstrapFlow;
  initialState: AppInitialState | undefined;
  pendingSecondFactorOption: {
    factorCode?: string;
    challengeId?: string;
    promptMessage?: string | null;
    factorName?: string;
  } | null;
  completeSuccessfulLogin: (loginResponse: LoginResponse, remember?: boolean) => Promise<void>;
  startForcedPasswordChange: (loginResponse: LoginResponse, currentPassword: string) => Promise<void>;
};

export const useLoginFlowAuthInteractions = ({
  flowState,
  bootstrapFlow,
  initialState,
  pendingSecondFactorOption,
  completeSuccessfulLogin,
  startForcedPasswordChange,
}: UseLoginFlowAuthInteractionsParams) => {
  const { securitySettings, captchaChallenge, captchaLoading, captchaImageLoadFailed, refreshCaptcha, setCaptchaChallenge, setCaptchaImageLoadFailed } = useLoginSecurityFlow({
    initialSecuritySettings: initialState?.securitySettings,
  });
  useEffect(() => {
    if (securitySettings.captchaEnabled) {
      flowState.loginForm.setFieldValue('captchaCode', undefined);
      flowState.loginForm.setFieldValue('captchaProof', undefined);
    }
  }, [captchaChallenge?.captchaId, flowState.loginForm, securitySettings.captchaEnabled]);

  const setCaptchaProof = useCallback(
    (captchaProof?: string) => {
      flowState.loginForm.setFieldValue('captchaProof', captchaProof);
    },
    [flowState.loginForm],
  );

  const resetCaptchaProof = useCallback(() => {
    flowState.loginForm.setFieldValue('captchaProof', undefined);
  }, [flowState.loginForm]);

  const [sendingLoginType, setSendingLoginType] = useState<CodeLoginMode | null>(null);
  const [loginCodeChallenges, setLoginCodeChallenges] = useState<Partial<Record<CodeLoginMode, LoginCodeChallenge | null>>>({});
  const [loginCodeCooldownEndsAt, setLoginCodeCooldownEndsAt] = useState<Partial<Record<CodeLoginMode, number>>>({});
  const [loginCodeClock, setLoginCodeClock] = useState(() => Date.now());
  useEffect(() => {
    const hasActiveCooldown =
      (loginCodeCooldownEndsAt.sms || 0) > loginCodeClock || (loginCodeCooldownEndsAt.email || 0) > loginCodeClock;
    if (!hasActiveCooldown) {
      return;
    }
    const timer = window.setInterval(() => setLoginCodeClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [loginCodeClock, loginCodeCooldownEndsAt.email, loginCodeCooldownEndsAt.sms]);
  const loginCodeCooldownSeconds = useMemo(
    () => ({
      sms: Math.max(0, Math.ceil(((loginCodeCooldownEndsAt.sms || 0) - loginCodeClock) / 1000)),
      email: Math.max(0, Math.ceil(((loginCodeCooldownEndsAt.email || 0) - loginCodeClock) / 1000)),
    }),
    [loginCodeClock, loginCodeCooldownEndsAt.email, loginCodeCooldownEndsAt.sms],
  );

  const handleSendLoginCode = useCallback(
    async (mode: CodeLoginMode) => {
      if (!bootstrapFlow.availableLoginModes.includes(mode)) {
        message.warning(
          mode === 'sms'
            ? formatMessage({ id: 'page.login.error.smsDisabled', defaultMessage: 'SMS login is not enabled' })
            : formatMessage({ id: 'page.login.error.emailDisabled', defaultMessage: 'Email login is not enabled' }),
        );
        return;
      }

      const remainingCooldownSeconds = loginCodeCooldownSeconds[mode] || 0;
      if (remainingCooldownSeconds > 0) {
        message.warning(
          formatMessage(
            { id: 'page.login.code.cooldown', defaultMessage: 'Please wait {seconds}s before sending again' },
            { seconds: remainingCooldownSeconds },
          ),
        );
        return;
      }

      const accountField = mode === 'sms' ? 'smsAccount' : 'emailAccount';
      try {
        await flowState.loginForm.validateFields([accountField]);
      } catch {
        return;
      }

      const account = sanitizeLoginInputValue(flowState.loginForm.getFieldValue(accountField), mode === 'sms' ? 'mobile' : 'email');
      flowState.loginForm.setFieldsValue({ [accountField]: account } as Partial<LoginFormValues>);

      setSendingLoginType(mode);
      try {
        const challenge = await request<LoginCodeChallenge>('/v1/auth/login/code/challenge', {
          method: 'POST',
          data: {
            loginType: mode,
            account,
          },
          autoRedirectOnUnauthorized: false,
          silent: true,
          skipAuth: true,
        });
        setLoginCodeChallenges((prev) => ({
          ...prev,
          [mode]: challenge,
        }));
        const cooldownSeconds = Math.max(1, Math.floor(challenge.cooldownSeconds || securitySettings.verificationCodeCooldownSeconds));
        setLoginCodeCooldownEndsAt((prev) => ({
          ...prev,
          [mode]: Date.now() + cooldownSeconds * 1000,
        }));
        setLoginCodeClock(Date.now());
        flowState.loginForm.setFieldsValue({
          [mode === 'sms' ? 'smsVerificationCode' : 'emailVerificationCode']: undefined,
        } as Partial<LoginFormValues>);
        message.success(formatMessage({ id: 'page.login.success.codeSent', defaultMessage: 'Verification code sent' }));
      } catch (error) {
        message.error(error instanceof Error && error.message ? error.message : formatMessage({ id: 'page.login.error.codeSendFailed', defaultMessage: 'Failed to send the verification code, please try again later' }));
      } finally {
        setSendingLoginType(null);
      }
    },
    [
      bootstrapFlow.availableLoginModes,
      flowState.loginForm,
      loginCodeCooldownSeconds,
      securitySettings.verificationCodeCooldownSeconds,
    ],
  );

  const handlePasswordLogin = useCallback(
    async (values: LoginFormValues) => {
      const encryptionKey: LoginEncryptionKey | null = bootstrapFlow.loginEncryptionKey || (await bootstrapFlow.loadLoginEncryptionKey());
      if (!encryptionKey && canEncryptLoginPassword()) {
        return null;
      }

      const submitLogin = async (key: LoginEncryptionKey | null) => {
        const loginPassword = await buildPasswordLoginPayload(values.passwordPassword || '', key);

        return request<LoginResponse>('/v2/auth/login', {
          method: 'POST',
          headers: loginPassword.headers,
          data: {
            account: values.passwordAccount,
            username: values.passwordAccount,
            password: loginPassword.password,
            captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
            captchaCode: securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE' ? values.captchaCode : undefined,
            captchaProof: securitySettings.captchaEnabled && securitySettings.captchaType === 'SLIDER' ? values.captchaProof : undefined,
          },
          skipAuth: true,
          silent: true,
          allowDuplicate: true,
        })
          .catch((error) => {
            if (!shouldFallbackToLegacyPasswordLogin(error)) {
              throw error;
            }

            return request<LoginResponse>('/v1/auth/login', {
              method: 'POST',
              headers: loginPassword.headers,
              data: {
                account: values.passwordAccount,
                username: values.passwordAccount,
                password: loginPassword.password,
                captchaId: securitySettings.captchaEnabled ? captchaChallenge?.captchaId : undefined,
                captchaCode: securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE' ? values.captchaCode : undefined,
                captchaProof: securitySettings.captchaEnabled && securitySettings.captchaType === 'SLIDER' ? values.captchaProof : undefined,
              },
              skipAuth: true,
              silent: true,
              allowDuplicate: true,
            });
          });
      };

      try {
        return await submitLogin(encryptionKey);
      } catch (error) {
        if (!encryptionKey || !canEncryptLoginPassword() || !isRecoverableLoginEncryptionError(error)) {
          throw error;
        }

        const refreshedKey = await bootstrapFlow.loadLoginEncryptionKey(true);
        return submitLogin(refreshedKey);
      }
    },
    [bootstrapFlow, captchaChallenge?.captchaId, securitySettings.captchaEnabled, securitySettings.captchaType],
  );

  const handleCodeLogin = useCallback(
    async (mode: CodeLoginMode, values: LoginFormValues) => {
      const challenge = loginCodeChallenges[mode];
      if (!challenge?.challengeId) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseSendCode', defaultMessage: 'Please send the verification code first' }));
        return null;
      }

      const verificationCode = mode === 'sms' ? values.smsVerificationCode : values.emailVerificationCode;
      if (!verificationCode) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the verification code' }));
        return null;
      }

      return request<LoginResponse>('/v1/auth/login/code/complete', {
        method: 'POST',
        data: {
          challengeId: challenge.challengeId,
          verificationCode,
        },
        autoRedirectOnUnauthorized: false,
        silent: true,
        skipAuth: true,
      });
    },
    [loginCodeChallenges],
  );

  const handleWechatLogin = useCallback(async () => {
    flowState.setSubmitting(true);
    try {
      message.loading({
        content: formatMessage({ id: 'page.login.wechatStarting', defaultMessage: 'Redirecting to WeChat login...' }),
        key: 'wechat-login',
        duration: 1,
      });
      const result = await request<{ authorizeUrl: string }>('/v1/auth/wechat/authorize-url', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        silent: true,
        skipAuth: true,
      });
      window.location.assign(result.authorizeUrl);
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
    } finally {
      flowState.setSubmitting(false);
    }
  }, [flowState]);

  const handlePasskeyLogin = useCallback(async () => {
    if (!isPasskeySupported()) {
      message.warning(formatMessage({ id: 'page.login.passkey.unsupported', defaultMessage: '当前浏览器不支持通行密钥' }));
      return;
    }
    flowState.setPasskeySubmitting(true);
    try {
      const options = await request<PasskeyOptions>('/v1/auth/passkeys/authentication/options', {
        method: 'POST',
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      const credential = await navigator.credentials.get({
        publicKey: toPublicKeyRequestOptions(options),
      });
      if (!credential) {
        return;
      }
      const loginResponse = await request<LoginResponse>('/v1/auth/passkeys/authentication/complete', {
        method: 'POST',
        data: toAuthenticationPayload(options.challengeId, credential as PublicKeyCredential),
        skipAuth: true,
        silent: true,
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
      });
      clearWechatContactBindRequired();
      await completeSuccessfulLogin(loginResponse, Boolean(flowState.loginForm.getFieldValue('remember')));
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        message.info(formatMessage({ id: 'page.login.passkey.cancelled', defaultMessage: '已取消通行密钥验证' }));
        return;
      }
      message.error(error instanceof Error && error.message ? error.message : formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
    } finally {
      flowState.setPasskeySubmitting(false);
    }
  }, [completeSuccessfulLogin, flowState]);

  const authMethodAccess = {
    handleWechatLogin,
    handlePasskeyLogin,
    sendingLoginType,
    loginCodeChallenges,
    loginCodeCooldownSeconds,
    handleSendLoginCode,
    handlePasswordLogin,
    handleCodeLogin,
  };

  const buildLoginSecondFactorPrompt = useCallback(
    (nextPendingSecondFactorOption: UseLoginFlowAuthInteractionsParams['pendingSecondFactorOption']) =>
      nextPendingSecondFactorOption?.promptMessage ||
      (nextPendingSecondFactorOption?.factorName
        ? formatMessage(
            { id: 'page.login.secondFactor.prompt', defaultMessage: '{name} requires second-factor verification' },
            { name: nextPendingSecondFactorOption.factorName },
          )
        : formatMessage({ id: 'page.login.code.secondFactor', defaultMessage: 'Please enter the verification code to complete second-factor verification' })),
    [],
  );

  const validateLoginSubmit = useCallback(
    (values: LoginFormValues, submitLoginMode: LoginMode) => {
      if (submitLoginMode === 'passkey') {
        return true;
      }

      if (!bootstrapFlow.availableLoginModes.includes(submitLoginMode)) {
        message.warning(
          submitLoginMode === 'sms'
            ? formatMessage({ id: 'page.login.error.smsDisabled', defaultMessage: 'SMS login is not enabled' })
            : submitLoginMode === 'email'
              ? formatMessage({ id: 'page.login.error.emailDisabled', defaultMessage: 'Email login is not enabled' })
              : formatMessage({ id: 'page.login.error.loginModeUnavailable', defaultMessage: 'Current login mode is unavailable' }),
        );
        return false;
      }

      if (submitLoginMode === 'password' && securitySettings.captchaEnabled && !captchaChallenge?.captchaId) {
        message.warning(formatMessage({ id: 'page.login.error.captchaExpired', defaultMessage: 'The captcha has expired, please refresh and try again' }));
        return false;
      }

      if (submitLoginMode === 'password' && securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE' && !values.captchaCode) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseEnterCaptcha', defaultMessage: 'Please enter the captcha' }));
        return false;
      }

      if (submitLoginMode === 'password' && securitySettings.captchaEnabled && securitySettings.captchaType === 'SLIDER' && !values.captchaProof) {
        message.warning(formatMessage({ id: 'page.login.error.pleaseCompleteSliderCaptcha', defaultMessage: 'Please complete the slider captcha first' }));
        return false;
      }

      return true;
    },
    [bootstrapFlow.availableLoginModes, captchaChallenge?.captchaId, securitySettings.captchaEnabled, securitySettings.captchaType],
  );

  const handleLoginResponse = useCallback(
    async (loginResponse: LoginResponse, values: LoginFormValues) => {
      if (flowState.pendingSecondFactorLogin) {
        flowState.resetSecondFactorFlow();
      }

      if (loginResponse.requiresSecondFactor) {
        flowState.setPendingSecondFactorLogin(loginResponse);
        message.info(
          loginResponse.secondFactorOptions?.[0]?.promptMessage ||
            formatMessage({
              id: 'page.login.code.secondFactor',
              defaultMessage: 'Please enter the verification code to complete second-factor verification',
            }),
        );
        return false;
      }

      if (loginResponse.requiresPasswordChange) {
        await startForcedPasswordChange(loginResponse, values.passwordPassword || INITIAL_PASSWORD);
        return false;
      }

      if (!flowState.pendingSecondFactorLogin) {
        clearWechatContactBindRequired();
      }
      await completeSuccessfulLogin(loginResponse, Boolean(values.remember));
      return true;
    },
    [completeSuccessfulLogin, flowState, startForcedPasswordChange],
  );

  const handlePendingSecondFactorSubmit = useCallback(
    async (values: LoginFormValues) => {
      const factorCode = pendingSecondFactorOption?.factorCode || '';
      const challengeId = pendingSecondFactorOption?.challengeId || '';
      const verificationCode = values.verificationCode || '';
      if (!factorCode || !challengeId || !verificationCode) {
        message.warning(
          formatMessage({
            id: 'page.login.code.secondFactor',
            defaultMessage: 'Please enter the verification code to complete second-factor verification',
          }),
        );
        return null;
      }
      return request<LoginResponse>('/v1/auth/second-factor/complete', {
        method: 'POST',
        data: {
          factorCode,
          challengeId,
          verificationCode,
        },
        skipAuth: true,
        silent: true,
      });
    },
    [pendingSecondFactorOption?.challengeId, pendingSecondFactorOption?.factorCode],
  );

  const handleLoginSubmissionError = useCallback(
    (error: unknown) => {
      if (error instanceof ApiRequestError) {
        const feedback = resolveLoginErrorFeedback(error, (descriptor) => formatMessage(descriptor));
        message.open({
          type: feedback.type,
          content: feedback.message,
        });
        if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
          void refreshCaptcha();
        }
        return false;
      }

      if (error instanceof Error) {
        message.error(error.message || formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
        if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
          void refreshCaptcha();
        }
        return false;
      }

      message.error(formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }));
      if (securitySettings.captchaEnabled && securitySettings.captchaType === 'IMAGE') {
        void refreshCaptcha();
      }
      return false;
    },
    [refreshCaptcha, securitySettings.captchaEnabled, securitySettings.captchaType],
  );

  const pendingSecondFactorPrompt = buildLoginSecondFactorPrompt(pendingSecondFactorOption);
  const handleSubmit = useCallback(
    async (values: LoginFormValues): Promise<boolean> => {
      const submitLoginMode: LoginMode =
        values.passwordAccount || values.passwordPassword ? 'password' : bootstrapFlow.activeLoginMode;
      if (!flowState.pendingSecondFactorLogin) {
        if (submitLoginMode === 'passkey') {
          await handlePasskeyLogin();
          return false;
        }

        if (submitLoginMode === 'wechat') {
          await handleWechatLogin();
          return false;
        }

        if (!validateLoginSubmit(values, submitLoginMode)) {
          return false;
        }
      }

      flowState.setSubmitting(true);
      beginLoginFlow();
      try {
        const loginResponse = flowState.pendingSecondFactorLogin
          ? await handlePendingSecondFactorSubmit(values)
          : submitLoginMode === 'password'
            ? await handlePasswordLogin(values)
            : await handleCodeLogin(submitLoginMode as CodeLoginMode, values);

        if (!loginResponse) {
          return false;
        }

        return handleLoginResponse(loginResponse, values);
      } catch (error) {
        return handleLoginSubmissionError(error);
      } finally {
        endLoginFlow();
        flowState.setSubmitting(false);
      }
    },
    [
      handleCodeLogin,
      handlePasskeyLogin,
      handleWechatLogin,
      handlePasswordLogin,
      bootstrapFlow.activeLoginMode,
      flowState,
      handleLoginResponse,
      handleLoginSubmissionError,
      handlePendingSecondFactorSubmit,
      validateLoginSubmit,
    ],
  );
  const handleFinishFailed: NonNullable<FormProps<LoginFormValues>['onFinishFailed']> = useCallback(({ errorFields }) => {
    const hasSliderCaptchaError = errorFields.some((field) => field.name.includes('captchaProof'));
    if (!hasSliderCaptchaError) {
      return;
    }
    message.warning(
      formatMessage({
        id: 'page.login.error.pleaseCompleteSliderCaptcha',
        defaultMessage: 'Please complete the slider captcha first',
      }),
    );
  }, []);

  return {
    securitySettings,
    captchaChallenge,
    captchaLoading,
    captchaImageLoadFailed,
    refreshCaptcha,
    setCaptchaChallenge,
    setCaptchaImageLoadFailed,
    setCaptchaProof,
    resetCaptchaProof,
    ...authMethodAccess,
    pendingSecondFactorPrompt,
    handleSubmit,
    handleFinishFailed,
  };
};

type UseLoginFlowRuntimeParams = {
  flowState: LoginFlowState;
  bootstrapFlow: LoginBootstrapFlow;
  initialState: AppInitialState | undefined;
  locationSearch: string;
  locationPathname: string;
  setInitialState: (updater: (prev: AppInitialState | undefined) => AppInitialState | undefined) => void;
};

export const useLoginFlowRuntime = ({
  flowState,
  bootstrapFlow,
  initialState,
  locationSearch,
  locationPathname,
  setInitialState,
}: UseLoginFlowRuntimeParams) => {
  const wechatCallbackHandledRef = useRef(false);
  const passwordChangeRestoreHandledRef = useRef(false);
  const suppressLoginBroadcastRedirectRef = useRef(false);
  const currentUserRequiresPasswordChange = Boolean(isLoggedIn() && initialState?.currentUser?.requiresPasswordChange);
  const forcePasswordChangeRequested = useMemo(() => {
    const searchParams = new URLSearchParams(locationSearch || '');
    return searchParams.get('forcePasswordChange') === '1';
  }, [locationSearch]);
  const redirectTarget = resolveLoginPageRuntimeRedirectTarget({
    pathname: locationPathname,
    search: locationSearch,
    isAuthenticated: isLoggedIn(),
    forcePasswordChangeRequested,
  });

  const {
    postLoginPack,
    authPack,
  } = useLoginFlowInteractions({
    flowState,
    bootstrapFlow,
    initialState,
    locationSearch,
    setInitialState,
    suppressLoginBroadcastRedirectRef,
  });

  const {
    completeSuccessfulLogin,
    forcedPasswordChangeOpen,
    handleForcedPasswordChange,
  } = postLoginPack;
  const {
    handleWechatLogin,
    handlePasskeyLogin,
    securitySettings,
    captchaChallenge,
    captchaLoading,
    captchaImageLoadFailed,
    refreshCaptcha,
    setCaptchaChallenge,
    setCaptchaImageLoadFailed,
    setCaptchaProof,
    resetCaptchaProof,
    sendingLoginType,
    loginCodeChallenges,
    loginCodeCooldownSeconds,
    handleSendLoginCode,
    pendingSecondFactorPrompt,
    handleSubmit,
    handleFinishFailed,
  } = authPack;

  useEffect(() => {
    if (flowState.pendingPasswordChangeLogin || currentUserRequiresPasswordChange) {
      return;
    }
    if (!isLoggedIn() || flowState.submitting) {
      return;
    }

    if (initialState?.currentUser) {
      history.replace(resolveAuthorizedLoginRedirectTarget(locationSearch, initialState.currentUser, initialState.menuTree || []));
    }
  }, [
    flowState,
    flowState.pendingPasswordChangeLogin,
    flowState.submitting,
    currentUserRequiresPasswordChange,
    initialState?.currentUser,
    initialState?.menuTree,
    locationSearch,
  ]);
  useEffect(() => {
    if (!forcePasswordChangeRequested || !isLoggedIn()) {
      return;
    }

    let cancelled = false;
    void (async () => {
      const restoredSession = await restoreSession().catch(() => null);
      if (cancelled) {
        return;
      }
      if (!restoredSession?.currentUser) {
        clearAuthSession();
        flowState.setRestoredPasswordChangeRequired(false);
        flowState.setPendingPasswordChangeLogin(null);
        flowState.setPendingPasswordChangeCurrentPassword('');
        flowState.forcedPasswordChangeForm.resetFields();
        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                currentUser: undefined,
              }
            : prev,
        );
        history.replace(`${locationPathname}?redirect=${encodeURIComponent(redirectTarget)}`);
        return;
      }

      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: restoredSession.currentUser,
              securitySettings: restoredSession.securitySettings,
              menuTree:
                restoredSession.menuTree !== undefined
                  ? normalizeAuthenticatedMenuTree(restoredSession.menuTree, restoredSession.currentUser)
                  : prev.menuTree,
              availablePlugins: restoredSession.availablePlugins ?? prev.availablePlugins,
            }
          : prev,
      );

      const restoredMenuTree = restoredSession.menuTree !== undefined
        ? normalizeAuthenticatedMenuTree(restoredSession.menuTree, restoredSession.currentUser)
        : (initialState?.menuTree || []);

      if (!restoredSession.currentUser.requiresPasswordChange) {
        flowState.setRestoredPasswordChangeRequired(false);
        flowState.setPendingPasswordChangeLogin(null);
        flowState.setPendingPasswordChangeCurrentPassword('');
        flowState.forcedPasswordChangeForm.resetFields();
        history.replace(resolveAuthorizedLoginRedirectTarget(locationSearch, restoredSession.currentUser, restoredMenuTree));
        return;
      }

      flowState.setRestoredPasswordChangeRequired(true);
      flowState.setPendingPasswordChangeCurrentPassword(INITIAL_PASSWORD);
      flowState.forcedPasswordChangeForm.resetFields();
    })();
    return () => {
      cancelled = true;
    };
  }, [
    flowState,
    flowState.forcedPasswordChangeForm,
    forcePasswordChangeRequested,
    initialState?.menuTree,
    locationPathname,
    locationSearch,
    redirectTarget,
    setInitialState,
  ]);
  useEffect(() => {
    if (!isLoggedIn() || !initialState?.currentUser?.requiresPasswordChange) {
      passwordChangeRestoreHandledRef.current = false;
      return;
    }
    if (passwordChangeRestoreHandledRef.current) {
      return;
    }
    passwordChangeRestoreHandledRef.current = true;
    let cancelled = false;
    void (async () => {
      const restoredSession = await restoreSession().catch(() => null);
      if (cancelled) {
        return;
      }
      if (restoredSession?.currentUser && !restoredSession.currentUser.requiresPasswordChange) {
        const restoredMenuTree = restoredSession.menuTree !== undefined
          ? normalizeAuthenticatedMenuTree(restoredSession.menuTree, restoredSession.currentUser)
          : (initialState?.menuTree || []);
        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                currentUser: restoredSession.currentUser,
                securitySettings: restoredSession.securitySettings,
                menuTree:
                  restoredSession.menuTree !== undefined
                    ? restoredMenuTree
                    : prev.menuTree,
                availablePlugins: restoredSession.availablePlugins ?? prev.availablePlugins,
              }
            : prev,
        );
        history.replace(resolveAuthorizedLoginRedirectTarget(locationSearch, restoredSession.currentUser, restoredMenuTree));
        return;
      }
      if (!restoredSession?.currentUser) {
        clearAuthSession();
        flowState.setRestoredPasswordChangeRequired(false);
        flowState.setPendingPasswordChangeLogin(null);
        flowState.setPendingPasswordChangeCurrentPassword('');
        flowState.forcedPasswordChangeForm.resetFields();
        setInitialState((prev: AppInitialState | undefined) =>
          prev
            ? {
                ...prev,
                currentUser: undefined,
              }
            : prev,
        );
        return;
      }
      flowState.setRestoredPasswordChangeRequired(true);
      flowState.setPendingPasswordChangeCurrentPassword(INITIAL_PASSWORD);
      flowState.forcedPasswordChangeForm.resetFields();
      message.warning(formatMessage({ id: 'page.login.initialPasswordChange.required', defaultMessage: '当前账号仍在使用初始密码，请先修改密码' }));
    })();
    return () => {
      cancelled = true;
    };
  }, [
    flowState.forcedPasswordChangeForm,
    flowState.setPendingPasswordChangeCurrentPassword,
    flowState.setRestoredPasswordChangeRequired,
    flowState,
    initialState?.menuTree,
    initialState?.currentUser?.requiresPasswordChange,
    locationSearch,
    setInitialState,
  ]);
  useEffect(() => {
    return createLoginSessionBroadcastListener(redirectTarget, (target) => {
      window.location.replace(target);
    }, () => !suppressLoginBroadcastRedirectRef.current);
  }, [redirectTarget]);
  useEffect(() => {
    if (wechatCallbackHandledRef.current) {
      return;
    }

    const callback = consumeWechatOAuthCallback({
      locationSearch,
      locationPathname,
      loginAvailable: Boolean(bootstrapFlow.loginCapabilities.wechatLoginAvailable),
      replaceLocation: history.replace,
    });
    if (!callback) {
      return;
    }

    wechatCallbackHandledRef.current = true;
    const { code, state } = callback;
    flowState.setSubmitting(true);
    beginLoginFlow();
    void request<LoginResponse>('/v1/auth/wechat/login', {
      method: 'POST',
      data: { code, state },
      autoRedirectOnUnauthorized: false,
      silent: true,
      skipAuth: true,
    })
      .then(async (loginResponse) => {
        markWechatContactBindRequired();
        if (loginResponse.requiresSecondFactor) {
          flowState.setPendingSecondFactorLogin(loginResponse);
          message.info(
            loginResponse.secondFactorOptions?.[0]?.promptMessage ||
              formatMessage({
                id: 'page.login.code.secondFactor',
                defaultMessage: 'Please enter the verification code to complete second-factor verification',
              }),
          );
          return;
        }
        await completeSuccessfulLogin(loginResponse, Boolean(flowState.loginForm.getFieldValue('remember')));
      })
      .catch((error) => {
        showErrorMessage(
          error,
          formatMessage({ id: 'page.login.error.loginFailed', defaultMessage: 'Login failed, please try again later' }),
        );
      })
      .finally(() => {
        endLoginFlow();
        flowState.setSubmitting(false);
      });
  }, [
    bootstrapFlow.loginCapabilities.wechatLoginAvailable,
    completeSuccessfulLogin,
    flowState,
    locationPathname,
    locationSearch,
  ]);

  return {
    loginPageStyle: bootstrapFlow.loginPageStyle,
    brandingWebsiteName: bootstrapFlow.brandingWebsiteName,
    brandingFooterItems: bootstrapFlow.brandingFooterItems,
    agreementSettings: bootstrapFlow.agreementSettings,
    availableLoginModes: bootstrapFlow.availableLoginModes,
    activeLoginMode: bootstrapFlow.activeLoginMode,
    setActiveLoginMode: bootstrapFlow.setActiveLoginMode,
    loginCapabilities: bootstrapFlow.loginCapabilities,
    loginForm: flowState.loginForm,
    forcedPasswordChangeForm: flowState.forcedPasswordChangeForm,
    viewState: {
      submitting: flowState.submitting,
      passkeySubmitting: flowState.passkeySubmitting,
      sendingLoginType,
      pendingSecondFactorLogin: flowState.pendingSecondFactorLogin,
      pendingSecondFactorPrompt,
      securitySettings,
      captchaChallenge,
      captchaLoading,
      captchaImageLoadFailed,
      loginCodeChallenges,
      loginCodeCooldownSeconds,
      forcedPasswordChangeOpen,
      passwordChangeSubmitting: flowState.passwordChangeSubmitting,
    },
    dialogState: {
      agreementPreviewOpen: bootstrapFlow.agreementPreviewOpen,
      agreementPreviewTitle: bootstrapFlow.agreementPreviewTitle,
      agreementPreviewMarkdown: bootstrapFlow.agreementPreviewMarkdown,
      setAgreementPreviewOpen: bootstrapFlow.setAgreementPreviewOpen,
      handleForcedPasswordChange,
    },
    actions: {
      openAgreementPreview: bootstrapFlow.openAgreementPreview,
      handleSendLoginCode,
      handleWechatLogin,
      handlePasskeyLogin,
      refreshCaptcha,
      setCaptchaImageLoadFailed,
      setCaptchaChallenge,
      handleSubmit,
      handleFinishFailed,
      setCaptchaProof,
      resetCaptchaProof,
    },
  };
};
