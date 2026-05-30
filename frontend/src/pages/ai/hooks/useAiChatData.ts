import { useEffect, useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { message } from 'antd';
import dayjs from 'dayjs';
import { aiService } from '@/services/ai';
import type { AiEmployeeRecord } from '@/types/api';
import type { ChatSession } from '../types';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';

import {
  CONVERSATIONS_QUERY_KEY,
  EMPTY_CONVERSATIONS,
  EMPTY_EMPLOYEES,
  buildInitialSession,
  buildSessionFromConversation,
  buildSessionFromShare,
  mapMessageRecord,
  sortSessions,
  isDraftSession,
  buildBubbleKey,
  buildAssistantGreeting,
} from '../utils';

export const useAiChatData = (shareToken: string) => {
  const queryClient = useQueryClient();
  const isShareMode = Boolean(shareToken);

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string>('session-default');
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([]);

  const employeesQuery = useQuery({
    queryKey: ['ai-assistant-employees'],
    enabled: !isShareMode,
    queryFn: async () => aiService.employees({ pageNo: 1, pageSize: 50 }, API_OPTS.NO_REDIRECT),
  });

  const assistantQuery = useQuery({
    queryKey: ['ai-assistant-default'],
    enabled: !isShareMode,
    queryFn: async () =>
      aiService.assistant(API_OPTS.SILENT_NO_REDIRECT).catch((error) => {
        if (error && typeof error === 'object' && 'code' in error && (error as { code?: string }).code === 'A0404') {
          return null;
        }
        throw error;
      }),
    retry: false,
  });

  const shareQuery = useQuery({
    queryKey: ['ai-assistant-share', shareToken],
    enabled: isShareMode && Boolean(shareToken),
    queryFn: async () => aiService.conversationShare(shareToken, API_OPTS.NO_REDIRECT),
    retry: false,
  });

  const employees = employeesQuery.data?.records ?? EMPTY_EMPLOYEES;
  const assistantEmployee = assistantQuery.data || null;
  const shareConversation = shareQuery.data?.conversation || null;
  const shareEmployee = shareConversation
    ? ({
        id: shareConversation.employeeId,
        username: shareConversation.employeeName || 'ai-assistant',
        nickname: shareConversation.employeeName || 'AI 助手',
        avatarKey: null,
        enabled: true,
      } as AiEmployeeRecord)
    : null;

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
    if (isShareMode) return shareEmployee ? [shareEmployee] : [];
    return selectedEmployeeIds
      .map((employeeId) => selectedEmployeeOptions.find((employee) => employee.id === employeeId) || null)
      .filter((employee): employee is AiEmployeeRecord => Boolean(employee));
  }, [isShareMode, selectedEmployeeIds, selectedEmployeeOptions, shareEmployee]);

  const selectedEmployee = selectedEmployees[0] || null;

  const conversationsQuery = useQuery({
    queryKey: CONVERSATIONS_QUERY_KEY,
    enabled: !isShareMode,
    queryFn: async () => aiService.conversations({ pageNo: 1, pageSize: 50 }, API_OPTS.NO_REDIRECT),
  });

  useEffect(() => {
    if (isShareMode) return;
    const records = conversationsQuery.data?.records ?? EMPTY_CONVERSATIONS;
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
      return mergedSessions.length ? mergedSessions : [buildInitialSession(selectedEmployee)];
    });

    setActiveSessionId((currentActiveSessionId) => {
      if (!records.length) return currentActiveSessionId;
      if (currentActiveSessionId.startsWith('session_') && currentActiveSessionId !== 'session-default') return currentActiveSessionId;
      if (currentActiveSessionId !== 'session-default' && records.some((record) => String(record.id) === currentActiveSessionId)) {
        return currentActiveSessionId;
      }
      return records[0] ? String(records[0].id) : 'session-default';
    });
  }, [conversationsQuery.data, employeeById, isShareMode, selectedEmployee]);

  useEffect(() => {
    if (!isShareMode || !shareQuery.data) return;
    const session = buildSessionFromShare(shareQuery.data);
    setSessions([session]);
    setActiveSessionId(session.id);
  }, [isShareMode, shareQuery.data]);

  const activeSession = useMemo(() => sessions.find((session) => session.id === activeSessionId) || sessions[0] || null, [activeSessionId, sessions]);

  const updateSession = (sessionId: string, updater: (session: ChatSession) => ChatSession) => {
    setSessions((currentSessions) => sortSessions(currentSessions.map((session) => (session.id === sessionId ? updater(session) : session))));
  };

  useEffect(() => {
    if (isShareMode || !activeSession?.conversationId || activeSession.messages.length > 0) return;
    let alive = true;
    const loadMessages = async () => {
      try {
        const records = await aiService.conversationMessages(activeSession.conversationId!, API_OPTS.NO_REDIRECT);
        if (!alive) return;
        updateSession(activeSession.id, (session) => ({
          ...session,
          messages: records.map(mapMessageRecord),
        }));
      } catch (error) {
        if (alive) {
          showErrorMessage(error, '加载对话记录失败');
        }
      }
    };
    void loadMessages();
    return () => { alive = false; };
  }, [activeSession?.conversationId, activeSession?.id, activeSession?.messages.length, isShareMode]);

  const handleCreateSession = () => {
    if (isShareMode) return;
    if (isDraftSession(activeSession)) {
      message.info({ key: 'ai-assistant-current-new-session', content: '已经是最新的对话了' });
      return;
    }
    const nextSession = buildInitialSession(selectedEmployee);
    nextSession.id = buildBubbleKey('session');
    nextSession.title = '新对话';
    nextSession.preview = buildAssistantGreeting(selectedEmployee);
    nextSession.messages = [];
    nextSession.pendingAttachments = [];
    nextSession.isDraft = true;
    nextSession.updatedAt = dayjs().format('YYYY-MM-DD HH:mm');
    setSessions((currentSessions) => sortSessions([nextSession, ...currentSessions.filter((session) => session.id !== nextSession.id)]));
    setActiveSessionId(nextSession.id);
  };

  const handleDeleteConversation = async (session: ChatSession) => {
    if (session.isDraft || !session.conversationId) {
      setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
      setActiveSessionId((current) => {
        if (current !== session.id) return current;
        const remaining = sessions.filter((item) => item.id !== session.id);
        return remaining[0] ? remaining[0].id : 'session-default';
      });
      return;
    }

    await aiService.deleteConversation(session.conversationId, API_OPTS.NO_REDIRECT);
    setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
    setActiveSessionId((current) => {
      if (current !== session.id) return current;
      const remaining = sessions.filter((item) => item.id !== session.id);
      return remaining[0] ? remaining[0].id : 'session-default';
    });
    void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
  };

  const handleTogglePinConversation = async (session: ChatSession) => {
    if (session.isDraft || !session.conversationId) {
      updateSession(session.id, (current) => ({ ...current, isPinned: !current.isPinned }));
      return;
    }
    try {
      await aiService.updateConversation(session.conversationId, { pinned: !session.isPinned }, API_OPTS.NO_REDIRECT);
      updateSession(session.id, (current) => ({ ...current, isPinned: !current.isPinned }));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    } catch (error) {
      showErrorMessage(error, '置顶设置失败');
    }
  };

  const handleRenameSession = async (sessionId: string, nextTitle: string) => {
    const session = sessions.find((item) => item.id === sessionId);
    if (!session) return;
    if (session.isDraft || !session.conversationId) {
      updateSession(session.id, (current) => ({
        ...current,
        title: nextTitle,
        preview: current.preview === current.title || current.preview === session.title ? nextTitle : current.preview,
      }));
      return;
    }
    await aiService.updateConversation(session.conversationId, { title: nextTitle }, API_OPTS.NO_REDIRECT);
    updateSession(session.id, (current) => ({
      ...current,
      title: nextTitle,
      preview: current.preview === current.title || current.preview === session.title ? nextTitle : current.preview,
      updatedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    }));
    void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
  };

  const handleSessionSelect = (sessionId: string) => {
    const nextSession = sessions.find((session) => session.id === sessionId);
    if (!isShareMode && nextSession) {
      setSelectedEmployeeIds(nextSession.employeeId ? [nextSession.employeeId] : []);
    }
    setActiveSessionId(sessionId);
  };

  return {
    isShareMode,
    shareQuery,
    sessions,
    activeSessionId,
    activeSession,
    selectedEmployees,
    selectedEmployeeOptions,
    employeeById,
    setSessions,
    setActiveSessionId,
    setSelectedEmployeeIds,
    updateSession,
    handleCreateSession,
    handleDeleteConversation,
    handleTogglePinConversation,
    handleRenameSession,
    handleSessionSelect,
  };
};
