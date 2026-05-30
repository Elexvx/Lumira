import { Conversations as XConversations } from '@ant-design/x';
import { PlusOutlined } from '@ant-design/icons';
import React from 'react';
import type { MenuProps } from 'antd';
import type { ConversationsProps as XConversationsProps } from '@ant-design/x';

export interface ConversationsProps {
  items: XConversationsProps['items'];
  activeKey: string;
  onActiveChange: (key: string) => void;
  isShareMode: boolean;
  onCreateSession: () => void;
  buildMenu: (conversationKey: string) => MenuProps;
}

export const Conversations: React.FC<ConversationsProps> = ({
  items,
  activeKey,
  onActiveChange,
  isShareMode,
  onCreateSession,
  buildMenu,
}) => {
  return (
    <XConversations
      items={items}
      activeKey={activeKey}
      onActiveChange={onActiveChange}
      creation={
        isShareMode
          ? undefined
          : {
              label: '新建对话',
              icon: <PlusOutlined />,
              onClick: onCreateSession,
              align: 'center' as const,
            }
      }
      menu={(conversation) => buildMenu(String(conversation.key))}
      groupable
      className="saas-ai-assistant-conversations"
    />
  );
};
