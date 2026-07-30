import { describe, expect, it } from 'vitest';
import { dedupeRuntimeMenuItems } from './runtimeMenuDedupe';

describe('dedupeRuntimeMenuItems', () => {
  it('keeps one placement for globally duplicated paths and keys', () => {
    expect(dedupeRuntimeMenuItems([
      {
        key: 'registration',
        children: [
          { path: '/competitions/register' },
          { path: '/certificates/mine' },
        ],
      },
      {
        key: 'certificates',
        children: [
          { path: '/certificates/mine/' },
          { key: '/experts/management', path: '/experts/management' },
        ],
      },
      { key: '/experts/management', path: '/experts/secondary' },
    ])).toEqual([
      {
        key: 'registration',
        children: [
          { path: '/competitions/register', children: undefined },
          { path: '/certificates/mine', children: undefined },
        ],
      },
      {
        key: 'certificates',
        children: [
          { key: '/experts/management', path: '/experts/management', children: undefined },
        ],
      },
    ]);
  });

  it('hoists unique children from a discarded duplicate group', () => {
    expect(dedupeRuntimeMenuItems([
      { path: '/group', children: [{ path: '/group/first' }] },
      { path: '/group/', children: [{ path: '/group/second' }] },
    ])).toEqual([
      { path: '/group', children: [{ path: '/group/first', children: undefined }] },
      { path: '/group/second', children: undefined },
    ]);
  });
});
