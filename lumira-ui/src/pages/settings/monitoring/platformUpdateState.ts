import type { PlatformUpdatePreflight, PlatformUpdateStatus, PlatformUpdateTask } from '@/types/api';

export const canSubmitPlatformUpdate = (
  serverImage?: string | null,
  taskStatus?: string | null,
  updateAvailable?: boolean,
) => updateAvailable === true
  && Boolean(serverImage?.trim())
  && taskStatus !== 'PENDING'
  && taskStatus !== 'RUNNING';

const normalizedText = (value?: string | null) => value?.trim() || null;

export const isPlatformUpdateFailure = (task?: PlatformUpdateTask | null) => {
  if (!task || task.taskType !== 'INSTALL') {
    return false;
  }
  if (task.status === 'FAILED') {
    return true;
  }
  return task.status === 'ROLLED_BACK' && Boolean(normalizedText(task.errorMessage));
};

export const didPlatformUpdateBecomeFailed = (
  previous?: Pick<PlatformUpdateTask, 'id' | 'status'> | null,
  current?: PlatformUpdateTask | null,
) => Boolean(
  previous
  && current
  && previous.id === current.id
  && (previous.status === 'PENDING' || previous.status === 'RUNNING')
  && isPlatformUpdateFailure(current),
);

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
