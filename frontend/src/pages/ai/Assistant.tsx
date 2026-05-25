import {
  AppstoreAddOutlined,
  BulbOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileOutlined,
  GlobalOutlined,
  PaperClipOutlined,
  PlusOutlined,
  PushpinOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  ScheduleOutlined,
  ShareAltOutlined,
  SmileOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { history, useParams } from '@umijs/max';
import { Alert, Avatar, Button, Dropdown, Input, Modal, Result, Select, Space, Spin, Tag, Tabs, message } from 'antd';
import type { MenuProps } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
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
  FileObjectRecord,
} from '@/types/api';
import { copyTextToClipboard } from '@/utils/clipboard';
import { confirmAction } from '@/utils/confirm';
import { ALLOWED_UPLOAD_EXTENSIONS, FILE_ACCEPT, MAX_UPLOAD_FILE_COUNT } from '@/pages/files/fileCenter.utils';
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

type ActionItem = {
  key: string;
  label?: string;
  ariaLabel?: string;
  icon?: React.ReactNode;
  onItemClick?: () => void;
};

type AssistantPrompt = {
  key: string;
  title?: string;
  description: string;
  icon?: React.ReactNode;
  rank?: React.ReactNode;
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
  selectedEmployee?: AiEmployeeRecord | null;
  selectedEmployeeOptions: AiEmployeeRecord[];
  readOnly: boolean;
  activeSession?: ChatSession | null;
  sending: boolean;
  attachmentUploading: boolean;
  onEmployeeChange: (employeeId: number) => void;
  onOpenUploadDialog: () => void;
  onCopyShareLink: () => void;
  onShareConversation: () => void;
  onExportConversation: (format: 'markdown' | 'text') => void;
  onSend: (messageText: string) => void;
  onPromptSubmit: (messageText: string) => void;
  onPasteFile: (files: FileList) => void;
  onRemoveAttachment: (fileId: number) => void;
};

const HOT_TOPIC_PROMPTS: AssistantPrompt[] = [
  {
    key: 'hot-1',
    rank: <span className="saas-ai-assistant-prompt-rank saas-ai-assistant-prompt-rank--hot">1</span>,
    description: '帮我梳理当前知识库中最适合自动化处理的业务流程',
  },
  {
    key: 'hot-2',
    rank: <span className="saas-ai-assistant-prompt-rank">2</span>,
    description: '根据最近上传的资料，生成一份可执行的问题清单',
  },
];

const GUIDE_PROMPTS: AssistantPrompt[] = [
  {
    key: 'guide-1',
    title: '意图',
    description: '先理解目标，再给出可落地的解决方案',
    icon: <BulbOutlined />,
  },
  {
    key: 'guide-2',
    title: '角色',
    description: '以企业数字员工身份协助分析和执行',
    icon: <SmileOutlined />,
  },
];

const SENDER_PROMPTS: AssistantPrompt[] = [
  {
    key: 'sender-1',
    description: '动态',
    icon: <ScheduleOutlined />,
  },
  {
    key: 'sender-2',
    description: '组件',
    icon: <RobotOutlined />,
  },
  {
    key: 'sender-3',
    description: '指南',
    icon: <FileSearchOutlined />,
  },
  {
    key: 'sender-4',
    description: '教程',
    icon: <AppstoreAddOutlined />,
  },
];

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

const PromptPanel = ({
  title,
  items,
  disabled,
  onItemClick,
}: {
  title: string;
  items: AssistantPrompt[];
  disabled?: boolean;
  onItemClick: (description: string) => void;
}) => (
  <div className="saas-ai-assistant-prompt-panel">
    <div className="saas-ai-assistant-prompt-panel__title">{title}</div>
    <div className="saas-ai-assistant-prompt-panel__list">
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          className="saas-ai-assistant-prompt-card"
          disabled={disabled}
          onClick={() => onItemClick(item.description)}
        >
          <span className="saas-ai-assistant-prompt-card__icon">{item.rank || item.icon}</span>
          <span className="saas-ai-assistant-prompt-card__content">
            {item.title ? <span className="saas-ai-assistant-prompt-card__title">{item.title}</span> : null}
            <span className="saas-ai-assistant-prompt-card__description">{item.description}</span>
          </span>
        </button>
      ))}
    </div>
  </div>
);

const SenderPrompts = ({
  items,
  disabled,
  onItemClick,
}: {
  items: AssistantPrompt[];
  disabled?: boolean;
  onItemClick: (description: string) => void;
}) => (
  <div className="saas-ai-assistant-sender-prompts">
    {items.map((item) => (
      <button
        key={item.key}
        type="button"
        className="saas-ai-assistant-sender-prompt"
        disabled={disabled}
        onClick={() => onItemClick(item.description)}
      >
        {item.icon}
        <span>{item.description}</span>
      </button>
    ))}
  </div>
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

const SenderHeader = ({
  title,
  children,
}: {
  open?: boolean;
  closable?: boolean;
  forceRender?: boolean;
  title?: React.ReactNode;
  children?: React.ReactNode;
}) => (
  <div className="saas-ai-assistant-sender-header">
    {title ? <div className="saas-ai-assistant-sender-header__title">{title}</div> : null}
    {children}
  </div>
);

const SenderBase = ({
  value,
  loading,
  readOnly,
  disabled,
  onChange,
  onSubmit,
  onPasteFile,
  placeholder,
  header,
  footer,
}: {
  value: string;
  loading?: boolean;
  readOnly?: boolean;
  disabled?: boolean;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  onPasteFile?: (files: FileList) => void;
  placeholder?: string;
  header?: React.ReactNode;
  footer?: React.ReactNode;
  suffix?: React.ReactNode;
}) => (
  <div className="saas-ai-assistant-sender">
    {header}
    <Input.TextArea
      value={value}
      autoSize={{ minRows: 2, maxRows: 6 }}
      readOnly={readOnly}
      disabled={disabled}
      placeholder={placeholder}
      onChange={(event) => onChange(event.target.value)}
      onPaste={(event) => {
        const files = event.clipboardData?.files;
        if (files?.length) {
          onPasteFile?.(files);
        }
      }}
      onPressEnter={(event) => {
        if (!event.shiftKey) {
          event.preventDefault();
          if (value.trim() && !disabled && !loading) {
            onSubmit(value);
          }
        }
      }}
    />
    <div className="saas-ai-assistant-sender__footer">
      <div>{footer}</div>
      <Button type="primary" loading={loading} disabled={disabled || !value.trim()} onClick={() => onSubmit(value)}>
        发送
      </Button>
    </div>
  </div>
);

const Sender = Object.assign(SenderBase, {
  Header: SenderHeader,
});

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
                <Button
                  key={item.key}
                  block
                  type={item.key === activeKey ? 'primary' : 'text'}
                  icon={item.icon}
                  disabled={item.disabled}
                  onClick={() => onActiveChange(item.key)}
                >
                  {item.label}
                </Button>
              );
              const menuProps = menu?.(item);
              return menuProps ? (
                <Dropdown key={item.key} menu={menuProps} trigger={['contextMenu']}>
                  <div>{content}</div>
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
  employeeId: conversation.employeeId,
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
  employeeId: detail.conversation.employeeId,
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

const mapMessageRecord = (record: AiConversationMessageRecord): ChatBubble => ({
  key: `message_${record.id}`,
  role: record.role.trim().toUpperCase() === 'USER' ? 'user' : 'ai',
  content: record.content,
  attachments: (record.attachments || []).map(mapAttachmentRecord),
});

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
    <details className="saas-ai-assistant-thinking" open={Boolean(thinkingContent)}>
      <summary className="saas-ai-assistant-thinking__summary">
        <RobotOutlined className="saas-ai-assistant-thinking__icon" />
        <span>{statusText}</span>
      </summary>
      <div className="saas-ai-assistant-thinking__body">
        {thinkingContent ? (
          <div className="saas-ai-assistant-thinking__content">{thinkingContent}</div>
        ) : (
          <div className="saas-ai-assistant-thinking__loading">
            <Spin size="small" />
            <span>正在调用模型并生成回复，当前接口暂不支持实时过程流。</span>
          </div>
        )}
      </div>
    </details>
  );
};

const MarkdownMessage = ({ content }: { content: string }) => (
  <ReactMarkdown
    remarkPlugins={[remarkGfm]}
    components={{
      a: ({ children, ...props }) => (
        <a {...props} target="_blank" rel="noreferrer">
          {children}
        </a>
      ),
    }}
  >
    {content}
  </ReactMarkdown>
);

const renderMessageContent = (item: ChatBubble, visibleReplyText?: string) => {
  const content = visibleReplyText ?? item.content;
  if (item.role === 'ai') {
    const thinking = renderThinkingContent(item);
    return (
      <div className="saas-ai-assistant-ai-content">
        {thinking}
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

const renderAttachmentTag = (attachment: ComposerAttachment) => (
  <Tag key={attachment.id} icon={<FileOutlined />} color="blue">
    {attachment.originalFileName}
    {attachment.fileSizeLabel ? ` · ${attachment.fileSizeLabel}` : ''}
  </Tag>
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
  selectedEmployee,
  selectedEmployeeOptions,
  readOnly,
  activeSession,
  sending,
  attachmentUploading,
  onEmployeeChange,
  onOpenUploadDialog,
  onCopyShareLink,
  onShareConversation,
  onExportConversation,
  onSend,
  onPromptSubmit,
  onPasteFile,
  onRemoveAttachment,
}: ComposerProps) => {
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    setInputValue('');
  }, [activeSession?.id, readOnly]);

  const employeeSelect = selectedEmployeeOptions.length ? (
    <Select
      value={selectedEmployee?.id}
      onChange={(value) => onEmployeeChange(Number(value))}
      options={selectedEmployeeOptions.map((employee) => ({
        label: employee.nickname || employee.username,
        value: employee.id,
      }))}
      style={{ minWidth: 240 }}
      disabled={readOnly}
    />
  ) : null;

  const toolbar = readOnly ? (
    <Space wrap>
      <Tag color="blue">只读分享</Tag>
      {activeSession?.conversationId ? (
        <Button icon={<DownloadOutlined />} onClick={() => onExportConversation('markdown')}>
          导出
        </Button>
      ) : null}
      <Button icon={<ShareAltOutlined />} onClick={onCopyShareLink}>
        复制分享链接
      </Button>
    </Space>
  ) : (
    <Space wrap>
      {employeeSelect}
      <Button icon={<PaperClipOutlined />} onClick={onOpenUploadDialog} disabled={!selectedEmployee || !activeSession || sending || attachmentUploading}>
        上传附件
      </Button>
    </Space>
  );

  const attachmentFooter = !readOnly && activeSession?.pendingAttachments.length ? (
    <Space wrap className="saas-ai-assistant-composer__attachments">
      {activeSession.pendingAttachments.map((attachment) => (
        <Tag
          key={attachment.id}
          closable
          icon={<FileOutlined />}
          onClose={(event) => {
            event.preventDefault();
            onRemoveAttachment(attachment.fileId);
          }}
        >
          {attachment.originalFileName}
        </Tag>
      ))}
      {attachmentUploading ? <Tag color="processing">附件上传中…</Tag> : null}
    </Space>
  ) : attachmentUploading ? (
    <Tag color="processing">附件上传中…</Tag>
  ) : null;

  return (
    <div className="saas-ai-assistant-composer">
      {!readOnly ? (
        <SenderPrompts
          items={SENDER_PROMPTS}
          disabled={!selectedEmployee || !activeSession || sending}
          onItemClick={onPromptSubmit}
        />
      ) : null}
      <Sender
        value={inputValue}
        loading={sending}
        readOnly={readOnly}
        disabled={readOnly || !selectedEmployee || !activeSession}
        onChange={(nextValue) => setInputValue(nextValue)}
        onSubmit={(nextValue) => {
          onSend(nextValue);
          setInputValue('');
        }}
        onPasteFile={onPasteFile}
        placeholder={readOnly ? '当前为只读分享页面' : selectedEmployee && activeSession ? '向我提问吧' : '暂无可用数字员工'}
        header={
          <Sender.Header open closable={false} title="会话工具" forceRender>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div className="saas-ai-assistant-composer__toolbar">{toolbar}</div>
              {!readOnly && attachmentFooter ? <div className="saas-ai-assistant-composer__attachments">{attachmentFooter}</div> : null}
            </Space>
          </Sender.Header>
        }
        footer={readOnly ? null : attachmentFooter}
        suffix={null}
      />
    </div>
  );
};

const AiAssistantPage = () => {
  const queryClient = useQueryClient();
  const params = useParams<RouteParams>();
  const shareToken = params.token?.trim() || '';
  const isShareMode = Boolean(shareToken);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [sending, setSending] = useState(false);
  const [attachmentUploading, setAttachmentUploading] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string>('session-default');
  const [mobilePanel, setMobilePanel] = useState<'chat' | 'sessions'>('chat');
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<number | null>(null);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [renameTargetSessionId, setRenameTargetSessionId] = useState<string | null>(null);
  const [streamProgress, setStreamProgress] = useState<Record<string, number>>({});

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

  const employees = employeesQuery.data?.records || [];
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

    if (!employees.length) {
      setSelectedEmployeeId(assistantEmployee?.id ?? null);
      return;
    }

    setSelectedEmployeeId((currentValue) => {
      if (currentValue && employees.some((employee) => employee.id === currentValue)) {
        return currentValue;
      }
      const defaultEmployee = employees.find((employee) => employee.enabled) || employees[0];
      return defaultEmployee?.id ?? assistantEmployee?.id ?? null;
    });
  }, [assistantEmployee?.id, employees, isShareMode]);

  const selectedEmployeeOptions = useMemo(() => {
    if (employees.length) {
      return employees;
    }
    return assistantEmployee ? [assistantEmployee] : [];
  }, [assistantEmployee, employees]);

  const selectedEmployee = useMemo(() => {
    if (isShareMode) {
      return shareEmployee;
    }

    if (!selectedEmployeeOptions.length) {
      return null;
    }

    if (selectedEmployeeId) {
      return selectedEmployeeOptions.find((employee) => employee.id === selectedEmployeeId) || selectedEmployeeOptions[0];
    }

    return selectedEmployeeOptions.find((employee) => employee.enabled) || selectedEmployeeOptions[0] || null;
  }, [isShareMode, selectedEmployeeId, selectedEmployeeOptions, shareEmployee]);

  const conversationsQuery = useQuery({
    queryKey: ['ai-assistant-conversations', selectedEmployee?.id],
    enabled: !isShareMode && Boolean(selectedEmployee?.id),
    queryFn: async () =>
      aiService.conversations(
        {
          employeeId: selectedEmployee!.id,
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

    if (!selectedEmployee) {
      setSessions([]);
      setActiveSessionId('session-default');
      return;
    }

    const records = conversationsQuery.data?.records || [];
    setSessions((currentSessions) => {
      const draftSessions = currentSessions.filter((session) => session.isDraft || !session.conversationId);
      const persistedSessions = records.map((record) => {
        const existingSession = currentSessions.find((session) => session.conversationId === record.id || session.id === String(record.id));
        if (!existingSession) {
          return buildSessionFromConversation(record, selectedEmployee);
        }

        return {
          ...existingSession,
          id: String(record.id),
          title: record.title?.trim() || existingSession.title,
          preview: record.preview?.trim() || record.title?.trim() || existingSession.preview,
          employeeId: record.employeeId,
          employeeName: record.employeeName?.trim() || existingSession.employeeName,
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
  }, [conversationsQuery.data, isShareMode, selectedEmployee]);

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
      void queryClient.invalidateQueries({ queryKey: ['ai-assistant-conversations', selectedEmployee?.id] });
      closeRenameModal();
      message.success('会话名称已更新');
    } catch (error) {
      message.error(error instanceof Error && error.message ? error.message : '重命名失败');
    }
  };

  const uploadAttachments = async (files: File[]) => {
    if (isShareMode || !selectedEmployee || !activeSession) {
      return;
    }

    const allowedFiles = files.filter((file) => {
      const fileExtension = file.name.split('.').pop()?.toLowerCase() || '';
      return ALLOWED_UPLOAD_EXTENSIONS.includes(fileExtension);
    });

    if (!allowedFiles.length) {
      message.error(`仅支持 ${ALLOWED_UPLOAD_EXTENSIONS.map((item) => item.toUpperCase()).join('、')} 文件`);
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

  const handleSend = async (messageText: string) => {
    const trimmed = messageText.trim();
    if (!trimmed || !selectedEmployee || !activeSession || isShareMode) {
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

    setSending(true);
    updateSession(activeSession.id, (session) => ({
      ...session,
      employeeId: selectedEmployee.id,
      employeeName: selectedEmployee.nickname?.trim() || selectedEmployee.username,
      employeeAvatarKey: selectedEmployee.avatarKey ?? null,
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
            employeeId: selectedEmployee.id,
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

        const recoveredSession = buildSessionFromConversation(latestConversation, selectedEmployee);
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
          queryKey: ['ai-assistant-conversations', selectedEmployee.id],
        });
        return true;
      };

      try {
        await aiService.streamChat(
          {
            employeeId: selectedEmployee.id,
            conversationId: activeSession.conversationId ?? null,
            message: trimmed,
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
        queryKey: ['ai-assistant-conversations', selectedEmployee.id],
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

  const handlePromptSubmit = (messageText: string) => {
    if (isShareMode || sending) {
      return;
    }
    void handleSend(messageText);
  };

  const handleCreateSession = () => {
    if (isShareMode || !selectedEmployee) {
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
          queryKey: ['ai-assistant-conversations', selectedEmployee?.id],
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
        queryKey: ['ai-assistant-conversations', selectedEmployee?.id],
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
        ),
        footer: (
          <Space direction="vertical" size={8} className="saas-ai-assistant-bubble__footer">
            {item.attachments.length ? <Space wrap>{item.attachments.map(renderAttachmentTag)}</Space> : null}
            {item.references?.length ? (
              <Space wrap>
                {item.references.slice(0, 4).map((reference) => (
                  <Tag key={reference.chunkId} color="cyan">
                    {reference.documentTitle || reference.originalFileName || reference.knowledgeBaseName || '知识库引用'}
                  </Tag>
                ))}
              </Space>
            ) : null}
            <div className="saas-ai-assistant-bubble__actions">
              {createActions(item, {
                onCopy: handleCopyMessage,
              })}
            </div>
          </Space>
        ),
      })) || [],
    [activeSession, streamProgress],
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

  const emptyWelcome = buildAssistantGreeting(selectedEmployee, shareConversation?.employeeName || undefined);
  const hasContent = Boolean(activeSession?.messages?.length);
  const pageTitle = isShareMode ? 'AI 会话分享' : 'AI 助手';

  const triggerUploadDialog = () => {
    fileInputRef.current?.click();
  };

  const handleUploadFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextFiles = Array.from(event.target.files || []);
    event.target.value = '';
    if (!nextFiles.length) {
      return;
    }
    void uploadAttachments(nextFiles);
  };

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
            extra={
              isShareMode ? (
                <Bubble.System content={emptyWelcome} variant="borderless" />
              ) : (
                <div className="saas-ai-assistant-welcome-prompts">
                  <Bubble.System content={emptyWelcome} variant="borderless" />
                  <div className="saas-ai-assistant-prompt-grid">
                    <PromptPanel
                      title="最热话题"
                      items={HOT_TOPIC_PROMPTS}
                      disabled={!selectedEmployee || sending}
                      onItemClick={handlePromptSubmit}
                    />
                    <PromptPanel
                      title="设计指南"
                      items={GUIDE_PROMPTS}
                      disabled={!selectedEmployee || sending}
                      onItemClick={handlePromptSubmit}
                    />
                  </div>
                </div>
              )
            }
            className="saas-ai-assistant-shell__welcome"
          />
        ) : (
          <Bubble.List items={activeMessageItems} role={bubbleRole} autoScroll className="saas-ai-assistant-bubbles" />
        )}
      </div>

      <div className="saas-ai-assistant-shell__composer">
        <Composer
          selectedEmployee={selectedEmployee}
          selectedEmployeeOptions={isShareMode ? [] : selectedEmployeeOptions}
          readOnly={isShareMode}
          activeSession={activeSession}
          sending={sending}
          attachmentUploading={attachmentUploading}
          onEmployeeChange={(employeeId) => {
            setSelectedEmployeeId(employeeId);
            setActiveSessionId('session-default');
          }}
          onOpenUploadDialog={triggerUploadDialog}
          onCopyShareLink={handleCopyShareLink}
          onShareConversation={() => void handleShareConversation(activeSession)}
          onExportConversation={(format) => void handleExportConversation(format, activeSession)}
          onSend={(messageText) => void handleSend(messageText)}
          onPromptSubmit={handlePromptSubmit}
          onPasteFile={(files) => void uploadAttachments(Array.from(files))}
          onRemoveAttachment={(fileId) => handleRemoveDraftAttachment(activeSession?.id || '', fileId)}
        />
        <input
          ref={fileInputRef}
          type="file"
          accept={FILE_ACCEPT}
          multiple
          className="saas-ai-assistant-file-input"
          onChange={handleUploadFileInputChange}
        />
      </div>
    </section>
  );

  const sessionsPanel = (
    <aside className="saas-ai-assistant-layout__sidebar">
      <div className="saas-ai-assistant-sidebar__brand">
        <span className="saas-ai-assistant-sidebar__logo">
          <RobotOutlined />
        </span>
        <span>AI 助手</span>
      </div>
      <Conversations
        items={conversationItems}
        activeKey={activeSessionId}
        onActiveChange={(key) => {
          handleSessionSelect(String(key));
          setMobilePanel('chat');
        }}
        creation={
          selectedEmployee
            ? {
                label: '新建对话',
                icon: <PlusOutlined />,
                onClick: () => {
                  handleCreateSession();
                  setMobilePanel('chat');
                },
                align: 'center' as const,
              }
            : undefined
        }
        menu={(conversation) => buildConversationMenu(sessions.find((session) => session.id === String(conversation.key)) || activeSession || buildInitialSession(selectedEmployee))}
        groupable
        className="saas-ai-assistant-conversations"
      />
      <div className="saas-ai-assistant-sidebar__footer">
        <Space size={8}>
          <Avatar size={24} icon={<GlobalOutlined />} />
          <span>当前对话</span>
        </Space>
        <Button type="text" icon={<QuestionCircleOutlined />} aria-label="帮助" title="帮助" />
      </div>
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
