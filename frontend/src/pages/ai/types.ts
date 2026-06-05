import type {
  AiKnowledgeReferenceRecord,
  AiToolPlanRecord,
  AiToolExecuteResultRecord,
} from '@/types/api';

export type BubbleRole = 'user' | 'ai';

export type ComposerAttachment = {
  id: string;
  fileId: number;
  originalFileName: string;
  fileExtension?: string | null;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  fileSizeLabel?: string | null;
  publicUrl?: string | null;
  previewUrl?: string | null;
  downloadUrl?: string | null;
  previewMode?: string | null;
};

export type ChatBubble = {
  key: string;
  role: BubbleRole;
  content: string;
  attachments: ComposerAttachment[];
  thinkingContent?: string | null;
  thinkingLoading?: boolean;
  references?: AiKnowledgeReferenceRecord[] | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
};

export type ChatSession = {
  id: string;
  title: string;
  preview: string;
  employeeId: number | null;
  employeeName: string;
  employeeAvatarKey?: string | null;
  messages: ChatBubble[];
  conversationId?: number | null;
  updatedAt: string;
  isDraft?: boolean;
  isPinned?: boolean;
  pendingAttachments: ComposerAttachment[];
};

export type RouteParams = {
  token?: string;
};

export type ActionItem = {
  key: string;
  label?: string;
  ariaLabel?: string;
  icon?: React.ReactNode;
  onItemClick?: () => void;
};

export type ConversationItem = {
  key: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  group?: string;
  disabled?: boolean;
};

export type BubbleItem = {
  key: string;
  role: BubbleRole;
  content: React.ReactNode;
  footer?: React.ReactNode;
};
