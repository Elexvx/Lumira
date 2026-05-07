import { PlusOutlined, RobotOutlined, SettingOutlined } from '@ant-design/icons';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { history, useAccess } from '@umijs/max';
import { Bubble, Conversations, Sender, Welcome } from '@ant-design/x';
import { Avatar, Button, Space, Spin, Tag, Typography, message } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { aiService } from '@/services/ai';
import type { AiEmployeeRecord } from '@/types/api';
import './Assistant.css';

type BubbleRole = 'user' | 'ai';

type ChatBubble = {
  key: string;
  role: BubbleRole;
  content: string;
};

type ChatSession = {
  id: string;
  title: string;
  preview: string;
  employeeId: number | null;
  employeeName: string;
  employeeAvatarKey?: string | null;
  messages: ChatBubble[];
  conversationId?: number | null;
  updatedAt: string;
};

const buildBubbleKey = (prefix: string) => `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`;

const buildSessionTitle = (message: string) => {
  const trimmed = message.trim();
  if (!trimmed) {
    return '新对话';
  }
  return trimmed.length > 18 ? `${trimmed.slice(0, 18)}...` : trimmed;
};

const buildAssistantGreeting = (employee?: AiEmployeeRecord | null) => {
  if (employee?.greeting?.trim()) {
    return employee.greeting.trim();
  }

  const nickname = employee?.nickname?.trim() || 'AI 助手';
  return `你好，我是${nickname}，有什么可以帮你？`;
};

const buildInitialSession = (employee?: AiEmployeeRecord | null): ChatSession => {
  const greeting = buildAssistantGreeting(employee);
  return {
    id: 'session-default',
    title: '新对话',
    preview: greeting,
    employeeId: employee?.id ?? null,
    employeeName: employee?.nickname?.trim() || 'AI 助手',
    employeeAvatarKey: employee?.avatarKey ?? null,
    messages: [],
    updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
  };
};

const getConversationGroup = (updatedAt: string) => {
  const time = dayjs(updatedAt);
  if (time.isSame(dayjs(), 'day')) {
    return '今天';
  }
  if (time.isSame(dayjs().subtract(1, 'day'), 'day')) {
    return '昨天';
  }
  return '更早';
};

const AiAssistantPage = () => {
  const access = useAccess();
  const [inputValue, setInputValue] = useState('');
  const [sending, setSending] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string>('session-default');
  const [sessions, setSessions] = useState<ChatSession[]>([]);

  const employeesQuery = useQuery({
    queryKey: ['ai-assistant-employees'],
    queryFn: async () => aiService.employees({ pageNo: 1, pageSize: 50 }, { autoRedirectOnUnauthorized: false }),
  });

  const employees = employeesQuery.data?.records || [];
  const selectedEmployee = useMemo(() => {
    if (!employees.length) {
      return null;
    }
    return employees.find((item) => item.enabled) || employees[0];
  }, [employees]);

  useEffect(() => {
    if (!sessions.length) {
      setSessions([buildInitialSession(selectedEmployee)]);
      return;
    }

    setSessions((currentSessions) => {
      if (!currentSessions.length) {
        return [buildInitialSession(selectedEmployee)];
      }

      const activeSession = currentSessions.find((session) => session.id === activeSessionId);
      if (!activeSession) {
        return currentSessions;
      }

      if (activeSession.employeeId || !selectedEmployee) {
        return currentSessions;
      }

      return currentSessions.map((session) =>
        session.id === activeSessionId
          ? {
              ...session,
              employeeId: selectedEmployee.id,
              employeeName: selectedEmployee.nickname?.trim() || selectedEmployee.username,
              employeeAvatarKey: selectedEmployee.avatarKey ?? null,
            }
          : session,
      );
    });
  }, [activeSessionId, selectedEmployee, sessions.length]);

  const activeSession = useMemo(() => {
    return sessions.find((session) => session.id === activeSessionId) || sessions[0] || null;
  }, [activeSessionId, sessions]);

  const updateSession = (sessionId: string, updater: (session: ChatSession) => ChatSession) => {
    setSessions((currentSessions) => currentSessions.map((session) => (session.id === sessionId ? updater(session) : session)));
  };

  const createSession = () => {
    const nextSession = buildInitialSession(selectedEmployee);
    nextSession.id = buildBubbleKey('session');
    nextSession.title = '新对话';
    nextSession.preview = buildAssistantGreeting(selectedEmployee);
    nextSession.messages = [];
    nextSession.updatedAt = dayjs().format('YYYY-MM-DD HH:mm');
    setSessions((currentSessions) => [nextSession, ...currentSessions.filter((session) => session.id !== nextSession.id)]);
    setActiveSessionId(nextSession.id);
    setInputValue('');
  };

  const handleSessionSelect = (sessionId: string) => {
    setActiveSessionId(sessionId);
    setInputValue('');
  };

  const handleSend = async (messageText: string) => {
    const trimmed = messageText.trim();
    if (!trimmed || !selectedEmployee || !activeSession) {
      return;
    }

    const userBubble: ChatBubble = {
      key: buildBubbleKey('user'),
      role: 'user',
      content: trimmed,
    };

    const assistantPlaceholder: ChatBubble = {
      key: buildBubbleKey('assistant'),
      role: 'ai',
      content: '正在思考中...',
    };

    setSending(true);
    setInputValue('');
    updateSession(activeSession.id, (session) => ({
      ...session,
      employeeId: selectedEmployee.id,
      employeeName: selectedEmployee.nickname?.trim() || selectedEmployee.username,
      employeeAvatarKey: selectedEmployee.avatarKey ?? null,
      title: session.messages.length ? session.title : buildSessionTitle(trimmed),
      preview: trimmed,
      messages: [...session.messages, userBubble, assistantPlaceholder],
      updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
    }));

    try {
      const response = await aiService.chat(
        {
          employeeId: selectedEmployee.id,
          conversationId: activeSession.conversationId ?? null,
          message: trimmed,
        },
        { autoRedirectOnUnauthorized: false, silent: true },
      );

      updateSession(activeSession.id, (session) => ({
        ...session,
        conversationId: response.conversationId ?? session.conversationId,
        title: session.title === '新对话' ? buildSessionTitle(trimmed) : session.title,
        preview: response.replyText || trimmed,
        messages: [
          ...session.messages.filter((item) => item.key !== assistantPlaceholder.key),
          {
            key: buildBubbleKey('assistant'),
            role: 'ai',
            content: response.replyText || '我已经收到你的消息。',
          },
        ],
        updatedAt: dayjs(response.replyAt || undefined).isValid()
          ? dayjs(response.replyAt).format('YYYY-MM-DD HH:mm')
          : dayjs().format('YYYY-MM-DD HH:mm'),
      }));
    } catch (error) {
      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
      }));
      message.error(error instanceof Error && error.message ? error.message : '发送失败，请稍后重试');
    } finally {
      setSending(false);
    }
  };

  const conversationItems = useMemo(
    () =>
      sessions.map((session) => ({
        key: session.id,
        label: (
          <Space direction="vertical" size={2} style={{ width: '100%', minWidth: 0 }}>
            <Typography.Text ellipsis strong>
              {session.title}
            </Typography.Text>
            <Typography.Text ellipsis type="secondary" style={{ fontSize: 12 }}>
              {session.preview}
            </Typography.Text>
            <Space wrap size={4}>
              <Tag color="blue">{session.employeeName}</Tag>
              <Tag>{session.updatedAt}</Tag>
            </Space>
          </Space>
        ),
        icon: <RobotOutlined />,
        group: getConversationGroup(session.updatedAt),
      })),
    [sessions],
  );

  const bubbleRole = useMemo(
    () => ({
      user: {
        placement: 'end' as const,
        variant: 'filled' as const,
        shape: 'round' as const,
      },
      ai: {
        placement: 'start' as const,
        variant: 'borderless' as const,
        shape: 'round' as const,
        avatar: <Avatar size={32} icon={<RobotOutlined />} />,
      },
    }),
    [],
  );

  const activeMessageItems = useMemo(
    () =>
      activeSession?.messages.map((item) => ({
        key: item.key,
        role: item.role,
        content: item.content,
      })) || [],
    [activeSession?.messages],
  );

  const emptyWelcome = buildAssistantGreeting(selectedEmployee);
  const canManageEmployees = Boolean((access as Record<string, unknown>).canVisitAiEmployees);
  const hasContent = Boolean(activeSession?.messages?.length);

  return (
    <PageContainer
      title="AI 助手"
      ghost
      className="saas-ai-assistant-page"
      token={{
        paddingInlinePageContainerContent: 24,
        paddingBlockPageContainerContent: 20,
      }}
      extra={
        canManageEmployees ? (
          <Button icon={<SettingOutlined />} onClick={() => history.push('/settings/ai-employees')}>
            数字员工配置
          </Button>
        ) : null
      }
    >
      <ProCard className="saas-ai-assistant-shell" variant="outlined">
        <div className="saas-ai-assistant-shell__sidebar">
          <div className="saas-ai-assistant-shell__sidebar-header">
            <Conversations
              items={conversationItems}
              activeKey={activeSessionId}
              onActiveChange={(key) => handleSessionSelect(String(key))}
              creation={{
                label: '新建对话',
                icon: <PlusOutlined />,
                onClick: createSession,
                align: 'center',
              }}
              groupable
              className="saas-ai-assistant-conversations"
            />
          </div>
          <div className="saas-ai-assistant-shell__sidebar-footer">
            {employeesQuery.isLoading ? (
              <div className="saas-ai-assistant-shell__loading">
                <Spin />
              </div>
            ) : (
              <Space align="center" size={12} className="saas-ai-assistant-shell__employee-card">
                <Avatar size={44} icon={<RobotOutlined />} />
                <Space direction="vertical" size={0} style={{ minWidth: 0 }}>
                  <Typography.Text strong ellipsis>
                    {selectedEmployee?.nickname?.trim() || selectedEmployee?.username || 'AI 助手'}
                  </Typography.Text>
                  <Space wrap size={6}>
                    <Tag color="blue">{selectedEmployee?.defaultLlmServiceTitle || '未绑定 LLM 服务'}</Tag>
                    <Tag color={selectedEmployee?.enabled ? 'green' : 'red'}>
                      {selectedEmployee?.enabled ? '启用中' : '已禁用'}
                    </Tag>
                  </Space>
                </Space>
              </Space>
            )}
          </div>
        </div>

        <div className="saas-ai-assistant-shell__chat">
          <div className="saas-ai-assistant-shell__chat-header">
            <Space size={12} align="center" wrap>
              <Avatar size={48} icon={<RobotOutlined />} />
              <Space direction="vertical" size={0}>
                <Typography.Title level={4} style={{ margin: 0 }}>
                  {activeSession?.employeeName || selectedEmployee?.nickname || 'AI 助手'}
                </Typography.Title>
                <Space wrap size={8}>
                  <Tag color="blue">{selectedEmployee?.defaultLlmServiceTitle || '未绑定 LLM 服务'}</Tag>
                  <Tag color={selectedEmployee?.enabled ? 'green' : 'red'}>
                    {selectedEmployee?.enabled ? '启用中' : '已禁用'}
                  </Tag>
                </Space>
              </Space>
            </Space>
          </div>

          <div className="saas-ai-assistant-shell__chat-body">
            {!hasContent ? (
              <Welcome
                icon={<RobotOutlined />}
                title="你好，有什么可以帮你？"
                description="你可以直接输入问题，或者先创建一个新的对话继续。"
                extra={<Bubble.System content={emptyWelcome} variant="borderless" />}
                className="saas-ai-assistant-shell__welcome"
              />
            ) : (
              <Bubble.List items={activeMessageItems} role={bubbleRole} autoScroll className="saas-ai-assistant-bubbles" />
            )}
          </div>

          <div className="saas-ai-assistant-shell__composer">
            <Sender
              value={inputValue}
              loading={sending}
              disabled={!selectedEmployee}
              onChange={(nextValue) => setInputValue(nextValue)}
              onSubmit={(nextValue) => void handleSend(nextValue)}
              placeholder={selectedEmployee ? '输入消息，按 Enter 发送...' : '暂无可用数字员工'}
            />
          </div>
        </div>
      </ProCard>
    </PageContainer>
  );
};

export default AiAssistantPage;
