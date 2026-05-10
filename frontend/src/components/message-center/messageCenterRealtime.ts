import { API_ORIGIN } from '@/constants/http';
import { request } from '@/services/common/request';
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
let connectingKey: string | null = null;

interface MessageWebSocketTicket {
  ticket: string;
  expiresInSeconds?: number;
}

const requestWebSocketTicket = async () => {
  const response = await request<MessageWebSocketTicket>('/v1/message/ws-ticket', {
    method: 'POST',
    autoRedirectOnUnauthorized: false,
    silent: true,
  });
  return response.ticket;
};

const buildWebSocketUrl = (ticket: string) => {
  const apiOrigin = API_ORIGIN ? new URL(API_ORIGIN) : window.location;
  const wsScheme = apiOrigin.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsScheme}//${apiOrigin.host}/ws/message?ticket=${encodeURIComponent(ticket)}`;
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
  connectingKey = null;

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

const connect = async (key: string) => {
  if (
    connectionKey === key &&
    (connectingKey === key || (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)))
  ) {
    return;
  }

  closeSocket();
  connectionKey = key;

  connectingKey = key;
  let ticket: string;
  try {
    ticket = await requestWebSocketTicket();
  } catch {
    if (connectionKey === key && listeners.size > 0) {
      clearReconnectTimer();
      reconnectTimer = window.setTimeout(() => void connect(key), 3000);
    }
    return;
  } finally {
    if (connectingKey === key) {
      connectingKey = null;
    }
  }

  if (connectionKey !== key || listeners.size === 0) {
    return;
  }

  const nextSocket = new WebSocket(buildWebSocketUrl(ticket));
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
    if (connectionKey === key && listeners.size > 0) {
      clearReconnectTimer();
      reconnectTimer = window.setTimeout(() => void connect(key), 3000);
    }
  };
};

export const subscribeMessageCenterRealtime = (listener: MessageCenterRealtimeListener, options: { enabled: boolean; key: string }) => {
  listeners.add(listener);

  if (options.enabled) {
    void connect(options.key);
  }

  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      closeSocket();
    }
  };
};
