import { UserOutlined } from '@ant-design/icons';
import { GradientAvatar } from '@outpacelabs/avatars';
import { Avatar } from 'antd';
import type { AvatarProps } from 'antd';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

export const GENERATED_USER_AVATAR_PREFIX = '/api/v1/profile/avatar/generated/';

export type UserAvatarIdentity = {
  userId?: number | string | null;
  userUuid?: string | null;
  username?: string | null;
};

export type UserAvatarProps = Omit<AvatarProps, 'src' | 'srcSet' | 'children'> &
  UserAvatarIdentity & {
    avatarUrl?: string | null;
  };

const normalizeIdentityPart = (value?: number | string | null) => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? String(value) : '';
  }
  return value?.trim() || '';
};

export const resolveUserAvatarSeed = ({ userId, userUuid, username }: UserAvatarIdentity) => {
  const stableIdentity =
    normalizeIdentityPart(userUuid) ||
    normalizeIdentityPart(userId) ||
    normalizeIdentityPart(username) ||
    'anonymous';
  return `lumira-user:${stableIdentity}`;
};

export const isGeneratedUserAvatarUrl = (value?: string | null) =>
  Boolean(value?.trim().startsWith(GENERATED_USER_AVATAR_PREFIX));

const resolvePersistedGeneratedAvatarSeed = (value: string | null | undefined, fallbackSeed: string) => {
  const trimmed = value?.trim() || '';
  if (!isGeneratedUserAvatarUrl(trimmed)) {
    return fallbackSeed;
  }
  const persistedIdentity = trimmed.slice(GENERATED_USER_AVATAR_PREFIX.length);
  if (!persistedIdentity) {
    return fallbackSeed;
  }
  try {
    return `lumira-user:${decodeURIComponent(persistedIdentity)}`;
  } catch {
    return `lumira-user:${persistedIdentity}`;
  }
};

const resolveGeneratedAvatarSize = (size: AvatarProps['size']) => {
  if (typeof size === 'number') {
    return size;
  }
  if (size === 'small') {
    return 24;
  }
  if (size === 'large') {
    return 40;
  }
  return 32;
};

export const UserAvatar = ({
  avatarUrl,
  userId,
  userUuid,
  username,
  size = 'default',
  icon = <UserOutlined />,
  alt,
  ...avatarProps
}: UserAvatarProps) => {
  const fallbackSeed = resolveUserAvatarSeed({ userId, userUuid, username });
  const normalizedAvatarUrl = isGeneratedUserAvatarUrl(avatarUrl) ? '' : normalizeUploadUrl(avatarUrl);
  const generatedAvatar = (
    <GradientAvatar
      seed={resolvePersistedGeneratedAvatarSeed(avatarUrl, fallbackSeed)}
      size={resolveGeneratedAvatarSize(size)}
    />
  );

  return (
    <Avatar
      {...avatarProps}
      size={size}
      src={normalizedAvatarUrl || generatedAvatar}
      icon={icon}
      alt={alt || username || undefined}
    />
  );
};
