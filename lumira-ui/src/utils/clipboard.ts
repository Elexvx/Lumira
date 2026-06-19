export const copyTextToClipboard = async (text: string) => {
  if (!text) {
    throw new Error('empty clipboard text');
  }

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
      // Fall through to the legacy copy path below.
    }
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', 'true');
  textarea.style.position = 'fixed';
  textarea.style.top = '-9999px';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();

  const successful = document.execCommand('copy');
  document.body.removeChild(textarea);

  if (!successful) {
    throw new Error('copy failed');
  }
};
