import type { LoginEncryptionKey } from '@/types/api';

const TEXT_ENCODER = new TextEncoder();
const KEY_CACHE = new Map<string, Promise<CryptoKey>>();

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
    throw new Error('当前浏览器不支持登录加密，请升级浏览器后重试');
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

export const encryptLoginPassword = async (password: string, key: LoginEncryptionKey) => {
  const publicKey = await importLoginPublicKey(key);
  const encrypted = await window.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, TEXT_ENCODER.encode(password));
  return arrayBufferToBase64(encrypted);
};
