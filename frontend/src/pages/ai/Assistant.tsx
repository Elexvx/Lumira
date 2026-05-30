import { Alert, Button, Input, Modal, Result, Spin, Tabs, message } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history, useParams } from '@umijs/max';
import React, { useMemo, useState } from 'react';
import type { DragEvent } from 'react';

import { copyTextToClipboard } from '@/utils/clipboard';
import { confirmAction } from '@/utils/confirm';
import { aiService } from '@/services/ai';

import type { RouteParams, ChatSession } from './types';
import { useAiChatData } from './hooks/useAiChatData';
import { useAiChatAttachments } from './hooks/useAiChatAttachments';
import { useAiChatSend } from './hooks/useAiChatSend';
import {
  getConversationGroup,
  buildExportContent,
  formatExportFileName,
  downloadText,
} from './utils';

import { Welcome } from './components/Welcome';
import { BubbleList } from './components/BubbleList';
import { Conversations } from './components/Conversations';
import { Composer } from './components/Composer';
import { buildConversationMenu, buildMessageActions } from './components/menuBuilders';

import './Assistant.css';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


const AiAssistantPage = () => {
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

  const { attachmentUploading, uploadAttachments, handleRemoveDraftAttachment } = useAiChatAttachments();
  const { sending, handleSend, handleConfirmTool, confirmingToolId } = useAiChatSend({
    isShareMode,
    activeSession,
    activeEmployeeIds: selectedEmployees.map(e => e.id),
    employeeById,
    updateSession,
    setActiveSessionId,
  });

  const [mobilePanel, setMobilePanel] = useState<'chat' | 'sessions'>('chat');
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [renameTargetSessionId, setRenameTargetSessionId] = useState<string | null>(null);

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
    if (!renameTargetSessionId || !renameValue.trim()) {
      if (!renameValue.trim()) message.warning('请输入会话名称');
      return;
    }
    await handleRenameSession(renameTargetSessionId, renameValue.trim());
    closeRenameModal();
    message.success('会话名称已更新');
  };

  const currentExportSession = useMemo(() => activeSession || sessions[0] || null, [activeSession, sessions]);

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
      const share = await aiService.shareConversation(targetSession.conversationId, API_OPTS.NO_REDIRECT);
      const shareUrl = new URL(`/ai/share/${share.shareToken}`, window.location.origin).toString();
      await copyTextToClipboard(shareUrl);
      message.success('分享链接已复制');
    } catch (error) {
      showErrorMessage(error, '创建分享链接失败');
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
        const exportResult = await aiService.exportConversation(targetSession.conversationId, { format }, API_OPTS.NO_REDIRECT);
        downloadText(exportResult.content, exportResult.fileName, exportResult.mimeType);
        return;
      }
      const content = buildExportContent(targetSession, format);
      const fileName = formatExportFileName(targetSession.title, format);
      downloadText(content, fileName, format === 'markdown' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8');
    } catch (error) {
      showErrorMessage(error, '导出失败');
    }
  };

  const menuActions = {
    openRenameModal,
    handleTogglePinConversation,
    handleShareConversation,
    handleExportConversation,
    handleDeleteConversation: (s: ChatSession) => {
      confirmAction({
        title: '删除会话',
        content: `确认删除会话「${s.title}」吗？删除后消息、附件和分享记录都会清理。`,
        okText: '确认删除',
        okButtonProps: { danger: true },
        onOk: () => handleDeleteConversation(s),
      });
    },
  };

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

  const activeMessageItems = buildMessageActions(activeSession?.messages || [], {
    onConfirmTool: handleConfirmTool,
    confirmingToolId,
    onCopy: handleCopyMessage,
  });

  const handleDropFiles = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isShareMode) return;
    const nextFiles = Array.from(event.dataTransfer.files || []);
    if (!nextFiles.length) return;
    void uploadAttachments(nextFiles, { isShareMode, activeSession, updateSession });
  };

  const chatPanel = (
    <section className="saas-ai-assistant-layout__chat">
      <div className="saas-ai-assistant-shell__chat-body" onDrop={handleDropFiles} onDragOver={(event) => event.preventDefault()}>
        {!activeSession?.messages?.length ? (
          <Welcome isShareMode={isShareMode} />
        ) : (
          <BubbleList items={activeMessageItems} />
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
      <Conversations
        items={conversationItems}
        activeKey={activeSessionId}
        onActiveChange={(key) => {
          handleSessionSelect(String(key));
          setMobilePanel('chat');
        }}
        isShareMode={isShareMode}
        onCreateSession={() => {
          handleCreateSession();
          setMobilePanel('chat');
        }}
        buildMenu={(convKey) => buildConversationMenu(sessions.find((s) => s.id === convKey)!, menuActions)}
      />
    </aside>
  );

  if (isShareMode && shareQuery.isError) {
    return (
      <PageContainer title="AI 会话分享" ghost>
        <Result
          status="404"
          title="分享链接不可用"
          subTitle="这条分享链接不存在、已过期，或者已被撤销。"
          extra={<Button type="primary" onClick={() => history.push('/ai/assistant')}>返回 AI 助手</Button>}
        />
      </PageContainer>
    );
  }

  const pageTitle = isShareMode ? 'AI 会话分享' : 'AI 助手';

  return (
    <PageContainer
      title={pageTitle}
      ghost
      className="saas-ai-assistant-page"
      token={{ paddingInlinePageContainerContent: 24, paddingBlockPageContainerContent: 20 }}
      extra={isShareMode ? <Button icon={<RobotOutlined />} onClick={() => history.push('/ai/assistant')}>返回 AI 助手</Button> : null}
    >
      {isShareMode && shareQuery.isLoading ? (
        <div className="saas-ai-assistant-loading"><Spin size="large" /></div>
      ) : (
        <>
          {isShareMode ? (
            <Alert type="info" showIcon className="saas-ai-assistant-share-alert" message="只读分享" description="当前会话以分享链接方式打开，仅支持查看、复制和导出。" />
          ) : null}

          <div className={`saas-ai-assistant-layout${isShareMode ? ' saas-ai-assistant-layout--share' : ''}`}>
            {isShareMode ? (
              <>{sessionsPanel}{chatPanel}</>
            ) : (
              <>
                <div className="saas-ai-assistant-mobile-shell">
                  <Tabs
                    className="saas-ai-assistant-mobile-tabs"
                    activeKey={mobilePanel}
                    onChange={(key) => setMobilePanel(key as 'chat' | 'sessions')}
                    items={[{ key: 'chat', label: '聊天', children: chatPanel }, { key: 'sessions', label: '会话', children: sessionsPanel }]}
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

      <Modal open={renameModalOpen} title="重命名会话" onOk={applyRename} onCancel={closeRenameModal} centered okText="保存" cancelText="取消">
        <Input value={renameValue} onChange={(e) => setRenameValue(e.target.value)} placeholder="请输入会话名称" />
      </Modal>
    </PageContainer>
  );
};

export default AiAssistantPage;
