import { Alert, Button, Input, Modal, Result, Spin, Tabs } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { getLocale, history } from '@umijs/max';
import { useResponsive } from '@/hooks/useResponsive';
import { useAssistantPageAccess } from './hooks/useAssistantPageAccess';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { normalizeLocale } from '@/i18n/locale';

import './Assistant.css';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
  const responsive = useResponsive();
  const pagePaddingInline = resolveResponsiveValue(APP_SPACING.pageContainerPaddingInline, responsive.isMobile);
  const pagePaddingBlock = resolveResponsiveValue(APP_SPACING.pageContainerPaddingBlock, responsive.isMobile);

  return (
    <PageContainer
      title={pageTitle}
      ghost
      className="saas-ai-assistant-page"
      token={{ paddingInlinePageContainerContent: pagePaddingInline, paddingBlockPageContainerContent: pagePaddingBlock }}
      extra={isShareMode ? <Button icon={<RobotOutlined />} onClick={() => history.push('/ai/assistant')}>{t('返回 AI 助手', 'Back to AI Assistant')}</Button> : null}
    >
      {isShareMode ? (
        <Alert type="info" showIcon className="saas-ai-assistant-share-alert" message={t('只读分享', 'Read-only share')} description={t('当前会话以分享链接方式打开，仅支持查看、复制和导出。', 'This conversation is opened via a share link and supports view, copy, and export only.')} />
      ) : null}

      {isShareMode && shareQuery.isError ? (
        <Result
          status="404"
          title={t('分享链接不可用', 'Share link unavailable')}
          subTitle={t('这条分享链接不存在、已过期，或者已被撤销。', 'This share link does not exist, has expired, or has been revoked.')}
          extra={<Button type="primary" onClick={() => history.push('/ai/assistant')}>{t('返回 AI 助手', 'Back to AI Assistant')}</Button>}
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
                  items={[{ key: 'chat', label: t('聊天', 'Chat'), children: chatPanel }, { key: 'sessions', label: t('会话', 'Sessions'), children: sessionsPanel }]}
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

      <Modal open={renameModalOpen} title={t('重命名会话', 'Rename conversation')} onOk={applyRename} onCancel={closeRenameModal} centered okText={t('保存', 'Save')} cancelText={t('取消', 'Cancel')}>
        <Input value={renameValue} onChange={(e) => setRenameValue(e.target.value)} placeholder={t('请输入会话名称', 'Enter a conversation name')} />
      </Modal>
    </PageContainer>
  );
};

export default AiAssistantPage;
