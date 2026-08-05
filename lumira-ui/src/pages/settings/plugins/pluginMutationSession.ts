import {
  hasUsableTokenAfterRefresh,
  performLogout,
  tryRefreshTokenOutcome,
} from '@/auth/sessionLifecycle';

export type PluginMutationSessionState =
  | 'ready'
  | 'temporarily_unavailable'
  | 'session_expired';

/**
 * Plugin mutations can advance the global permission snapshot version. Refresh
 * the token before the page fans out into follow-up reads so those requests do
 * not mistake the expected version transition for an expired login session.
 */
export const refreshPluginMutationSession = async (): Promise<PluginMutationSessionState> => {
  const outcome = await tryRefreshTokenOutcome();
  if (hasUsableTokenAfterRefresh(outcome)) {
    return 'ready';
  }

  if (outcome === 'session_expired') {
    await performLogout({ reason: 'forced_expired' });
    return 'session_expired';
  }

  return 'temporarily_unavailable';
};
