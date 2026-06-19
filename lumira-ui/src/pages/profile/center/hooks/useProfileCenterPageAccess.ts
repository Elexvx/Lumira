import { formatMessage } from '@umijs/max';
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
  SecondFactorProviderStatus,
  SecondFactorVerification,
  PasskeyCredentialRecord,
  PasskeyOptions,
} from '@/types/api';

interface ProfileBasicInfoPayload {
  avatarUrl?: string;
  nickname?: string;
  realName?: string;
  mobile?: string;
  email?: string;
  birthMonth?: string;
  gender?: string;
  region?: string;
  idCardNumber?: string;
  extraProfileValues?: Record<string, string>;
}

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
    queryKey: ['profile-summary', initialState?.currentUser?.userId],
    queryFn: async () =>
      request<ProfileSummary>('/v1/profile/summary', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
  });
  const summary = profileQuery.data;
  const currentUser = useMemo(
    () => normalizeCurrentUserText(summary?.currentUser || initialState?.currentUser),
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
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingProvider, setBindingProvider] = useState<SecondFactorProviderStatus | null>(null);
  const [bindingChallenge, setBindingChallenge] = useState<SecondFactorChallenge | null>(null);
  const [bindingLoading, setBindingLoading] = useState(false);
  const [bindingSubmitting, setBindingSubmitting] = useState(false);
  const [bindingCompleted, setBindingCompleted] = useState(false);
  const [bindingAlert, setBindingAlert] = useState<{ type: 'info' | 'warning' | 'error'; message: string }>();
  const resetBindState = useCallback(() => {
    setBindingProvider(null);
    setBindingChallenge(null);
    setBindingLoading(false);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
  }, []);
  const closeBindModal = useCallback(() => {
    if (bindingSubmitting) {
      return;
    }
    setBindModalOpen(false);
    window.setTimeout(() => {
      resetBindState();
    }, 0);
  }, [bindingSubmitting, resetBindState]);
  const openBindModal = useCallback(async (provider: SecondFactorProviderStatus) => {
    setBindingProvider(provider);
    setBindingChallenge(null);
    setBindingLoading(true);
    setBindingSubmitting(false);
    setBindingCompleted(false);
    setBindingAlert(undefined);
    setBindModalOpen(true);
    try {
      const challenge = await request<SecondFactorChallenge>(`/v1/auth/verification/providers/${provider.factorCode}/bind`, {
        method: 'POST',
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      setBindingChallenge(challenge);
    } catch (error) {
      setBindingAlert({
        type: 'error',
        message: error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.fetchFailed', defaultMessage: 'Failed to load binding info, please try again later' }),
      });
    } finally {
      setBindingLoading(false);
    }
  }, []);
  const retryBindChallenge = useCallback(async () => {
    if (!bindingProvider) {
      return;
    }
    setBindingChallenge(null);
    await openBindModal(bindingProvider);
  }, [bindingProvider, openBindModal]);
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
        try {
          await request<boolean>(`/v1/auth/verification/providers/${provider.factorCode}/unbind`, {
            method: 'POST',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(formatMessage({ id: 'page.profile.bind.unbound', defaultMessage: 'Unbound' }));
          await providersQuery.refetch();
        } catch (error) {
          showErrorMessage(error, formatMessage({ id: 'page.profile.bind.unbindFailed', defaultMessage: 'Failed to unbind, please try again later' }));
        }
      })();
    },
    [providersQuery],
  );

  type ContactBindType = 'mobile' | 'email' | null;
  const [contactBindType, setContactBindType] = useState<ContactBindType>(null);
  const [contactBindChallenge, setContactBindChallenge] = useState<SecondFactorChallenge | null>(null);
  const [contactBindChallengeTarget, setContactBindChallengeTarget] = useState<string | null>(null);
  const [contactBindChallengeLoading, setContactBindChallengeLoading] = useState(false);
  const [contactBindSubmitting, setContactBindSubmitting] = useState(false);
  const [contactBindAlert, setContactBindAlert] = useState<string | null>(null);
  const [contactBindForm] = Form.useForm<{ value?: string; verificationCode?: string }>();
  const contactBindValue = Form.useWatch('value', contactBindForm);
  const contactBindFormProps = useStandardFormProps({
    form: contactBindForm,
    initialValues: { value: '' },
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
      setContactBindChallengeLoading(false);
      setContactBindSubmitting(false);
      setContactBindAlert(null);
      contactBindForm.setFieldsValue({
        value: type === 'mobile' ? currentUser?.mobile || '' : currentUser?.email || '',
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
    if (contactBindSubmitting || contactBindChallengeLoading) {
      return;
    }
    setContactBindType(null);
    setContactBindChallenge(null);
    setContactBindChallengeTarget(null);
    setContactBindAlert(null);
    setContactBindChallengeLoading(false);
    contactBindForm.resetFields();
  }, [contactBindChallengeLoading, contactBindForm, contactBindSubmitting]);

  const requestContactBindChallenge = useCallback(
    async (nextValue: string) => {
      if (!contactBindType) {
        return false;
      }

      setContactBindChallengeLoading(true);
      try {
        const challenge = await request<SecondFactorChallenge>('/v1/profile/contact-bind/challenge', {
          method: 'POST',
          data: {
            contactType: contactBindType,
            value: nextValue,
          },
          ...API_OPTS.SILENT_NO_REDIRECT,
        });
        setContactBindChallenge(challenge);
        setContactBindChallengeTarget(nextValue);
        contactBindForm.setFieldsValue({ verificationCode: undefined });
        message.success(formatMessage({ id: 'page.profile.bind.codeSent', defaultMessage: 'Verification code sent, please enter it to continue' }));
        return true;
      } catch (error) {
        setContactBindAlert(error instanceof Error ? error.message : formatMessage({ id: 'page.profile.bind.sendFailed', defaultMessage: 'Failed to send verification code, please try again later' }));
        return false;
      } finally {
        setContactBindChallengeLoading(false);
      }
    },
    [contactBindForm, contactBindType],
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
              currentUser: updatedUser,
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
  const profileBasicCardRef = useRef<HTMLDivElement | null>(null);
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
      birthMonth: currentUser.birthMonth ? dayjs(currentUser.birthMonth, 'YYYY-MM') : null,
      gender: currentUser.gender || undefined,
      region: currentUser.region || '',
      idCardNumber: currentUser.idCardNumber || '',
      extraProfileValues: Object.fromEntries(
        visibleCustomProfileFields.map((item) => [item.fieldKey, currentUser.extraProfileValues?.[item.fieldKey] || '']),
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
          mobile: currentUser?.mobile || '',
          email: currentUser?.email || '',
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
              values.extraProfileValues?.[item.fieldKey] ?? currentUser?.extraProfileValues?.[item.fieldKey] ?? '',
            ]),
          ),
        } satisfies ProfileBasicInfoPayload,
        ...API_OPTS.NO_REDIRECT,
      }));
      setInitialState((prev: AppInitialState | undefined) =>
        prev
          ? {
              ...prev,
              currentUser: updatedUser,
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
    if (passkeyBinding) {
      return;
    }
    setPasskeyBinding(true);
    try {
      const options = await request<PasskeyOptions>('/v1/auth/passkeys/registration/options', {
        method: 'POST',
        ...API_OPTS.SILENT_NO_REDIRECT,
      });
      const credential = await createPasskeyCredential(toPublicKeyCreationOptions(options));
      if (!credential) {
        return;
      }
      await request<PasskeyCredentialRecord>('/v1/auth/passkeys/registration/complete', {
        method: 'POST',
        data: toRegistrationPayload(options.challengeId, credential as PublicKeyCredential),
        autoRedirectOnUnauthorized: false,
      });
      message.success(formatMessage({ id: 'page.profile.passkey.bound', defaultMessage: '通行密钥已绑定' }));
      await passkeyQuery.refetch();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'NotAllowedError') {
        message.info(formatMessage({ id: 'page.profile.passkey.cancelled', defaultMessage: '已取消通行密钥绑定' }));
        return;
      }
      if (error instanceof DOMException && error.name === 'TimeoutError') {
        message.warning(formatMessage({ id: 'page.profile.passkey.timeout', defaultMessage: '通行密钥绑定超时，请重新尝试' }));
        return;
      }
      showErrorMessage(error, formatMessage({ id: 'page.profile.passkey.failed', defaultMessage: '通行密钥绑定失败' }));
    } finally {
      setPasskeyBinding(false);
    }
  }, [passkeyBinding, passkeyLoginAvailable, passkeyQuery]);
  const handleRenamePasskey = useCallback(
    async (id: number, currentLabel?: string) => {
      const label = window.prompt(formatMessage({ id: 'page.profile.passkey.renamePrompt', defaultMessage: '请输入通行密钥名称' }), currentLabel || '通行密钥');
      if (!label?.trim()) {
        return;
      }
      await request<PasskeyCredentialRecord>(`/v1/auth/passkeys/${id}`, {
        method: 'PATCH',
        data: { label: label.trim() },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(formatMessage({ id: 'page.profile.passkey.renamed', defaultMessage: '通行密钥已重命名' }));
      await passkeyQuery.refetch();
    },
    [passkeyQuery],
  );
  const handleDeletePasskey = useCallback(
    async (id: number) => {
      await request<boolean>(`/v1/auth/passkeys/${id}`, {
        method: 'DELETE',
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(formatMessage({ id: 'common.deleted', defaultMessage: '已删除' }));
      await passkeyQuery.refetch();
    },
    [passkeyQuery],
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
        passkeyEnabled: passkeyLoginAvailable,
        onBindPasskey: handleBindPasskey,
        onRenamePasskey: handleRenamePasskey,
        onDeletePasskey: handleDeletePasskey,
      },
      securityAccess: {
        bindingLoading,
        bindingSubmitting,
        onBindProvider: openBindModal,
        onUnbindProvider: handleUnbind,
        bindModalOpen,
        bindingProvider,
        bindingChallenge,
        bindingCompleted,
        bindingAlert,
        closeBindModal,
        retryBindChallenge,
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
        contactBindVerificationRequired,
        contactBindChallenge,
        contactBindSubmitting,
        contactBindChallengeLoading,
        contactBindFormProps,
        openContactBindModal,
        closeContactBindModal,
        handleContactBindConfirm,
      },
    },
  };
};
