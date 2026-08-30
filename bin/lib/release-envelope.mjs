import { createHash, createPrivateKey, createPublicKey, sign, timingSafeEqual, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';

export const RELEASE_ENVELOPE_VERSION = 1;
export const RELEASE_SIGNATURE_ALGORITHM = 'Ed25519';
export const DEFAULT_MAX_MANIFEST_BYTES = 512 * 1024;

const releaseIdPattern = /^v[A-Za-z0-9][A-Za-z0-9._-]{0,126}$/u;
const keyIdPattern = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/u;
const sha256Pattern = /^[0-9a-f]{64}$/u;

export function assertReleaseId(value) {
  const releaseId = String(value || '').trim();
  if (!releaseIdPattern.test(releaseId)) throw new Error('releaseId is invalid');
  return releaseId;
}

export function createReleaseEnvelope(payload, { keyId, privateKey } = {}) {
  const normalizedKeyId = String(keyId || '').trim();
  if (!keyIdPattern.test(normalizedKeyId)) throw new Error('release signing keyId is invalid');
  const payloadBytes = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload), 'utf8');
  const signingKey = privateKey?.type === 'private' ? privateKey : createPrivateKey(privateKey);
  if (signingKey.asymmetricKeyType !== 'ed25519') throw new Error('release signing private key must be Ed25519');
  return {
    envelopeVersion: RELEASE_ENVELOPE_VERSION,
    keyId: normalizedKeyId,
    algorithm: RELEASE_SIGNATURE_ALGORITHM,
    payloadSha256: createHash('sha256').update(payloadBytes).digest('hex'),
    payload: payloadBytes.toString('base64url'),
    signature: sign(null, payloadBytes, signingKey).toString('base64url'),
  };
}

export function verifyReleaseEnvelope(rawEnvelope, options = {}) {
  const envelope = typeof rawEnvelope === 'string' ? parseJson(rawEnvelope, 'release envelope') : rawEnvelope;
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) throw new Error('release envelope must be an object');
  if (Number(envelope.envelopeVersion) !== RELEASE_ENVELOPE_VERSION) throw new Error('release envelopeVersion is not supported');
  if (envelope.algorithm !== RELEASE_SIGNATURE_ALGORITHM) throw new Error('release envelope algorithm is not allowed');
  const keyId = String(envelope.keyId || '').trim();
  if (!keyIdPattern.test(keyId)) throw new Error('release envelope keyId is invalid');
  const allowedKeyIds = normalizeStringSet(options.allowedKeyIds);
  if (allowedKeyIds.size > 0 && !allowedKeyIds.has(keyId)) throw new Error(`release envelope keyId is not trusted: ${keyId}`);
  if (!sha256Pattern.test(String(envelope.payloadSha256 || ''))) throw new Error('release envelope payloadSha256 is invalid');
  const payload = decodeBase64Url(envelope.payload, 'payload');
  const maximumBytes = boundedPositiveInteger(options.maxManifestBytes, DEFAULT_MAX_MANIFEST_BYTES);
  if (payload.length === 0 || payload.length > maximumBytes) throw new Error(`release manifest payload exceeds the ${maximumBytes} byte limit`);
  const expectedDigest = Buffer.from(envelope.payloadSha256, 'hex');
  const actualDigest = createHash('sha256').update(payload).digest();
  if (!timingSafeEqual(expectedDigest, actualDigest)) throw new Error('release envelope payload digest does not match');
  const publicKeyValue = options.trustedKeys instanceof Map ? options.trustedKeys.get(keyId) : options.trustedKeys?.[keyId];
  if (!publicKeyValue) throw new Error(`trusted public key is unavailable for keyId ${keyId}`);
  const publicKey = publicKeyValue?.type === 'public' ? publicKeyValue : createPublicKey(publicKeyValue);
  if (publicKey.asymmetricKeyType !== 'ed25519') throw new Error('trusted release public key must be Ed25519');
  const signature = decodeBase64Url(envelope.signature, 'signature');
  if (!verify(null, payload, publicKey, signature)) throw new Error('release envelope signature verification failed');
  const manifest = parseJson(payload.toString('utf8'), 'release manifest payload');
  return { envelope, payload, manifest, manifestDigest: actualDigest.toString('hex'), keyId };
}

export function loadTrustedPublicKeys(file) {
  const source = readFileSync(file, 'utf8');
  const root = parseJson(source, 'trusted release public keys file');
  const rawKeys = root.keys && typeof root.keys === 'object' ? root.keys : root;
  const result = new Map();
  if (Array.isArray(rawKeys)) {
    for (const entry of rawKeys) addTrustedKey(result, entry?.keyId, entry?.publicKey || entry?.pem);
  } else if (rawKeys && typeof rawKeys === 'object') {
    for (const [keyId, publicKey] of Object.entries(rawKeys)) addTrustedKey(result, keyId, publicKey);
  }
  if (result.size === 0) throw new Error('trusted release public keys file does not contain any keys');
  return result;
}

function addTrustedKey(result, keyIdValue, publicKeyValue) {
  const keyId = String(keyIdValue || '').trim();
  if (!keyIdPattern.test(keyId)) throw new Error('trusted release public key id is invalid');
  const publicKey = publicKeyValue?.type === 'public' ? publicKeyValue : createPublicKey(publicKeyValue);
  if (publicKey.asymmetricKeyType !== 'ed25519') throw new Error(`trusted release public key ${keyId} must be Ed25519`);
  if (result.has(keyId)) throw new Error(`duplicate trusted release public key id: ${keyId}`);
  result.set(keyId, publicKey);
}

function decodeBase64Url(value, fieldName) {
  const text = String(value || '');
  if (!/^[A-Za-z0-9_-]+$/u.test(text)) throw new Error(`release envelope ${fieldName} is not valid base64url`);
  const decoded = Buffer.from(text, 'base64url');
  if (decoded.length === 0 || decoded.toString('base64url') !== text.replace(/=+$/u, '')) throw new Error(`release envelope ${fieldName} is not canonical base64url`);
  return decoded;
}

function parseJson(value, label) {
  try {
    return JSON.parse(value);
  } catch (error) {
    throw new Error(`${label} is invalid JSON`, { cause: error });
  }
}

function normalizeStringSet(value) {
  if (value instanceof Set) return new Set([...value].map(String).map((item) => item.trim()).filter(Boolean));
  if (Array.isArray(value)) return new Set(value.map(String).map((item) => item.trim()).filter(Boolean));
  return new Set(String(value || '').split(',').map((item) => item.trim()).filter(Boolean));
}

function boundedPositiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}
