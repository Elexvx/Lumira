const fallbackRandomPart = () => Math.random().toString(36).slice(2, 10);

export const createRequestId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  const randomValues = new Uint32Array(2);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(randomValues);
    return `${Date.now().toString(36)}-${randomValues[0].toString(36)}-${randomValues[1].toString(36)}`;
  }

  return `${Date.now().toString(36)}-${fallbackRandomPart()}-${fallbackRandomPart()}`;
};
