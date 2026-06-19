import dayjs from 'dayjs';
import type {
  AiConversationAttachmentRecord,
  AiConversationRecord,
  AiConversationShareDetailRecord,
  AiConversationMessageRecord,
  AiKnowledgeReferenceRecord,
  AiToolPlanRecord,
  AiToolExecuteResultRecord,
  AiEmployeeRecord,
} from '@/types/api';
import type { ChatSession, ChatBubble } from '../types';
import { resolveRuntimeLocale } from '@/i18n/locale';

const isEnglishLocale = () => resolveRuntimeLocale().startsWith('en');
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

type MessageRecordWithSources = AiConversationMessageRecord & {
  thinkingContent?: string | null;
  references?: AiKnowledgeReferenceRecord[] | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
};

const mapAttachmentRecord = (attachment: AiConversationAttachmentRecord) => ({
  id: `attachment_${attachment.id}`,
  fileId: attachment.fileId,
  originalFileName: attachment.originalFileName,
  fileExtension: attachment.fileExtension,
  mimeType: attachment.mimeType,
  fileSizeBytes: attachment.fileSizeBytes,
  fileSizeLabel: attachment.fileSizeLabel,
  publicUrl: attachment.publicUrl,
  previewUrl: attachment.previewUrl,
  downloadUrl: attachment.downloadUrl,
  previewMode: attachment.previewMode,
});

export const CONVERSATIONS_QUERY_KEY = ['ai-assistant-conversations'] as const;
export const EMPTY_CONVERSATIONS: AiConversationRecord[] = [];
export const EMPTY_EMPLOYEES: AiEmployeeRecord[] = [];

export const buildBubbleKey = (prefix: string) => `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`;

export const buildSessionTitle = (message: string) => {
  const trimmed = message.trim();
  if (!trimmed) return t('新对话', 'New conversation');
  return trimmed.length > 24 ? `${trimmed.slice(0, 24)}...` : trimmed;
};

export const buildAssistantGreeting = (employee?: Pick<AiEmployeeRecord, 'greeting' | 'nickname' | 'username'> | null, fallbackName?: string) => {
  if (employee?.greeting?.trim()) return employee.greeting.trim();
  const nickname = employee?.nickname?.trim() || employee?.username?.trim() || fallbackName || t('AI 助手', 'AI Assistant');
  return isEnglishLocale() ? `Hello, I'm ${nickname}. How can I help?` : `你好，我是${nickname}，有什么可以帮你？`;
};

export const buildInitialSession = (employee?: AiEmployeeRecord | null): ChatSession => {
  const greeting = buildAssistantGreeting(employee);
  return {
    id: 'session-default',
    title: t('新对话', 'New conversation'),
    preview: greeting,
    employeeId: employee?.id ?? null,
    employeeName: employee?.nickname?.trim() || employee?.username || t('AI 助手', 'AI Assistant'),
    employeeAvatarKey: employee?.avatarKey ?? null,
    messages: [],
    isDraft: true,
    isPinned: false,
    pendingAttachments: [],
    updatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
  };
};

export const buildSessionFromConversation = (conversation: AiConversationRecord, employee?: AiEmployeeRecord | null): ChatSession => ({
  id: String(conversation.id),
  title: conversation.title?.trim() || t('新对话', 'New conversation'),
  preview: conversation.preview?.trim() || conversation.title?.trim() || t('新对话', 'New conversation'),
  employeeId: conversation.employeeId ?? null,
  employeeName: conversation.employeeName?.trim() || employee?.nickname?.trim() || employee?.username || t('AI 助手', 'AI Assistant'),
  employeeAvatarKey: employee?.avatarKey ?? null,
  messages: [],
  conversationId: conversation.id,
  updatedAt: conversation.latestMessageAt || conversation.updateTime || conversation.createTime || dayjs().format('YYYY-MM-DD HH:mm'),
  isDraft: false,
  isPinned: Boolean(conversation.pinned ?? conversation.isPinned),
  pendingAttachments: [],
});

export const buildSessionFromShare = (detail: AiConversationShareDetailRecord): ChatSession => ({
  id: String(detail.conversation.id),
  title: detail.share.shareTitle?.trim() || detail.conversation.title?.trim() || t('共享会话', 'Shared conversation'),
  preview: detail.conversation.preview?.trim() || detail.conversation.title?.trim() || t('共享会话', 'Shared conversation'),
  employeeId: detail.conversation.employeeId ?? null,
  employeeName: detail.conversation.employeeName?.trim() || t('AI 助手', 'AI Assistant'),
  employeeAvatarKey: null,
  messages: (detail.messages || []).map(mapMessageRecord),
  conversationId: detail.conversation.id,
  updatedAt: detail.conversation.latestMessageAt || detail.conversation.updateTime || detail.conversation.createTime || dayjs().format('YYYY-MM-DD HH:mm'),
  isDraft: false,
  isPinned: Boolean(detail.conversation.isPinned),
  pendingAttachments: [],
});

export const mapMessageRecord = (record: AiConversationMessageRecord): ChatBubble => {
  const messageRecord = record as MessageRecordWithSources;
  return {
    key: `message_${record.id}`,
    role: record.role.trim().toUpperCase() === 'USER' ? 'user' : 'ai',
    content: record.content,
    attachments: (record.attachments || []).map(mapAttachmentRecord),
    thinkingContent: messageRecord.thinkingContent,
    references: messageRecord.references,
    toolPlan: messageRecord.toolPlan,
    toolResult: messageRecord.toolResult,
  };
};

export const getConversationGroup = (session: ChatSession) => {
  if (session.isDraft) return t('草稿', 'Draft');
  if (session.isPinned) return t('置顶', 'Pinned');
  const time = dayjs(session.updatedAt);
  if (time.isSame(dayjs(), 'day')) return t('今天', 'Today');
  if (time.isSame(dayjs().subtract(1, 'day'), 'day')) return t('昨天', 'Yesterday');
  return t('更早', 'Earlier');
};

export const sortSessions = (sessions: ChatSession[]) =>
  [...sessions].sort((a, b) => {
    if (a.isDraft !== b.isDraft) return a.isDraft ? -1 : 1;
    if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
    const diff = dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf();
    if (diff !== 0) return diff;
    return Number(b.conversationId || 0) - Number(a.conversationId || 0);
  });

export const isDraftSession = (session?: ChatSession | null) => Boolean(session && (session.isDraft || !session.conversationId));
