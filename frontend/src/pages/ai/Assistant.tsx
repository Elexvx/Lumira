import {
  AppstoreOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  MoreOutlined,
  PaperClipOutlined,
  PlusOutlined,
  PushpinOutlined,
  RobotOutlined,
  ShareAltOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { FileCard, Sender as XSender, Sources, Suggestion, Think } from '@ant-design/x';
import type { FileCardProps } from '@ant-design/x';
import { XMarkdown } from '@ant-design/x-markdown';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { history, useParams } from '@umijs/max';
import { Alert, Avatar, Button, Dropdown, Input, Modal, Result, Space, Spin, Tag, Tabs, message } from 'antd';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { DragEvent } from 'react';
import { aiService } from '@/services/ai';
import { fileService } from '@/services/file';
import type {
  AiConversationAttachmentRecord,
  AiConversationMessageRecord,
  AiConversationRecord,
  AiConversationShareDetailRecord,
  AiChatResponseRecord,
  AiEmployeeRecord,
  AiKnowledgeReferenceRecord,
  AiToolExecuteResultRecord,
  AiToolPlanRecord,
  FileObjectRecord,
} from '@/types/api';
import { copyTextToClipboard } from '@/utils/clipboard';
import { confirmAction } from '@/utils/confirm';
import { MAX_UPLOAD_FILE_COUNT } from '@/pages/files/fileCenter.utils';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import './Assistant.css';

type BubbleRole = 'user' | 'ai';

type ComposerAttachment = {
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

type ChatBubble = {
  key: string;
  role: BubbleRole;
  content: string;
  attachments: ComposerAttachment[];
  thinkingContent?: string | null;
  thinkingLoading?: boolean;
  streamingContent?: string;
  references?: AiKnowledgeReferenceRecord[] | null;
  toolPlan?: AiToolPlanRecord | null;
  toolResult?: AiToolExecuteResultRecord | null;
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
  isDraft?: boolean;
  isPinned?: boolean;
  pendingAttachments: ComposerAttachment[];
};

type RouteParams = {
  token?: string;
};

const CONVERSATIONS_QUERY_KEY = ['ai-assistant-conversations'] as const;
const EMPTY_CONVERSATIONS: AiConversationRecord[] = [];
const EMPTY_EMPLOYEES: AiEmployeeRecord[] = [];

type ActionItem = {
  key: string;
  label?: string;
  ariaLabel?: string;
  icon?: React.ReactNode;
  onItemClick?: () => void;
};

type ConversationItem = {
  key: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  group?: string;
  disabled?: boolean;
};

type BubbleItem = {
  key: string;
  role: BubbleRole;
  content: React.ReactNode;
  footer?: React.ReactNode;
};

type ComposerProps = {
  employees: AiEmployeeRecord[];
  selectedEmployees: AiEmployeeRecord[];
  readOnly: boolean;
  activeSession?: ChatSession | null;
  sending: boolean;
  attachmentUploading: boolean;
  onAgentsChange: (employeeIds: number[]) => void;
  onSend: (messageText: string, options: { enableThinking: boolean; employeeIds: number[] }) => void;
  onUploadFiles: (files: File[]) => void;
  onRemoveAttachment: (fileId: number) => void;
};

const AI_ATTACHMENT_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt', 'png', 'jpg', 'jpeg', 'gif', 'bmp'];
const AI_ATTACHMENT_ACCEPT = AI_ATTACHMENT_EXTENSIONS.map((extension) => `.${extension}`).join(',');
const AI_CHAT_ATTACHMENT_BUCKET = 'ai_chat';

const getFileExtension = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || '';

const isAllowedAiAttachment = (file: File) => AI_ATTACHMENT_EXTENSIONS.includes(getFileExtension(file.name));

const getAttachmentFileIcon = (attachment: ComposerAttachment): FileCardProps['icon'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['xls', 'xlsx'].includes(extension)) {
    return 'excel';
  }
  if (['doc', 'docx'].includes(extension)) {
    return 'word';
  }
  if (['ppt', 'pptx'].includes(extension)) {
    return 'ppt';
  }
  if (extension === 'pdf') {
    return 'pdf';
  }
  if (extension === 'md') {
    return 'markdown';
  }
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) {
    return 'image';
  }
  return 'default';
};

const getAttachmentFileType = (attachment: ComposerAttachment): FileCardProps['type'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) {
    return 'image';
  }
  return 'file';
};

const toAttachmentFileCardItem = (attachment: ComposerAttachment): FileCardProps => ({
  key: attachment.fileId,
  name: attachment.originalFileName,
  byte: attachment.fileSizeBytes ?? undefined,
  description: attachment.fileSizeLabel || undefined,
  icon: getAttachmentFileIcon(attachment),
  type: getAttachmentFileType(attachment),
  src: attachment.previewUrl || attachment.publicUrl || attachment.downloadUrl || undefined,
});

const Actions = ({ items }: { items: ActionItem[]; variant?: string }) => (
  <Space size={4} wrap>
    {items.map((item) => (
      <Button
        key={item.key}
        type="text"
        size="small"
        icon={item.icon}
        aria-label={item.ariaLabel || item.label}
        title={item.ariaLabel || item.label}
        onClick={item.onItemClick}
      >
        {item.label}
      </Button>
    ))}
  </Space>
);

const BubbleSystem = ({ content }: { content: React.ReactNode; variant?: string }) => (
  <div className="saas-ai-assistant-bubble-system">{content}</div>
);

const BubbleList = ({ items, className }: { items: BubbleItem[]; role?: unknown; autoScroll?: boolean; className?: string }) => (
  <div className={className}>
    {items.map((item) => (
      <div key={item.key} className={`saas-ai-assistant-bubble saas-ai-assistant-bubble--${item.role}`}>
        {item.role === 'ai' ? (
          <div className="saas-ai-assistant-bubble__header">
            <Avatar size={28} icon={<RobotOutlined />} />
            <span>AI 助手</span>
          </div>
        ) : null}
        <div className="saas-ai-assistant-bubble__content">{item.content}</div>
        {item.footer ? <div className="saas-ai-assistant-bubble__footer">{item.footer}</div> : null}
      </div>
    ))}
  </div>
);

const Bubble = {
  List: BubbleList,
  System: BubbleSystem,
};

const Welcome = ({
  icon,
  title,
  description,
  extra,
  className,
}: {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  extra?: React.ReactNode;
  className?: string;
}) => (
  <div className={className}>
    {icon ? <div className="saas-ai-assistant-welcome__icon">{icon}</div> : null}
    <h2>{title}</h2>
    {description ? <p>{description}</p> : null}
    {extra}
  </div>
);

const Conversations = ({
  items,
  activeKey,
  onActiveChange,
  creation,
  menu,
  className,
}: {
  items: ConversationItem[];
  activeKey?: string;
  onActiveChange: (key: string) => void;
  creation?: { label: string; icon?: React.ReactNode; onClick: () => void; align?: string };
  menu?: (conversation: ConversationItem) => MenuProps;
  groupable?: boolean;
  className?: string;
}) => {
  const [openActionKey, setOpenActionKey] = useState<string | null>(null);
  const groupedItems = items.reduce<Record<string, ConversationItem[]>>((result, item) => {
    const groupName = item.group || '会话';
    result[groupName] = [...(result[groupName] || []), item];
    return result;
  }, {});

  return (
    <div className={className}>
      {creation ? (
        <Button block type="primary" icon={creation.icon} onClick={creation.onClick}>
          {creation.label}
        </Button>
      ) : null}
      <div className="saas-ai-assistant-conversations__groups">
        {Object.entries(groupedItems).map(([groupName, groupItems]) => (
          <div key={groupName} className="saas-ai-assistant-conversations__group">
            <div className="saas-ai-assistant-conversations__group-title">{groupName}</div>
            {groupItems.map((item) => {
              const content = (
                <div className={`saas-ai-assistant-conversation-item${item.key === activeKey ? ' saas-ai-assistant-conversation-item--active' : ''}`}>
                  <Button
                    className="saas-ai-assistant-conversation-item__main"
                    type="text"
                    icon={item.icon}
                    disabled={item.disabled}
                    onClick={() => onActiveChange(item.key)}
                  >
                    {item.label}
                  </Button>
                </div>
              );
              const menuProps = menu?.(item);
              const itemWrapClassName = [
                'saas-ai-assistant-conversation-item-wrap',
                item.key === activeKey ? 'saas-ai-assistant-conversation-item-wrap--active' : '',
                openActionKey === item.key ? 'saas-ai-assistant-conversation-item-wrap--menu-open' : '',
              ].filter(Boolean).join(' ');
              return menuProps ? (
                <Dropdown
                  key={item.key}
                  menu={menuProps}
                  trigger={['contextMenu']}
                  onOpenChange={(open) => setOpenActionKey(open ? item.key : null)}
                >
                  <div className={itemWrapClassName}>
                    {content}
                    <Dropdown
                      menu={menuProps}
                      trigger={['click']}
                      placement="bottomRight"
                      onOpenChange={(open) => setOpenActionKey(open ? item.key : null)}
                    >
                      <Button
                        className="saas-ai-assistant-conversation-item__more"
                        type="text"
                        icon={<MoreOutlined />}
                        aria-label="会话操作"
                        disabled={item.disabled}
                        onClick={(event) => event.stopPropagation()}
                      />
                    </Dropdown>
                  </div>
                </Dropdown>
              ) : content;
            })}
          </div>
        ))}
      </div>
    </div>
  );
};

const buildBubbleKey = (prefix: string) => `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}`;

const buildSessionTitle = (message: string) => {
  const trimmed = message.trim();
  if (!trimmed) {
    return '新对话';
  }
  return trimmed.length > 24 ? `${trimmed.slice(0, 24)}...` : trimmed;
};

const buildAssistantGreeting = (employee?: Pick<AiEmployeeRecord, 'greeting' | 'nickname' | 'username'> | null, fallbackName?: string) => {
  if (employee?.greeting?.trim()) {
    return employee.greeting.trim();
  }

  const nickname = employee?.nickname?.trim() || employee?.username?.trim() || fallbackName || 'AI 助手';
  return `你好，我是${nickname}，有什么可以帮你？`;
};

const buildInitialSession = (employee?: AiEmployeeRecord | null): ChatSession => {
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

const buildSessionFromConversation = (conversation: AiConversationRecord, employee?: AiEmployeeRecord | null): ChatSession => ({
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

const buildSessionFromShare = (detail: AiConversationShareDetailRecord): ChatSession => ({
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

const mapAttachmentRecord = (attachment: AiConversationAttachmentRecord): ComposerAttachment => ({
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

const mapFileObjectToAttachment = (file: FileObjectRecord): ComposerAttachment => ({
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

const mapMessageRecord = (record: AiConversationMessageRecord): ChatBubble => {
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

const getConversationGroup = (session: ChatSession) => {
  if (session.isDraft) {
    return '草稿';
  }
  if (session.isPinned) {
    return '置顶';
  }
  const time = dayjs(session.updatedAt);
  if (time.isSame(dayjs(), 'day')) {
    return '今天';
  }
  if (time.isSame(dayjs().subtract(1, 'day'), 'day')) {
    return '昨天';
  }
  return '更早';
};

const sortSessions = (sessions: ChatSession[]) =>
  [...sessions].sort((a, b) => {
    if (a.isDraft !== b.isDraft) {
      return a.isDraft ? -1 : 1;
    }
    if (a.isPinned !== b.isPinned) {
      return a.isPinned ? -1 : 1;
    }
    const diff = dayjs(b.updatedAt).valueOf() - dayjs(a.updatedAt).valueOf();
    if (diff !== 0) {
      return diff;
    }
    return Number(b.conversationId || 0) - Number(a.conversationId || 0);
  });

const isDraftSession = (session?: ChatSession | null) => Boolean(session && (session.isDraft || !session.conversationId));

const formatExportFileName = (title: string, format: 'markdown' | 'text') => {
  const safeTitle = title.trim().replaceAll(/[\\/:*?"<>|]/g, '_') || 'ai-conversation';
  return `${safeTitle}.${format === 'markdown' ? 'md' : 'txt'}`;
};

const buildExportContent = (session: ChatSession, format: 'markdown' | 'text') => {
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

const renderThinkingContent = (item: ChatBubble) => {
  const thinkingContent = item.thinkingContent?.trim();
  if (!thinkingContent && !item.thinkingLoading) {
    return null;
  }

  const statusText = thinkingContent ? '处理过程' : '正在生成回复';

  return (
    <Think
      title={statusText}
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
          <span>正在调用模型并生成回复。</span>
        </div>
      )}
    </Think>
  );
};

const renderSources = (references?: AiKnowledgeReferenceRecord[] | null) => {
  if (!references?.length) {
    return null;
  }

  const dedupedReferences = Array.from(
    new Map<number, AiKnowledgeReferenceRecord>(references.map((reference) => [reference.chunkId, reference])).values(),
  ).slice(0, 6);

  const sourceItems = dedupedReferences.map((reference) => ({
    key: reference.chunkId,
    title: reference.documentTitle || reference.originalFileName || `知识片段 #${reference.chunkId}`,
    description: reference.knowledgeBaseName
      ? `知识库：${reference.knowledgeBaseName}${reference.chunkIndex != null ? ` · 分片 #${reference.chunkIndex + 1}` : ''}`
      : reference.chunkIndex != null
        ? `分片 #${reference.chunkIndex + 1}`
        : '知识引用',
  }));

  return (
    <Sources
      classNames={{
        root: 'saas-ai-assistant-sources',
        title: 'saas-ai-assistant-sources__title',
        content: 'saas-ai-assistant-sources__content',
      }}
      title={`参考来源（${sourceItems.length}）`}
      expandIconPosition="end"
      items={sourceItems}
    />
  );
};

const MarkdownMessage = ({ content }: { content: string }) => (
  <XMarkdown content={content} openLinksInNewTab escapeRawHtml />
);

const renderToolPlanCard = (
  item: ChatBubble,
  options: {
    onConfirm?: (plan: AiToolPlanRecord) => void;
    confirming?: boolean;
  } = {},
) => {
  const plan = item.toolPlan;
  if (!plan) {
    return null;
  }
  const blocked = plan.status === 'BLOCKED' || plan.policyVerdict === 'DENY' || plan.supervisorVerdict === 'DENY';
  const result = item.toolResult;
  const args = plan.arguments || {};

  return (
    <div className={`saas-ai-tool-card ${blocked ? 'saas-ai-tool-card--blocked' : ''}`}>
      <div className="saas-ai-tool-card__head">
        <span className="saas-ai-tool-card__title">{plan.toolName || plan.toolCode}</span>
        <Tag color={blocked ? 'red' : plan.riskLevel === 'HIGH' ? 'orange' : 'blue'}>{plan.riskLevel || 'MEDIUM'}</Tag>
      </div>
      <div className="saas-ai-tool-card__summary">{plan.summary || 'AI 已生成一个系统操作计划。'}</div>
      <div className="saas-ai-tool-card__meta">
        <span>权限：{plan.permissionKey || '按当前用户权限'}</span>
        <span>监督：{plan.supervisorVerdict || 'REQUIRE_CONFIRM'}</span>
      </div>
      {Object.keys(args).length ? (
        <pre className="saas-ai-tool-card__args">{JSON.stringify(args, null, 2)}</pre>
      ) : null}
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
          message={result.message || (result.resultStatus === 'SUCCESS' ? '系统操作已完成' : '系统操作失败')}
          className="saas-ai-tool-card__alert"
        />
      ) : (
        <div className="saas-ai-tool-card__actions">
          <Button
            type="primary"
            size="small"
            icon={<CheckCircleOutlined />}
            disabled={blocked || options.confirming}
            loading={options.confirming}
            onClick={() => options.onConfirm?.(plan)}
          >
            确认执行
          </Button>
          <Button size="small" icon={<CloseCircleOutlined />} disabled={options.confirming}>
            取消
          </Button>
        </div>
      )}
    </div>
  );
};

const renderMessageContent = (
  item: ChatBubble,
  visibleReplyText?: string,
  options?: {
    onConfirmTool?: (plan: AiToolPlanRecord) => void;
    confirmingToolId?: number | null;
  },
) => {
  const content = visibleReplyText ?? item.content;
  if (item.role === 'ai') {
    const thinking = renderThinkingContent(item);
    return (
      <div className="saas-ai-assistant-ai-content">
        {thinking}
        {renderToolPlanCard(item, {
          onConfirm: options?.onConfirmTool,
          confirming: Boolean(item.toolPlan?.id && options?.confirmingToolId === item.toolPlan.id),
        })}
        {content ? (
          <div className="saas-ai-assistant-markdown">
            <MarkdownMessage content={content} />
          </div>
        ) : null}
      </div>
    );
  }

  return content;
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

const renderAttachmentCardList = (
  attachments: ComposerAttachment[],
  options?: {
    removable?: boolean;
    onRemove?: (fileId: number) => void;
    className?: string;
  },
) => (
  <FileCard.List
    className={options?.className}
    items={attachments.map(toAttachmentFileCardItem)}
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

const createActions = (
  messageItem: ChatBubble,
  handlers: {
    onCopy: (text: string) => void;
  },
) => {
  const items = [
    {
      key: 'copy',
      ariaLabel: '复制',
      icon: <CopyOutlined />,
      onItemClick: () => handlers.onCopy(messageItem.content),
    },
  ];

  return <Actions items={items} variant="borderless" />;
};

const Composer = ({
  employees,
  selectedEmployees,
  readOnly,
  activeSession,
  sending,
  attachmentUploading,
  onAgentsChange,
  onSend,
  onUploadFiles,
  onRemoveAttachment,
}: ComposerProps) => {
  const [inputValue, setInputValue] = useState('');
  const [senderKey, setSenderKey] = useState(0);
  const [deepThink, setDeepThink] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const selectedEmployeeIds = useMemo(() => selectedEmployees.map((employee) => employee.id), [selectedEmployees]);
  const firstSelectedEmployee = selectedEmployees[0] || null;
  const agentButtonLabel = selectedEmployees.length > 1
    ? `${selectedEmployees.length} 个助手`
    : firstSelectedEmployee?.nickname?.trim() || firstSelectedEmployee?.username || '助手';
  const agentControlLabel = selectedEmployees.length ? '切换助手' : '助手';

  const agentItems = useMemo(
    () =>
      employees
        .filter((employee) => employee.enabled !== false)
        .map((employee) => ({
          label: employee.nickname?.trim() || employee.username,
          value: String(employee.id),
          icon: <RobotOutlined />,
          extra: employee.defaultLlmServiceTitle || employee.position || undefined,
        })),
    [employees],
  );
  const conversationAgentItems = useMemo(
    () => [
      {
        label: '普通对话',
        value: 'general',
        icon: <RobotOutlined />,
        extra: '不使用数字员工',
      },
      ...agentItems,
    ],
    [agentItems],
  );
  const agentSuggestionItems = useMemo(
    () => (info?: { keyword?: string }) => {
      const keyword = info?.keyword?.trim().toLowerCase();
      if (!keyword) {
        return conversationAgentItems;
      }
      return conversationAgentItems.filter((item) => String(item.label).toLowerCase().includes(keyword));
    },
    [conversationAgentItems],
  );

  const selectedAgentSkill = selectedEmployees.length
    ? {
        title: selectedEmployees.length > 1 ? `${selectedEmployees.length} 个助手协同` : agentButtonLabel,
        value: selectedEmployeeIds.join(','),
        closable: {
          disabled: readOnly || sending,
          onClose: () => onAgentsChange([]),
        },
      }
    : undefined;

  useEffect(() => {
    setInputValue('');
    setSenderKey((current) => current + 1);
  }, [activeSession?.id, readOnly]);

  const handleFiles = (files: File[]) => {
    const safeFiles = files.filter(isAllowedAiAttachment);
    const blockedCount = files.length - safeFiles.length;
    if (blockedCount > 0) {
      message.warning('已拦截不支持或存在风险的文件格式');
    }
    if (!safeFiles.length) {
      message.error(`仅支持 ${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join('、')} 文件`);
      return;
    }
    onUploadFiles(safeFiles);
  };

  const handleSubmit = (messageText: string) => {
    const normalizedMessage = messageText.trim();
    if (!normalizedMessage || normalizedMessage === '请') {
      message.warning('请输入要处理的任务或问题');
      return;
    }
    onSend(normalizedMessage, { enableThinking: deepThink, employeeIds: selectedEmployeeIds });
    setInputValue('');
    setSenderKey((current) => current + 1);
  };
  const handleAgentSuggestionSelect = (value: string) => {
    if (value === 'general') {
      onAgentsChange([]);
      setInputValue((current) => current.replace(/(?:^|\s)\/[^\s/]*$/, '').trimStart());
      return;
    }

    const nextEmployeeId = Number(value);
    if (Number.isFinite(nextEmployeeId)) {
      const nextEmployeeIds = selectedEmployeeIds.includes(nextEmployeeId)
        ? selectedEmployeeIds
        : [...selectedEmployeeIds, nextEmployeeId];
      onAgentsChange(nextEmployeeIds);
      setInputValue((current) => current.replace(/(?:^|\s)\/[^\s/]*$/, '').trimStart());
    }
  };

  return (
    <div className="saas-ai-assistant-composer">
      <input
        ref={fileInputRef}
        type="file"
        accept={AI_ATTACHMENT_ACCEPT}
        multiple
        hidden
        onChange={(event) => {
          const files = Array.from(event.target.files || []);
          event.target.value = '';
          if (files.length) {
            handleFiles(files);
          }
        }}
      />
      <Suggestion items={agentSuggestionItems} onSelect={handleAgentSuggestionSelect} rootClassName="saas-ai-assistant-agent-suggestion">
        {({ onTrigger }) => (
          <XSender
            key={senderKey}
            rootClassName="saas-ai-assistant-sender"
            loading={sending}
            readOnly={readOnly}
            disabled={readOnly || !activeSession}
            autoSize={{ minRows: 1, maxRows: 5 }}
            submitType="enter"
            value={inputValue}
            skill={selectedAgentSkill}
            onChange={(nextValue) => {
              setInputValue(nextValue);
              const slashMatch = nextValue.match(/(?:^|\s)\/([^\s/]*)$/);
              onTrigger(slashMatch ? { keyword: slashMatch[1] } : false);
            }}
            onSubmit={(nextValue) => {
              handleSubmit(nextValue);
            }}
            onPasteFile={(files) => handleFiles(Array.from(files))}
            placeholder={readOnly ? '当前为只读分享页面' : activeSession ? '向我提问吧' : '暂无可用对话'}
            header={
              activeSession?.pendingAttachments.length ? (
                <div className="saas-ai-assistant-composer__header">
                  {activeSession?.pendingAttachments.length ? (
                    <div className="saas-ai-assistant-composer__attachments">
                      {renderAttachmentCardList(activeSession.pendingAttachments, {
                        removable: !sending && !readOnly,
                        onRemove: onRemoveAttachment,
                        className: 'saas-ai-assistant-file-card-list saas-ai-assistant-file-card-list--pending',
                      })}
                    </div>
                  ) : null}
                </div>
              ) : false
            }
            prefix={false}
            footer={(_, { components }) => {
              const { SendButton, LoadingButton } = components;
              return (
                <div className="saas-ai-assistant-composer__footer">
                  <div className="saas-ai-assistant-composer__tools">
                    <Button
                      type="text"
                      icon={<PaperClipOutlined />}
                      aria-label="上传附件"
                      title={`上传附件，支持 ${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join('、')}`}
                      loading={attachmentUploading}
                      disabled={readOnly || sending || attachmentUploading || !activeSession}
                      onClick={() => fileInputRef.current?.click()}
                    />
                    <XSender.Switch
                      icon={<ThunderboltOutlined />}
                      value={deepThink}
                      disabled={readOnly || sending || !activeSession}
                      checkedChildren="思考"
                      unCheckedChildren="思考"
                      onChange={(nextValue) => setDeepThink(Boolean(nextValue))}
                    />
                    <Suggestion items={conversationAgentItems} onSelect={handleAgentSuggestionSelect}>
                      {({ onTrigger, onKeyDown }) => (
                        <Button
                          className="saas-ai-assistant-composer__agent-button"
                          icon={<AppstoreOutlined />}
                          type={selectedEmployees.length ? 'primary' : 'default'}
                          disabled={readOnly || sending || !activeSession}
                          title={selectedEmployees.length ? `当前助手：${selectedEmployees.map((employee) => employee.nickname?.trim() || employee.username).join('、')}` : '选择助手'}
                          onClick={() => onTrigger({})}
                          onKeyDown={onKeyDown}
                        >
                          <span className="saas-ai-assistant-composer__agent-label">{agentControlLabel}</span>
                        </Button>
                      )}
                    </Suggestion>
                  </div>
                  <div className="saas-ai-assistant-composer__actions">
                    {sending ? <LoadingButton /> : <SendButton disabled={!inputValue.trim()} />}
                  </div>
                </div>
              );
            }}
          />
        )}
      </Suggestion>
    </div>
  );
};

const AiAssistantPage = () => {
  const queryClient = useQueryClient();
  const params = useParams<RouteParams>();
  const shareToken = params.token?.trim() || '';
  const isShareMode = Boolean(shareToken);
  const [sending, setSending] = useState(false);
  const [attachmentUploading, setAttachmentUploading] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string>('session-default');
  const [mobilePanel, setMobilePanel] = useState<'chat' | 'sessions'>('chat');
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [selectedEmployeeIds, setSelectedEmployeeIds] = useState<number[]>([]);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [renameTargetSessionId, setRenameTargetSessionId] = useState<string | null>(null);
  const [streamProgress, setStreamProgress] = useState<Record<string, number>>({});
  const [confirmingToolId, setConfirmingToolId] = useState<number | null>(null);

  const employeesQuery = useQuery({
    queryKey: ['ai-assistant-employees'],
    enabled: !isShareMode,
    queryFn: async () => aiService.employees({ pageNo: 1, pageSize: 50 }, { autoRedirectOnUnauthorized: false }),
  });

  const assistantQuery = useQuery({
    queryKey: ['ai-assistant-default'],
    enabled: !isShareMode,
    queryFn: async () =>
      aiService.assistant({
        autoRedirectOnUnauthorized: false,
        silent: true,
      }).catch((error) => {
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
    queryFn: async () => aiService.conversationShare(shareToken, { autoRedirectOnUnauthorized: false }),
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
    if (isShareMode) {
      return;
    }

    setSelectedEmployeeIds((currentValue) => {
      const validIds = currentValue.filter(
        (employeeId) => employees.some((employee) => employee.id === employeeId) || assistantEmployee?.id === employeeId,
      );
      return validIds.length === currentValue.length ? currentValue : validIds;
    });
  }, [assistantEmployee?.id, employees, isShareMode]);

  const selectedEmployeeOptions = useMemo(() => {
    if (employees.length) {
      return employees;
    }
    return assistantEmployee ? [assistantEmployee] : [];
  }, [assistantEmployee, employees]);

  const employeeById = useMemo(
    () => new Map(selectedEmployeeOptions.map((employee) => [employee.id, employee])),
    [selectedEmployeeOptions],
  );

  const selectedEmployees = useMemo(() => {
    if (isShareMode) {
      return shareEmployee ? [shareEmployee] : [];
    }

    return selectedEmployeeIds
      .map((employeeId) => selectedEmployeeOptions.find((employee) => employee.id === employeeId) || null)
      .filter((employee): employee is AiEmployeeRecord => Boolean(employee));
  }, [isShareMode, selectedEmployeeIds, selectedEmployeeOptions, shareEmployee]);

  const selectedEmployee = selectedEmployees[0] || null;
  const activeEmployeeIds = selectedEmployees.map((employee) => employee.id);

  const conversationsQuery = useQuery({
    queryKey: CONVERSATIONS_QUERY_KEY,
    enabled: !isShareMode,
    queryFn: async () =>
      aiService.conversations(
        {
          pageNo: 1,
          pageSize: 50,
        },
        { autoRedirectOnUnauthorized: false },
      ),
  });

  useEffect(() => {
    if (isShareMode) {
      return;
    }

    const records = conversationsQuery.data?.records ?? EMPTY_CONVERSATIONS;
    setSessions((currentSessions) => {
      const draftSessions = currentSessions.filter((session) => session.isDraft || !session.conversationId);
      const persistedSessions = records.map((record) => {
        const existingSession = currentSessions.find((session) => session.conversationId === record.id || session.id === String(record.id));
        const recordEmployee = record.employeeId ? employeeById.get(record.employeeId) : null;
        if (!existingSession) {
          return buildSessionFromConversation(record, recordEmployee);
        }

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
      if (!records.length) {
        return currentActiveSessionId;
      }

      if (currentActiveSessionId.startsWith('session_') && currentActiveSessionId !== 'session-default') {
        return currentActiveSessionId;
      }

      if (currentActiveSessionId !== 'session-default' && records.some((record) => String(record.id) === currentActiveSessionId)) {
        return currentActiveSessionId;
      }

      return records[0] ? String(records[0].id) : 'session-default';
    });
  }, [conversationsQuery.data, employeeById, isShareMode, selectedEmployee]);

  useEffect(() => {
    if (!isShareMode || !shareQuery.data) {
      return;
    }

    const session = buildSessionFromShare(shareQuery.data);
    setSessions([session]);
    setActiveSessionId(session.id);
  }, [isShareMode, shareQuery.data]);

  const activeSession = useMemo(() => sessions.find((session) => session.id === activeSessionId) || sessions[0] || null, [activeSessionId, sessions]);

  const updateSession = (sessionId: string, updater: (session: ChatSession) => ChatSession) => {
    setSessions((currentSessions) => sortSessions(currentSessions.map((session) => (session.id === sessionId ? updater(session) : session))));
  };

  useEffect(() => {
    if (isShareMode || !activeSession?.conversationId || activeSession.messages.length > 0) {
      return;
    }

    let alive = true;

    const loadMessages = async () => {
      try {
        const records = await aiService.conversationMessages(activeSession.conversationId!, {
          autoRedirectOnUnauthorized: false,
        });
        if (!alive) {
          return;
        }

        updateSession(activeSession.id, (session) => ({
          ...session,
          messages: records.map(mapMessageRecord),
        }));
      } catch (error) {
        if (alive) {
          message.error(error instanceof Error && error.message ? error.message : '加载对话记录失败');
        }
      }
    };

    void loadMessages();

    return () => {
      alive = false;
    };
  }, [activeSession?.conversationId, activeSession?.id, activeSession?.messages.length, isShareMode]);

  const currentExportSession = useMemo(() => {
    if (activeSession) {
      return activeSession;
    }
    return sessions[0] || null;
  }, [activeSession, sessions]);

  const openRenameModal = (session: ChatSession) => {
    setRenameTargetSessionId(session.id);
    setRenameValue(session.title);
    setRenameModalOpen(true);
  };

  const closeRenameModal = () => {
    setRenameTargetSessionId(null);
    setRenameModalOpen(false);
    setRenameValue('');
  };

  const applyRename = async () => {
    if (!renameTargetSessionId) {
      return;
    }

    const nextTitle = renameValue.trim();
    if (!nextTitle) {
      message.warning('请输入会话名称');
      return;
    }

    const session = sessions.find((item) => item.id === renameTargetSessionId);
    if (!session) {
      closeRenameModal();
      return;
    }

    if (session.isDraft || !session.conversationId) {
      updateSession(session.id, (current) => ({
        ...current,
        title: nextTitle,
        preview: current.preview === current.title || current.preview === session.title ? nextTitle : current.preview,
      }));
      closeRenameModal();
      return;
    }

    try {
      await aiService.updateConversation(session.conversationId, { title: nextTitle }, { autoRedirectOnUnauthorized: false });
      updateSession(session.id, (current) => ({
        ...current,
        title: nextTitle,
        preview: current.preview === current.title || current.preview === session.title ? nextTitle : current.preview,
        updatedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      }));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
      closeRenameModal();
      message.success('会话名称已更新');
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '重命名失败');
    }
  };

  const uploadAttachments = async (files: File[]) => {
    if (isShareMode || !activeSession) {
      return;
    }

    const allowedFiles = files.filter(isAllowedAiAttachment);

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
        const record = await fileService.upload(
          file,
          {
            category: 'AI 会话附件',
            tags: 'ai,conversation',
            remark: activeSession.title,
            bucket: AI_CHAT_ATTACHMENT_BUCKET,
          },
          { autoRedirectOnUnauthorized: false },
        );
        uploadedAttachments.push(mapFileObjectToAttachment(record));
      }

      updateSession(activeSession.id, (session) => ({
        ...session,
        pendingAttachments: [...session.pendingAttachments, ...uploadedAttachments],
      }));
      message.success(`已添加 ${uploadedAttachments.length} 个附件`);
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '附件上传失败');
    } finally {
      setAttachmentUploading(false);
    }
  };

  const handleSend = async (messageText: string, options: { enableThinking?: boolean; employeeIds?: number[] } = {}) => {
    const trimmed = messageText.trim();
    if (!trimmed || !activeSession || isShareMode) {
      return;
    }

    const draftAttachments = activeSession.pendingAttachments;
    const userBubble: ChatBubble = {
      key: buildBubbleKey('user'),
      role: 'user',
      content: trimmed,
      attachments: draftAttachments,
    };

    const assistantPlaceholder: ChatBubble = {
      key: buildBubbleKey('assistant'),
      role: 'ai',
      content: '',
      attachments: [],
      thinkingLoading: true,
    };

    const requestEmployeeIds = options.employeeIds ?? activeEmployeeIds;
    const requestEmployeeId = requestEmployeeIds.length === 1 ? requestEmployeeIds[0] : null;
    const requestEmployees = requestEmployeeIds
      .map((employeeId) => employeeById.get(employeeId) || null)
      .filter((employee): employee is AiEmployeeRecord => Boolean(employee));
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
      const streamState: { response?: AiChatResponseRecord; error?: Error; replyText: string } = {
        replyText: '',
      };

      const recoverPersistedReply = async () => {
        if (!streamState.replyText) {
          return false;
        }

        const conversations = await aiService.conversations(
          {
            ...(requestEmployeeId ? { employeeId: requestEmployeeId } : {}),
            pageNo: 1,
            pageSize: 1,
          },
          { autoRedirectOnUnauthorized: false, silent: true },
        );
        const latestConversation = conversations.records?.[0];
        if (!latestConversation) {
          return false;
        }

        const persistedMessages = await aiService.conversationMessages(latestConversation.id, {
          autoRedirectOnUnauthorized: false,
          silent: true,
        });
        const hasAssistantReply = persistedMessages.some((record) => record.role.trim().toUpperCase() === 'ASSISTANT');
        if (!hasAssistantReply) {
          return false;
        }

        const recoveredEmployee = latestConversation.employeeId ? employeeById.get(latestConversation.employeeId) : null;
        const recoveredSession = buildSessionFromConversation(latestConversation, recoveredEmployee);
        setSessions((currentSessions) =>
          sortSessions(
            currentSessions.map((session) =>
              session.id === activeSession.id
                ? {
                  ...recoveredSession,
                  messages: persistedMessages.map(mapMessageRecord),
                }
                : session,
            ),
          ),
        );
        setActiveSessionId(String(latestConversation.id));
        void queryClient.invalidateQueries({
          queryKey: CONVERSATIONS_QUERY_KEY,
        });
        return true;
      };

      try {
        await aiService.streamChat(
          {
            employeeId: requestEmployeeId,
            employeeIds: requestEmployeeIds.length > 1 ? requestEmployeeIds : undefined,
            conversationId: activeSession.conversationId ?? null,
            message: trimmed,
            enableThinking: options.enableThinking ?? null,
            attachments: draftAttachments.map((attachment) => ({ fileId: attachment.fileId })),
          },
          (event) => {
            if (event.type === 'status' && event.message) {
              updateSession(activeSession.id, (session) => ({
                ...session,
                messages: session.messages.map((item) =>
                  item.key === assistantPlaceholder.key
                    ? {
                      ...item,
                      thinkingContent: [item.thinkingContent, event.message].filter(Boolean).join('\n'),
                      thinkingLoading: true,
                    }
                    : item,
                ),
              }));
              return;
            }

            if (event.type === 'thinking' && event.delta) {
              updateSession(activeSession.id, (session) => ({
                ...session,
                messages: session.messages.map((item) =>
                  item.key === assistantPlaceholder.key
                    ? {
                      ...item,
                      thinkingContent: `${item.thinkingContent || ''}${event.delta}`,
                      thinkingLoading: true,
                    }
                    : item,
                ),
              }));
              return;
            }

            if (event.type === 'delta' && event.delta) {
              streamState.replyText += event.delta;
              updateSession(activeSession.id, (session) => ({
                ...session,
                preview: streamState.replyText || session.preview,
                messages: session.messages.map((item) =>
                  item.key === assistantPlaceholder.key
                    ? {
                      ...item,
                      content: streamState.replyText,
                      thinkingLoading: false,
                    }
                    : item,
                ),
              }));
              return;
            }

            if ((event.type === 'tool_proposal' || event.type === 'tool_blocked') && event.toolPlan) {
              updateSession(activeSession.id, (session) => ({
                ...session,
                messages: session.messages.map((item) =>
                  item.key === assistantPlaceholder.key
                    ? {
                      ...item,
                      content: event.message || (event.type === 'tool_blocked' ? '该操作已被平台防护规则拦截。' : '我已生成系统操作计划，请确认后执行。'),
                      thinkingLoading: false,
                      toolPlan: event.toolPlan,
                    }
                    : item,
                ),
              }));
              return;
            }

            if (event.type === 'tool_result' && event.toolResult) {
              updateSession(activeSession.id, (session) => ({
                ...session,
                messages: session.messages.map((item) =>
                  item.key === assistantPlaceholder.key
                    ? {
                      ...item,
                      content: event.toolResult?.message || '系统操作已完成。',
                      thinkingLoading: false,
                      toolResult: event.toolResult,
                    }
                    : item,
                ),
              }));
              return;
            }

            if (event.type === 'done' && event.response) {
              streamState.response = event.response;
              return;
            }

            if (event.type === 'error') {
              streamState.error = new Error(event.message || '发送失败，请稍后重试');
            }
          },
          { autoRedirectOnUnauthorized: false, silent: true },
        );
      } catch (streamError) {
        if (!streamState.response) {
          const recovered = await recoverPersistedReply().catch(() => false);
          if (recovered) {
            return;
          }
          throw streamError;
        }
      }

      if (streamState.error) {
        throw streamState.error;
      }

      const response = streamState.response;
      if (!response) {
        throw new Error('AI 回复生成失败');
      }

      const responseConversationId = response.conversationId ?? activeSession.conversationId;
      const responseSessionId = responseConversationId ? String(responseConversationId) : activeSession.id;

      setSessions((currentSessions) =>
        sortSessions(
          currentSessions.map((session) => {
            if (session.id !== activeSession.id) {
              return session;
            }

            return {
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
                  streamingContent: response.replyText || '我已经收到你的消息。',
                  references: response.references,
                  toolPlan: response.toolPlan,
                  toolResult: response.toolResult,
                },
              ],
              updatedAt: dayjs(response.replyAt || undefined).isValid()
                ? dayjs(response.replyAt).format('YYYY-MM-DD HH:mm')
                : dayjs().format('YYYY-MM-DD HH:mm'),
            };
          }),
        ),
      );

      if (responseConversationId) {
        setActiveSessionId(responseSessionId);
      }

      void queryClient.invalidateQueries({
        queryKey: CONVERSATIONS_QUERY_KEY,
      });
    } catch (error) {
      updateSession(activeSession.id, (session) => ({
        ...session,
        pendingAttachments: draftAttachments,
        messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
      }));
      message.error(error instanceof Error && error.message ? error.message : '发送失败，请稍后重试');
    } finally {
      setSending(false);
    }
  };

  const handleConfirmTool = async (plan: AiToolPlanRecord) => {
    if (!activeSession || isShareMode || confirmingToolId) {
      return;
    }
    const confirmText = `确认执行：${plan.summary || plan.toolName || plan.toolCode}`;
    const userBubble: ChatBubble = {
      key: buildBubbleKey('user'),
      role: 'user',
      content: confirmText,
      attachments: [],
    };
    const assistantPlaceholder: ChatBubble = {
      key: buildBubbleKey('assistant'),
      role: 'ai',
      content: '',
      attachments: [],
      thinkingLoading: true,
      toolPlan: plan,
    };
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
                  ? {
                    ...item,
                    thinkingContent: [item.thinkingContent, event.message].filter(Boolean).join('\n'),
                    thinkingLoading: true,
                  }
                  : item,
              ),
            }));
            return;
          }
          if (event.type === 'tool_result' && event.toolResult) {
            toolResult = event.toolResult;
            updateSession(activeSession.id, (session) => ({
              ...session,
              messages: session.messages.map((item) =>
                item.key === assistantPlaceholder.key
                  ? {
                    ...item,
                    content: event.toolResult?.message || '系统操作已完成。',
                    thinkingLoading: false,
                    toolResult: event.toolResult,
                  }
                  : item,
              ),
            }));
            return;
          }
          if (event.type === 'done' && event.response) {
            response = event.response;
          }
          if (event.type === 'error') {
            throw new Error(event.message || '系统操作执行失败');
          }
        },
        { autoRedirectOnUnauthorized: false, silent: true },
      );
      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: session.messages.map((item) =>
          item.key === assistantPlaceholder.key
            ? {
              ...item,
              content: response?.replyText || toolResult?.message || '系统操作已完成。',
              thinkingLoading: false,
              toolResult: response?.toolResult || toolResult,
            }
            : item,
        ),
      }));
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    } catch (error) {
      updateSession(activeSession.id, (session) => ({
        ...session,
        messages: session.messages.filter((item) => item.key !== assistantPlaceholder.key),
      }));
      message.error(error instanceof Error && error.message ? error.message : '系统操作执行失败');
    } finally {
      setSending(false);
      setConfirmingToolId(null);
    }
  };

  const handleCreateSession = () => {
    if (isShareMode) {
      return;
    }

    if (isDraftSession(activeSession)) {
      message.info({
        key: 'ai-assistant-current-new-session',
        content: '已经是最新的对话了',
      });
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

  const handleSessionSelect = (sessionId: string) => {
    const nextSession = sessions.find((session) => session.id === sessionId);
    if (!isShareMode && nextSession) {
      setSelectedEmployeeIds(nextSession.employeeId ? [nextSession.employeeId] : []);
    }
    setActiveSessionId(sessionId);
  };

  const handleRemoveDraftAttachment = (sessionId: string, fileId: number) => {
    updateSession(sessionId, (session) => ({
      ...session,
      pendingAttachments: session.pendingAttachments.filter((attachment) => attachment.fileId !== fileId),
    }));
  };

  const handleCopyMessage = async (content: string) => {
    try {
      await copyTextToClipboard(content);
      message.success('已复制');
    } catch {
      message.error('复制失败');
    }
  };

  const handleShareConversation = async (session?: ChatSession | null) => {
    const targetSession = session || currentExportSession;
    if (!targetSession?.conversationId) {
      message.warning('请先发送至少一条消息后再分享');
      return;
    }

    try {
      const share = await aiService.shareConversation(targetSession.conversationId, { autoRedirectOnUnauthorized: false });
      const shareUrl = new URL(`/ai/share/${share.shareToken}`, window.location.origin).toString();
      await copyTextToClipboard(shareUrl);
      message.success('分享链接已复制');
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '创建分享链接失败');
    }
  };

  const handleCopyShareLink = async () => {
    try {
      const shareUrl = isShareMode && typeof window !== 'undefined' ? window.location.href : null;
      if (shareUrl) {
        await copyTextToClipboard(shareUrl);
        message.success('分享链接已复制');
        return;
      }
      await handleShareConversation(currentExportSession);
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '复制分享链接失败');
    }
  };

  const handleExportConversation = async (format: 'markdown' | 'text', session?: ChatSession | null) => {
    const targetSession = session || currentExportSession;
    if (!targetSession) {
      message.warning('暂无可导出的会话');
      return;
    }

    try {
      if (targetSession.conversationId) {
        const exportResult = await aiService.exportConversation(targetSession.conversationId, { format }, { autoRedirectOnUnauthorized: false });
        downloadText(exportResult.content, exportResult.fileName, exportResult.mimeType);
        return;
      }

      const content = buildExportContent(targetSession, format);
      const fileName = formatExportFileName(targetSession.title, format);
      downloadText(content, fileName, format === 'markdown' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8');
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '导出失败');
    }
  };

  const handleDeleteConversation = (session: ChatSession) => {
    confirmAction({
      title: '删除会话',
      content: `确认删除会话「${session.title}」吗？删除后消息、附件和分享记录都会清理。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (session.isDraft || !session.conversationId) {
          setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
          setActiveSessionId((current) => {
            if (current !== session.id) {
              return current;
            }
            const remaining = sessions.filter((item) => item.id !== session.id);
            return remaining[0] ? remaining[0].id : 'session-default';
          });
          return;
        }

        await aiService.deleteConversation(session.conversationId, { autoRedirectOnUnauthorized: false });
        setSessions((currentSessions) => currentSessions.filter((item) => item.id !== session.id));
        setActiveSessionId((current) => {
          if (current !== session.id) {
            return current;
          }
          const remaining = sessions.filter((item) => item.id !== session.id);
          return remaining[0] ? remaining[0].id : 'session-default';
        });
        void queryClient.invalidateQueries({
          queryKey: CONVERSATIONS_QUERY_KEY,
        });
      },
    });
  };

  const handleTogglePinConversation = async (session: ChatSession) => {
    if (session.isDraft || !session.conversationId) {
      updateSession(session.id, (current) => ({
        ...current,
        isPinned: !current.isPinned,
      }));
      return;
    }

    try {
      await aiService.updateConversation(session.conversationId, { pinned: !session.isPinned }, { autoRedirectOnUnauthorized: false });
      updateSession(session.id, (current) => ({
        ...current,
        isPinned: !current.isPinned,
      }));
      void queryClient.invalidateQueries({
        queryKey: CONVERSATIONS_QUERY_KEY,
      });
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '置顶设置失败');
    }
  };

  const buildConversationMenu = (session: ChatSession): MenuProps => {
    const canShare = Boolean(session.conversationId);
    return {
      items: [
        {
          key: 'rename',
          label: '重命名',
          icon: <EditOutlined />,
        },
        {
          key: 'pin',
          label: session.isPinned ? '取消置顶' : '置顶',
          icon: <PushpinOutlined />,
        },
        ...(canShare
          ? [
              {
                key: 'share',
                label: '复制分享链接',
                icon: <ShareAltOutlined />,
              },
              {
                key: 'export-markdown',
                label: '导出 Markdown',
                icon: <DownloadOutlined />,
              },
              {
                key: 'export-text',
                label: '导出文本',
                icon: <DownloadOutlined />,
              },
            ]
          : []),
        { type: 'divider' as const },
        {
          key: 'delete',
          label: '删除',
          icon: <DeleteOutlined />,
          danger: true,
        },
      ],
      onClick: ({ key }) => {
        if (key === 'rename') {
          openRenameModal(session);
          return;
        }
        if (key === 'pin') {
          void handleTogglePinConversation(session);
          return;
        }
        if (key === 'share') {
          void handleShareConversation(session);
          return;
        }
        if (key === 'export-markdown') {
          void handleExportConversation('markdown', session);
          return;
        }
        if (key === 'export-text') {
          void handleExportConversation('text', session);
          return;
        }
        if (key === 'delete') {
          handleDeleteConversation(session);
        }
      },
    };
  };

  const conversationItems = useMemo(
    () =>
      sortSessions(sessions).map((session) => ({
        key: session.id,
        label: (
          <Space size={6}>
            <span>{session.title}</span>
            {session.isPinned ? <Tag color="gold">置顶</Tag> : null}
          </Space>
        ),
        icon: <RobotOutlined />,
        group: getConversationGroup(session),
        disabled: false,
      })),
    [sessions],
  );

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

  const activeMessageItems = useMemo(
    () =>
      activeSession?.messages.map((item) => ({
        key: item.key,
        role: item.role,
        content: renderMessageContent(
          item,
          item.streamingContent ? item.streamingContent.slice(0, streamProgress[item.key] ?? 0) : undefined,
          {
            onConfirmTool: handleConfirmTool,
            confirmingToolId,
          },
        ),
        footer: (
          <Space direction="vertical" size={8} className="saas-ai-assistant-bubble__footer">
            {item.attachments.length ? (
              <div className="saas-ai-assistant-message-attachments">
                {renderAttachmentCardList(item.attachments, {
                  className: 'saas-ai-assistant-file-card-list saas-ai-assistant-file-card-list--message',
                })}
              </div>
            ) : null}
            {renderSources(item.references)}
            <div className="saas-ai-assistant-bubble__actions">
              {createActions(item, {
                onCopy: handleCopyMessage,
              })}
            </div>
          </Space>
        ),
      })) || [],
    [activeSession, streamProgress, confirmingToolId],
  );

  useEffect(() => {
    const nextReply = activeSession?.messages.find(
      (item) => item.streamingContent && (streamProgress[item.key] ?? 0) < item.streamingContent.length,
    );
    const target = nextReply;

    if (!target) {
      return undefined;
    }

    const total = target.streamingContent?.length ?? 0;
    const current = streamProgress[target.key] ?? 0;
    const nextValue = Math.min(current + 1, total);
    const delay = 12;

    const timer = window.setTimeout(() => {
      setStreamProgress((prev) => ({
        ...prev,
        [target.key]: nextValue,
      }));
    }, delay);

    return () => window.clearTimeout(timer);
  }, [activeSession?.messages, streamProgress]);

  const hasContent = Boolean(activeSession?.messages?.length);
  const pageTitle = isShareMode ? 'AI 会话分享' : 'AI 助手';

  const handleDropFiles = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isShareMode) {
      return;
    }
    const nextFiles = Array.from(event.dataTransfer.files || []);
    if (!nextFiles.length) {
      return;
    }
    void uploadAttachments(nextFiles);
  };

  const chatPanel = (
    <section className="saas-ai-assistant-layout__chat">
      <div className="saas-ai-assistant-shell__chat-body" onDrop={handleDropFiles} onDragOver={(event) => event.preventDefault()}>
        {!hasContent ? (
          <Welcome
            icon={<RobotOutlined />}
            title={isShareMode ? '分享会话为空' : '你好，我是企业 AI 助手'}
            description={isShareMode ? '这条分享会话还没有消息。' : '可以帮你查资料、写方案、拆任务。'}
            className="saas-ai-assistant-shell__welcome"
          />
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
          sending={sending}
          attachmentUploading={attachmentUploading}
          onAgentsChange={(employeeIds) => setSelectedEmployeeIds(employeeIds)}
          onSend={(messageText, options) => void handleSend(messageText, options)}
          onUploadFiles={(files) => void uploadAttachments(files)}
          onRemoveAttachment={(fileId) => {
            if (activeSession) {
              handleRemoveDraftAttachment(activeSession.id, fileId);
            }
          }}
        />
      </div>
    </section>
  );

  const sessionsPanel = (
    <aside className="saas-ai-assistant-layout__sidebar">
      <Conversations
        items={conversationItems}
        activeKey={activeSessionId}
        onActiveChange={(key) => {
          handleSessionSelect(String(key));
          setMobilePanel('chat');
        }}
        creation={
          isShareMode
            ? undefined
            : {
              label: '新建对话',
              icon: <PlusOutlined />,
              onClick: () => {
                handleCreateSession();
                setMobilePanel('chat');
              },
              align: 'center' as const,
            }
        }
        menu={(conversation) => buildConversationMenu(sessions.find((session) => session.id === String(conversation.key)) || activeSession || buildInitialSession(selectedEmployee))}
        groupable
        className="saas-ai-assistant-conversations"
      />
    </aside>
  );

  if (isShareMode && shareQuery.isError) {
    return (
      <PageContainer title={pageTitle} ghost>
        <Result
          status="404"
          title="分享链接不可用"
          subTitle="这条分享链接不存在、已过期，或者已被撤销。"
          extra={
            <Button type="primary" onClick={() => history.push('/ai/assistant')}>
              返回 AI 助手
            </Button>
          }
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title={pageTitle}
      ghost
      className="saas-ai-assistant-page"
      token={{
        paddingInlinePageContainerContent: 24,
        paddingBlockPageContainerContent: 20,
      }}
      extra={
        isShareMode ? (
          <Button icon={<RobotOutlined />} onClick={() => history.push('/ai/assistant')}>
            返回 AI 助手
          </Button>
        ) : null
      }
    >
      {isShareMode && shareQuery.isLoading ? (
        <div className="saas-ai-assistant-loading">
          <Spin size="large" />
        </div>
      ) : (
        <>
          {isShareMode ? (
            <Alert
              type="info"
              showIcon
              className="saas-ai-assistant-share-alert"
              message="只读分享"
              description="当前会话以分享链接方式打开，仅支持查看、复制和导出。"
            />
          ) : null}

          <div className={`saas-ai-assistant-layout${isShareMode ? ' saas-ai-assistant-layout--share' : ''}`}>
            {isShareMode ? (
              <>
                {sessionsPanel}
                {chatPanel}
              </>
            ) : (
              <>
                <div className="saas-ai-assistant-mobile-shell">
                  <Tabs
                    className="saas-ai-assistant-mobile-tabs"
                    activeKey={mobilePanel}
                    onChange={(key) => setMobilePanel(key as 'chat' | 'sessions')}
                    items={[
                      {
                        key: 'chat',
                        label: '聊天',
                        children: chatPanel,
                      },
                      {
                        key: 'sessions',
                        label: '会话',
                        children: sessionsPanel,
                      },
                    ]}
                  />
                </div>
                <div className="saas-ai-assistant-desktop-shell">
                  {sessionsPanel}
                  {chatPanel}
                </div>
              </>
            )}
          </div>
        </>
      )}

      <Modal
        open={renameModalOpen}
        title="重命名会话"
        okText="保存"
        cancelText="取消"
        centered
        onOk={() => void applyRename()}
        onCancel={closeRenameModal}
      >
        <Input value={renameValue} onChange={(event) => setRenameValue(event.target.value)} placeholder="请输入会话名称" />
      </Modal>
    </PageContainer>
  );
};

export default AiAssistantPage;
