import { afterEach, describe, expect, it, vi } from 'vitest';
import { createLoginSessionBroadcastListener } from '@/auth/loginRedirect';
import { beginLoginFlow, endLoginFlow } from '@/auth/loginFlowState';

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = [];
  onmessage: ((event: MessageEvent<{ type?: string }>) => void) | null = null;

  constructor(public name: string) {
    FakeBroadcastChannel.instances.push(this);
  }

  close() {
    FakeBroadcastChannel.instances = FakeBroadcastChannel.instances.filter((instance) => instance !== this);
  }
}

const originalBroadcastChannel = globalThis.BroadcastChannel;

afterEach(() => {
  endLoginFlow();
  FakeBroadcastChannel.instances = [];
  vi.unstubAllGlobals();
  if (originalBroadcastChannel) {
    vi.stubGlobal('BroadcastChannel', originalBroadcastChannel);
  }
});

describe('login session broadcast listener', () => {
  it('does not reload the current tab while login flow is still deciding the next step', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate);
    beginLoginFlow();
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('navigates on session updates outside the active login flow', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate);
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).toHaveBeenCalledWith('/dashboard/home');
  });

  it('does not navigate when the caller suppresses login broadcast redirects', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const onNavigate = vi.fn();

    createLoginSessionBroadcastListener('/dashboard/home', onNavigate, () => false);
    FakeBroadcastChannel.instances[0].onmessage?.({ data: { type: 'updated' } } as MessageEvent<{ type?: string }>);

    expect(onNavigate).not.toHaveBeenCalled();
  });
});
