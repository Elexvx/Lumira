import { Alert, Button, Input, Modal, Result, Spin, Tabs } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { useAssistantPageAccess } from './hooks/useAssistantPageAccess';

import './Assistant.css';

const AiAssistantPage = () => {
  const {
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
  } = useAssistantPageAccess();

  return (
    <PageContainer
      title={pageTitle}
      ghost
      className="saas-ai-assistant-page"
      token={{ paddingInlinePageContainerContent: 24, paddingBlockPageContainerContent: 20 }}
      extra={isShareMode ? <Button icon={<RobotOutlined />} onClick={() => history.push('/ai/assistant')}>返回 AI 助手</Button> : null}
    >
      {isShareMode ? (
        <Alert type="info" showIcon className="saas-ai-assistant-share-alert" message="只读分享" description="当前会话以分享链接方式打开，仅支持查看、复制和导出。" />
      ) : null}

      {isShareMode && shareQuery.isError ? (
        <Result
          status="404"
          title="分享链接不可用"
          subTitle="这条分享链接不存在、已过期，或者已被撤销。"
          extra={<Button type="primary" onClick={() => history.push('/ai/assistant')}>返回 AI 助手</Button>}
        />
      ) : isShareMode && shareQuery.isLoading ? (
        <div className="saas-ai-assistant-loading"><Spin size="large" /></div>
      ) : (
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
      )}

      <Modal open={renameModalOpen} title="重命名会话" onOk={applyRename} onCancel={closeRenameModal} centered okText="保存" cancelText="取消">
        <Input value={renameValue} onChange={(e) => setRenameValue(e.target.value)} placeholder="请输入会话名称" />
      </Modal>
    </PageContainer>
  );
};

export default AiAssistantPage;
