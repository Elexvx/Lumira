export type ActivityCtaTarget = {
  kind: 'internal' | 'external';
  href: string;
};

const HTTP_URL_PATTERN = /^https?:\/\//i;
const URI_SCHEME_PATTERN = /^[a-z][a-z0-9+.-]*:/i;

export const resolveActivityCtaTarget = (href?: string | null): ActivityCtaTarget | null => {
  const trimmedHref = href?.trim();
  if (!trimmedHref) {
    return null;
  }
  if (HTTP_URL_PATTERN.test(trimmedHref)) {
    return { kind: 'external', href: trimmedHref };
  }
  if (URI_SCHEME_PATTERN.test(trimmedHref)) {
    return null;
  }

  const internalHref = trimmedHref.startsWith('/') ? trimmedHref : `/${trimmedHref}`;
  if (internalHref.startsWith('//') || internalHref.includes('\\')) {
    return null;
  }
  return { kind: 'internal', href: internalHref };
};
