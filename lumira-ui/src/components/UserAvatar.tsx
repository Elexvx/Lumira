import { UserOutlined } from '@ant-design/icons';
import { GradientAvatar } from '@outpacelabs/avatars';
import { Avatar } from 'antd';
import type { AvatarProps } from 'antd';
import { normalizeUploadUrl } from '@/utils/uploadUrl';

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
  const normalizedAvatarUrl = normalizeUploadUrl(avatarUrl);
  const generatedAvatar = (
    <GradientAvatar
      seed={resolveUserAvatarSeed({ userId, userUuid, username })}
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
