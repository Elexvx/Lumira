import type { PlatformUpdatePreflight, PlatformUpdateStatus } from '@/types/api';

export const canSubmitPlatformUpdate = (
  serverImage?: string | null,
  taskStatus?: string | null,
  updateAvailable?: boolean,
) => updateAvailable === true
  && Boolean(serverImage?.trim())
  && taskStatus !== 'PENDING'
  && taskStatus !== 'RUNNING';

const normalizedText = (value?: string | null) => value?.trim() || null;

export const resolvePlatformUpdateConfirmationDetails = (
  status?: PlatformUpdateStatus | null,
  preflight?: PlatformUpdatePreflight | null,
) => ({
  currentVersion: normalizedText(status?.current?.version) || '-',
  currentCommit: normalizedText(status?.current?.commitId) || '-',
  targetVersion: normalizedText(preflight?.targetVersion) || normalizedText(status?.latest?.version) || '-',
  targetCommit: normalizedText(preflight?.targetCommit) || normalizedText(status?.latest?.commitId) || '-',
  releaseNotes: normalizedText(status?.manifest?.releaseNotes) || normalizedText(status?.latest?.title),
});
