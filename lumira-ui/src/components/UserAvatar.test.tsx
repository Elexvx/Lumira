import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { UserAvatar, resolveUserAvatarSeed } from './UserAvatar';

vi.mock('@outpacelabs/avatars', () => ({
  GradientAvatar: ({ seed }: { seed: string | number }) => (
    <span data-testid="generated-avatar" data-seed={seed} />
  ),
}));

describe('resolveUserAvatarSeed', () => {
  it('uses the stable user UUID before mutable account fields', () => {
    expect(
      resolveUserAvatarSeed({
        userUuid: 'user-uuid-42',
        userId: 42,
        username: 'alice',
      }),
    ).toBe('lumira-user:user-uuid-42');
  });

  it('falls back through user ID and username when needed', () => {
    expect(resolveUserAvatarSeed({ userId: 42, username: 'alice' })).toBe('lumira-user:42');
    expect(resolveUserAvatarSeed({ username: ' alice ' })).toBe('lumira-user:alice');
  });
});

describe('UserAvatar', () => {
  it('keeps a supplied avatar URL instead of generating a default avatar', () => {
    const markup = renderToStaticMarkup(
      <UserAvatar avatarUrl="https://thirdwx.qlogo.cn/avatar.jpg" userId={42} username="alice" />,
    );

    expect(markup).toContain('https://thirdwx.qlogo.cn/avatar.jpg');
    expect(markup).not.toContain('data-testid="generated-avatar"');
  });

  it('generates a stable default avatar only when the avatar URL is empty', () => {
    const markup = renderToStaticMarkup(
      <UserAvatar avatarUrl="  " userUuid="user-uuid-42" userId={42} username="alice" />,
    );

    expect(markup).toContain('data-testid="generated-avatar"');
    expect(markup).toContain('data-seed="lumira-user:user-uuid-42"');
  });
});
