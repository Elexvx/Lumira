const DANGEROUS_HTML_BLOCK_PATTERN = /<\/?(script|style|iframe|object|embed|link|meta|base|form|input|button|svg|math)\b[^>]*>/gi;
const INLINE_EVENT_HANDLER_PATTERN = /\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi;
const DANGEROUS_MARKDOWN_URL_PATTERN = /(\]\()\s*(javascript:|vbscript:|data:(?!image\/(?:png|gif|jpeg|webp);base64,))/gi;

export const sanitizeMarkdownInput = (value: unknown) => {
  if (typeof value !== 'string') {
    return '';
  }
  return value
    .replace(DANGEROUS_HTML_BLOCK_PATTERN, '')
    .replace(INLINE_EVENT_HANDLER_PATTERN, '')
    .replace(DANGEROUS_MARKDOWN_URL_PATTERN, '$1#blocked-unsafe-url');
};
