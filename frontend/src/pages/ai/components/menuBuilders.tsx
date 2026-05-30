import { Alert, Avatar, Button, Tag, Space, Spin } from 'antd';
import { Actions, FileCard, Sources, Think } from '@ant-design/x';
import { XMarkdown } from '@ant-design/x-markdown';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { CheckCircleOutlined, CloseCircleOutlined, CopyOutlined, DeleteOutlined, DownloadOutlined, EditOutlined, PushpinOutlined, ShareAltOutlined } from '@ant-design/icons';
import React from 'react';
import type { MenuProps } from 'antd';
import type { BubbleListProps as XBubbleListProps } from '@ant-design/x';
import type { AiKnowledgeReferenceRecord, AiToolPlanRecord } from '@/types/api';
import type { ChatBubble, ChatSession, ComposerAttachment, BubbleItem } from '../types';
import { toAttachmentFileCardItem } from '../utils';

export const renderAttachmentCardList = (
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

export const MarkdownMessage = ({ content }: { content: string }) => (
  <XMarkdown content={content} openLinksInNewTab escapeRawHtml />
);

export const renderThinkingContent = (item: ChatBubble) => {
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

export const renderSources = (references?: AiKnowledgeReferenceRecord[] | null) => {
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

export const renderToolPlanCard = (
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

export const renderMessageContent = (
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

export const createActions = (
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

export const buildMessageActions = (
  messages: ChatBubble[],
  options: {
    onConfirmTool: (plan: AiToolPlanRecord) => void;
    confirmingToolId: number | null;
    onCopy: (text: string) => void;
  }
): XBubbleListProps['items'] => {
  return messages.map((item) => ({
    key: item.key,
    role: item.role,
    content: renderMessageContent(item, undefined, {
      onConfirmTool: options.onConfirmTool,
      confirmingToolId: options.confirmingToolId,
    }),
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
          {createActions(item, { onCopy: options.onCopy })}
        </div>
      </Space>
    ),
  }));
};

export const buildConversationMenu = (
  session: ChatSession,
  actions: {
    openRenameModal: (session: ChatSession) => void;
    handleTogglePinConversation: (session: ChatSession) => void;
    handleShareConversation: (session: ChatSession) => void;
    handleExportConversation: (format: 'markdown' | 'text', session: ChatSession) => void;
    handleDeleteConversation: (session: ChatSession) => void;
  }
): MenuProps => {
  const canShare = Boolean(session.conversationId);
  return {
    items: [
      { key: 'rename', label: '重命名', icon: <EditOutlined /> },
      { key: 'pin', label: session.isPinned ? '取消置顶' : '置顶', icon: <PushpinOutlined /> },
      ...(canShare
        ? [
            { key: 'share', label: '复制分享链接', icon: <ShareAltOutlined /> },
            { key: 'export-markdown', label: '导出 Markdown', icon: <DownloadOutlined /> },
            { key: 'export-text', label: '导出文本', icon: <DownloadOutlined /> },
          ]
        : []),
      { type: 'divider' as const },
      { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
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
