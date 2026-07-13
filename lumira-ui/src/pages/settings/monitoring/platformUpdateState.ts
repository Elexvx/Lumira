export const canSubmitPlatformUpdate = (
  serverImage?: string | null,
  taskStatus?: string | null,
) => Boolean(serverImage?.trim()) && taskStatus !== 'PENDING' && taskStatus !== 'RUNNING';
