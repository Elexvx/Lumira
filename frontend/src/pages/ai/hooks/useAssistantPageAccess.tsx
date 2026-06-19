import { createElement, useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Avatar, Button, Spin, Space, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import type { MenuProps } from 'antd';
import { useParams } from '@umijs/max';
import { useResponsive } from '@/hooks/useResponsive';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Conversations as XConversations } from '@ant-design/x';
import { FileCard } from '@ant-design/x';
import type { FileCardProps } from '@ant-design/x';
import { PlusOutlined, CheckCircleOutlined, CloseCircleOutlined, CopyOutlined, DeleteOutlined, DownloadOutlined, EditOutlined, PushpinOutlined, RobotOutlined, ShareAltOutlined } from '@ant-design/icons';
import { Bubble } from '@ant-design/x';
import { Actions, Sources, Think } from '@ant-design/x';
import { XMarkdown } from '@ant-design/x-markdown';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { confirmAction } from '@/utils/confirm';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB, validateDocumentUploadFile } from '@/utils/uploadValidation';
import { request, requestEventStream } from '@/services/common/request';
import { copyTextToClipboard } from '@/utils/clipboard';
import type {
  AiConversationExportRecord,
  AiConversationMessageRecord,
  AiConversationRecord,
  AiConversationShareDetailRecord,
  AiConversationShareRecord,
  AiEmployeeRecord,
  AiKnowledgeReferenceRecord,
  AiToolPlanRecord,
  FileObjectRecord,
  PagedResult,
} from '@/types/api';
import type { ChatSession } from '../types';
import type { ChatBubble } from '../types';
import type { ComposerAttachment } from '../types';
import type { RouteParams } from '../types';
import { Composer } from '@/pages/ai/components/Composer';
import { MAX_UPLOAD_FILE_COUNT } from '@/pages/files/fileCenter.utils';
import {
  CONVERSATIONS_QUERY_KEY,
  EMPTY_CONVERSATIONS,
  EMPTY_EMPLOYEES,
  buildAssistantGreeting,
  buildBubbleKey,
  buildInitialSession,
  buildSessionFromConversation,
  buildSessionFromShare,
  isDraftSession,
  mapMessageRecord,
  sortSessions,
} from '../utils/sessions';
import { getConversationGroup } from '@/pages/ai/utils/sessions';
import type { AiChatStreamEvent } from '@/services/ai/types';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const resolveNextActiveSessionId = (sessions: ChatSession[], currentSessionId: string, deletedSessionId: string) => {
  if (currentSessionId !== deletedSessionId) {
    return currentSessionId;
  }
  const remaining = sessions.filter((item) => item.id !== deletedSessionId);
  return remaining[0] ? remaining[0].id : 'session-default';
};

const applyConversationTitleUpdate = (session: ChatSession, nextTitle: string) => ({
  ...session,
  title: nextTitle,
  preview: session.preview === session.title ? nextTitle : session.preview,
  updatedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
});

const applyDraftConversationTitleUpdate = (session: ChatSession, nextTitle: string) => ({
  ...session,
  title: nextTitle,
  preview: session.preview === session.title ? nextTitle : session.preview,
});

const toggleConversationPin = (session: ChatSession) => ({
  ...session,
  isPinned: !session.isPinned,
});

const useAiChatEmployeeSelection = ({
  isShareMode,
  employees,
  assistantEmployee,
}: {
  isShareMode: boolean;
  employees: AiEmployeeRecord[];
  assistantEmployee: AiEmployeeRecord | null;
}) => {
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([]);

  useEffect(() => {
    if (isShareMode) return;
    setSelectedEmployeeIds((currentValue) => {
      const validIds = currentValue.filter(
        (employeeId) => employees.some((employee) => employee.id === employeeId) || assistantEmployee?.id === employeeId,
      );
      return validIds.length === currentValue.length ? currentValue : validIds;
    });
  }, [assistantEmployee?.id, employees, isShareMode]);

  const selectedEmployeeOptions = useMemo(() => {
    if (employees.length) return employees;
    return assistantEmployee ? [assistantEmployee] : [];
  }, [assistantEmployee, employees]);

  const employeeById = useMemo(
    () => new Map(selectedEmployeeOptions.map((employee) => [employee.id, employee])),
    [selectedEmployeeOptions],
  );

  const selectedEmployees = useMemo(() => {
    if (isShareMode) return assistantEmployee ? [assistantEmployee] : [];
    return selectedEmployeeIds
      .map((employeeId) => selectedEmployeeOptions.find((employee) => employee.id === employeeId) || null)
      .filter((employee): employee is AiEmployeeRecord => Boolean(employee));
  }, [assistantEmployee, isShareMode, selectedEmployeeIds, selectedEmployeeOptions]);

  const selectedEmployee = selectedEmployees[0] || null;

  return {
    selectedEmployeeIds,
    setSelectedEmployeeIds,
    selectedEmployeeOptions,
    employeeById,
    selectedEmployees,
    selectedEmployee,
  };
};

const useAiChatData = (shareToken: string) => {
  const queryClient = useQueryClient();
  const isShareMode = Boolean(shareToken);

  const employeesQuery = useQuery({
    queryKey: ['ai-assistant-employees'],
    enabled: !isShareMode,
    queryFn: async () =>
      request<PagedResult<AiEmployeeRecord>>('/ai/employees', {
        method: 'GET',
        params: { pageNo: 1, pageSize: 50 },
        ...API_OPTS.NO_REDIRECT,
      }),
  });

  const assistantQuery = useQuery({
    queryKey: ['ai-assistant-default'],
    enabled: !isShareMode,
    queryFn: async () =>
      request<AiEmployeeRecord>('/ai/assistant', {
        method: 'GET',
        ...API_OPTS.SILENT_NO_REDIRECT,
      }).catch((error) => {
        if (error && typeof error === 'object' && 'code' in error && (error as { code?: string }).code === 'A0404') {
          return null;
        }
        throw error;
      }),
    retry: false,
  });

  const conversationsQuery = useQuery({
    queryKey: ['ai-assistant-conversations'],
    enabled: !isShareMode,
    queryFn: async () =>
      request<PagedResult<AiConversationRecord>>('/ai/conversations', {
        method: 'GET',
        params: { pageNo: 1, pageSize: 50 },
        ...API_OPTS.NO_REDIRECT,
      }),
  });

  const shareQuery = useQuery({
    queryKey: ['ai-assistant-share', shareToken],
    enabled: isShareMode && Boolean(shareToken),
    queryFn: async () =>
      request<AiConversationShareDetailRecord>(`/ai/shares/${shareToken}`, {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    retry: false,
  });

  const employees = employeesQuery.data?.records ?? EMPTY_EMPLOYEES;
  const assistantEmployee = assistantQuery.data || null;
  const conversationRecords = conversationsQuery.data?.records ?? EMPTY_CONVERSATIONS;
  const shareSession = useMemo(() => (shareQuery.data ? buildSessionFromShare(shareQuery.data) : null), [shareQuery.data]);
  const employeeSelection = useAiChatEmployeeSelection({
    isShareMode,
    employees,
    assistantEmployee,
  });

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string>('session-default');
  const employeeById = useMemo(
    () => new Map(employeeSelection.selectedEmployee ? [[employeeSelection.selectedEmployee.id, employeeSelection.selectedEmployee]] : []),
    [employeeSelection.selectedEmployee],
  );
  useEffect(() => {
    if (isShareMode) return;
    const records: AiConversationRecord[] = conversationRecords;
    setSessions((currentSessions) => {
      const draftSessions = currentSessions.filter((session) => session.isDraft || !session.conversationId);
      const persistedSessions = records.map((record) => {
        const existingSession = currentSessions.find((session) => session.conversationId === record.id || session.id === String(record.id));
        const recordEmployee = record.employeeId ? employeeById.get(record.employeeId) : null;
        if (!existingSession) return buildSessionFromConversation(record, recordEmployee);

        return {
          ...existingSession,
          id: String(record.id),
          title: record.title?.trim() || existingSession.title,
          preview: record.preview?.trim() || record.title?.trim() || existingSession.preview,
          employeeId: record.employeeId ?? null,
          employeeName: record.employeeName?.trim() || existingSession.employeeName,
          employeeAvatarKey: recordEmployee?.avatarKey ?? existingSession.employeeAvatarKey,
          updatedAt: record.latestMessageAt || record.updateTime || record.createTime || existingSession.updatedAt,
          conversationId: record.id,
          isDraft: false,
          isPinned: Boolean(record.pinned ?? record.isPinned),
        };
      });

      const mergedSessions = sortSessions([...draftSessions, ...persistedSessions]);
      return mergedSessions.length ? mergedSessions : [buildInitialSession(employeeSelection.selectedEmployee)];
    });

    setActiveSessionId((currentActiveSessionId) => {
      if (!records.length) return currentActiveSessionId;
      if (currentActiveSessionId.startsWith('session_') && currentActiveSessionId !== 'session-default') return currentActiveSessionId;
      if (currentActiveSessionId !== 'session-default' && records.some((record) => String(record.id) === currentActiveSessionId)) {
        return currentActiveSessionId;
      }
      return records[0] ? String(records[0].id) : 'session-default';
    });
  }, [conversationRecords, employeeById, isShareMode, employeeSelection.selectedEmployee]);

  useEffect(() => {
    if (!isShareMode || !shareSession) return;
    setSessions([shareSession]);
    setActiveSessionId(shareSession.id);
  }, [isShareMode, shareSession]);

  const activeSession = useMemo(() => sessions.find((session) => session.id === activeSessionId) || sessions[0] || null, [activeSessionId, sessions]);

  const updateSession = useCallback((sessionId: string, updater: (session: ChatSession) => ChatSession) => {
    setSessions((currentSessions) => sortSessions(currentSessions.map((session) => (session.id === sessionId ? updater(session) : session))));
  }, []);
  useEffect(() => {
    if (isShareMode || !activeSession?.conversationId || activeSession.messages.length > 0) return;
    let alive = true;
    const loadMessages = async () => {
      try {
        const records = await request<AiConversationMessageRecord[]>(`/ai/conversations/${activeSession.conversationId}/messages`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        });
        if (!alive) return;
        updateSession(activeSession.id, (session) => ({
          ...session,
          messages: records.map(mapMessageRecord),
        }));
      } catch (error) {
        if (alive) {
          showErrorMessage(error, t('加载对话记录失败', 'Failed to load conversation messages'));
        }
      }
    };
    void loadMessages();
    return () => {
      alive = false;
    };
  }, [activeSession?.conversationId, activeSession?.id, activeSession?.messages.length, isShareMode, updateSession]);

  const handleCreateSession = useCallback(() => {
    if (isShareMode) return;
    if (isDraftSession(activeSession)) {
      message.info({ key: 'ai-assistant-current-new-session', content: t('已经是最新的对话了', 'You are already on the latest conversation') });
      return;
    }
    const nextSession = buildInitialSession(employeeSelection.selectedEmployee);
    nextSession.id = buildBubbleKey('session');
    nextSession.title = t('新对话', 'New conversation');
    nextSession.preview = buildAssistantGreeting(employeeSelection.selectedEmployee);
    nextSession.messages = [];
    nextSession.pendingAttachments = [];
    nextSession.isDraft = true;
    nextSession.updatedAt = dayjs().format('YYYY-MM-DD HH:mm');
    setSessions((currentSessions) =>
      sortSessions([nextSession, ...currentSessions.filter((session) => session.id !== nextSession.id)]),
    );
    setActiveSessionId(nextSession.id);
  }, [activeSession, employeeSelection.selectedEmployee, isShareMode]);

  const handleDeleteConversation = useCallback(
    async (session: ChatSession) => {
      if (session.isDraft || !session.conversationId) {
        setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
        setActiveSessionId((current) => resolveNextActiveSessionId(sessions, current, session.id));
        return;
      }

      await request<boolean>(`/ai/conversations/${session.conversationId}`, {
        method: 'DELETE',
        ...API_OPTS.NO_REDIRECT,
      });
      setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
      setActiveSessionId((current) => resolveNextActiveSessionId(sessions, current, session.id));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
    [queryClient, sessions],
  );

  const handleTogglePinConversation = useCallback(
    async (session: ChatSession) => {
      if (session.isDraft || !session.conversationId) {
        updateSession(session.id, toggleConversationPin);
        return;
      }
      try {
        await request<boolean>(`/ai/conversations/${session.conversationId}`, {
          method: 'PUT',
          data: { pinned: !session.isPinned },
          ...API_OPTS.NO_REDIRECT,
        });
        updateSession(session.id, toggleConversationPin);
        void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
      } catch (error) {
        showErrorMessage(error, t('置顶设置失败', 'Failed to update pinned state'));
      }
    },
    [queryClient, updateSession],
  );

  const handleRenameSession = useCallback(
    async (sessionId: string, nextTitle: string) => {
      const session = sessions.find((item) => item.id === sessionId);
      if (!session) return;
      if (session.isDraft || !session.conversationId) {
        updateSession(session.id, (current) => applyDraftConversationTitleUpdate(current, nextTitle));
        return;
      }
      await request<boolean>(`/ai/conversations/${session.conversationId}`, {
        method: 'PUT',
        data: { title: nextTitle },
        ...API_OPTS.NO_REDIRECT,
      });
      updateSession(session.id, (current) => applyConversationTitleUpdate(current, nextTitle));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
    [queryClient, sessions, updateSession],
  );

  const handleSessionSelect = (sessionId: string) => {
    const nextSession = sessions.find((session) => session.id === sessionId);
    if (!isShareMode && nextSession) {
      employeeSelection.setSelectedEmployeeIds(nextSession.employeeId ? [nextSession.employeeId] : []);
    }
    setActiveSessionId(sessionId);
  };

  return {
    isShareMode,
    shareQuery,
    ...employeeSelection,
    sessions,
    activeSessionId,
    activeSession,
    employeeById,
    setSessions,
    setActiveSessionId,
    updateSession,
    handleCreateSession,
    handleDeleteConversation,
    handleTogglePinConversation,
    handleRenameSession,
    handleSessionSelect,
  };
};

type AiChatStreamTracker = {
  response?: import('@/types/api').AiChatResponseRecord | null;
  error?: Error | null;
  replyText: string;
  toolResult?: import('@/types/api').AiToolExecuteResultRecord | null;
};

const createAiChatStreamTracker = (): AiChatStreamTracker => ({
  response: null,
  error: null,
  replyText: '',
  toolResult: null,
});

type UpdateSession = (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;

const updateAssistantMessage = (
  updateSession: UpdateSession,
  sessionId: string,
  placeholderKey: string,
  updater: (current: ChatBubble) => ChatBubble,
) => {
  updateSession(sessionId, (session) => ({
    ...session,
    messages: session.messages.map((item) => (item.key === placeholderKey ? updater(item) : item)),
  }));
};

const handleAiChatStreamStatusEvent = ({
  event,
  sessionId,
  placeholderKey,
  updateSession,
}: {
  event: AiChatStreamEvent;
  sessionId: string;
  placeholderKey: string;
  updateSession: UpdateSession;
}) => {
  if (event.type === 'status' && event.message) {
    updateAssistantMessage(updateSession, sessionId, placeholderKey, (current) => ({
      ...current,
      thinkingContent: [current.thinkingContent, event.message].filter(Boolean).join('\n'),
      thinkingLoading: true,
    }));
    return true;
  }

  return false;
};

const handleAiChatStreamThinkingEvent = ({
  event,
  sessionId,
  placeholderKey,
  updateSession,
}: {
  event: AiChatStreamEvent;
  sessionId: string;
  placeholderKey: string;
  updateSession: UpdateSession;
}) => {
  if (event.type === 'thinking' && event.delta) {
    updateAssistantMessage(updateSession, sessionId, placeholderKey, (current) => ({
      ...current,
      thinkingContent: `${current.thinkingContent || ''}${event.delta}`,
      thinkingLoading: true,
    }));
    return true;
  }

  return false;
};

const handleAiChatStreamDeltaEvent = ({
  event,
  sessionId,
  placeholderKey,
  updateSession,
  tracker,
  previewUpdate,
}: {
  event: AiChatStreamEvent;
  sessionId: string;
  placeholderKey: string;
  updateSession: UpdateSession;
  tracker: AiChatStreamTracker;
  previewUpdate?: (replyText: string) => void;
}) => {
  if (event.type === 'delta' && event.delta) {
    tracker.replyText += event.delta;
    previewUpdate?.(tracker.replyText);
    updateAssistantMessage(updateSession, sessionId, placeholderKey, (current) => ({
      ...current,
      content: tracker.replyText,
      thinkingLoading: false,
    }));
    return true;
  }

  return false;
};

const handleAiChatStreamToolEvent = ({
  event,
  sessionId,
  placeholderKey,
  updateSession,
  tracker,
  toolBlockedMessage = t('该操作已被平台防护规则拦截。', 'This operation was blocked by platform protection rules.'),
  toolProposalMessage = t('我已生成系统操作计划，请确认后执行。', 'I have generated a system operation plan. Please confirm to proceed.'),
  toolResultMessage = t('系统操作已完成。', 'System operation completed.'),
}: {
  event: AiChatStreamEvent;
  sessionId: string;
  placeholderKey: string;
  updateSession: UpdateSession;
  tracker: AiChatStreamTracker;
  toolBlockedMessage?: string;
  toolProposalMessage?: string;
  toolResultMessage?: string;
}) => {
  if ((event.type === 'tool_proposal' || event.type === 'tool_blocked') && event.toolPlan) {
    updateAssistantMessage(updateSession, sessionId, placeholderKey, (current) => ({
      ...current,
      content: event.message || (event.type === 'tool_blocked' ? toolBlockedMessage : toolProposalMessage),
      thinkingLoading: false,
      toolPlan: event.toolPlan,
    }));
    return true;
  }

  if (event.type === 'tool_result' && event.toolResult) {
    tracker.toolResult = event.toolResult;
    updateAssistantMessage(updateSession, sessionId, placeholderKey, (current) => ({
      ...current,
      content: event.toolResult?.message || toolResultMessage,
      thinkingLoading: false,
      toolResult: event.toolResult,
    }));
    return true;
  }

  return false;
};

const handleAiChatStreamDoneEvent = ({
  event,
  tracker,
}: {
  event: AiChatStreamEvent;
  tracker: AiChatStreamTracker;
}) => {
  if (event.type === 'done' && event.response) {
    tracker.response = event.response;
    return true;
  }

  return false;
};

const handleAiChatStreamErrorEvent = ({
  event,
  tracker,
  errorMessage = t('发送失败，请稍后重试', 'Send failed. Please try again later'),
}: {
  event: AiChatStreamEvent;
  tracker: AiChatStreamTracker;
  errorMessage?: string;
}) => {
  if (event.type === 'error') {
    tracker.error = new Error(event.message || errorMessage);
    return true;
  }

  return false;
};

type ApplyAiChatStreamEventParams = {
  event: AiChatStreamEvent;
  sessionId: string;
  placeholderKey: string;
  updateSession: UpdateSession;
  tracker: AiChatStreamTracker;
  previewUpdate?: (replyText: string) => void;
  toolBlockedMessage?: string;
  toolProposalMessage?: string;
  toolResultMessage?: string;
  errorMessage?: string;
};

const applyAiChatStreamEvent = ({
  event,
  sessionId,
  placeholderKey,
  updateSession,
  tracker,
  previewUpdate,
  toolBlockedMessage,
  toolProposalMessage,
  toolResultMessage,
  errorMessage,
}: ApplyAiChatStreamEventParams) => {
  if (handleAiChatStreamStatusEvent({ event, sessionId, placeholderKey, updateSession })) return;
  if (handleAiChatStreamThinkingEvent({ event, sessionId, placeholderKey, updateSession })) return;
  if (handleAiChatStreamDeltaEvent({ event, sessionId, placeholderKey, updateSession, tracker, previewUpdate })) return;
  if (handleAiChatStreamToolEvent({
    event,
    sessionId,
    placeholderKey,
    updateSession,
    tracker,
    toolBlockedMessage,
    toolProposalMessage,
    toolResultMessage,
  })) return;
  if (handleAiChatStreamDoneEvent({ event, tracker })) return;
  handleAiChatStreamErrorEvent({ event, tracker, errorMessage });
};

const AI_ATTACHMENT_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt', 'png', 'jpg', 'jpeg', 'gif', 'bmp'];
const AI_CHAT_ATTACHMENT_BUCKET = 'ai_chat';

const getFileExtension = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || '';

const renderAttachmentCardList = (
  attachments: ComposerAttachment[],
  options?: {
    removable?: boolean;
    onRemove?: (fileId: number) => void;
    className?: string;
  },
) =>
  (
    <FileCard.List
      className={options?.className}
      items={attachments.map((attachment) => ({
        key: attachment.fileId,
        name: attachment.originalFileName,
        byte: attachment.fileSizeBytes ?? undefined,
        description: attachment.fileSizeLabel || undefined,
        icon: (() => {
          const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
          if (['xls', 'xlsx'].includes(extension)) return 'excel';
          if (['doc', 'docx'].includes(extension)) return 'word';
          if (['ppt', 'pptx'].includes(extension)) return 'ppt';
          if (extension === 'pdf') return 'pdf';
          if (extension === 'md') return 'markdown';
          if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
          return 'default';
        })() as FileCardProps['icon'],
        type: ((): FileCardProps['type'] => {
          const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
          if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
          return 'file';
        })(),
        src: attachment.previewUrl || attachment.publicUrl || attachment.downloadUrl || undefined,
      }))}
      size="small"
      removable={options?.removable}
      overflow="wrap"
      onRemove={(item) => {
        const fileId = Number(item.key);
        if (Number.isFinite(fileId)) {
          options?.onRemove?.(fileId);
        }
      }}
    />
  );

const mapFileObjectToAttachment = (file: {
  id: number;
  originalFileName: string;
  fileExtension?: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  fileSizeLabel?: string | null;
  publicUrl?: string | null;
  previewUrl?: string | null;
  downloadUrl?: string | null;
  previewMode?: string | null;
}): ComposerAttachment => ({
  id: `file_${file.id}_${Date.now()}`,
  fileId: file.id,
  originalFileName: file.originalFileName,
  fileExtension: file.fileExtension,
  mimeType: file.mimeType,
  fileSizeBytes: file.fileSizeBytes,
  fileSizeLabel: file.fileSizeLabel,
  publicUrl: file.publicUrl,
  previewUrl: file.previewUrl,
  downloadUrl: file.downloadUrl,
  previewMode: file.previewMode,
});

const formatExportFileName = (title: string, format: 'markdown' | 'text') => {
  const safeTitle = title.trim().replaceAll(/[\\/:*?"<>|]/g, '_') || 'ai-conversation';
  return `${safeTitle}.${format === 'markdown' ? 'md' : 'txt'}`;
};

const buildExportContent = (session: ChatSession, format: 'markdown' | 'text') => {
  const markdown = format === 'markdown';
  const lines: string[] = [];
  lines.push(markdown ? `# ${session.title}` : session.title);
  lines.push('');
  lines.push(`${t('AI 员工', 'AI employee')}: ${session.employeeName}`);
  lines.push(`${t('更新时间', 'Updated at')}: ${session.updatedAt}`);
  lines.push('');

  session.messages.forEach((messageItem) => {
    lines.push(markdown ? `## ${messageItem.role === 'user' ? t('用户', 'User') : 'AI'}` : `${messageItem.role === 'user' ? t('用户', 'User') : 'AI'}:`);
    lines.push(messageItem.content);
    if (messageItem.attachments.length) {
      lines.push('');
      lines.push(t('附件:', 'Attachments:'));
      messageItem.attachments.forEach((attachment) => {
        lines.push(`- ${attachment.originalFileName}`);
      });
    }
    lines.push('');
  });

  return lines.join('\n').trim();
};

const downloadText = (content: string, fileName: string, mimeType: string) => {
  const blob = new Blob([content], { type: mimeType });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  window.URL.revokeObjectURL(url);
};

const buildWelcomePanel = (isShareMode: boolean, sectionGap: number, isMobile: boolean) => (
  <div className="saas-ai-assistant-shell__welcome">
    <Space direction="vertical" align="center" size={sectionGap}>
      <Avatar size={resolveResponsiveValue(APP_SPACING.avatarSize.normal, isMobile)} icon={<RobotOutlined />} style={{ backgroundColor: '#1890ff' }} />
      <Typography.Title level={4} style={{ margin: 0 }}>
        {isShareMode ? t('分享会话为空', 'Shared conversation is empty') : t('你好，我是企业 AI 助手', 'Hello, I am your enterprise AI assistant')}
      </Typography.Title>
      <Typography.Text type="secondary">{isShareMode ? t('这条分享会话还没有消息。', 'This shared conversation has no messages yet.') : t('可以帮你查资料、写方案、拆任务。', 'I can help with research, planning, and breaking down tasks.')}</Typography.Text>
    </Space>
  </div>
);

const buildConversationMenu = (
  session: ChatSession,
  actions: {
    openRenameModal: (session: ChatSession) => void;
    handleTogglePinConversation: (session: ChatSession) => void;
    handleShareConversation: (session: ChatSession) => void;
    handleExportConversation: (format: 'markdown' | 'text', session: ChatSession) => void;
    handleDeleteConversation: (session: ChatSession) => void;
  },
): MenuProps => {
  const canShare = Boolean(session.conversationId);
  return {
    items: [
      { key: 'rename', label: t('重命名', 'Rename'), icon: <EditOutlined /> },
      { key: 'pin', label: session.isPinned ? t('取消置顶', 'Unpin') : t('置顶', 'Pin'), icon: <PushpinOutlined /> },
      ...(canShare
        ? [
            { key: 'share', label: t('复制分享链接', 'Copy share link'), icon: <ShareAltOutlined /> },
            { key: 'export-markdown', label: t('导出 Markdown', 'Export Markdown'), icon: <DownloadOutlined /> },
            { key: 'export-text', label: t('导出文本', 'Export text'), icon: <DownloadOutlined /> },
          ]
        : []),
      { type: 'divider' as const },
      { key: 'delete', label: t('删除', 'Delete'), icon: <DeleteOutlined />, danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'rename') actions.openRenameModal(session);
      else if (key === 'pin') actions.handleTogglePinConversation(session);
      else if (key === 'share') actions.handleShareConversation(session);
      else if (key === 'export-markdown') actions.handleExportConversation('markdown', session);
      else if (key === 'export-text') actions.handleExportConversation('text', session);
      else if (key === 'delete') actions.handleDeleteConversation(session);
    },
  };
};

const MarkdownMessage = ({ content }: { content: string }) => <XMarkdown content={content} openLinksInNewTab escapeRawHtml />;

const AiMessageContent = ({
  item,
  visibleReplyText,
  onConfirmTool,
  confirmingToolId,
}: {
  item: ChatBubble;
  visibleReplyText?: string;
  onConfirmTool?: (plan: AiToolPlanRecord) => void;
  confirmingToolId?: number | null;
}) => {
  const content = visibleReplyText ?? item.content;
  const thinkingContent = item.thinkingContent?.trim();
  const shouldRenderThinking = Boolean(thinkingContent || item.thinkingLoading);
  const plan = item.toolPlan;
  const blocked = plan ? plan.status === 'BLOCKED' || plan.policyVerdict === 'DENY' || plan.supervisorVerdict === 'DENY' : false;
  const result = item.toolResult;
  const args = plan?.arguments || {};

  return (
    <div className="saas-ai-assistant-ai-content">
      {shouldRenderThinking ? (
        <Think
          title={thinkingContent ? '处理过程' : '正在生成回复'}
          loading={item.thinkingLoading}
          defaultExpanded={Boolean(thinkingContent)}
          classNames={{
            root: 'saas-ai-assistant-thinking',
            status: 'saas-ai-assistant-thinking__status',
            content: 'saas-ai-assistant-thinking__content',
          }}
        >
          {thinkingContent ? (
            <MarkdownMessage content={thinkingContent} />
          ) : (
            <div className="saas-ai-assistant-thinking__loading">
              <Spin size="small" />
              <span>{t('正在调用模型并生成回复。', 'Calling the model and generating a reply.')}</span>
            </div>
          )}
        </Think>
      ) : null}
      {plan ? (
        <div className={`saas-ai-tool-card ${blocked ? 'saas-ai-tool-card--blocked' : ''}`}>
          <div className="saas-ai-tool-card__head">
            <span className="saas-ai-tool-card__title">{plan.toolName || plan.toolCode}</span>
            <Tag color={blocked ? 'red' : plan.riskLevel === 'HIGH' ? 'orange' : 'blue'}>{plan.riskLevel || 'MEDIUM'}</Tag>
          </div>
          <div className="saas-ai-tool-card__summary">{plan.summary || t('AI 已生成一个系统操作计划。', 'AI has generated a system operation plan.')}</div>
          <div className="saas-ai-tool-card__meta">
            <span>{t('权限：', 'Permission: ')}{plan.permissionKey || t('按当前用户权限', 'Current user permission')}</span>
            <span>{t('监督：', 'Supervision: ')}{plan.supervisorVerdict || 'REQUIRE_CONFIRM'}</span>
          </div>
          {Object.keys(args).length ? <pre className="saas-ai-tool-card__args">{JSON.stringify(args, null, 2)}</pre> : null}
          {plan.policyMessage || plan.supervisorMessage ? (
            <Alert
              type={blocked ? 'error' : 'warning'}
              showIcon
              message={plan.policyMessage || plan.supervisorMessage}
              className="saas-ai-tool-card__alert"
            />
          ) : null}
          {result ? (
            <Alert
              type={result.resultStatus === 'SUCCESS' ? 'success' : 'error'}
              showIcon
              message={result.message || (result.resultStatus === 'SUCCESS' ? t('系统操作已完成', 'System operation completed') : t('系统操作失败', 'System operation failed'))}
              className="saas-ai-tool-card__alert"
            />
          ) : (
            <div className="saas-ai-tool-card__actions">
              <Button
                type="primary"
                size="small"
                icon={<CheckCircleOutlined />}
                disabled={blocked || !onConfirmTool || Boolean(item.toolPlan?.id && confirmingToolId === item.toolPlan.id)}
                loading={Boolean(item.toolPlan?.id && confirmingToolId === item.toolPlan.id)}
                onClick={() => onConfirmTool?.(plan)}
              >
                {t('确认执行', 'Confirm execution')}
              </Button>
              <Button size="small" icon={<CloseCircleOutlined />} disabled={Boolean(item.toolPlan?.id && confirmingToolId === item.toolPlan.id)}>
                {t('取消', 'Cancel')}
              </Button>
            </div>
          )}
        </div>
      ) : null}
      {content ? (
        <div className="saas-ai-assistant-markdown">
          <MarkdownMessage content={content} />
        </div>
      ) : null}
    </div>
  );
};

const renderSourcesSection = (references?: AiKnowledgeReferenceRecord[] | null) => {
  if (!references?.length) {
    return null;
  }

  const dedupedReferences = Array.from(new Map<number, AiKnowledgeReferenceRecord>(references.map((reference) => [reference.chunkId, reference])).values()).slice(0, 6);
  const sourceItems = dedupedReferences.map((reference) => ({
    key: reference.chunkId,
    title: reference.documentTitle || reference.originalFileName || `${t('知识片段', 'Knowledge chunk')} #${reference.chunkId}`,
    description: reference.knowledgeBaseName
      ? `${t('知识库', 'Knowledge base')}: ${reference.knowledgeBaseName}${reference.chunkIndex != null ? ` ${t('· 分片', ' · Chunk')} #${reference.chunkIndex + 1}` : ''}`
      : reference.chunkIndex != null
        ? `${t('分片', 'Chunk')} #${reference.chunkIndex + 1}`
        : t('知识引用', 'Knowledge reference'),
  }));

  return (
    <Sources
      classNames={{
        root: 'saas-ai-assistant-sources',
        title: 'saas-ai-assistant-sources__title',
        content: 'saas-ai-assistant-sources__content',
      }}
      title={`${t('参考来源', 'References')} (${sourceItems.length})`}
      expandIconPosition="end"
      items={sourceItems}
    />
  );
};

const MessageBubbleFooter = ({
  item,
  onCopy,
}: {
  item: ChatBubble;
  onCopy: (text: string) => void;
}) => (
  <div className="saas-ai-assistant-bubble__footer">
    {item.attachments.length ? (
      <div className="saas-ai-assistant-message-attachments">
        {renderAttachmentCardList(item.attachments, {
          className: 'saas-ai-assistant-file-card-list saas-ai-assistant-file-card-list--message',
        })}
      </div>
    ) : null}
    {renderSourcesSection(item.references)}
    <div className="saas-ai-assistant-bubble__actions">
      <Actions
        items={[
          {
            key: 'copy',
            label: t('复制', 'Copy'),
            icon: <CopyOutlined />,
            onItemClick: () => onCopy(item.content),
          },
        ]}
        variant="borderless"
      />
    </div>
  </div>
);

type ConversationItem = {
  key: string;
  label: string;
  isPinned?: boolean;
  group?: string;
  disabled?: boolean;
};

const renderConversationsPanel = (
  items: ConversationItem[],
  activeKey: string,
  isShareMode: boolean,
  onActiveChange: (key: string) => void,
  onCreateSession: () => void,
  buildMenu: (conversationKey: string) => MenuProps,
) => (
  <XConversations
    items={items}
    activeKey={activeKey}
    onActiveChange={onActiveChange}
    creation={
      isShareMode
        ? undefined
        : {
            label: t('新建对话', 'New conversation'),
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

const buildAiChatSendBubbles = (activeSession: ChatSession, trimmedMessage: string) => {
  const draftAttachments = activeSession.pendingAttachments;
  const userBubble = {
    key: `bubble_user_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    role: 'user' as const,
    content: trimmedMessage,
    attachments: draftAttachments,
  };
  const assistantPlaceholder = {
    key: `bubble_ai_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    role: 'ai' as const,
    content: '',
    attachments: [],
    thinkingLoading: true,
  };

  return { draftAttachments, userBubble, assistantPlaceholder };
};

const buildAiChatRequestEmployee = (
  requestEmployeeIds: number[],
  employeeById: Map<number, AiEmployeeRecord>,
  activeSession: ChatSession,
) => {
  const requestEmployeeId = requestEmployeeIds.length === 1 ? requestEmployeeIds[0] : null;
  const requestEmployees = requestEmployeeIds.map((id) => employeeById.get(id) || null).filter(Boolean) as AiEmployeeRecord[];
  const requestEmployee = requestEmployees[0] || null;
  const requestEmployeeName =
    requestEmployees.length > 1
      ? t('{count} 个 Agent 协同', '{count} agents collaborating').replace('{count}', String(requestEmployees.length))
      : requestEmployee?.nickname?.trim() || requestEmployee?.username || activeSession.employeeName || t('AI 助手', 'AI assistant');

  return {
    requestEmployeeId,
    requestEmployeeName,
    requestEmployee,
  };
};

const commitAiChatSendSuccess = (
  updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void,
  params: {
    sessionId: string;
    placeholderKey: string;
    responseConversationId: number | null;
    activeSession: ChatSession;
    trimmedMessage: string;
    replyText?: string | null;
    replyAt?: string | null;
    response: {
      replyText?: string | null;
      thinkingContent?: string | null;
      references?: ChatSession['messages'][number]['references'];
      toolPlan?: ChatSession['messages'][number]['toolPlan'];
      toolResult?: ChatSession['messages'][number]['toolResult'];
    };
    setActiveSessionId: (id: string) => void;
  },
) => {
  const responseSessionId = params.responseConversationId ? String(params.responseConversationId) : params.activeSession.id;

  updateSession(params.activeSession.id, (session) => ({
    ...session,
    id: responseSessionId,
    conversationId: params.responseConversationId,
    isDraft: false,
    title: session.conversationId ? session.title : params.trimmedMessage,
    preview: params.replyText || params.trimmedMessage,
    updatedAt: dayjs(params.replyAt || undefined).isValid()
      ? dayjs(params.replyAt).format('YYYY-MM-DD HH:mm')
      : dayjs().format('YYYY-MM-DD HH:mm'),
  }));

  updateSession(responseSessionId, (session) => ({
    ...session,
    messages: [
      ...session.messages.filter((item) => item.key !== params.placeholderKey),
      {
        key: `${params.placeholderKey}_final`,
        role: 'ai',
        content: params.replyText || t('我已经收到你的消息。', 'I have received your message.'),
        attachments: [],
        thinkingContent: params.response.thinkingContent,
        references: params.response.references,
        toolPlan: params.response.toolPlan,
        toolResult: params.response.toolResult,
      },
    ],
  }));

  if (params.responseConversationId) {
    params.setActiveSessionId(responseSessionId);
  }
};

const rollbackAiChatSendFailure = (
  updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void,
  params: {
    sessionId: string;
    placeholderKey: string;
    draftAttachments: ChatSession['pendingAttachments'];
  },
) => {
  updateSession(params.sessionId, (session) => ({
    ...session,
    pendingAttachments: params.draftAttachments,
    messages: session.messages.filter((item) => item.key !== params.placeholderKey),
  }));
};

export const useAssistantPageAccess = () => {
  const queryClient = useQueryClient();
  const params = useParams<RouteParams>();
  const shareToken = params.token?.trim() || '';
  const {
    isShareMode,
    shareQuery,
    sessions,
    activeSessionId,
    activeSession,
    selectedEmployees,
    selectedEmployeeOptions,
    employeeById,
    setActiveSessionId,
    setSelectedEmployeeIds,
    updateSession,
    handleCreateSession,
    handleDeleteConversation,
    handleTogglePinConversation,
    handleRenameSession,
    handleSessionSelect,
  } = useAiChatData(shareToken);
  const [sending, setSending] = useState(false);
  const [attachmentUploading, setAttachmentUploading] = useState(false);
  const handleSend = async (messageText: string, opts: { enableThinking?: boolean; employeeIds?: number[] } = {}) => {
    setSending(true);
    try {
      const trimmed = messageText.trim();
      if (!trimmed || !activeSession || isShareMode) return;

      const requestEmployeeIds = opts.employeeIds ?? selectedEmployees.map((employee) => employee.id);
      const { draftAttachments, userBubble, assistantPlaceholder } = buildAiChatSendBubbles(activeSession, trimmed);
      const { requestEmployeeId, requestEmployeeName, requestEmployee } = buildAiChatRequestEmployee(requestEmployeeIds, employeeById, activeSession);
      const sessionUpdate = {
        employeeId: requestEmployeeId,
        employeeName: requestEmployeeName,
        employeeAvatarKey: requestEmployee?.avatarKey ?? activeSession.employeeAvatarKey ?? null,
        title: activeSession.conversationId ? activeSession.title : trimmed,
        preview: trimmed,
        messages: [...activeSession.messages, userBubble, assistantPlaceholder],
        pendingAttachments: [],
        updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
      };
      const requestPayload = {
        employeeId: requestEmployeeId,
        employeeIds: requestEmployeeIds.length > 1 ? requestEmployeeIds : undefined,
        conversationId: activeSession.conversationId ?? null,
        message: trimmed,
        enableThinking: opts.enableThinking ?? null,
        attachments: draftAttachments.map((attachment) => ({ fileId: attachment.fileId })),
      };

      updateSession(activeSession.id, (session) => ({
        ...session,
        ...sessionUpdate,
      }));

      try {
        const streamState = createAiChatStreamTracker();

        await requestEventStream('/ai/chat/stream', {
          method: 'POST',
          data: requestPayload,
          ...API_OPTS.SILENT_NO_REDIRECT,
          onEvent: ({ data }) => {
            applyAiChatStreamEvent({
              event: JSON.parse(data) as AiChatStreamEvent,
              sessionId: activeSession.id,
              placeholderKey: assistantPlaceholder.key,
              updateSession,
              tracker: streamState,
              previewUpdate: (replyText) =>
                updateSession(activeSession.id, (session) => ({
                  ...session,
                  preview: replyText || session.preview,
                })),
            });
          },
        });

        if (streamState.error) throw streamState.error;
        const response = streamState.response;
        if (!response) throw new Error(t('AI 回复生成失败', 'Failed to generate AI reply'));

        const responseConversationId = response.conversationId ?? activeSession.conversationId ?? null;

        commitAiChatSendSuccess(updateSession, {
          sessionId: activeSession.id,
          placeholderKey: assistantPlaceholder.key,
          responseConversationId,
          activeSession,
          trimmedMessage: trimmed,
          replyText: response.replyText,
          replyAt: response.replyAt,
          response: {
            replyText: response.replyText,
            thinkingContent: response.thinkingContent,
            references: response.references,
            toolPlan: response.toolPlan,
            toolResult: response.toolResult,
          },
          setActiveSessionId,
        });

        void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
      } catch (error) {
        rollbackAiChatSendFailure(updateSession, {
          sessionId: activeSession.id,
          placeholderKey: assistantPlaceholder.key,
          draftAttachments,
        });
        throw error;
      }
    } catch (error) {
      showErrorMessage(error, t('发送失败，请稍后重试', 'Send failed. Please try again later'));
    } finally {
      setSending(false);
    }
  };
  const uploadAttachments = async (
    files: File[],
    options: {
      isShareMode: boolean;
      activeSession: ChatSession | null;
      updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;
    },
  ) => {
    const { isShareMode, activeSession, updateSession } = options;
    if (isShareMode || !activeSession) return;

    const allowedFiles = files.filter((file) => {
      const validationMessage = validateDocumentUploadFile(file, {
        allowedExtensions: AI_ATTACHMENT_EXTENSIONS,
        maxSizeMb: DEFAULT_DOCUMENT_UPLOAD_MAX_SIZE_MB,
        allowedTypeLabelZh: `${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join('、')} 文件`,
        allowedTypeLabelEn: `${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join(', ')} files`,
      });
      if (validationMessage) {
        message.warning(validationMessage);
        return false;
      }
      return true;
    });

    if (!allowedFiles.length) {
      message.error(`仅支持 ${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join('、')} 文件`);
      return;
    }

    const remainingSlots = MAX_UPLOAD_FILE_COUNT - activeSession.pendingAttachments.length;
    if (remainingSlots <= 0) {
      message.warning(`一次最多选择 ${MAX_UPLOAD_FILE_COUNT} 个附件`);
      return;
    }

    const nextFiles = allowedFiles.slice(0, remainingSlots);
    setAttachmentUploading(true);
    try {
      const uploadedAttachments: ComposerAttachment[] = [];
      for (const file of nextFiles) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('category', t('AI 会话附件', 'AI conversation attachments'));
        formData.append('tags', 'ai,conversation');
        formData.append('remark', activeSession.title);
        formData.append('bucket', AI_CHAT_ATTACHMENT_BUCKET);
        const record = await request<FileObjectRecord>('/v1/files/upload', {
          method: 'POST',
          headers: {},
          data: formData,
          ...API_OPTS.NO_REDIRECT,
          silent: true,
        });
        uploadedAttachments.push(mapFileObjectToAttachment(record));
      }

      updateSession(activeSession.id, (session) => ({
        ...session,
        pendingAttachments: [...session.pendingAttachments, ...uploadedAttachments],
      }));
      message.success(`已添加 ${uploadedAttachments.length} 个附件`);
    } catch (error) {
      showErrorMessage(error, t('附件上传失败', 'Attachment upload failed'));
    } finally {
      setAttachmentUploading(false);
    }
  };
  const handleRemoveDraftAttachment = (
    fileId: number,
    options: {
      activeSession: ChatSession | null;
      updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;
    },
  ) => {
    const { activeSession, updateSession } = options;
    if (!activeSession) return;
    updateSession(activeSession.id, (session) => ({
      ...session,
      pendingAttachments: session.pendingAttachments.filter((attachment) => attachment.fileId !== fileId),
    }));
  };
  const [toolSending, setToolSending] = useState(false);
  const [confirmingToolId, setConfirmingToolId] = useState<number | null>(null);
  const replaceAssistantPlaceholderWithResponse = useCallback(
    (
      sessionId: string,
      placeholderKey: string,
      responseContent: {
        replyText?: string | null;
        thinkingContent?: string | null;
        references?: ChatSession['messages'][number]['references'];
        toolPlan?: ChatSession['messages'][number]['toolPlan'];
        toolResult?: ChatSession['messages'][number]['toolResult'];
        fallbackMessage: string;
      },
    ) => {
      updateSession(sessionId, (session) => ({
        ...session,
        messages: [
          ...session.messages.filter((item) => item.key !== placeholderKey),
          {
            key: `${placeholderKey}_final`,
            role: 'ai',
            content: responseContent.replyText || responseContent.fallbackMessage,
            attachments: [],
            thinkingContent: responseContent.thinkingContent,
            references: responseContent.references,
            toolPlan: responseContent.toolPlan,
            toolResult: responseContent.toolResult,
          },
        ],
      }));
    },
    [updateSession],
  );
  const handleConfirmTool = useCallback(
    async (plan: AiToolPlanRecord) => {
      if (!activeSession || isShareMode || confirmingToolId) return;

      const confirmText = `${t('确认执行：', 'Confirm execution: ')}${plan.summary || plan.toolName || plan.toolCode}`;
      const userBubble: ChatBubble = { key: buildBubbleKey('user'), role: 'user', content: confirmText, attachments: [] };
      const assistantPlaceholder: ChatBubble = { key: buildBubbleKey('assistant'), role: 'ai', content: '', attachments: [], thinkingLoading: true, toolPlan: plan };

      setConfirmingToolId(plan.id);
      setToolSending(true);
      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: [...session.messages, userBubble, assistantPlaceholder],
        updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
      }));

      try {
        const streamState = createAiChatStreamTracker();

        await requestEventStream('/ai/chat/stream', {
          method: 'POST',
          data: {
            employeeId: plan.employeeId ?? activeSession.employeeId ?? null,
            conversationId: activeSession.conversationId ?? plan.conversationId ?? null,
            pendingToolCallId: plan.id,
            message: confirmText,
            confirmed: true,
          },
          ...API_OPTS.SILENT_NO_REDIRECT,
          onEvent: ({ data }) => {
            applyAiChatStreamEvent({
              event: JSON.parse(data) as AiChatStreamEvent,
              sessionId: activeSession.id,
              placeholderKey: assistantPlaceholder.key,
              updateSession,
              tracker: streamState,
              errorMessage: t('系统操作执行失败', 'System operation execution failed'),
            });
          },
        });

        if (streamState.error) throw streamState.error;
        replaceAssistantPlaceholderWithResponse(activeSession.id, assistantPlaceholder.key, {
          replyText: streamState.response?.replyText || streamState.replyText || streamState.toolResult?.message,
          toolResult: streamState.response?.toolResult || streamState.toolResult,
          fallbackMessage: t('系统操作已完成。', 'System operation completed.'),
        });
        void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
      } catch (error) {
        updateSession(activeSession.id, (session) => ({
          ...session,
          messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
        }));
        showErrorMessage(error, t('系统操作执行失败', 'System operation execution failed'));
      } finally {
        setToolSending(false);
        setConfirmingToolId(null);
      }
    },
    [activeSession, confirmingToolId, isShareMode, queryClient, replaceAssistantPlaceholderWithResponse, updateSession],
  );
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [renameTargetSessionId, setRenameTargetSessionId] = useState<string | null>(null);
  const currentExportSession = useMemo(() => activeSession || sessions[0] || null, [activeSession, sessions]);
  const [mobilePanel, setMobilePanel] = useState<'chat' | 'sessions'>('chat');
  const handleCopyMessage = useCallback(async (content: string) => {
    try {
      await copyTextToClipboard(content);
      message.success('已复制');
    } catch {
      message.error('复制失败');
    }
  }, []);
  const handleShareConversation = useCallback(async (session?: ChatSession | null) => {
    const targetSession = session || currentExportSession;
    if (!targetSession?.conversationId) {
      message.warning('请先发送至少一条消息后再分享');
      return;
    }
    try {
      const share = await request<AiConversationShareRecord>(`/ai/conversations/${targetSession.conversationId}/share`, {
        method: 'POST',
        ...API_OPTS.NO_REDIRECT,
      });
      const shareUrl = new URL(`/ai/share/${share.shareToken}`, window.location.origin).toString();
      await copyTextToClipboard(shareUrl);
      message.success('分享链接已复制');
    } catch (error) {
      showErrorMessage(error, t('创建分享链接失败', 'Failed to create share link'));
    }
  }, [currentExportSession]);
  const handleExportConversation = useCallback(async (format: 'markdown' | 'text', session?: ChatSession | null) => {
    const targetSession = session || currentExportSession;
    if (!targetSession) {
      message.warning('暂无可导出的会话');
      return;
    }
    try {
      if (targetSession.conversationId) {
        const exportResult = await request<AiConversationExportRecord>(`/ai/conversations/${targetSession.conversationId}/export`, {
          method: 'GET',
          params: { format },
          ...API_OPTS.NO_REDIRECT,
        });
        downloadText(exportResult.content, exportResult.fileName, exportResult.mimeType);
        return;
      }
      const content = buildExportContent(targetSession, format);
      const fileName = formatExportFileName(targetSession.title, format);
      downloadText(content, fileName, format === 'markdown' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8');
    } catch (error) {
      showErrorMessage(error, t('导出失败', 'Export failed'));
    }
  }, [currentExportSession]);
  const handleDeleteConversationWithConfirm = useCallback(
    (session: ChatSession) => {
      confirmAction({
        title: t('删除会话', 'Delete conversation'),
        content: t(`确认删除会话「${session.title}」吗？删除后消息、附件和分享记录都会清理。`, `Delete conversation "${session.title}"? Messages, attachments, and share records will be removed.`),
        okText: t('确认删除', 'Delete'),
        okButtonProps: { danger: true },
        onOk: () => handleDeleteConversation(session),
      });
    },
    [handleDeleteConversation],
  );
  const menuActions = useMemo(
    () => ({
      openRenameModal: (session: ChatSession) => {
        setRenameTargetSessionId(session.id);
        setRenameValue(session.title);
        setRenameModalOpen(true);
      },
      handleTogglePinConversation,
      handleShareConversation,
      handleExportConversation,
      handleDeleteConversation: handleDeleteConversationWithConfirm,
    }),
    [
      handleDeleteConversationWithConfirm,
      handleExportConversation,
      handleShareConversation,
      handleTogglePinConversation,
    ],
  );
  const closeRenameModal = useCallback(() => {
    setRenameTargetSessionId(null);
    setRenameModalOpen(false);
    setRenameValue('');
  }, []);
  const applyRename = useCallback(async () => {
    if (!renameTargetSessionId || !renameValue.trim()) {
      if (!renameValue.trim()) {
        message.warning('请输入会话名称');
      }
      return;
    }
    await handleRenameSession(renameTargetSessionId, renameValue.trim());
    closeRenameModal();
    message.success('会话名称已更新');
  }, [closeRenameModal, handleRenameSession, renameTargetSessionId, renameValue]);
  const conversationItems = useMemo(
    () =>
      sessions.map((session) => ({
        key: session.id,
        label: session.title,
        isPinned: session.isPinned,
        group: getConversationGroup(session),
      })),
    [sessions],
  );
  const activeMessageItems = useMemo(
    () =>
      (activeSession?.messages || []).map((item) => ({
        key: item.key,
        role: item.role,
        content:
          item.role === 'ai'
            ? createElement(AiMessageContent, {
                item,
                onConfirmTool: handleConfirmTool,
                confirmingToolId,
              })
            : item.content,
        footer: createElement(MessageBubbleFooter, { item, onCopy: handleCopyMessage }),
      })),
    [activeSession?.messages, confirmingToolId, handleConfirmTool, handleCopyMessage],
  );
  const responsive = useResponsive();
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
        avatar: <Avatar size={resolveResponsiveValue(APP_SPACING.avatarSize.tiny, responsive.isMobile)} icon={<RobotOutlined />} />,
      },
    }),
    [responsive.isMobile],
  );
  const handleDropFiles = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isShareMode) return;
    const nextFiles = Array.from(event.dataTransfer.files || []);
    if (!nextFiles.length) return;
    void uploadAttachments(nextFiles, { isShareMode, activeSession, updateSession });
  };
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile);
  const chatPanel = (
    <section className="saas-ai-assistant-layout__chat">
      <div className="saas-ai-assistant-shell__chat-body" onDrop={handleDropFiles} onDragOver={(event) => event.preventDefault()}>
        {!activeSession?.messages?.length ? (
          buildWelcomePanel(isShareMode, sectionGap, responsive.isMobile)
        ) : (
          <Bubble.List items={activeMessageItems} role={bubbleRole} autoScroll className="saas-ai-assistant-bubbles" />
        )}
      </div>
      <div className="saas-ai-assistant-shell__composer">
        <Composer
          employees={selectedEmployeeOptions}
          selectedEmployees={selectedEmployees}
          readOnly={isShareMode}
          activeSession={activeSession}
          sending={sending || toolSending}
          attachmentUploading={attachmentUploading}
          onAgentsChange={setSelectedEmployeeIds}
          onSend={(messageText, options) => void handleSend(messageText, options)}
          onUploadFiles={(files) => void uploadAttachments(files, { isShareMode, activeSession, updateSession })}
          onRemoveAttachment={(fileId) => handleRemoveDraftAttachment(fileId, { activeSession, updateSession })}
        />
      </div>
    </section>
  );
  const sessionsPanel = (
    <aside className="saas-ai-assistant-layout__sidebar">
      {renderConversationsPanel(
        conversationItems,
        activeSessionId,
        isShareMode,
        (key) => {
          handleSessionSelect(String(key));
          setMobilePanel('chat');
        },
        () => {
          handleCreateSession();
          setMobilePanel('chat');
        },
        (convKey) => buildConversationMenu(sessions.find((session) => session.id === convKey)!, menuActions),
      )}
    </aside>
  );

  const pageTitle = isShareMode ? t('AI 会话分享', 'AI conversation share') : t('AI 助手', 'AI assistant');

  return {
    isShareMode,
    shareQuery,
    pageTitle,
    mobilePanel,
    chatPanel,
    sessionsPanel,
    renameModalOpen,
    renameValue,
    setMobilePanel,
    setRenameValue,
    closeRenameModal,
    applyRename,
  };
};
