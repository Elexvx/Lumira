import { Avatar } from 'antd';
import { Bubble } from '@ant-design/x';
import { RobotOutlined } from '@ant-design/icons';
import React, { useMemo } from 'react';
import type { BubbleListProps as XBubbleListProps } from '@ant-design/x';

export interface BubbleListProps {
  items: XBubbleListProps['items'];
}

export const BubbleList: React.FC<BubbleListProps> = ({ items }) => {
  const bubbleRole = useMemo(
    () => ({
      user: {
        placement: 'end' as const,
        variant: 'filled' as const,
        shape: 'round' as const,
        footerPlacement: 'outer-end' as const,
      },
      ai: {
        placement: 'start' as const,
        variant: 'borderless' as const,
        shape: 'round' as const,
        footerPlacement: 'outer-end' as const,
        avatar: <Avatar size={32} icon={<RobotOutlined />} />,
      },
    }),
    [],
  );

  return <Bubble.List items={items} role={bubbleRole} autoScroll className="saas-ai-assistant-bubbles" />;
};
