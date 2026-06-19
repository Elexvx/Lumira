import * as AntIcons from '@ant-design/icons';
import React from 'react';
import type { ComponentType, ReactNode } from 'react';

type AntdIconComponent = ComponentType<Record<string, unknown>>;
const ANT_DESIGN_ICONS = AntIcons as unknown as Record<string, AntdIconComponent | undefined>;
const OUTLINED_ICON_SUFFIX = 'Outlined';

const normalizeMenuIconName = (iconName?: string | null) =>
  (iconName || '')
    .trim()
    .replace(/(^\w)|-(\w)/g, (_, firstChar: string, hyphenChar: string) => (firstChar || hyphenChar).toUpperCase());

export const resolveNavigationIcon = (icon?: ReactNode | string) => {
  if (!icon) {
    return undefined;
  }

  if (typeof icon !== 'string') {
    return icon;
  }

  const normalizedIconName = normalizeMenuIconName(icon);
  const IconComponent = ANT_DESIGN_ICONS[normalizedIconName] || ANT_DESIGN_ICONS[`${normalizedIconName}${OUTLINED_ICON_SUFFIX}`];

  if (!IconComponent) {
    return undefined;
  }

  return React.createElement(IconComponent);
};
