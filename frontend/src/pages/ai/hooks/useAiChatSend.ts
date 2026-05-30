import { useState } from 'react';
import { message } from 'antd';
import dayjs from 'dayjs';
import { useQueryClient } from '@tanstack/react-query';
import { aiService } from '@/services/ai';
import type { AiEmployeeRecord, AiChatResponseRecord, AiToolPlanRecord, AiToolExecuteResultRecord } from '@/types/api';
import type { ChatSession, ChatBubble } from '../types';
import { CONVERSATIONS_QUERY_KEY, buildBubbleKey, buildSessionTitle, buildSessionFromConversation, mapMessageRecord, sortSessions } from '../utils';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


export const useAiChatSend = (options: {
  isShareMode: boolean;
  activeSession: ChatSession | null;
  activeEmployeeIds: number[];
  employeeById: Map<number, AiEmployeeRecord>;
  updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;
  setActiveSessionId: (id: string) => void;
}) => {
  const { isShareMode, activeSession, activeEmployeeIds, employeeById, updateSession, setActiveSessionId } = options;
  const queryClient = useQueryClient();
  const [sending, setSending] = useState(false);
  const [confirmingToolId, setConfirmingToolId] = useState<number | null>(null);

  const handleSend = async (messageText: string, opts: { enableThinking?: boolean; employeeIds?: number[] } = {}) => {
    const trimmed = messageText.trim();
    if (!trimmed || !activeSession || isShareMode) return;

    const draftAttachments = activeSession.pendingAttachments;
    const userBubble: ChatBubble = { key: buildBubbleKey('user'), role: 'user', content: trimmed, attachments: draftAttachments };
    const assistantPlaceholder: ChatBubble = { key: buildBubbleKey('assistant'), role: 'ai', content: '', attachments: [], thinkingLoading: true };

    const requestEmployeeIds = opts.employeeIds ?? activeEmployeeIds;
    const requestEmployeeId = requestEmployeeIds.length === 1 ? requestEmployeeIds[0] : null;
    const requestEmployees = requestEmployeeIds.map(id => employeeById.get(id) || null).filter(Boolean) as AiEmployeeRecord[];
    const requestEmployee = requestEmployees[0] || null;
    const requestEmployeeName = requestEmployees.length > 1
      ? `${requestEmployees.length} 个 Agent 协同`
      : requestEmployee?.nickname?.trim() || requestEmployee?.username || activeSession.employeeName || 'AI 助手';

    setSending(true);
    updateSession(activeSession.id, (session) => ({
      ...session,
      employeeId: requestEmployeeId,
      employeeName: requestEmployeeName,
      employeeAvatarKey: requestEmployee?.avatarKey ?? activeSession.employeeAvatarKey ?? null,
      title: session.conversationId ? session.title : buildSessionTitle(trimmed),
      preview: trimmed,
      messages: [...session.messages, userBubble, assistantPlaceholder],
      pendingAttachments: [],
      updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
    }));

    try {
      const streamState: { response?: AiChatResponseRecord; error?: Error; replyText: string } = { replyText: '' };
      await aiService.streamChat(
        {
          employeeId: requestEmployeeId,
          employeeIds: requestEmployeeIds.length > 1 ? requestEmployeeIds : undefined,
          conversationId: activeSession.conversationId ?? null,
          message: trimmed,
          enableThinking: opts.enableThinking ?? null,
          attachments: draftAttachments.map(a => ({ fileId: a.fileId })),
        },
        (event) => {
          if (event.type === 'status' && event.message) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, thinkingContent: [item.thinkingContent, event.message].filter(Boolean).join('\n'), thinkingLoading: true }
                  : item
              ),
            }));
          } else if (event.type === 'thinking' && event.delta) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, thinkingContent: `${item.thinkingContent || ''}${event.delta}`, thinkingLoading: true }
                  : item
              ),
            }));
          } else if (event.type === 'delta' && event.delta) {
            streamState.replyText += event.delta;
            updateSession(activeSession.id, (session) => ({
              ...session,
              preview: streamState.replyText || session.preview,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, content: streamState.replyText, thinkingLoading: false }
                  : item
              ),
            }));
          } else if ((event.type === 'tool_proposal' || event.type === 'tool_blocked') && event.toolPlan) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, content: event.message || (event.type === 'tool_blocked' ? '该操作已被平台防护规则拦截。' : '我已生成系统操作计划，请确认后执行。'), thinkingLoading: false, toolPlan: event.toolPlan }
                  : item
              ),
            }));
          } else if (event.type === 'tool_result' && event.toolResult) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, content: event.toolResult?.message || '系统操作已完成。', thinkingLoading: false, toolResult: event.toolResult }
                  : item
              ),
            }));
          } else if (event.type === 'done' && event.response) {
            streamState.response = event.response;
          } else if (event.type === 'error') {
            streamState.error = new Error(event.message || '发送失败，请稍后重试');
          }
        },
        API_OPTS.SILENT_NO_REDIRECT
      );

      if (streamState.error) throw streamState.error;
      const response = streamState.response;
      if (!response) throw new Error('AI 回复生成失败');

      const responseConversationId = response.conversationId ?? activeSession.conversationId;
      const responseSessionId = responseConversationId ? String(responseConversationId) : activeSession.id;

      updateSession(activeSession.id, (session) => ({
        ...session,
        id: responseSessionId,
        conversationId: responseConversationId,
        isDraft: false,
        title: session.conversationId ? session.title : buildSessionTitle(trimmed),
        preview: response.replyText || trimmed,
        messages: [
          ...session.messages.filter((item) => item.key !== assistantPlaceholder.key),
          {
            key: buildBubbleKey('assistant'),
            role: 'ai',
            content: response.replyText || '我已经收到你的消息。',
            attachments: [],
            thinkingContent: response.thinkingContent,
            references: response.references,
            toolPlan: response.toolPlan,
            toolResult: response.toolResult,
          },
        ],
        updatedAt: dayjs(response.replyAt || undefined).isValid()
          ? dayjs(response.replyAt).format('YYYY-MM-DD HH:mm')
          : dayjs().format('YYYY-MM-DD HH:mm'),
      }));

      if (responseConversationId) setActiveSessionId(responseSessionId);
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    } catch (error) {
      updateSession(activeSession.id, (session) => ({
        ...session,
        pendingAttachments: draftAttachments,
        messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
      }));
      showErrorMessage(error, '发送失败，请稍后重试');
    } finally {
      setSending(false);
    }
  };

  const handleConfirmTool = async (plan: AiToolPlanRecord) => {
    if (!activeSession || isShareMode || confirmingToolId) return;

    const confirmText = `确认执行：${plan.summary || plan.toolName || plan.toolCode}`;
    const userBubble: ChatBubble = { key: buildBubbleKey('user'), role: 'user', content: confirmText, attachments: [] };
    const assistantPlaceholder: ChatBubble = { key: buildBubbleKey('assistant'), role: 'ai', content: '', attachments: [], thinkingLoading: true, toolPlan: plan };
    
    setConfirmingToolId(plan.id);
    setSending(true);
    updateSession(activeSession.id, (session) => ({
      ...session,
      messages: [...session.messages, userBubble, assistantPlaceholder],
      updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
    }));

    try {
      let response: AiChatResponseRecord | null = null;
      let toolResult: AiToolExecuteResultRecord | null = null;

      await aiService.streamChat(
        {
          employeeId: plan.employeeId ?? activeSession.employeeId ?? null,
          conversationId: activeSession.conversationId ?? plan.conversationId ?? null,
          pendingToolCallId: plan.id,
          message: confirmText,
          confirmed: true,
        },
        (event) => {
          if (event.type === 'status' && event.message) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, thinkingContent: [item.thinkingContent, event.message].filter(Boolean).join('\n'), thinkingLoading: true }
                  : item
              ),
            }));
          } else if (event.type === 'thinking' && event.delta) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, thinkingContent: `${item.thinkingContent || ''}${event.delta}`, thinkingLoading: true }
                  : item
              ),
            }));
          } else if (event.type === 'delta' && event.delta) {
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, content: (item.content || '') + event.delta, thinkingLoading: false }
                  : item
              ),
            }));
          } else if (event.type === 'tool_result' && event.toolResult) {
            toolResult = event.toolResult;
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? { ...item, content: event.toolResult?.message || '系统操作已完成。', thinkingLoading: false, toolResult: event.toolResult }
                  : item
              ),
            }));
          } else if (event.type === 'done' && event.response) {
            response = event.response;
          } else if (event.type === 'error') {
            throw new Error(event.message || '系统操作执行失败');
          }
        },
        API_OPTS.SILENT_NO_REDIRECT
      );

      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: session.messages.map((item) =>
          item.key === assistantPlaceholder.key
            ? { ...item, content: response?.replyText || toolResult?.message || '系统操作已完成。', thinkingLoading: false, toolResult: response?.toolResult || toolResult }
            : item
        ),
      }));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    } catch (error) {
      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
      }));
      showErrorMessage(error, '系统操作执行失败');
    } finally {
      setSending(false);
      setConfirmingToolId(null);
    }
  };

  return { sending, confirmingToolId, handleSend, handleConfirmTool };
};
