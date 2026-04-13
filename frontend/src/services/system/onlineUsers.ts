import { API_PREFIX, AUTHORIZATION_HEADER, TENANT_HEADER } from '@/constants/http';
import { performLogout } from '@/auth/session';
import { buildUnauthorizedRuntimeState, captureAuthRequestSnapshot } from '@/auth/unauthorized';
import { shouldSuppressUnauthorizedSideEffects } from '@/auth/unauthorizedDecision';
import { tenantContext } from '@/tenant/context';
import type { OnlineSessionEventRecord } from '@/types/api';

export interface OnlineSessionStreamOptions {
  onEvent: (event: OnlineSessionEventRecord) => void;
  onConnected?: () => void;
  onUnauthorized?: () => void;
}

export const connectOnlineSessionStream = (options: OnlineSessionStreamOptions) => {
  const controller = new AbortController();
  let stopped = false;
  let reconnectTimer: number | null = null;

  const stop = () => {
    stopped = true;
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    controller.abort();
  };

  const scheduleReconnect = () => {
    if (stopped) {
      return;
    }
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
    }
    reconnectTimer = window.setTimeout(() => {
      void open();
    }, 3000);
  };

  const open = async () => {
    if (stopped) {
      return;
    }

    const requestAuthSnapshot = captureAuthRequestSnapshot();
    const accessToken = requestAuthSnapshot.accessToken;
    const tenantId = tenantContext.getTenantId();
    if (!accessToken || !tenantId) {
      scheduleReconnect();
      return;
    }

    try {
      const response = await fetch(`${API_PREFIX}/v1/system/online-users/events`, {
        method: 'GET',
        headers: {
          [AUTHORIZATION_HEADER]: `Bearer ${accessToken}`,
          [TENANT_HEADER]: tenantId,
          Accept: 'text/event-stream',
          'Cache-Control': 'no-cache',
        },
        signal: controller.signal,
        credentials: 'same-origin',
      });

      if (response.status === 401 || response.status === 403) {
        if (shouldSuppressUnauthorizedSideEffects(requestAuthSnapshot, buildUnauthorizedRuntimeState())) {
          stop();
          return;
        }
        options.onUnauthorized?.();
        await performLogout({ reason: 'forced_expired' }).catch(() => {
          // Ignore logout failures when the server has already revoked the session.
        });
        stop();
        return;
      }

      if (!response.ok || !response.body) {
        scheduleReconnect();
        return;
      }

      options.onConnected?.();
      await readEventStream(response.body, options.onEvent, controller.signal);
      scheduleReconnect();
    } catch (error) {
      if (!stopped && !(error instanceof DOMException && error.name === 'AbortError')) {
        scheduleReconnect();
      }
    }
  };

  void open();
  return stop;
};

const readEventStream = async (
  body: ReadableStream<Uint8Array>,
  onEvent: (event: OnlineSessionEventRecord) => void,
  signal: AbortSignal,
) => {
  const reader = body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let currentEvent = '';
  let currentData = '';

  const flush = () => {
    const eventName = currentEvent || 'message';
    const data = currentData.trim();
    currentEvent = '';
    currentData = '';

    if (!data || eventName === 'heartbeat') {
      return;
    }

    try {
      onEvent(JSON.parse(data) as OnlineSessionEventRecord);
    } catch {
      // Ignore malformed payloads so a single bad event does not break the stream.
    }
  };

  while (!signal.aborted) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    let newlineIndex = buffer.indexOf('\n');
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).replace(/\r$/, '');
      buffer = buffer.slice(newlineIndex + 1);

      if (!line) {
        flush();
      } else if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        currentData += `${line.slice(5).trim()}\n`;
      }

      newlineIndex = buffer.indexOf('\n');
    }
  }

  if (currentData.trim()) {
    const eventName = currentEvent || 'message';
    if (eventName !== 'heartbeat') {
      try {
        onEvent(JSON.parse(currentData.trim()) as OnlineSessionEventRecord);
      } catch {
        // Ignore malformed payloads.
      }
    }
  }
};
