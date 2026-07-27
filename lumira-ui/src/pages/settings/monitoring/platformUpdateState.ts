export const canSubmitPlatformUpdate = (
  serverImage?: string | null,
  taskStatus?: string | null,
  updateAvailable?: boolean,
) => updateAvailable === true
  && Boolean(serverImage?.trim())
  && taskStatus !== 'PENDING'
  && taskStatus !== 'RUNNING';
