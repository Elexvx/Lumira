import { NotificationOutlined } from '@ant-design/icons';
import { Badge, Button } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useIntl } from '@umijs/max';
import { MESSAGE_CENTER_DRAWER_WIDTH } from '@/constants/ui';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { MessageCenterContent } from '@/components/message-center/MessageCenterContent';
import { MESSAGE_CENTER_REFRESH_EVENT } from '@/components/message-center/messageCenterEvents';
import { messageService } from '@/services/message';
import { useMessageCenterRealtime } from '@/components/message-center/useMessageCenterRealtime';
import type { MessageCenterRealtimeEvent } from '@/components/message-center/messageCenterRealtime';
import { ManagementDrawer } from '@/features/management';

export const MessageCenterDrawer = () => {
  const { initialState } = useInitialStateModel();
  const intl = useIntl();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
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
    if (!canOpenMessageCenter) {
      setUnreadCount(0);
      return;
    }

    try {
      const result = await messageService.unreadCount(requestOptions);
      setUnreadCount(Number(result.unreadCount || 0));
    } catch {
      setUnreadCount(0);
    }
  }, [canOpenMessageCenter, requestOptions]);

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

  useMessageCenterRealtime(canOpenMessageCenter, useCallback((event: MessageCenterRealtimeEvent) => {
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
  }, [reloadUnreadCount]));

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
  };

  return (
    <>
      <Badge count={unreadCount} overflowCount={99} offset={[0, 6]}>
        <Button
          type="text"
          icon={<NotificationOutlined />}
          aria-label={intl.formatMessage({ id: 'message.center.ariaLabel', defaultMessage: '消息中心，当前有 {count} 条未读消息' }, { count: unreadCount })}
          onClick={() => handleOpenChange(true)}
        />
      </Badge>

      <ManagementDrawer
        title={intl.formatMessage({ id: 'message.center.title', defaultMessage: '消息中心' })}
        open={open}
        onClose={() => handleOpenChange(false)}
        width={MESSAGE_CENTER_DRAWER_WIDTH}
        destroyOnHidden={false}
      >
        <MessageCenterContent onUnreadCountChange={setUnreadCount} />
      </ManagementDrawer>
    </>
  );
};
