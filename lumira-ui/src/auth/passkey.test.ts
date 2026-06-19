import { afterEach, describe, expect, it, vi } from 'vitest';
import { createPasskeyCredential } from './passkey';

describe('createPasskeyCredential', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('rejects with TimeoutError when the browser credential request does not settle', async () => {
    vi.useFakeTimers();
    const abortSpy = vi.fn();
    vi.stubGlobal('AbortController', class {
      signal = {};
      abort = abortSpy;
    });
    vi.stubGlobal('navigator', {
      credentials: {
        create: vi.fn(() => new Promise(() => undefined)),
      },
    });

    const assertion = expect(
      createPasskeyCredential({ challenge: new ArrayBuffer(0), rp: { name: 'Lumira' }, user: { id: new ArrayBuffer(0), name: 'admin', displayName: 'Admin' }, pubKeyCredParams: [] }, 25),
    ).rejects.toMatchObject({ name: 'TimeoutError' });

    await vi.advanceTimersByTimeAsync(25);

    await assertion;
    expect(abortSpy).toHaveBeenCalledWith(expect.objectContaining({ name: 'TimeoutError' }));
  });
});
