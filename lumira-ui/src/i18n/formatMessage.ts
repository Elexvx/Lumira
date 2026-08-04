import { resolveBuiltinMessage } from './messages';
import { resolveRuntimeLocale } from './locale';
import {
  isMessageCompatibleWithLocale,
  resolveBuiltinLocaleMessage,
} from './builtinMessages';

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
  const locale = resolveRuntimeLocale();
  const localizedFallback = resolveBuiltinLocaleMessage(locale, descriptor.id);
  const resolvedTemplate = resolveBuiltinMessage(
    descriptor.id,
    localizedFallback || descriptor.defaultMessage || descriptor.id || '',
  );
  const template = isMessageCompatibleWithLocale(locale, resolvedTemplate, localizedFallback)
    ? resolvedTemplate
    : localizedFallback || resolvedTemplate;

  return template.replace(/\{([A-Za-z_][\w-]*)\}/g, (token, key: string) => {
    const value = values[key];
    return value === undefined || value === null ? token : String(value);
  });
};
