import { describe, expect, it, vi } from 'vitest';
import { transitionAfterCompetitionSettingsSave } from './competitionSettingsPanelTransition';

describe('competition settings panel transition', () => {
  it('waits for the current panel save before changing panels', async () => {
    let finishSave: ((saved: boolean) => void) | undefined;
    const transition = vi.fn();
    const pendingTransition = transitionAfterCompetitionSettingsSave({
      flushPendingSave: () => new Promise<boolean>((resolve) => {
        finishSave = resolve;
      }),
    }, transition);

    await Promise.resolve();
    expect(transition).not.toHaveBeenCalled();

    finishSave?.(true);
    await expect(pendingTransition).resolves.toBe(true);
    expect(transition).toHaveBeenCalledOnce();
  });

  it('keeps the current panel open when its pending values cannot be saved', async () => {
    const transition = vi.fn();

    await expect(transitionAfterCompetitionSettingsSave({
      flushPendingSave: async () => false,
    }, transition)).resolves.toBe(false);
    expect(transition).not.toHaveBeenCalled();
  });

  it('changes panels immediately when there is no mounted panel', async () => {
    const transition = vi.fn();

    await expect(transitionAfterCompetitionSettingsSave(null, transition)).resolves.toBe(true);
    expect(transition).toHaveBeenCalledOnce();
  });

  it('does not change panels when flushing rejects', async () => {
    const transition = vi.fn();
    const failure = new Error('save failed');

    await expect(transitionAfterCompetitionSettingsSave({
      flushPendingSave: async () => Promise.reject(failure),
    }, transition)).rejects.toBe(failure);
    expect(transition).not.toHaveBeenCalled();
  });
});
