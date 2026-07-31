import DOMPurify from 'dompurify';

export const sanitizeRichText = (html?: string) => {
  if (typeof document === 'undefined') {
    return '';
  }
  const sanitized = DOMPurify.sanitize(html || '', {
    ALLOWED_TAGS: [
      'p', 'br', 'div', 'span',
      'strong', 'b', 'em', 'i', 'u', 's',
      'ul', 'ol', 'li', 'blockquote', 'pre', 'code',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'a', 'img',
    ],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'target', 'rel', 'loading', 'width', 'height'],
    ALLOW_DATA_ATTR: false,
    FORBID_ATTR: ['style'],
  });
  const template = document.createElement('template');
  template.innerHTML = sanitized;
  template.content.querySelectorAll<HTMLAnchorElement>('a').forEach((node) => {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer nofollow');
  });
  return template.innerHTML;
};
