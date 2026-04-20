import { useEffect } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { tokenManager } from '@/auth/token';
import { subscribeMessageCenterRealtime, type MessageCenterRealtimeEvent } from '@/components/message-center/messageCenterRealtime';

export const useMessageCenterRealtime = (
  enabled: boolean,
  onEvent: (event: MessageCenterRealtimeEvent) => void,
) => {
  const { initialState } = useInitialStateModel();
  const tenantId = initialState?.currentTenant?.tenantId;
  const userId = initialState?.currentUser?.userId;
  const tokenGeneration = tokenManager.getTokenGeneration();
  const accessToken = tokenManager.getAccessToken();

  useEffect(() => {
    if (!enabled || !tenantId || !userId || !accessToken) {
      return undefined;
    }

    const connectionKey = `${tenantId}:${userId}:${tokenGeneration}`;
    return subscribeMessageCenterRealtime(onEvent, {
      enabled: true,
      key: connectionKey,
      accessToken,
    });
  }, [accessToken, enabled, onEvent, tenantId, tokenGeneration, userId]);
};
