import type { MessageNoticeRecord } from '@/types/api';

export interface MessageCenterRealtimeEvent {
  eventType?: string;
  tenantId?: number;
  userId?: number;
  unreadCount?: number;
  message?: string;
  notice?: MessageNoticeRecord;
  timestamp?: string;
}

type MessageCenterRealtimeListener = (event: MessageCenterRealtimeEvent) => void;

const listeners = new Set<MessageCenterRealtimeListener>();
let socket: WebSocket | null = null;
let reconnectTimer: number | null = null;
let connectionKey: string | null = null;

const buildWebSocketUrl = (accessToken: string) => {
  const wsScheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsScheme}//${window.location.host}/ws/message?accessToken=${encodeURIComponent(accessToken)}`;
};

const clearReconnectTimer = () => {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
};

const closeSocket = () => {
  clearReconnectTimer();
  if (!socket) {
    connectionKey = null;
    return;
  }

  const currentSocket = socket;
  socket = null;
  connectionKey = null;

  currentSocket.onopen = null;
  currentSocket.onmessage = null;
  currentSocket.onerror = null;
  currentSocket.onclose = null;

  if (currentSocket.readyState === WebSocket.OPEN || currentSocket.readyState === WebSocket.CONNECTING) {
    currentSocket.close();
  }
};

const notify = (event: MessageCenterRealtimeEvent) => {
  listeners.forEach((listener) => {
    try {
      listener(event);
    } catch {
      // Keep the shared channel healthy even if one listener fails.
    }
  });
};

const connect = (key: string, accessToken: string) => {
  if (!accessToken) {
    closeSocket();
    return;
  }

  if (connectionKey === key && socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return;
  }

  closeSocket();
  connectionKey = key;

  const nextSocket = new WebSocket(buildWebSocketUrl(accessToken));
  socket = nextSocket;

  nextSocket.onopen = () => {
    notify({ eventType: 'CONNECTED', message: '消息通道已连接', timestamp: new Date().toISOString() });
  };

  nextSocket.onmessage = (event) => {
    if (typeof event.data !== 'string') {
      return;
    }

    try {
      const payload = JSON.parse(event.data) as MessageCenterRealtimeEvent;
      if (payload && typeof payload === 'object') {
        notify(payload);
      }
    } catch {
      // Ignore malformed messages; the next push or reconnect will recover state.
    }
  };

  nextSocket.onerror = () => {
    if (socket === nextSocket) {
      nextSocket.close();
    }
  };

  nextSocket.onclose = () => {
    if (socket === nextSocket) {
      socket = null;
    }
    if (connectionKey === key && listeners.size > 0 && accessToken) {
      clearReconnectTimer();
      reconnectTimer = window.setTimeout(() => connect(key, accessToken), 3000);
    }
  };
};

export const subscribeMessageCenterRealtime = (listener: MessageCenterRealtimeListener, options: { enabled: boolean; key: string; accessToken: string }) => {
  listeners.add(listener);

  if (options.enabled) {
    connect(options.key, options.accessToken);
  }

  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      closeSocket();
    }
  };
};
