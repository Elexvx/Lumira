import { useEffect } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { subscribeMessageCenterRealtime, type MessageCenterRealtimeEvent } from '@/components/message-center/messageCenterRealtime';

export const useMessageCenterRealtime = (
  enabled: boolean,
  onEvent: (event: MessageCenterRealtimeEvent) => void,
) => {
  const { initialState } = useInitialStateModel();
  const sessionId = initialState?.currentUser?.sessionId;

  useEffect(() => {
    if (!enabled || !sessionId) {
      return undefined;
    }

    const connectionKey = sessionId;
    return subscribeMessageCenterRealtime(onEvent, {
      enabled: true,
      key: connectionKey,
    });
  }, [enabled, onEvent, sessionId]);
};
