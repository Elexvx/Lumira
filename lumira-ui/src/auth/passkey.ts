import type { PasskeyOptions } from '@/types/api';

export interface PasskeyRegistrationCompletePayload {
  challengeId: string;
  id: string;
  rawId: string;
  type: string;
  response: {
    clientDataJSON: string;
    attestationObject: string;
  };
  authenticatorAttachment?: string | null;
  transports?: string[];
  label?: string;
}

export interface PasskeyAuthenticationCompletePayload {
  challengeId: string;
  id: string;
  rawId: string;
  type: string;
  response: {
    clientDataJSON: string;
    authenticatorData: string;
    signature: string;
    userHandle?: string | null;
  };
  authenticatorAttachment?: string | null;
}

const PASSKEY_OPERATION_TIMEOUT_MS = 60_000;

const base64UrlToBuffer = (value: string): ArrayBuffer => {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  const binary = window.atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
};

const bufferToBase64Url = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return window.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
};

const credentialDescriptors = (value: unknown): PublicKeyCredentialDescriptor[] | undefined => {
  if (!Array.isArray(value)) {
    return undefined;
  }
  return value.map((item) => {
    const descriptor = item as { id: string; type: PublicKeyCredentialType; transports?: AuthenticatorTransport[] };
    return {
      ...descriptor,
      id: base64UrlToBuffer(descriptor.id),
    };
  });
};

export const isPasskeySupported = () =>
  typeof window !== 'undefined' && Boolean(window.PublicKeyCredential && navigator.credentials);

const buildPasskeyTimeoutError = () => new DOMException('Passkey operation timed out', 'TimeoutError');

export const createPasskeyCredential = async (
  publicKey: PublicKeyCredentialCreationOptions,
  timeoutMs = PASSKEY_OPERATION_TIMEOUT_MS,
): Promise<PublicKeyCredential | null> => {
  const controller = new AbortController();
  let timeoutError: DOMException | undefined;
  let timeoutId: ReturnType<typeof globalThis.setTimeout> | undefined;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutId = globalThis.setTimeout(() => {
      timeoutError = buildPasskeyTimeoutError();
      controller.abort(timeoutError);
      reject(timeoutError);
    }, timeoutMs);
  });
  const createPromise = navigator.credentials.create({
    publicKey,
    signal: controller.signal,
  } as CredentialCreationOptions);

  try {
    const credential = await Promise.race([createPromise, timeoutPromise]);
    return credential as PublicKeyCredential | null;
  } catch (error) {
    if (
      timeoutError
      && error instanceof DOMException
      && (error.name === 'AbortError' || error.name === 'TimeoutError')
    ) {
      throw timeoutError;
    }
    throw error;
  } finally {
    if (timeoutId !== undefined) {
      globalThis.clearTimeout(timeoutId);
    }
  }
};

export const toPublicKeyCreationOptions = (options: PasskeyOptions): PublicKeyCredentialCreationOptions => {
  const publicKey = options.publicKey as Record<string, unknown>;
  const user = publicKey.user as { id: string; name: string; displayName: string };
  return {
    ...publicKey,
    challenge: base64UrlToBuffer(publicKey.challenge as string),
    user: {
      ...user,
      id: base64UrlToBuffer(user.id),
    },
    excludeCredentials: credentialDescriptors(publicKey.excludeCredentials),
  } as PublicKeyCredentialCreationOptions;
};

export const toPublicKeyRequestOptions = (options: PasskeyOptions): PublicKeyCredentialRequestOptions => {
  const publicKey = options.publicKey as Record<string, unknown>;
  return {
    ...publicKey,
    challenge: base64UrlToBuffer(publicKey.challenge as string),
    allowCredentials: credentialDescriptors(publicKey.allowCredentials),
  } as PublicKeyCredentialRequestOptions;
};

export const toRegistrationPayload = (
  challengeId: string,
  credential: PublicKeyCredential,
  label?: string,
): PasskeyRegistrationCompletePayload => {
  const response = credential.response as AuthenticatorAttestationResponse;
  return {
    challengeId,
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      attestationObject: bufferToBase64Url(response.attestationObject),
    },
    authenticatorAttachment: credential.authenticatorAttachment,
    transports: response.getTransports?.() || [],
    label,
  };
};

export const toAuthenticationPayload = (
  challengeId: string,
  credential: PublicKeyCredential,
): PasskeyAuthenticationCompletePayload => {
  const response = credential.response as AuthenticatorAssertionResponse;
  return {
    challengeId,
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      authenticatorData: bufferToBase64Url(response.authenticatorData),
      signature: bufferToBase64Url(response.signature),
      userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : undefined,
    },
    authenticatorAttachment: credential.authenticatorAttachment,
  };
};
