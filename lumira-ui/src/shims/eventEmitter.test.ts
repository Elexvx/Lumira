import { describe, expect, it, vi } from 'vitest';
import EventEmitter from './eventEmitter';

describe('EventEmitter', () => {
  it('supports the locale plugin on/off/emit lifecycle', () => {
    const emitter = new EventEmitter();
    const listener = vi.fn();

    emitter.on('locale-change', listener);
    emitter.emit('locale-change', 'en-US');
    emitter.off('locale-change', listener);
    emitter.emit('locale-change', 'zh-CN');

    expect(listener).toHaveBeenCalledOnce();
    expect(listener).toHaveBeenCalledWith('en-US');
  });

  it('removes one-time listeners before invoking them', () => {
    const emitter = new EventEmitter();
    const listener = vi.fn(() => emitter.emit('locale-change', 'nested'));

    emitter.once('locale-change', listener);
    emitter.emit('locale-change', 'en-US');

    expect(listener).toHaveBeenCalledOnce();
  });
});
