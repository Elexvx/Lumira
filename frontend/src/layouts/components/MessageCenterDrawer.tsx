import { NotificationOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useResponsive } from '@/hooks/useResponsive';
import { MessageCenterContent } from '@/components/message-center/MessageCenterContent';
import { MESSAGE_CENTER_REFRESH_EVENT } from '@/components/message-center/messageCenterEvents';
import { messageService } from '@/services/message';
import { useMessageCenterRealtime } from '@/components/message-center/useMessageCenterRealtime';
import type { MessageCenterRealtimeEvent } from '@/components/message-center/messageCenterRealtime';

export const MessageCenterDrawer = () => {
  const { isMobile } = useResponsive();
  const { initialState } = useInitialStateModel();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const tenantId = initialState?.currentTenant?.tenantId;
  const permissions = useMemo(() => new Set(initialState?.currentUser?.permissions || []), [initialState?.currentUser?.permissions]);
  const canOpenMessageCenter =
    permissions.has('*') ||
    permissions.has('message:message:view') ||
    permissions.has('system:notification:view');

  const requestOptions = useMemo(
    () => ({
      autoRedirectOnUnauthorized: false,
      silent: true,
    }),
    [],
  );

  const reloadUnreadCount = useCallback(async () => {
    if (!tenantId || !canOpenMessageCenter) {
      setUnreadCount(0);
      return;
    }

    try {
      const result = await messageService.unreadCount(requestOptions);
      setUnreadCount(Number(result.unreadCount || 0));
    } catch {
      setUnreadCount(0);
    }
  }, [canOpenMessageCenter, requestOptions, tenantId]);

  useEffect(() => {
    void reloadUnreadCount();
  }, [reloadUnreadCount]);

  useEffect(() => {
    const handleRefresh = () => {
      void reloadUnreadCount();
    };

    window.addEventListener(MESSAGE_CENTER_REFRESH_EVENT, handleRefresh);
    return () => {
      window.removeEventListener(MESSAGE_CENTER_REFRESH_EVENT, handleRefresh);
    };
  }, [reloadUnreadCount]);

  useMessageCenterRealtime(Boolean(tenantId && canOpenMessageCenter), useCallback((event: MessageCenterRealtimeEvent) => {
    if (typeof event.unreadCount === 'number') {
      setUnreadCount(Math.max(0, event.unreadCount));
      return;
    }

    if (
      event.eventType === 'NOTICE_CREATED' ||
      event.eventType === 'NOTICE_RETRACTED' ||
      event.eventType === 'NOTICE_READ' ||
      event.eventType === 'UNREAD_COUNT'
    ) {
      void reloadUnreadCount();
    }
  }, [reloadUnreadCount, canOpenMessageCenter, tenantId]));

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
  };

  return (
    <>
      <Badge count={unreadCount} overflowCount={99} offset={[0, 6]}>
        <Button
          type="text"
          icon={<NotificationOutlined />}
          aria-label={`消息中心，当前有 ${unreadCount} 条未读消息`}
          onClick={() => handleOpenChange(true)}
        />
      </Badge>

      <Drawer
        title="消息中心"
        open={open}
        onClose={() => handleOpenChange(false)}
        width={isMobile ? '100vw' : 720}
        destroyOnClose={false}
        styles={{
          body: { padding: 16 },
        }}
      >
        <MessageCenterContent onUnreadCountChange={setUnreadCount} />
      </Drawer>
    </>
  );
};
