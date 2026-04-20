import { NotificationOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer } from 'antd';
import { useState } from 'react';
import { useResponsive } from '@/hooks/useResponsive';
import { MessageCenterContent } from '@/components/message-center/MessageCenterContent';

export const MessageCenterDrawer = () => {
  const { isMobile } = useResponsive();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

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
        width={isMobile ? '100vw' : 860}
        destroyOnClose={false}
        styles={{
          body: {
            padding: isMobile ? 16 : 20,
          },
        }}
      >
        <MessageCenterContent onUnreadCountChange={setUnreadCount} />
      </Drawer>
    </>
  );
};
