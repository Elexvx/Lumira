import { sleep } from './http-utils.mjs';

export class RuntimeDrainClient {
  constructor({ baseUrl, token, requestTimeoutMs = 5_000 } = {}) {
    this.baseUrl = String(baseUrl || '').replace(/\/+$/u, '');
    this.token = String(token || '');
    this.requestTimeoutMs = requestTimeoutMs;
    if (!/^http:\/\/(?:127\.0\.0\.1|\[::1\]|(?:10|192\.168|172\.(?:1[6-9]|2\d|3[01]))\.)/u.test(this.baseUrl)) throw new Error('runtime control endpoint must use an internal or loopback HTTP address');
    if (this.token.length < 24) throw new Error('LUMIRA_RUNTIME_CONTROL_TOKEN must contain at least 24 characters');
  }

  quiesce() { return this.request('/quiesce', 'POST'); }
  resume() { return this.request('/resume', 'POST'); }
  status() { return this.request('/drain-status'); }
  version() { return this.request('/version'); }
  health() { return this.request('/health'); }

  async waitUntilDrained(timeoutSeconds) {
    const deadline = Date.now() + Math.max(1, Number(timeoutSeconds || 120)) * 1000;
    let lastStatus;
    while (Date.now() < deadline) {
      lastStatus = await this.status();
      if (lastStatus.acceptingNewWork !== false) throw new Error('runtime did not enter quiesced state');
      if (lastStatus.safeToStop === true && Number(lastStatus.inflightTasks || 0) === 0) return lastStatus;
      await sleep(1_000);
    }
    throw new Error(`runtime drain timed out with ${Number(lastStatus?.inflightTasks || 0)} in-flight tasks`);
  }

  async verify({ serviceName, releaseId, commit, event } = {}) {
    const [health, version] = await Promise.all([this.health(), this.version()]);
    if (health.status !== 'UP') throw new Error(`${serviceName} runtime health is not UP`);
    if (version.serviceName !== serviceName) throw new Error(`runtime identity mismatch: expected ${serviceName}, observed ${version.serviceName || 'unknown'}`);
    if (releaseId && version.releaseId !== releaseId) throw new Error(`${serviceName} releaseId mismatch`);
    if (commit && !(String(version.commit || '').startsWith(commit) || commit.startsWith(String(version.commit || '')))) throw new Error(`${serviceName} commit mismatch`);
    if (event && (Number(version.eventReadMin) !== Number(event.readMin) || Number(version.eventReadMax) !== Number(event.readMax) || Number(version.eventWriteVersion) !== Number(event.writeVersion))) throw new Error(`${serviceName} Event Schema compatibility identity mismatch`);
    return { health, version };
  }

  async request(pathname, method = 'GET') {
    const response = await fetch(`${this.baseUrl}/internal/runtime${pathname}`, {
      method,
      redirect: 'error',
      signal: AbortSignal.timeout(this.requestTimeoutMs),
      headers: { Accept: 'application/json', 'X-Lumira-Runtime-Control-Token': this.token },
    });
    if (!response.ok) throw new Error(`runtime control ${method} ${pathname} returned HTTP ${response.status}`);
    const declaredLength = Number(response.headers.get('content-length') || 0);
    if (declaredLength > 64 * 1024) throw new Error('runtime control response is too large');
    const text = await response.text();
    if (Buffer.byteLength(text) > 64 * 1024) throw new Error('runtime control response is too large');
    return JSON.parse(text);
  }
}
