import { describe, expect, it } from 'vitest';
import { protectedUserStatusOptions } from './users/options';
import type { DictOption } from '@/hooks/useDictOptions';

describe('protectedUserStatusOptions', () => {
  const runtimeOptions: DictOption[] = [
    { label: 'Disabled', value: 'DISABLED' },
    { label: 'Enabled', value: 'ENABLED' },
    { label: 'Locked', value: 'LOCKED' },
  ];

  it('keeps all runtime dictionary status options for normal users', () => {
    expect(protectedUserStatusOptions(runtimeOptions, false)).toEqual(runtimeOptions);
  });

  it('keeps only enabled status for the protected admin account', () => {
    expect(protectedUserStatusOptions(runtimeOptions, true)).toEqual([{ label: 'Enabled', value: 'ENABLED' }]);
  });
});
