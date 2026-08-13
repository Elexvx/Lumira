import { formatMessage } from '@/i18n/formatMessage';
import { Form } from 'antd';
import { type UploadProps } from 'antd';
import dayjs from 'dayjs';
import { message } from '@/theme/antdFeedbackBridge';
import { useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { HTMLAttributes } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { createPasskeyCredential, isPasskeySupported, toPublicKeyCreationOptions, toRegistrationPayload } from '@/auth/passkey';
import { mergeSameSessionCurrentUser, mergeTrustedCurrentUser } from '@/auth/sessionState';
import type { AppInitialState } from '@/app';
import { useStandardFormProps } from '@/features/form/config';
import { API_OPTS } from '@/utils/errorMessage';
import { showErrorMessage } from '@/utils/errorMessage';
import { request } from '@/services/common/request';
import { repairOptionalMojibakeText } from '@/utils/textEncoding';
import type {
  CurrentUser,
  LoginCapabilities,
  ProfileSummary,
  SecondFactorChallenge,
  SecondFactorBindingChallenge,
  SecondFactorProviderStatus,
  SecondFactorVerification,
  PasskeyCredentialRecord,
  PasskeyOptions,
} from '@/types/api';

interface ProfileBasicInfoPayload {
  avatarUrl?: string;
  nickname?: string;
  realName?: string;
  availableTime?: string;
  birthMonth?: string;
  gender?: string;
  region?: string;
  idCardNumber?: string;
  extraProfileValues?: Record<string, string>;
}

const isDateProfileField = (field: { fieldType?: string | null }) =>
  ['DATE', 'MONTH'].includes((field.fieldType || '').toUpperCase());

const profileFieldDateFormat = (field: { fieldType?: string | null }) =>
  (field.fieldType || '').toUpperCase() === 'MONTH' ? 'YYYY-MM' : 'YYYY-MM-DD';

const profileFieldFormValue = (field: { fieldType?: string | null }, value?: string | null) => {
  if (!isDateProfileField(field)) {
    return value || '';
  }
  return value ? dayjs(value, profileFieldDateFormat(field)) : null;
};

const profileFieldPayloadValue = (field: { fieldType?: string | null }, value: unknown) => {
  if (value === null || value === undefined) {
    return '';
  }
  if (isDateProfileField(field) && dayjs.isDayjs(value)) {
    return value.format(profileFieldDateFormat(field));
  }
  return String(value);
};

export interface LoginMethodItem {
  key: string;
  title: string;
  statusLabel?: string;
  statusColor?: string;
  value?: string | null;
  methodLabel?: string;
  methodColor?: string;
  actionLabel: string;
  actionLoading?: boolean;
  disabled?: boolean;
  onAction: () => void;
}

const maskMobile = (mobile?: string | null) => {
  if (!mobile) {
    return '';
  }
  return mobile.length >= 7 ? `${mobile.slice(0, 3)}****${mobile.slice(-4)}` : mobile;
};

const maskEmail = (email?: string | null) => {
  if (!email) {
    return '';
  }
  const [localPart, domainPart] = email.split('@');
  if (!domainPart) {
    return email;
  }
  if (localPart.length <= 2) {
    return `**@${domainPart}`;
  }
  return `${localPart.slice(0, 2)}***@${domainPart}`;
};

const resolvePreferredCurrentVerificationFactor = (
  providers: SecondFactorProviderStatus[],
  currentUser: CurrentUser | null | undefined,
  loginCapabilities: LoginCapabilities | undefined,
): SecondFactorProviderStatus | null => {
  const totpProvider = providers.find((item) => item.factorCode === 'totp' && item.bound && item.systemEnabled !== false);
  if (totpProvider) {
    return totpProvider;
  }
  if (currentUser?.mobile && loginCapabilities?.smsLoginAvailable) {
    return {
      factorCode: 'sms',
      factorName: 'SMS verification code',
      bound: true,
      enabled: true,
      maskedContact: currentUser.mobile,
    };
  }
  if (currentUser?.email && loginCapabilities?.emailLoginAvailable) {
    return {
      factorCode: 'email',
      factorName: 'Email verification code',
      bound: true,
      enabled: true,
      maskedContact: currentUser.email,
    };
  }
  return null;
};

function normalizeCurrentUserText(user: CurrentUser): CurrentUser;
function normalizeCurrentUserText(user?: CurrentUser | null): CurrentUser | null | undefined;
function normalizeCurrentUserText(user?: CurrentUser | null): CurrentUser | null | undefined {
  if (!user) {
    return user;
  }
  return {
    ...user,
    nickname: repairOptionalMojibakeText(user.nickname),
    realName: repairOptionalMojibakeText(user.realName),
    region: repairOptionalMojibakeText(user.region),
  };
}

export const useProfileCenterPageAccess = () => {
  const [profileForm] = Form.useForm();
  const { initialState, setInitialState } = useInitialStateModel();
  const responsive = useResponsive();
  const profileQuery = useQuery({
    queryKey: [
      'profile-summary',
      initialState?.currentUser?.userId,
      initialState?.currentUser?.sessionId,
    ],
    queryFn: async () =>
      request<ProfileSummary>('/v1/profile/summary', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });
  const summary = profileQuery.data;
  const currentUser = useMemo(
    () => normalizeCurrentUserText(
      mergeSameSessionCurrentUser(initialState?.currentUser, summary?.currentUser),
    ),
    [initialState?.currentUser, summary?.currentUser],
  );
  const recentLoginLogs = summary?.recentLoginLogs || [];
  const loginCapabilitiesQuery = useQuery({
    queryKey: ['profile-login-capabilities'],
    queryFn: async () =>
      request<LoginCapabilities>('/v1/public/login-capabilities', {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        allowUnauthorizedWithoutRedirect: true,
        silent: true,
      }),
  });
  const loginCapabilities = loginCapabilitiesQuery.data;
  const providersQuery = useQuery({
    queryKey: ['profile-second-factor-providers', currentUser?.userId],
    queryFn: async () =>
      request<SecondFactorProviderStatus[]>('/v1/auth/verification/providers', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: Boolean(currentUser),
  });
  const passkeyQuery = useQuery({
    queryKey: ['profile-passkeys', currentUser?.userId],
    queryFn: async () =>
      request<PasskeyCredentialRecord[]>('/v1/auth/passkeys', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    enabled: Boolean(currentUser),
  });
  const [passkeyBinding, setPasskeyBinding] = useState(false);
  const [passkeyVerificationAction, setPasskeyVerificationAction] = useState<'bind' | 'rename' | 'delete' | null>(null);
  const [passkeyVerificationTargetId, setPasskeyVerificationTargetId] = useState<number | null>(null);
  const [passkeyVerificationTargetLabel, setPasskeyVerificationTargetLabel] = useState<string | null>(null);
  const [passkeyVerificationChallenge, setPasskeyVerificationChallenge] = useState<SecondFactorChallenge | null>(null);
  const [passkeyVerificationChallengeLoading, setPasskeyVerificationChallengeLoading] = useState(false);
  const [passkeyVerificationSubmitting, setPasskeyVerificationSubmitting] = useState(false);
  const [passkeyVerificationAlert, setPasskeyVerificationAlert] = useState<string | null>(null);
  const [passkeyVerificationForm] = Form.useForm<{ currentPassword?: string; verificationCode?: string }>();
  const passkeyVerificationFormProps = useStandardFormProps({
    form: passkeyVerificationForm,
    initialValues: { currentPassword: '', verificationCode: '' },
  });
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorBindingChallenge | null>(null);
  const [bindingVerificationChallenge, setBindingVerificationChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingVerificationChallengeLoading, setBindingVerificationChallengeLoading] = useState(false);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();
  const [bindVerificationForm] = Form.useForm<{ currentPassword?: string; verificationCode?: string }>();
  const bindVerificationFormProps = useStandardFormProps({
    form: bindVerificationForm,
    initialValues: { currentPassword: '', verificationCode: '' },
  });
  const bindingVerificationFactor = useMemo<SecondFactorProviderStatus | null>(() => {
    const factor = resolvePreferredCurrentVerificationFactor(providersQuery.data || [], currentUser, loginCapabilities);
    if (!factor) {
      return null;
    }
    if (factor.factorCode === 'sms') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.bind.currentFactor.sms', defaultMessage: 'SMS verification code' }) };
    }
    if (factor.factorCode === 'email') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.bind.currentFactor.email', defaultMessage: 'Email verification code' }) };
    }
    return factor;
  }, [currentUser, loginCapabilities, providersQuery.data]);
  const [unbindProvider, setUnbindProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [unbindChallenge, setUnbindChallenge] = useState<SecondFactorChallenge | null>(null);
  const [unbindChallengeLoading, setUnbindChallengeLoading] = useState(false);
  const [unbindSubmitting, setUnbindSubmitting] = useState(false);
  const [unbindAlert, setUnbindAlert] = useState<string | null>(null);
  const [unbindForm] = Form.useForm<{ verificationCode?: string }>();
  const unbindFormProps = useStandardFormProps({
    form: unbindForm,
    initialValues: { verificationCode: '' },
  });
  const resetBindState = useCallback(() => {
    setBindingProvider(null);
    setBindingChallenge(null);
    setBindingVerificationChallenge(null);
    setBindingVerificationChallengeLoading(false);
    setBindingLoading(false);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    bindVerificationForm.resetFields();
  }, [bindVerificationForm]);
  const closeBindModal = useCallback(() => {
    if (bindingSubmitting || bindingVerificationChallengeLoading || bindingLoading) {
      return;
    }
    setBindModalOpen(false);
    window.setTimeout(() => {
      resetBindState();
    }, 0);
  }, [bindingLoading, bindingSubmitting, bindingVerificationChallengeLoading, resetBindState]);
  const resetUnbindState = useCallback(() => {
    setUnbindProvider(null);
    setUnbindChallenge(null);
    setUnbindChallengeLoading(false);
    setUnbindSubmitting(false);
    setUnbindAlert(null);
    unbindForm.resetFields();
  }, [unbindForm]);
  const closeUnbindModal = useCallback(() => {
    if (unbindChallengeLoading || unbindSubmitting) {
      return;
    }
    window.setTimeout(() => {
      resetUnbindState();
    }, 0);
  }, [resetUnbindState, unbindChallengeLoading, unbindSubmitting]);
  const openBindModal = useCallback((provider: SecondFactorProviderStatus) => {
    if (provider.bound) {
      message.warning(formatMessage({ id: 'page.profile.bind.rebindRequiresUnbind', defaultMessage: 'Please unbind the current authenticator before binding a new one.' }));
      return;
    }
    setBindingProvider(provider);
    setBindingChallenge(null);
    setBindingVerificationChallenge(null);
    setBindingVerificationChallengeLoading(false);
    setBindingLoading(false);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    bindVerificationForm.setFieldsValue({
      currentPassword: undefined,
      verificationCode: undefined,
    });
    setBindModalOpen(true);
  }, [bindVerificationForm]);
  const requestBindVerificationChallenge = useCallback(
    async (provider: SecondFactorProviderStatus | null = bindingVerificationFactor) => {
      if (!provider) {
        return null;
      }
      setBindingVerificationChallengeLoading(true);
      try {
        const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/challenge`, {
          method: 'POST',
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setBindingVerificationChallenge(challenge);
        bindVerificationForm.setFieldValue('verificationCode', undefined);
        return challenge;
      } catch (error) {
        setBindingAlert({
          type: 'error',
          message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.currentChallengeFailed', defaultMessage: 'Failed to load verification details, please try again later' }),
        });
        return null;
      } finally {
        setBindingVerificationChallengeLoading(false);
      }
    },
    [bindVerificationForm, bindingVerificationFactor],
  );
  useEffect(() => {
    if (!bindModalOpen || !bindingVerificationFactor || bindingChallenge || bindingVerificationChallenge || bindingVerificationChallengeLoading) {
      return;
    }
    void requestBindVerificationChallenge(bindingVerificationFactor);
  }, [bindModalOpen, bindingChallenge, bindingVerificationChallenge, bindingVerificationChallengeLoading, bindingVerificationFactor, requestBindVerificationChallenge]);
  const requestBindChallenge = useCallback(
    async (provider: SecondFactorProviderStatus, verificationPayload: Record<string, string | undefined>) => {
      setBindingLoading(true);
      try {
        const challenge = await request<SecondFactorBindingChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/bind`, {
          method: 'POST',
          data: verificationPayload,
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setBindingChallenge(challenge);
        setBindingVerificationChallenge(null);
        bindVerificationForm.resetFields();
        return true;
      } catch (error) {
        setBindingAlert({
          type: 'error',
          message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.fetchFailed', defaultMessage: 'Failed to load binding info, please try again later' }),
        });
        return false;
      } finally {
        setBindingLoading(false);
      }
    },
    [bindVerificationForm],
  );
  const retryBindChallenge = useCallback(async () => {
    if (!bindingProvider) {
      return;
    }
    setBindingChallenge(null);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    if (bindingVerificationFactor) {
      setBindingVerificationChallenge(null);
      await requestBindVerificationChallenge(bindingVerificationFactor);
      return;
    }
    bindVerificationForm.resetFields();
  }, [bindVerificationForm, bindingProvider, bindingVerificationFactor, requestBindVerificationChallenge]);
  const handleConfirmBindVerification = useCallback(
    async (values: { currentPassword?: string; verificationCode?: string }) => {
      if (!bindingProvider) {
        return false;
      }
      const currentPassword = values.currentPassword?.trim();
      const verificationCode = values.verificationCode?.trim();
      setBindingAlert(undefined);
      if (!bindingVerificationFactor) {
        if (!currentPassword) {
          setBindingAlert({
            type: 'warning',
            message: formatMessage({ id: 'page.profile.bind.enterCurrentPassword', defaultMessage: 'Please enter your current password first.' }),
          });
          return false;
        }
        return requestBindChallenge(bindingProvider, { currentPassword });
      }
      if (!verificationCode) {
        setBindingAlert({
          type: 'warning',
          message: formatMessage({ id: 'page.profile.bind.enterCurrentCode', defaultMessage: 'Please enter the current verification code or a recovery code first.' }),
        });
        return false;
      }
      let currentChallenge = bindingVerificationChallenge;
      if (!currentChallenge?.challengeId) {
        currentChallenge = await requestBindVerificationChallenge(bindingVerificationFactor);
        if (!currentChallenge?.challengeId) {
          return false;
        }
      }
      return requestBindChallenge(bindingProvider, {
        currentFactorCode: bindingVerificationFactor.factorCode,
        currentChallengeId: currentChallenge.challengeId,
        currentVerificationCode: verificationCode,
      });
    },
    [bindingProvider, bindingVerificationChallenge, bindingVerificationFactor, requestBindChallenge, requestBindVerificationChallenge],
  );
  const handleVerifyBind = useCallback(
    async (values: { verificationCode?: string }) => {
      if (!bindingProvider || !bindingChallenge) {
        setBindingAlert({
          type: 'warning',
          message: formatMessage({ id: 'page.profile.bind.expired', defaultMessage: 'Binding information has expired, please start again.' }),
        });
        return false;
      }
      if (!values.verificationCode) {
        setBindingAlert({
          type: 'warning',
          message: formatMessage({ id: 'page.profile.bind.enterCode', defaultMessage: 'Please enter the verification code.' }),
        });
        return false;
      }

      setBindingSubmitting(true);
      setBindingAlert(undefined);
      try {
        const result = await request<SecondFactorVerification>(`/v1/auth/verification/providers/${bindingProvider.factorCode}/verify`, {
          method: 'POST',
          data: {
            factorCode: bindingProvider.factorCode,
            challengeId: bindingChallenge.challengeId,
            verificationCode: values.verificationCode,
          },
          autoRedirectOnUnauthorized: false,
          silent: true,
        });

        if (!result.verified) {
          setBindingAlert({
            type: 'warning',
            message: result.message || formatMessage({ id: 'page.profile.bind.codeFailed', defaultMessage: 'Verification code validation failed, please try again.' }),
          });
          return false;
        }

        setBindingChallenge((current) =>
          current
            ? {
                ...current,
                recoveryCodes: result.recoveryCodes || [],
              }
            : current,
        );
        setBindingCompleted(true);
        await providersQuery.refetch();
        return true;
      } catch (error) {
        setBindingAlert({
          type: 'error',
          message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }),
        });
        return false;
      } finally {
        setBindingSubmitting(false);
      }
    },
    [bindingChallenge, bindingProvider, providersQuery, setBindingAlert, setBindingCompleted, setBindingSubmitting],
  );
  const handleUnbind = useCallback(
    (provider: SecondFactorProviderStatus) => {
      void (async () => {
        setUnbindProvider(provider);
        setUnbindChallenge(null);
        setUnbindAlert(null);
        setUnbindChallengeLoading(true);
        setUnbindSubmitting(false);
        unbindForm.resetFields();
        try {
          const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/challenge`, {
            method: 'POST',
            ...API_OPTS.NO_REDIRECT,
          });
          setUnbindChallenge(challenge);
        } catch (error) {
          setUnbindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.unbindChallengeFailed', defaultMessage: 'Failed to start verification, please try again later' }));
        }
        setUnbindChallengeLoading(false);
      })();
    },
    [unbindForm],
  );
  const retryUnbindChallenge = useCallback(async () => {
    if (!unbindProvider) {
      return;
    }
    setUnbindChallenge(null);
    setUnbindAlert(null);
    setUnbindChallengeLoading(true);
    try {
      const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${unbindProvider.factorCode}/challenge`, {
        method: 'POST',
        ...API_OPTS.NO_REDIRECT,
      });
      setUnbindChallenge(challenge);
    } catch (error) {
      setUnbindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.unbindChallengeFailed', defaultMessage: 'Failed to start verification, please try again later' }));
    } finally {
      setUnbindChallengeLoading(false);
    }
  }, [unbindProvider]);
  const handleConfirmUnbind = useCallback(
    async (values: { verificationCode?: string }) => {
      if (!unbindProvider || !unbindChallenge?.challengeId) {
        setUnbindAlert(formatMessage({ id: 'page.profile.bind.unbindExpired', defaultMessage: 'Verification information has expired, please start again.' }));
        return false;
      }
      if (!values.verificationCode?.trim()) {
        setUnbindAlert(formatMessage({ id: 'page.profile.bind.enterUnbindCode', defaultMessage: 'Please enter the verification code or a recovery code.' }));
        return false;
      }
      setUnbindSubmitting(true);
      setUnbindAlert(null);
      try {
        await request<boolean>(`/v1/auth/verification/providers/${unbindProvider.factorCode}/unbind`, {
          method: 'POST',
          data: {
            factorCode: unbindProvider.factorCode,
            challengeId: unbindChallenge.challengeId,
            verificationCode: values.verificationCode.trim(),
          },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(formatMessage({ id: 'page.profile.bind.unbound', defaultMessage: 'Unbound' }));
        closeUnbindModal();
        await providersQuery.refetch();
        return true;
      } catch (error) {
        setUnbindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.unbindFailed', defaultMessage: 'Failed to unbind, please try again later' }));
        return false;
      } finally {
        setUnbindSubmitting(false);
      }
    },
    [closeUnbindModal, providersQuery, unbindChallenge, unbindProvider],
  );

  type ContactBindType = 'mobile' | 'email' | null;
  const [contactBindType, setContactBindType] = useState<ContactBindType>(null);
  const [contactBindChallenge, setContactBindChallenge] = useState<SecondFactorChallenge | null>(null);
  const [contactBindChallengeTarget, setContactBindChallengeTarget] = useState<string | null>(null);
  const [contactBindCurrentChallenge, setContactBindCurrentChallenge] = useState<SecondFactorChallenge | null>(null);
  const [contactBindCurrentChallengeLoading, setContactBindCurrentChallengeLoading] = useState(false);
  const [contactBindChallengeLoading, setContactBindChallengeLoading] = useState(false);
  const [contactBindSubmitting, setContactBindSubmitting] = useState(false);
  const [contactBindAlert, setContactBindAlert] = useState<string | null>(null);
  const [contactBindForm] = Form.useForm<{ value?: string; verificationCode?: string; currentVerificationCode?: string; currentPassword?: string }>();
  const contactBindValue = Form.useWatch('value', contactBindForm);
  const contactBindCurrentFactor = useMemo<SecondFactorProviderStatus | null>(() => {
    const factor = resolvePreferredCurrentVerificationFactor(providersQuery.data || [], currentUser, loginCapabilities);
    if (!factor) {
      return null;
    }
    if (factor.factorCode === 'sms') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.bind.currentFactor.sms', defaultMessage: 'SMS verification code' }) };
    }
    if (factor.factorCode === 'email') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.bind.currentFactor.email', defaultMessage: 'Email verification code' }) };
    }
    return factor;
  }, [currentUser, loginCapabilities, providersQuery.data]);
  const passkeyVerificationFactor = useMemo<SecondFactorProviderStatus | null>(() => {
    const factor = resolvePreferredCurrentVerificationFactor(providersQuery.data || [], currentUser, loginCapabilities);
    if (!factor) {
      return null;
    }
    if (factor.factorCode === 'sms') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.passkey.currentFactor.sms', defaultMessage: 'SMS verification code' }) };
    }
    if (factor.factorCode === 'email') {
      return { ...factor, factorName: formatMessage({ id: 'page.profile.passkey.currentFactor.email', defaultMessage: 'Email verification code' }) };
    }
    return factor;
  }, [currentUser, loginCapabilities, providersQuery.data]);
  const contactBindFormProps = useStandardFormProps({
    form: contactBindForm,
    initialValues: { value: '', verificationCode: '', currentVerificationCode: '', currentPassword: '' },
  });
  const contactBindVerificationRequired = useMemo(
    () =>
      contactBindType === 'mobile'
        ? (summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired ?? loginCapabilities?.smsLoginAvailable ?? false)
        : contactBindType === 'email'
          ? (summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired ?? loginCapabilities?.emailLoginAvailable ?? false)
          : false,
    [contactBindType, loginCapabilities?.emailLoginAvailable, loginCapabilities?.smsLoginAvailable, summary?.emailBindAvailable, summary?.emailBindVerificationRequired, summary?.mobileBindAvailable, summary?.mobileBindVerificationRequired],
  );
  const contactBindAvailable = useMemo(
    () =>
      contactBindType === 'mobile'
        ? (summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired ?? loginCapabilities?.smsLoginAvailable ?? false)
        : contactBindType === 'email'
          ? (summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired ?? loginCapabilities?.emailLoginAvailable ?? false)
          : false,
    [contactBindType, loginCapabilities?.emailLoginAvailable, loginCapabilities?.smsLoginAvailable, summary?.emailBindAvailable, summary?.emailBindVerificationRequired, summary?.mobileBindAvailable, summary?.mobileBindVerificationRequired],
  );
  const contactBindChallengeMatchesValue = useMemo(
    () => Boolean(contactBindVerificationRequired && contactBindChallenge && contactBindChallengeTarget === (contactBindValue?.trim() || '')),
    [contactBindChallenge, contactBindChallengeTarget, contactBindValue, contactBindVerificationRequired],
  );
  const openContactBindModal = useCallback(
    (type: 'mobile' | 'email') => {
      if (
        (type === 'mobile' && !(summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired ?? loginCapabilities?.smsLoginAvailable)) ||
        (type === 'email' && !(summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired ?? loginCapabilities?.emailLoginAvailable))
      ) {
        message.warning(
          type === 'mobile'
            ? formatMessage({ id: 'page.profile.bind.mobileDisabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
            : formatMessage({ id: 'page.profile.bind.emailDisabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' }),
        );
        return;
      }
      setContactBindType(type);
      setContactBindChallenge(null);
      setContactBindChallengeTarget(null);
      setContactBindCurrentChallenge(null);
      setContactBindCurrentChallengeLoading(false);
      setContactBindChallengeLoading(false);
      setContactBindSubmitting(false);
      setContactBindAlert(null);
      contactBindForm.setFieldsValue({
        value: type === 'mobile' ? currentUser?.mobile || '' : currentUser?.email || '',
        currentVerificationCode: undefined,
        verificationCode: undefined,
      });
    },
    [
      contactBindForm,
      currentUser?.email,
      currentUser?.mobile,
      loginCapabilities?.emailLoginAvailable,
      loginCapabilities?.smsLoginAvailable,
      summary?.emailBindAvailable,
      summary?.emailBindVerificationRequired,
      summary?.mobileBindAvailable,
      summary?.mobileBindVerificationRequired,
    ],
  );
  const closeContactBindModal = useCallback(() => {
    if (contactBindSubmitting || contactBindChallengeLoading || contactBindCurrentChallengeLoading) {
      return;
    }
    setContactBindType(null);
    setContactBindChallenge(null);
    setContactBindChallengeTarget(null);
    setContactBindCurrentChallenge(null);
    setContactBindCurrentChallengeLoading(false);
    setContactBindAlert(null);
    setContactBindChallengeLoading(false);
    contactBindForm.resetFields();
  }, [contactBindChallengeLoading, contactBindCurrentChallengeLoading, contactBindForm, contactBindSubmitting]);

  const requestContactBindCurrentChallenge = useCallback(
    async (provider: SecondFactorProviderStatus | null = contactBindCurrentFactor) => {
      if (!provider) {
        return null;
      }
      setContactBindCurrentChallengeLoading(true);
      try {
        const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/challenge`, {
          method: 'POST',
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setContactBindCurrentChallenge(challenge);
        contactBindForm.setFieldValue('currentVerificationCode', undefined);
        return challenge;
      } catch (error) {
        setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.currentChallengeFailed', defaultMessage: 'Failed to load current verification method, please try again later' }));
        return null;
      } finally {
        setContactBindCurrentChallengeLoading(false);
      }
    },
    [contactBindCurrentFactor, contactBindForm],
  );
  useEffect(() => {
    if (!contactBindType || !contactBindCurrentFactor || contactBindCurrentChallenge || contactBindCurrentChallengeLoading) {
      return;
    }
    void requestContactBindCurrentChallenge(contactBindCurrentFactor);
  }, [contactBindCurrentChallenge, contactBindCurrentChallengeLoading, contactBindCurrentFactor, contactBindType, requestContactBindCurrentChallenge]);

  const requestContactBindChallenge = useCallback(
    async (nextValue: string) => {
      if (!contactBindType) {
        return false;
      }
      let currentChallenge = contactBindCurrentChallenge;
      if (contactBindCurrentFactor) {
        const currentVerificationCode = contactBindForm.getFieldValue('currentVerificationCode')?.trim();
        if (!currentVerificationCode) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.enterCurrentCode', defaultMessage: 'Please enter the current verification code first.' }));
          return false;
        }
        if (!currentChallenge?.challengeId) {
          currentChallenge = await requestContactBindCurrentChallenge(contactBindCurrentFactor);
          if (!currentChallenge?.challengeId) {
            return false;
          }
        }
      } else {
        const currentPassword = contactBindForm.getFieldValue('currentPassword')?.trim();
        if (!currentPassword) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.enterCurrentPassword', defaultMessage: 'Please enter your current password first.' }));
          return false;
        }
      }

      setContactBindChallengeLoading(true);
      try {
        const challenge = await request<SecondFactorChallenge>('/v1/profile/contact-bind/challenge', {
          method: 'POST',
          data: {
            contactType: contactBindType,
            value: nextValue,
            currentPassword: contactBindCurrentFactor ? undefined : contactBindForm.getFieldValue('currentPassword')?.trim(),
            currentFactorCode: contactBindCurrentFactor?.factorCode,
            currentChallengeId: currentChallenge?.challengeId,
            currentVerificationCode: contactBindCurrentFactor ? contactBindForm.getFieldValue('currentVerificationCode')?.trim() : undefined,
          },
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setContactBindChallenge(challenge);
        setContactBindChallengeTarget(nextValue);
        setContactBindCurrentChallenge(null);
        contactBindForm.setFieldsValue({ currentPassword: undefined, currentVerificationCode: undefined, verificationCode: undefined });
        message.success(formatMessage({ id: 'page.profile.bind.codeSent', defaultMessage: 'Verification code sent, please enter it to continue' }));
        return true;
      } catch (error) {
        setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.sendFailed', defaultMessage: 'Failed to send verification code, please try again later' }));
        return false;
      } finally {
        setContactBindChallengeLoading(false);
      }
    },
    [contactBindCurrentChallenge, contactBindCurrentFactor, contactBindForm, contactBindType, requestContactBindCurrentChallenge],
  );

  const prepareContactBindSubmission = useCallback(async () => {
    if (!contactBindType) {
      return null;
    }
    if (!contactBindAvailable) {
      setContactBindAlert(
        contactBindType === 'mobile'
          ? formatMessage({ id: 'page.profile.bind.mobileDisabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
          : formatMessage({ id: 'page.profile.bind.emailDisabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' }),
      );
      return null;
    }

    try {
      const values = await contactBindForm.validateFields();
      const nextValue = values.value?.trim();
      if (!nextValue) {
        return null;
      }

      setContactBindAlert(null);

      if (contactBindVerificationRequired && (!contactBindChallenge || contactBindChallengeTarget !== nextValue)) {
        const sent = await requestContactBindChallenge(nextValue);
        if (!sent) {
          return null;
        }
      }

      const verificationCode = contactBindVerificationRequired ? contactBindForm.getFieldValue('verificationCode')?.trim() : undefined;
      if (contactBindVerificationRequired) {
        if (!verificationCode) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.enterCode', defaultMessage: 'Please enter the verification code.' }));
          return null;
        }
        if (!contactBindChallenge?.challengeId) {
          setContactBindAlert(formatMessage({ id: 'page.profile.bind.expired', defaultMessage: 'Binding information has expired, please request a new code.' }));
          return null;
        }
      }

      return {
        value: nextValue,
        verificationCode,
      };
    } catch (error) {
      setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }));
      return null;
    }
  }, [
    contactBindAvailable,
    contactBindChallenge,
    contactBindChallengeTarget,
    contactBindForm,
    contactBindType,
    contactBindVerificationRequired,
    requestContactBindChallenge,
  ]);

  const handleContactBindConfirm = useCallback(async () => {
    const readyState = await prepareContactBindSubmission();
    if (!readyState) {
      return;
    }
    if (!contactBindType) {
      return;
    }
    setContactBindSubmitting(true);
    try {
      const updatedUser = await request<CurrentUser>('/v1/profile/contact-bind', {
        method: 'PUT',
        data: {
          contactType: contactBindType,
          value: readyState.value,
          challengeId: contactBindChallenge?.challengeId,
          verificationCode: readyState.verificationCode,
        },
        ...API_OPTS.NO_REDIRECT,
      });
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: mergeTrustedCurrentUser(prev.currentUser, updatedUser),
            }
          : prev,
      );
      profileForm.setFieldValue(contactBindType, readyState.value);
      message.success(
        contactBindType === 'mobile'
          ? formatMessage({ id: 'page.profile.bind.mobileBound', defaultMessage: 'Mobile number bound' })
          : formatMessage({ id: 'page.profile.bind.emailBound', defaultMessage: 'Email bound' }),
      );
      await profileQuery.refetch();
      setContactBindType(null);
      setContactBindChallenge(null);
      setContactBindChallengeTarget(null);
      setContactBindAlert(null);
      contactBindForm.resetFields();
    } catch (error) {
      setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.failed', defaultMessage: 'Binding failed, please try again later' }));
    } finally {
      setContactBindSubmitting(false);
    }
  }, [contactBindChallenge, contactBindForm, contactBindType, prepareContactBindSubmission, profileForm, profileQuery, setInitialState]);

  const resetPasskeyVerificationState = useCallback(() => {
    setPasskeyVerificationAction(null);
    setPasskeyVerificationTargetId(null);
    setPasskeyVerificationTargetLabel(null);
    setPasskeyVerificationChallenge(null);
    setPasskeyVerificationChallengeLoading(false);
    setPasskeyVerificationSubmitting(false);
    setPasskeyVerificationAlert(null);
    passkeyVerificationForm.resetFields();
  }, [passkeyVerificationForm]);
  const closePasskeyVerificationModal = useCallback(() => {
    if (passkeyVerificationChallengeLoading || passkeyVerificationSubmitting || passkeyBinding) {
      return;
    }
    resetPasskeyVerificationState();
  }, [passkeyBinding, passkeyVerificationChallengeLoading, passkeyVerificationSubmitting, resetPasskeyVerificationState]);
  const requestPasskeyVerificationChallenge = useCallback(
    async (provider: SecondFactorProviderStatus | null = passkeyVerificationFactor) => {
      if (!provider) {
        return null;
      }
      setPasskeyVerificationChallengeLoading(true);
      try {
        const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/challenge`, {
          method: 'POST',
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setPasskeyVerificationChallenge(challenge);
        passkeyVerificationForm.setFieldValue('verificationCode', undefined);
        return challenge;
      } catch (error) {
        setPasskeyVerificationAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.passkey.challengeFailed', defaultMessage: 'Failed to load verification details, please try again later' }));
        return null;
      } finally {
        setPasskeyVerificationChallengeLoading(false);
      }
    },
    [passkeyVerificationFactor, passkeyVerificationForm],
  );
  useEffect(() => {
    if (!passkeyVerificationAction || !passkeyVerificationFactor || passkeyVerificationChallenge || passkeyVerificationChallengeLoading) {
      return;
    }
    void requestPasskeyVerificationChallenge(passkeyVerificationFactor);
  }, [passkeyVerificationAction, passkeyVerificationChallenge, passkeyVerificationChallengeLoading, passkeyVerificationFactor, requestPasskeyVerificationChallenge]);
  const openPasskeyVerificationModal = useCallback((action: 'bind' | 'rename' | 'delete', credentialId?: number, label?: string) => {
    setPasskeyVerificationAction(action);
    setPasskeyVerificationTargetId(typeof credentialId === 'number' ? credentialId : null);
    setPasskeyVerificationTargetLabel(typeof label === 'string' ? label : null);
    setPasskeyVerificationChallenge(null);
    setPasskeyVerificationChallengeLoading(false);
    setPasskeyVerificationSubmitting(false);
    setPasskeyVerificationAlert(null);
    passkeyVerificationForm.setFieldsValue({
      currentPassword: undefined,
      verificationCode: undefined,
    });
  }, [passkeyVerificationForm]);

  const visibleProfileFields = useMemo(
    () => new Set(summary?.profileFieldSettings?.filter((item) => item.visible).map((item) => item.fieldKey)),
    [summary?.profileFieldSettings],
  );
  const visibleCustomProfileFields = useMemo(
    () => (summary?.profileFieldSettings || []).filter((item) => item.visible && item.custom),
    [summary?.profileFieldSettings],
  );
  const visibleCustomProfileFieldKeys = useMemo(
    () => new Set(visibleCustomProfileFields.map((item) => item.fieldKey)),
    [visibleCustomProfileFields],
  );
  const profileCompletionSummary = summary?.profileCompletion;
  const roleNames = currentUser?.availableRoles?.map((role) => role.roleName).filter(Boolean) || [];
  const activeRoleName =
    currentUser?.availableRoles?.find((role) => role.id === currentUser.simulatedRoleId)?.roleName || roleNames[0] || 'No role available';
  const displayName = currentUser?.nickname || currentUser?.realName || currentUser?.username || '-';
  const avatarValue = Form.useWatch('avatarUrl', profileForm);
  const hasVisibleProfileFields = visibleProfileFields.size > 0;
  const profileFormProps = useStandardFormProps({ form: profileForm });
  const profileBasicCardRef = useRef<HTMLDivElement>(null);
  const [profileEditingOpen, setProfileEditingOpen] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string>();

  useEffect(() => {
    if (!profileEditingOpen || !currentUser) {
      return;
    }

    profileForm.setFieldsValue({
      avatarUrl: currentUser.avatarUrl || '',
      nickname: currentUser.nickname || '',
      realName: currentUser.realName || '',
      availableTime: currentUser.availableTime || '',
      birthMonth: currentUser.birthMonth ? dayjs(currentUser.birthMonth, 'YYYY-MM') : null,
      gender: currentUser.gender || undefined,
      region: currentUser.region || '',
      idCardNumber: currentUser.idCardNumber || '',
      extraProfileValues: Object.fromEntries(
        visibleCustomProfileFields.map((item) => [
          item.fieldKey,
          profileFieldFormValue(item, currentUser.extraProfileValues?.[item.fieldKey]),
        ]),
      ),
    });
    setAvatarPreviewUrl(undefined);
  }, [currentUser, profileEditingOpen, profileForm, visibleCustomProfileFields]);

  const handleProfileEditOpenChange = useCallback((open: boolean) => {
    setProfileEditingOpen(open);
    if (!open) {
      setAvatarPreviewUrl(undefined);
    }
  }, []);

  const handleSaveProfile = useCallback(async () => {
    try {
      const values = await profileForm.validateFields();
      setProfileSaving(true);
      const updatedUser = normalizeCurrentUserText(await request<CurrentUser>('/v1/profile', {
        method: 'PUT',
        data: {
          avatarUrl: values.avatarUrl ?? currentUser?.avatarUrl ?? '',
          nickname: values.nickname ?? currentUser?.nickname ?? '',
          realName: values.realName ?? currentUser?.realName ?? '',
          availableTime: values.availableTime ?? currentUser?.availableTime ?? '',
          birthMonth: values.birthMonth === undefined
            ? currentUser?.birthMonth || ''
            : values.birthMonth
              ? values.birthMonth.format('YYYY-MM')
              : '',
          gender: values.gender ?? currentUser?.gender ?? '',
          region: values.region ?? currentUser?.region ?? '',
          idCardNumber: values.idCardNumber ?? currentUser?.idCardNumber ?? '',
          extraProfileValues: Object.fromEntries(
            visibleCustomProfileFields.map((item) => [
              item.fieldKey,
              profileFieldPayloadValue(
                item,
                values.extraProfileValues?.[item.fieldKey] ?? currentUser?.extraProfileValues?.[item.fieldKey] ?? '',
              ),
            ]),
          ),
        } satisfies ProfileBasicInfoPayload,
        ...API_OPTS.NO_REDIRECT,
      }));
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: mergeTrustedCurrentUser(prev.currentUser, updatedUser),
            }
          : prev,
      );
      message.success(formatMessage({ id: 'page.profile.bind.updateSuccess', defaultMessage: 'Profile updated' }));
      handleProfileEditOpenChange(false);
      await profileQuery.refetch();
    } finally {
      setProfileSaving(false);
    }
  }, [currentUser, handleProfileEditOpenChange, profileForm, profileQuery, setInitialState, visibleCustomProfileFields]);
  const handleAvatarBeforeCrop = useCallback((file: File) => {
    if (!file.type.startsWith('image/')) {
      message.error(formatMessage({ id: 'page.profile.avatar.selectImage', defaultMessage: 'Please select an image file' }));
      return false;
    }
    return true;
  }, []);
  const handleAvatarUploadRequest: UploadProps['customRequest'] = useCallback(async ({ file, onSuccess, onError }) => {
    try {
      setAvatarUploading(true);
      const formData = new FormData();
      formData.append('file', file as File);
      const avatarUrl = await request<string>('/v1/profile/uploads/avatar', {
        method: 'POST',
        headers: {},
        data: formData,
        ...API_OPTS.NO_REDIRECT,
      });
      profileForm.setFieldValue('avatarUrl', avatarUrl);
      setAvatarPreviewUrl(avatarUrl);
      message.success(formatMessage({ id: 'page.profile.avatar.uploadSuccess', defaultMessage: 'Avatar uploaded, please click save profile' }));
      onSuccess?.(avatarUrl);
    } catch (error) {
      onError?.(error as Error);
      showErrorMessage(error, formatMessage({ id: 'page.profile.avatar.uploadFailed', defaultMessage: 'Avatar upload failed, please try again later' }));
    } finally {
      setAvatarUploading(false);
    }
  }, [profileForm]);

  const summaryMobileBindAvailable = Boolean(summary?.mobileBindAvailable ?? summary?.mobileBindVerificationRequired);
  const summaryEmailBindAvailable = Boolean(summary?.emailBindAvailable ?? summary?.emailBindVerificationRequired);
  const emailLoginAvailable = Boolean(loginCapabilities?.emailLoginAvailable);
  const smsLoginAvailable = Boolean(loginCapabilities?.smsLoginAvailable);
  const passkeyLoginAvailable = Boolean(loginCapabilities?.passkeyLoginAvailable);
  const mobileBindAvailable = summaryMobileBindAvailable || smsLoginAvailable;
  const emailBindAvailable = summaryEmailBindAvailable || emailLoginAvailable;
  const mobileBindingVisible = mobileBindAvailable;
  const emailBindingVisible = emailBindAvailable;
  const contactBindSettingsLoading = profileQuery.isLoading || loginCapabilitiesQuery.isLoading || !summary;
  const loginMethodItems = useMemo<LoginMethodItem[]>(
    () => [
      ...(mobileBindingVisible
        ? [
            {
              key: 'mobile',
              title: formatMessage({ id: 'page.profile.loginMethod.mobile', defaultMessage: '手机号登录' }),
              statusLabel: currentUser?.mobile
                ? formatMessage({ id: 'page.profile.contact.bound', defaultMessage: 'Bound' })
                : formatMessage({ id: 'page.profile.contact.unbound', defaultMessage: 'Unbound' }),
              statusColor: currentUser?.mobile ? 'green' : 'default',
              value: currentUser?.mobile ? maskMobile(currentUser.mobile) : formatMessage({ id: 'page.profile.contact.notSetMobile', defaultMessage: 'Mobile number not set' }),
              methodLabel: formatMessage({ id: 'page.profile.loginMethod.smsCode', defaultMessage: '短信登录' }),
              methodColor: 'blue',
              actionLabel: currentUser?.mobile
                ? formatMessage({ id: 'page.profile.contact.editMobile', defaultMessage: 'Change mobile number' })
                : formatMessage({ id: 'page.profile.contact.bindMobile', defaultMessage: 'Bind mobile number' }),
              actionLoading: contactBindType === 'mobile' && (contactBindSubmitting || contactBindChallengeLoading),
              disabled: contactBindSubmitting || contactBindChallengeLoading || contactBindSettingsLoading,
              onAction: () => openContactBindModal('mobile'),
            },
          ]
        : []),
      ...(emailBindingVisible
        ? [
            {
              key: 'email',
              title: formatMessage({ id: 'page.profile.loginMethod.email', defaultMessage: '邮箱登录' }),
              statusLabel: currentUser?.email
                ? formatMessage({ id: 'page.profile.contact.bound', defaultMessage: 'Bound' })
                : formatMessage({ id: 'page.profile.contact.unbound', defaultMessage: 'Unbound' }),
              statusColor: currentUser?.email ? 'green' : 'default',
              value: currentUser?.email ? maskEmail(currentUser.email) : formatMessage({ id: 'page.profile.contact.notSetEmail', defaultMessage: 'Email not set' }),
              methodLabel: formatMessage({ id: 'page.profile.loginMethod.emailCode', defaultMessage: '邮箱登录' }),
              methodColor: 'blue',
              actionLabel: currentUser?.email
                ? formatMessage({ id: 'page.profile.contact.editEmail', defaultMessage: 'Change email' })
                : formatMessage({ id: 'page.profile.contact.bindEmail', defaultMessage: 'Bind email' }),
              actionLoading: contactBindType === 'email' && (contactBindSubmitting || contactBindChallengeLoading),
              disabled: contactBindSubmitting || contactBindChallengeLoading || contactBindSettingsLoading,
              onAction: () => openContactBindModal('email'),
            },
          ]
        : []),
    ],
    [
      contactBindSettingsLoading,
      currentUser,
      emailBindingVisible,
      contactBindChallengeLoading,
      contactBindSubmitting,
      contactBindType,
      mobileBindingVisible,
      openContactBindModal,
    ],
  );
  const handleBindPasskey = useCallback(async () => {
    if (!isPasskeySupported()) {
      message.warning(formatMessage({ id: 'page.profile.passkey.unsupported', defaultMessage: '当前浏览器不支持通行密钥' }));
      return;
    }
    if (!passkeyLoginAvailable) {
      message.warning(formatMessage({ id: 'page.profile.passkey.disabled', defaultMessage: '当前未开启通行密钥登录' }));
      return;
    }
    if (passkeyBinding || passkeyVerificationSubmitting) {
      return;
    }
    openPasskeyVerificationModal('bind');
  }, [openPasskeyVerificationModal, passkeyBinding, passkeyLoginAvailable, passkeyVerificationSubmitting]);
  const handleRenamePasskey = useCallback(
    async (id: number, currentLabel?: string) => {
      if (passkeyVerificationSubmitting) {
        return;
      }
      const label = window.prompt(formatMessage({ id: 'page.profile.passkey.renamePrompt', defaultMessage: '请输入通行密钥名称' }), currentLabel || '通行密钥');
      if (!label?.trim()) {
        return;
      }
      openPasskeyVerificationModal('rename', id, label.trim());
    },
    [openPasskeyVerificationModal, passkeyVerificationSubmitting],
  );
  const handleDeletePasskey = useCallback(
    async (id: number) => {
      if (passkeyVerificationSubmitting) {
        return;
      }
      openPasskeyVerificationModal('delete', id);
    },
    [openPasskeyVerificationModal, passkeyVerificationSubmitting],
  );
  const handleConfirmPasskeyVerification = useCallback(
    async (values: { currentPassword?: string; verificationCode?: string }) => {
      if (!passkeyVerificationAction) {
        return false;
      }
      const currentPassword = values.currentPassword?.trim();
      const verificationCode = values.verificationCode?.trim();
      if (!passkeyVerificationFactor && !currentPassword) {
        setPasskeyVerificationAlert(formatMessage({ id: 'page.profile.passkey.enterPassword', defaultMessage: 'Please enter your current password.' }));
        return false;
      }
      if (passkeyVerificationFactor) {
        if (!verificationCode) {
          setPasskeyVerificationAlert(formatMessage({ id: 'page.profile.passkey.enterCode', defaultMessage: 'Please enter the current verification code or a recovery code.' }));
          return false;
        }
        if (!passkeyVerificationChallenge?.challengeId) {
          setPasskeyVerificationAlert(formatMessage({ id: 'page.profile.passkey.challengeExpired', defaultMessage: 'Verification details have expired, please reload them and try again.' }));
          return false;
        }
      }
      setPasskeyVerificationSubmitting(true);
      setPasskeyVerificationAlert(null);
      const verificationPayload = passkeyVerificationFactor
        ? {
            currentFactorCode: passkeyVerificationFactor.factorCode,
            currentChallengeId: passkeyVerificationChallenge?.challengeId,
            currentVerificationCode: verificationCode,
          }
        : {
            currentPassword,
          };
      try {
        if (passkeyVerificationAction === 'bind') {
          setPasskeyBinding(true);
          const options = await request<PasskeyOptions>('/v1/auth/passkeys/registration/options', {
            method: 'POST',
            data: verificationPayload,
            ...API_OPTS.SILENT_NO_REDIRECT,
          });
          resetPasskeyVerificationState();
          const credential = await createPasskeyCredential(toPublicKeyCreationOptions(options));
          if (!credential) {
            return false;
          }
          await request<PasskeyCredentialRecord>('/v1/auth/passkeys/registration/complete', {
            method: 'POST',
            data: toRegistrationPayload(options.challengeId, credential as PublicKeyCredential),
            autoRedirectOnUnauthorized: false,
          });
          message.success(formatMessage({ id: 'page.profile.passkey.bound', defaultMessage: '通行密钥已绑定' }));
          await passkeyQuery.refetch();
          return true;
        }
        if (!passkeyVerificationTargetId) {
          setPasskeyVerificationAlert(formatMessage({ id: 'page.profile.passkey.targetMissing', defaultMessage: 'The selected passkey no longer exists. Please retry.' }));
          return false;
        }
        if (passkeyVerificationAction === 'rename') {
          if (!passkeyVerificationTargetLabel) {
            setPasskeyVerificationAlert(formatMessage({ id: 'page.profile.passkey.renameMissing', defaultMessage: 'The new passkey name is missing. Please retry.' }));
            return false;
          }
          await request<PasskeyCredentialRecord>(`/v1/auth/passkeys/${passkeyVerificationTargetId}`, {
            method: 'PATCH',
            data: {
              label: passkeyVerificationTargetLabel,
              ...verificationPayload,
            },
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(formatMessage({ id: 'page.profile.passkey.renamed', defaultMessage: '通行密钥已重命名' }));
          resetPasskeyVerificationState();
          await passkeyQuery.refetch();
          return true;
        }
        await request<boolean>(`/v1/auth/passkeys/${passkeyVerificationTargetId}`, {
          method: 'DELETE',
          data: verificationPayload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(formatMessage({ id: 'common.deleted', defaultMessage: '已删除' }));
        resetPasskeyVerificationState();
        await passkeyQuery.refetch();
        return true;
      } catch (error) {
        if (error instanceof DOMException && error.name === 'NotAllowedError') {
          message.info(formatMessage({ id: 'page.profile.passkey.cancelled', defaultMessage: '已取消通行密钥绑定' }));
          return false;
        }
        if (error instanceof DOMException && error.name === 'TimeoutError') {
          message.warning(formatMessage({ id: 'page.profile.passkey.timeout', defaultMessage: '通行密钥绑定超时，请重新尝试' }));
          return false;
        }
        setPasskeyVerificationAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.passkey.failed', defaultMessage: '通行密钥操作失败，请稍后重试' }));
        return false;
      } finally {
        setPasskeyVerificationSubmitting(false);
        setPasskeyBinding(false);
      }
    },
    [
      passkeyQuery,
      passkeyVerificationAction,
      passkeyVerificationChallenge?.challengeId,
      passkeyVerificationFactor,
      passkeyVerificationTargetId,
      passkeyVerificationTargetLabel,
      resetPasskeyVerificationState,
    ],
  );
  const profileSectionAccess = {
    profileBasicCardRef,
    avatarValue: avatarPreviewUrl ?? avatarValue,
    displayName,
    activeRoleName,
    loading: profileQuery.isLoading,
    hasVisibleProfileFields,
    profileSaving,
    profileFormProps,
    visibleProfileFields,
    visibleCustomProfileFields,
    visibleCustomProfileFieldKeys,
    avatarUploading,
    editingOpen: profileEditingOpen,
    onSave: handleSaveProfile,
    onEditOpenChange: handleProfileEditOpenChange,
    onAvatarBeforeCrop: handleAvatarBeforeCrop,
    onAvatarUploadRequest: handleAvatarUploadRequest,
    profileCompletionSummary,
    recentLoginLogs,
    setProfileEditingOpen,
  };

  return {
    responsive,
    profileForm,
    currentUser,
    recentLoginLogs,
    passkeys: passkeyQuery.data || [],
    loginMethodsLoading: profileQuery.isLoading || loginCapabilitiesQuery.isLoading || passkeyQuery.isLoading,
    providers: providersQuery.data || [],
    providersLoading: providersQuery.isLoading,
    profileSectionAccess,
    interactionAccess: {
      passkeyAccess: {
        loginMethods: loginMethodItems,
        passkeyBinding,
        passkeyVerificationOpen: passkeyVerificationAction !== null,
        passkeyVerificationAction,
        passkeyVerificationFactor,
        passkeyVerificationChallenge,
        passkeyVerificationChallengeLoading,
        passkeyVerificationSubmitting,
        passkeyVerificationAlert,
        passkeyVerificationFormProps,
        passkeyEnabled: passkeyLoginAvailable,
        onBindPasskey: handleBindPasskey,
        onRenamePasskey: handleRenamePasskey,
        onDeletePasskey: handleDeletePasskey,
        onClosePasskeyVerification: closePasskeyVerificationModal,
        onRetryPasskeyVerificationChallenge: requestPasskeyVerificationChallenge,
        onConfirmPasskeyVerification: handleConfirmPasskeyVerification,
      },
      securityAccess: {
        bindingLoading,
        bindingSubmitting,
        onBindProvider: openBindModal,
        onUnbindProvider: handleUnbind,
        unbindProvider,
        unbindChallenge,
        unbindChallengeLoading,
        unbindSubmitting,
        unbindAlert,
        unbindFormProps,
        closeUnbindModal,
        retryUnbindChallenge,
        handleConfirmUnbind,
        bindModalOpen,
        bindingProvider,
        bindingChallenge,
        bindingVerificationFactor,
        bindingVerificationChallenge,
        bindingVerificationChallengeLoading,
        bindVerificationFormProps,
        bindingCompleted,
        bindingAlert,
        closeBindModal,
        retryBindChallenge,
        onRetryBindVerificationChallenge: requestBindVerificationChallenge,
        onConfirmBindVerification: handleConfirmBindVerification,
        handleVerifyBind,
      },
      contactBindAccess: {
        contactBindType,
        contactBindOpen: contactBindType !== null,
        contactBindTitle:
          contactBindType === 'mobile'
            ? formatMessage({ id: 'page.profile.contact.bindMobile', defaultMessage: 'Bind mobile number' })
            : formatMessage({ id: 'page.profile.contact.bindEmail', defaultMessage: 'Bind email' }),
        contactBindDescription:
          contactBindType === 'mobile'
            ? contactBindVerificationRequired
              ? formatMessage({ id: 'page.profile.contact.description.mobile.required', defaultMessage: 'SMS verification is enabled, so you must request and enter a code to bind a mobile number.' })
              : formatMessage({ id: 'page.profile.contact.description.mobile.disabled', defaultMessage: 'SMS verification is not enabled, mobile binding is not allowed.' })
            : contactBindVerificationRequired
              ? formatMessage({ id: 'page.profile.contact.description.email.required', defaultMessage: 'Email verification is enabled, so you must request and enter a code to bind an email.' })
              : formatMessage({ id: 'page.profile.contact.description.email.disabled', defaultMessage: 'Email verification is not enabled, email binding is not allowed.' }),
        contactBindLabel:
          contactBindType === 'mobile'
            ? formatMessage({ id: 'page.profile.contact.label.mobile', defaultMessage: 'Mobile number' })
            : formatMessage({ id: 'page.profile.contact.label.email', defaultMessage: 'Email' }),
        contactBindPlaceholder:
          contactBindType === 'mobile'
            ? formatMessage({ id: 'page.profile.contact.placeholder.mobile', defaultMessage: 'Please enter your mobile number' })
            : formatMessage({ id: 'page.profile.contact.placeholder.email', defaultMessage: 'Please enter your email address' }),
        contactBindAutoComplete: contactBindType === 'mobile' ? 'tel' : 'email',
        contactBindInputMode: (contactBindType === 'mobile' ? 'tel' : 'email') as HTMLAttributes<HTMLInputElement>['inputMode'],
        contactBindOkText: contactBindVerificationRequired
          ? contactBindChallengeMatchesValue
            ? formatMessage({ id: 'page.profile.contact.confirm', defaultMessage: 'Confirm bind' })
            : formatMessage({ id: 'page.profile.contact.sendCode', defaultMessage: 'Send code' })
          : formatMessage({ id: 'page.profile.contact.save', defaultMessage: 'Save' }),
        contactBindAlert,
        contactBindCurrentFactor,
        contactBindCurrentChallenge,
        contactBindCurrentChallengeLoading,
        contactBindVerificationRequired,
        contactBindChallenge,
        contactBindSubmitting,
        contactBindChallengeLoading,
        contactBindFormProps,
        openContactBindModal,
        closeContactBindModal,
        retryContactBindCurrentChallenge: requestContactBindCurrentChallenge,
        handleContactBindConfirm,
      },
    },
  };
};
