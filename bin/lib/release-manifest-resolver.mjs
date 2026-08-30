import { lookup } from 'node:dns/promises';
import https from 'node:https';
import net from 'node:net';

import { assertReleaseId, verifyReleaseEnvelope } from './release-envelope.mjs';
import { normalizeReleaseManifest } from './platform-update-contract.mjs';

const allowedContentTypes = new Set(['application/json', 'application/octet-stream', 'application/vnd.lumira.release-envelope+json']);

export async function resolveSignedRelease({ releaseId, sourceUrl, allowedHosts, trustedKeys, allowedKeyIds, allowedChannels, maxManifestBytes, maxAgeSeconds, timeoutMs = 10_000, allowPrivateAddresses = false, transport = secureGet } = {}) {
  const normalizedReleaseId = assertReleaseId(releaseId);
  const url = releaseEnvelopeUrl(sourceUrl, normalizedReleaseId);
  const response = await transport(url, { allowedHosts, maxBytes: Number(maxManifestBytes || 512 * 1024) * 3, timeoutMs, allowPrivateAddresses });
  const verified = verifyReleaseEnvelope(response.body, { trustedKeys, allowedKeyIds, maxManifestBytes });
  const manifest = normalizeReleaseManifest(verified.manifest);
  validateResolvedManifest(manifest, { releaseId: normalizedReleaseId, allowedChannels, maxAgeSeconds });
  return { ...verified, manifest, sourceUrl: response.url };
}

export function releaseEnvelopeUrl(sourceUrl, releaseId) {
  const normalizedReleaseId = assertReleaseId(releaseId);
  const template = String(sourceUrl || '').trim();
  if (!template) throw new Error('release source URL is not configured');
  const rendered = template.includes('{releaseId}')
    ? template.replaceAll('{releaseId}', encodeURIComponent(normalizedReleaseId))
    : `${template.replace(/\/+$/u, '')}/${encodeURIComponent(normalizedReleaseId)}.envelope.json`;
  return new URL(rendered).toString();
}

export function validateResolvedManifest(manifest, { releaseId, allowedChannels, maxAgeSeconds, now = Date.now() } = {}) {
  if (manifest.schemaVersion !== 3) throw new Error('production release manifest must use schemaVersion 3');
  if (manifest.releaseId !== releaseId) throw new Error('resolved releaseId does not match the requested release');
  if (manifest.app !== 'lumira') throw new Error('release manifest app must be lumira');
  const channels = normalizeSet(allowedChannels);
  if (channels.size > 0 && !channels.has(manifest.channel)) throw new Error(`release channel is not allowed: ${manifest.channel}`);
  const releasedAt = Date.parse(manifest.releasedAt);
  if (!Number.isFinite(releasedAt) || releasedAt > now + 5 * 60_000) throw new Error('release manifest releasedAt is invalid');
  const maximumAge = Number(maxAgeSeconds || 0);
  if (maximumAge > 0 && now - releasedAt > maximumAge * 1000) throw new Error('release manifest is older than the configured maximum age');
  if (manifest.expiresAt) {
    const expiresAt = Date.parse(manifest.expiresAt);
    if (!Number.isFinite(expiresAt) || expiresAt <= now) throw new Error('release manifest has expired');
  }
  return manifest;
}

export async function secureGet(urlValue, { allowedHosts, maxBytes = 1_572_864, timeoutMs = 10_000, allowPrivateAddresses = false, redirectCount = 0, originalHost } = {}) {
  const url = validateSourceUrl(urlValue, allowedHosts);
  const initialHost = originalHost || url.hostname.toLowerCase();
  if (url.hostname.toLowerCase() !== initialHost) throw new Error('cross-host release source redirect is forbidden');
  if (redirectCount > 3) throw new Error('release source redirected too many times');
  const addresses = await lookup(url.hostname, { all: true, verbatim: true });
  if (addresses.length === 0) throw new Error('release source host did not resolve');
  if (!allowPrivateAddresses && addresses.some((entry) => !isPublicAddress(entry.address))) throw new Error('release source resolved to a private or reserved address');
  const selected = addresses[0];
  return new Promise((resolve, reject) => {
    const request = https.get(url, {
      headers: { Accept: 'application/vnd.lumira.release-envelope+json, application/json', 'User-Agent': 'lumira-updater-v3' },
      lookup: createPinnedLookup(selected),
    }, (response) => {
      const status = response.statusCode || 0;
      const location = response.headers.location;
      if (status >= 300 && status < 400 && location) {
        response.resume();
        resolve(secureGet(new URL(location, url).toString(), { allowedHosts, maxBytes, timeoutMs, allowPrivateAddresses, redirectCount: redirectCount + 1, originalHost: initialHost }));
        return;
      }
      if (status < 200 || status >= 300) {
        response.resume();
        reject(new Error(`release source returned HTTP ${status}`));
        return;
      }
      const contentType = String(response.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
      if (!allowedContentTypes.has(contentType)) {
        response.resume();
        reject(new Error(`release source content-type is not allowed: ${contentType || '<missing>'}`));
        return;
      }
      const declaredLength = Number(response.headers['content-length'] || 0);
      if (declaredLength > maxBytes) {
        response.destroy();
        reject(new Error('release source response is too large'));
        return;
      }
      const chunks = [];
      let size = 0;
      response.on('data', (chunk) => {
        size += chunk.length;
        if (size > maxBytes) {
          response.destroy(new Error('release source response is too large'));
          return;
        }
        chunks.push(chunk);
      });
      response.on('end', () => resolve({ url: url.toString(), status, body: Buffer.concat(chunks).toString('utf8') }));
      response.on('error', reject);
    });
    request.setTimeout(timeoutMs, () => request.destroy(new Error('release source request timed out')));
    request.on('error', reject);
  });
}

export function createPinnedLookup(selected) {
  const address = String(selected?.address || '');
  const family = Number(selected?.family || net.isIP(address));
  if (!net.isIP(address) || ![4, 6].includes(family)) throw new Error('release source resolved to an invalid IP address');
  return (_hostname, options, callback) => {
    if (options && typeof options === 'object' && options.all === true) {
      callback(null, [{ address, family }]);
      return;
    }
    callback(null, address, family);
  };
}

export function validateSourceUrl(urlValue, allowedHosts) {
  const url = new URL(String(urlValue || ''));
  if (url.protocol !== 'https:') throw new Error('release source must use HTTPS');
  if (url.username || url.password) throw new Error('release source URL credentials are forbidden');
  if (url.port && url.port !== '443') throw new Error('release source must use the standard HTTPS port');
  const hosts = normalizeSet(allowedHosts);
  if (hosts.size === 0 || !hosts.has(url.hostname.toLowerCase())) throw new Error(`release source host is not allowed: ${url.hostname}`);
  return url;
}

function normalizeSet(value) {
  if (value instanceof Set) return new Set([...value].map((item) => String(item).trim().toLowerCase()).filter(Boolean));
  if (Array.isArray(value)) return new Set(value.map((item) => String(item).trim().toLowerCase()).filter(Boolean));
  return new Set(String(value || '').split(',').map((item) => item.trim().toLowerCase()).filter(Boolean));
}

function isPublicAddress(address) {
  if (!net.isIP(address)) return false;
  if (address.includes(':')) {
    const normalized = address.toLowerCase();
    return normalized !== '::1' && !normalized.startsWith('fc') && !normalized.startsWith('fd') && !normalized.startsWith('fe8') && !normalized.startsWith('fe9') && !normalized.startsWith('fea') && !normalized.startsWith('feb') && !normalized.startsWith('2001:db8:');
  }
  const [a, b] = address.split('.').map(Number);
  return !(a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || a >= 224 || (a === 100 && b >= 64 && b <= 127) || (a === 192 && b === 0) || (a === 198 && (b === 18 || b === 19)));
}
