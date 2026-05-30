import { Avatar, Space, Typography } from 'antd';
import { Bubble } from '@ant-design/x';
import { RobotOutlined } from '@ant-design/icons';
import React, { useMemo } from 'react';
import type { BubbleProps } from '@ant-design/x';

export interface WelcomeProps {
  isShareMode?: boolean;
}

export const Welcome: React.FC<WelcomeProps> = ({ isShareMode }) => {
  return (
    <div className="saas-ai-assistant-shell__welcome">
      <Space direction="vertical" align="center" size={16}>
        <Avatar size={64} icon={<RobotOutlined />} style={{ backgroundColor: '#1890ff' }} />
        <Typography.Title level={4} style={{ margin: 0 }}>
          {isShareMode ? '分享会话为空' : '你好，我是企业 AI 助手'}
        </Typography.Title>
        <Typography.Text type="secondary">
          {isShareMode ? '这条分享会话还没有消息。' : '可以帮你查资料、写方案、拆任务。'}
        </Typography.Text>
      </Space>
    </div>
  );
};
