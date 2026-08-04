import { resolveBuiltinMessage } from './messages';

export interface MessageDescriptor {
  id?: string;
  defaultMessage?: string;
}

type MessageValues = Record<string, string | number | boolean | null | undefined>;

/**
 * Non-hook message resolver for utility modules and runtime configuration.
 */
export const formatMessage = (
  descriptor: MessageDescriptor,
  values: MessageValues = {},
) => {
  const template = resolveBuiltinMessage(
    descriptor.id,
    descriptor.defaultMessage || descriptor.id || '',
  );

  return template.replace(/\{([A-Za-z_][\w-]*)\}/g, (token, key: string) => {
    const value = values[key];
    return value === undefined || value === null ? token : String(value);
  });
};
