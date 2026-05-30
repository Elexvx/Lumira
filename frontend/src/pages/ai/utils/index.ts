import dayjs from 'dayjs';
import type { FileCardProps } from '@ant-design/x';
import type { AiEmployeeRecord, AiConversationRecord, AiConversationShareDetailRecord, AiConversationAttachmentRecord, FileObjectRecord, AiConversationMessageRecord, AiKnowledgeReferenceRecord, AiToolPlanRecord, AiToolExecuteResultRecord } from '@/types/api';
import type { ChatSession, ComposerAttachment, ChatBubble } from '../types';

export const CONVERSATIONS_QUERY_KEY = ['ai-assistant-conversations'] as const;
export const EMPTY_CONVERSATIONS: AiConversationRecord[] = [];
export const EMPTY_EMPLOYEES: AiEmployeeRecord[] = [];

export const AI_ATTACHMENT_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt', 'png', 'jpg', 'jpeg', 'gif', 'bmp'];
export const AI_ATTACHMENT_ACCEPT = AI_ATTACHMENT_EXTENSIONS.map((extension) => `.${extension}`).join(',');
export const AI_CHAT_ATTACHMENT_BUCKET = 'ai_chat';

export const getFileExtension = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || '';

export const isAllowedAiAttachment = (file: File) => AI_ATTACHMENT_EXTENSIONS.includes(getFileExtension(file.name));

export const getAttachmentFileIcon = (attachment: ComposerAttachment): FileCardProps['icon'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['xls', 'xlsx'].includes(extension)) return 'excel';
  if (['doc', 'docx'].includes(extension)) return 'word';
  if (['ppt', 'pptx'].includes(extension)) return 'ppt';
  if (extension === 'pdf') return 'pdf';
  if (extension === 'md') return 'markdown';
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
  return 'default';
};

export const getAttachmentFileType = (attachment: ComposerAttachment): FileCardProps['type'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
  return 'file';
};

export const toAttachmentFileCardItem = (attachment: ComposerAttachment): FileCardProps => ({
  key: attachment.fileId,
  name: attachment.originalFileName,
  byte: attachment.fileSizeBytes ?? undefined,
  description: attachment.fileSizeLabel || undefined,
  icon: getAttachmentFileIcon(attachment),
  type: getAttachmentFileType(attachment),
  src: attachment.previewUrl || attachment.publicUrl || attachment.downloadUrl || undefined,
});

export const buildBubbleKey = (prefix: string) => `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`;

export const buildSessionTitle = (message: string) => {
  const trimmed = message.trim();
  if (!trimmed) return '新对话';
  return trimmed.length > 24 ? `${trimmed.slice(0, 24)}...` : trimmed;
};

export const buildAssistantGreeting = (employee?: Pick<AiEmployeeRecord, 'greeting' | 'nickname' | 'username'> | null, fallbackName?: string) => {
  if (employee?.greeting?.trim()) return employee.greeting.trim();
  const nickname = employee?.nickname?.trim() || employee?.username?.trim() || fallbackName || 'AI 助手';
  return `你好，我是${nickname}，有什么可以帮你？`;
};

export const buildInitialSession = (employee?: AiEmployeeRecord | null): ChatSession => {
  const greeting = buildAssistantGreeting(employee);
  return {
    id: 'session-default',
    title: '新对话',
    preview: greeting,
    employeeId: employee?.id ?? null,
    employeeName: employee?.nickname?.trim() || employee?.username || 'AI 助手',
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
  title: conversation.title?.trim() || '新对话',
  preview: conversation.preview?.trim() || conversation.title?.trim() || '新对话',
  employeeId: conversation.employeeId ?? null,
  employeeName: conversation.employeeName?.trim() || employee?.nickname?.trim() || employee?.username || 'AI 助手',
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
  title: detail.share.shareTitle?.trim() || detail.conversation.title?.trim() || '共享会话',
  preview: detail.conversation.preview?.trim() || detail.conversation.title?.trim() || '共享会话',
  employeeId: detail.conversation.employeeId ?? null,
  employeeName: detail.conversation.employeeName?.trim() || 'AI 助手',
  employeeAvatarKey: null,
  messages: (detail.messages || []).map(mapMessageRecord),
  conversationId: detail.conversation.id,
  updatedAt: detail.conversation.latestMessageAt || detail.conversation.updateTime || detail.conversation.createTime || dayjs().format('YYYY-MM-DD HH:mm'),
  isDraft: false,
  isPinned: Boolean(detail.conversation.isPinned),
  pendingAttachments: [],
});

export const mapAttachmentRecord = (attachment: AiConversationAttachmentRecord): ComposerAttachment => ({
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

export const mapFileObjectToAttachment = (file: FileObjectRecord): ComposerAttachment => ({
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

type MessageRecordWithSources = AiConversationMessageRecord & {
  thinkingContent?: string | null;
  references?: AiKnowledgeReferenceRecord[] | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
};

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
  if (session.isDraft) return '草稿';
  if (session.isPinned) return '置顶';
  const time = dayjs(session.updatedAt);
  if (time.isSame(dayjs(), 'day')) return '今天';
  if (time.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天';
  return '更早';
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

export const formatExportFileName = (title: string, format: 'markdown' | 'text') => {
  const safeTitle = title.trim().replaceAll(/[\\/:*?"<>|]/g, '_') || 'ai-conversation';
  return `${safeTitle}.${format === 'markdown' ? 'md' : 'txt'}`;
};

export const buildExportContent = (session: ChatSession, format: 'markdown' | 'text') => {
  const markdown = format === 'markdown';
  const lines: string[] = [];
  lines.push(markdown ? `# ${session.title}` : session.title);
  lines.push('');
  lines.push(`AI 员工: ${session.employeeName}`);
  lines.push(`更新时间: ${session.updatedAt}`);
  lines.push('');

  session.messages.forEach((messageItem) => {
    lines.push(markdown ? `## ${messageItem.role === 'user' ? '用户' : 'AI'}` : `${messageItem.role === 'user' ? '用户' : 'AI'}:`);
    lines.push(messageItem.content);
    if (messageItem.attachments.length) {
      lines.push('');
      lines.push('附件:');
      messageItem.attachments.forEach((attachment) => {
        lines.push(`- ${attachment.originalFileName}`);
      });
    }
    lines.push('');
  });

  return lines.join('\n').trim();
};

export const downloadText = (content: string, fileName: string, mimeType: string) => {
  const blob = new Blob([content], { type: mimeType });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  window.URL.revokeObjectURL(url);
};
