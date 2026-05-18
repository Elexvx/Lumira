const maskWithEdges = (value: string, leftVisible: number, rightVisible: number) => {
  if (!value) {
    return '';
  }

  const normalizedValue = value.trim();
  if (!normalizedValue) {
    return '';
  }

  const visibleCount = leftVisible + rightVisible;
  if (normalizedValue.length <= visibleCount) {
    return '*'.repeat(normalizedValue.length);
  }

  return `${normalizedValue.slice(0, leftVisible)}${'*'.repeat(normalizedValue.length - visibleCount)}${normalizedValue.slice(-rightVisible)}`;
};

export const maskMobile = (value?: string | null) => {
  if (!value) {
    return '';
  }
  return maskWithEdges(value, 3, 4);
};

export const maskIdCardNumber = (value?: string | null) => {
  if (!value) {
    return '';
  }
  const normalizedValue = value.trim();
  if (!normalizedValue) {
    return '';
  }
  if (normalizedValue.length <= 8) {
    return maskWithEdges(normalizedValue, 2, 2);
  }
  return maskWithEdges(normalizedValue, 6, 4);
};

export const maskEmail = (value?: string | null) => {
  if (!value) {
    return '';
  }
  const normalizedValue = value.trim();
  const atIndex = normalizedValue.indexOf('@');
  if (atIndex <= 0) {
    return maskWithEdges(normalizedValue, 1, 1);
  }
  const localPart = normalizedValue.slice(0, atIndex);
  const domain = normalizedValue.slice(atIndex);
  return `${maskWithEdges(localPart, 1, localPart.length > 2 ? 1 : 0)}${domain}`;
};
