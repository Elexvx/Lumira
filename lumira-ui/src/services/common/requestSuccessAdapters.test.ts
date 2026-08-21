import { describe, expect, it, vi } from 'vitest';
import { adaptRequestSuccessData, registerRequestSuccessAdapter } from './requestSuccessAdapters';

describe('request success adapters', () => {
  it('adapts the existing response without replacing it', () => {
    const adapter = vi.fn();
    const unregister = registerRequestSuccessAdapter(adapter);
    const response = { challengeId: 'challenge-1' };

    adaptRequestSuccessData(response);

    expect(adapter).toHaveBeenCalledWith(response);
    unregister();
  });

  it('isolates adapter presentation failures from other adapters', () => {
    const healthyAdapter = vi.fn();
    const unregisterBroken = registerRequestSuccessAdapter(() => {
      throw new Error('presentation failed');
    });
    const unregisterHealthy = registerRequestSuccessAdapter(healthyAdapter);

    expect(() => adaptRequestSuccessData({ challengeId: 'challenge-2' })).not.toThrow();
    expect(healthyAdapter).toHaveBeenCalledOnce();

    unregisterBroken();
    unregisterHealthy();
  });
});
