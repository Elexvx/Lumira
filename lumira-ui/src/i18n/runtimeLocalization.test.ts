import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  addLocale: vi.fn(),
  request: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  addLocale: mocks.addLocale,
}));

vi.mock('@/services/common/request', () => ({
  request: mocks.request,
}));

describe('loadRuntimeLocalizationBundle', () => {
  beforeEach(async () => {
    mocks.addLocale.mockReset();
    mocks.request.mockReset();
    const { clearRuntimeLocalizationBundleCache } = await import('./runtimeLocalization');
    clearRuntimeLocalizationBundleCache();
  });

  it('deduplicates concurrent loads and caches completed bundles per locale', async () => {
    mocks.request.mockResolvedValue({
      localeCode: 'zh-CN',
      messages: {
        'common.ok': '确定',
      },
    });

    const { loadRuntimeLocalizationBundle } = await import('./runtimeLocalization');
    const [first, second] = await Promise.all([
      loadRuntimeLocalizationBundle('zh-CN'),
      loadRuntimeLocalizationBundle('zh-CN'),
    ]);
    const third = await loadRuntimeLocalizationBundle('zh-CN');

    expect(first).toBe(second);
    expect(third).toBe(first);
    expect(mocks.request).toHaveBeenCalledTimes(1);
    expect(mocks.addLocale).toHaveBeenCalledTimes(1);
  });
});
