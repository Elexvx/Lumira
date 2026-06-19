import React from 'react';
import { AppstoreOutlined, PaperClipOutlined, RobotOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { Suggestion, Sender as XSender } from '@ant-design/x';
import { FileCard } from '@ant-design/x';
import type { FileCardProps } from '@ant-design/x';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import type { ReactNode } from 'react';
import type { SenderProps, SuggestionProps } from '@ant-design/x';
import type { AiEmployeeRecord } from '@/types/api';
import type { ChatSession } from '../types';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const AI_ATTACHMENT_EXTENSIONS = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'md', 'txt', 'png', 'jpg', 'jpeg', 'gif', 'bmp'];
const AI_ATTACHMENT_ACCEPT = AI_ATTACHMENT_EXTENSIONS.map((extension) => `.${extension}`).join(',');

const getFileExtension = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || '';

const isAllowedAiAttachment = (file: File) => AI_ATTACHMENT_EXTENSIONS.includes(getFileExtension(file.name));

const getAttachmentFileIcon = (attachment: { fileExtension?: string | null; originalFileName: string }): FileCardProps['icon'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['xls', 'xlsx'].includes(extension)) return 'excel';
  if (['doc', 'docx'].includes(extension)) return 'word';
  if (['ppt', 'pptx'].includes(extension)) return 'ppt';
  if (extension === 'pdf') return 'pdf';
  if (extension === 'md') return 'markdown';
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
  return 'default';
};

const getAttachmentFileType = (attachment: { fileExtension?: string | null; originalFileName: string }): FileCardProps['type'] => {
  const extension = (attachment.fileExtension || getFileExtension(attachment.originalFileName)).toLowerCase();
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(extension)) return 'image';
  return 'file';
};

const renderAttachmentCardList = (
  attachments: Array<{
    fileId: number;
    originalFileName: string;
    fileExtension?: string | null;
    fileSizeBytes?: number | null;
    fileSizeLabel?: string | null;
    publicUrl?: string | null;
    previewUrl?: string | null;
    downloadUrl?: string | null;
  }>,
  options?: {
    removable?: boolean;
    onRemove?: (fileId: number) => void;
    className?: string;
  },
) =>
  (
    <FileCard.List
      className={options?.className}
      items={attachments.map((attachment) => ({
        key: attachment.fileId,
        name: attachment.originalFileName,
        byte: attachment.fileSizeBytes ?? undefined,
        description: attachment.fileSizeLabel || undefined,
        icon: getAttachmentFileIcon(attachment),
        type: getAttachmentFileType(attachment),
        src: attachment.previewUrl || attachment.publicUrl || attachment.downloadUrl || undefined,
      }))}
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

export interface ComposerProps {
  employees: AiEmployeeRecord[];
  selectedEmployees: AiEmployeeRecord[];
  readOnly?: boolean;
  activeSession: ChatSession | null;
  sending: boolean;
  attachmentUploading: boolean;
  onAgentsChange: (employeeIds: number[]) => void;
  onSend: (messageText: string, options?: { enableThinking?: boolean; employeeIds?: number[] }) => void;
  onUploadFiles: (files: File[]) => void;
  onRemoveAttachment: (fileId: number) => void;
}

const ComposerPendingAttachments = ({
  activeSession,
  sending,
  readOnly,
  onRemoveAttachment,
}: {
  activeSession: ChatSession | null;
  sending: boolean;
  readOnly?: boolean;
  onRemoveAttachment: (fileId: number) => void;
}) => {
  if (!activeSession?.pendingAttachments.length) {
    return null;
  }

  return (
    <div className="saas-ai-assistant-composer__header">
      <div className="saas-ai-assistant-composer__attachments">
        {renderAttachmentCardList(activeSession.pendingAttachments, {
          removable: !sending && !readOnly,
          onRemove: onRemoveAttachment,
          className: 'saas-ai-assistant-file-card-list saas-ai-assistant-file-card-list--pending',
        })}
      </div>
    </div>
  );
};

const ComposerFooterControls = ({
  senderKey,
  inputValue,
  deepThink,
  readOnly,
  activeSessionExists,
  sending,
  attachmentUploading,
  selectedAgentSkill,
  selectedAgentTitle,
  agentControlLabel,
  agentSuggestionItems,
  conversationAgentItems,
  onInputChange,
  onSubmit,
  onPasteFiles,
  onOuterSuggestionSelect,
  onConversationAgentSelect,
  onUploadClick,
  onDeepThinkChange,
}: {
  senderKey: string;
  inputValue: string;
  deepThink: boolean;
  readOnly?: boolean;
  activeSessionExists: boolean;
  sending: boolean;
  attachmentUploading: boolean;
  selectedAgentSkill: SenderProps['skill'];
  selectedAgentTitle: string;
  agentControlLabel: string;
  agentSuggestionItems: SuggestionProps<{ keyword?: string }>['items'];
  conversationAgentItems: Array<{ label: ReactNode; value: string; icon?: ReactNode; extra?: ReactNode }>;
  onInputChange: (nextValue: string) => void;
  onSubmit: NonNullable<SenderProps['onSubmit']>;
  onPasteFiles: (files: File[]) => void;
  onOuterSuggestionSelect: NonNullable<SuggestionProps<{ keyword?: string }>['onSelect']>;
  onConversationAgentSelect: (value: string) => void;
  onUploadClick: () => void;
  onDeepThinkChange: (nextValue: boolean) => void;
}) => (
  <Suggestion items={agentSuggestionItems} onSelect={onOuterSuggestionSelect} rootClassName="saas-ai-assistant-agent-suggestion">
    {({ onTrigger }) => (
      <XSender
        key={senderKey}
        rootClassName="saas-ai-assistant-sender"
        loading={sending}
        readOnly={readOnly}
        disabled={readOnly || !activeSessionExists}
        autoSize={{ minRows: 1, maxRows: 5 }}
        submitType="enter"
        value={inputValue}
        skill={selectedAgentSkill}
        onChange={(nextValue) => {
          onInputChange(nextValue);
          const slashMatch = nextValue.match(/(?:^|\s)\/([^\s/]*)$/);
          onTrigger(slashMatch ? { keyword: slashMatch[1] } : false);
        }}
        onSubmit={onSubmit}
        onPasteFile={(files) => onPasteFiles(Array.from(files))}
        placeholder={readOnly ? t('当前为只读分享页面', 'This is a read-only shared page') : activeSessionExists ? t('向我提问吧', 'Ask me anything') : t('暂无可用对话', 'No available conversation')}
        prefix={false}
        footer={(_, { components }) => {
          const { SendButton, LoadingButton } = components;
          return (
            <div className="saas-ai-assistant-composer__footer">
              <div className="saas-ai-assistant-composer__tools">
                <Button
                  type="text"
                  icon={<PaperClipOutlined />}
                  aria-label={t('上传附件', 'Upload attachment')}
                  title={t('上传附件', 'Upload attachment')}
                  loading={attachmentUploading}
                  disabled={readOnly || sending || attachmentUploading || !activeSessionExists}
                  onClick={onUploadClick}
                />
                <XSender.Switch
                  icon={<ThunderboltOutlined />}
                  value={deepThink}
                  disabled={readOnly || sending || !activeSessionExists}
                  onChange={(nextValue) => onDeepThinkChange(Boolean(nextValue))}
                />
                <Suggestion items={conversationAgentItems} onSelect={onConversationAgentSelect}>
                  {({ onTrigger: onAgentTrigger, onKeyDown }) => (
                    <Button
                      className="saas-ai-assistant-composer__agent-button"
                      icon={<AppstoreOutlined />}
                      type={selectedAgentSkill ? 'primary' : 'default'}
                      disabled={readOnly || sending || !activeSessionExists}
                      title={selectedAgentTitle}
                      onClick={() => onAgentTrigger({})}
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
);

export const Composer: React.FC<ComposerProps> = ({
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
}) => {
  const [inputValue, setInputValue] = useState('');
  const [senderKey, setSenderKey] = useState(0);
  const [deepThink, setDeepThink] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const selectedEmployeeIds = useMemo(() => selectedEmployees.map((employee) => employee.id), [selectedEmployees]);
  const firstSelectedEmployee = selectedEmployees[0] || null;

  const agentButtonLabel = selectedEmployees.length > 1
    ? t(`${selectedEmployees.length} 个助手`, `${selectedEmployees.length} assistants`)
    : firstSelectedEmployee?.nickname?.trim() || firstSelectedEmployee?.username || t('助手', 'Assistant');

  const agentControlLabel = selectedEmployees.length ? t('切换助手', 'Switch assistant') : t('助手', 'Assistant');

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
        label: t('普通对话', 'General chat'),
        value: 'general',
        icon: <RobotOutlined />,
        extra: t('不使用数字员工', 'Do not use an AI employee'),
      },
      ...agentItems,
    ],
    [agentItems],
  );

  const agentSuggestionItems = useMemo(
    () => (info?: { keyword?: string }) => {
      const keyword = info?.keyword?.trim().toLowerCase();
      if (!keyword) return conversationAgentItems;
      return conversationAgentItems.filter((item) => String(item.label).toLowerCase().includes(keyword));
    },
    [conversationAgentItems],
  );

  const selectedAgentSkill = selectedEmployees.length
    ? {
        title: selectedEmployees.length > 1 ? t(`${selectedEmployees.length} 个助手协同`, `${selectedEmployees.length} assistants collaborating`) : agentButtonLabel,
        value: selectedEmployeeIds.join(','),
        closable: {
          disabled: readOnly || sending,
          onClose: () => onAgentsChange([]),
        },
      }
    : undefined;

  const selectedAgentTitle = selectedEmployees.length
    ? t(
        `当前助手：${selectedEmployees.map((employee) => employee.nickname?.trim() || employee.username).join('、')}`,
        `Selected assistants: ${selectedEmployees.map((employee) => employee.nickname?.trim() || employee.username).join(', ')}`,
      )
    : t('选择助手', 'Choose assistant');

  const handleAgentSuggestionSelect = useCallback(
    (value: string) => {
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
    },
    [onAgentsChange, selectedEmployeeIds],
  );

  const handleFiles = useCallback(
    (files: File[]) => {
      const safeFiles = files.filter(isAllowedAiAttachment);
      const blockedCount = files.length - safeFiles.length;
      if (blockedCount > 0) {
        message.warning(t('已拦截不支持或存在风险的文件格式', 'Blocked unsupported or risky file types'));
      }
      if (!safeFiles.length) {
        message.error(t(`仅支持 ${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join('、')} 文件`, `Only ${AI_ATTACHMENT_EXTENSIONS.map((item) => item.toUpperCase()).join(', ')} files are supported`));
        return;
      }
      onUploadFiles(safeFiles);
    },
    [onUploadFiles],
  );
  const handleFileInputChange = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (files.length) {
      handleFiles(files);
    }
  }, [handleFiles]);

  useEffect(() => {
    setInputValue('');
    setSenderKey((current) => current + 1);
  }, [activeSession?.id, readOnly]);

  const handleSubmit = useCallback(
    (messageText: string) => {
      const normalizedMessage = messageText.trim();
      if (!normalizedMessage || normalizedMessage === t('请', 'Please')) {
        message.warning(t('请输入要处理的任务或问题', 'Please enter a task or question'));
        return;
      }
      onSend(normalizedMessage, { enableThinking: deepThink, employeeIds: selectedEmployeeIds });
      setInputValue('');
      setSenderKey((current) => current + 1);
    },
    [deepThink, onSend, selectedEmployeeIds],
  );

  return (
    <div className="saas-ai-assistant-composer">
      <input
        ref={fileInputRef}
        type="file"
        accept={AI_ATTACHMENT_ACCEPT}
        multiple
        hidden
        onChange={handleFileInputChange}
      />
      <ComposerPendingAttachments
        activeSession={activeSession}
        sending={sending}
        readOnly={readOnly}
        onRemoveAttachment={onRemoveAttachment}
      />
      <ComposerFooterControls
        senderKey={String(senderKey)}
        inputValue={inputValue}
        deepThink={deepThink}
        readOnly={readOnly}
        activeSessionExists={Boolean(activeSession)}
        sending={sending}
        attachmentUploading={attachmentUploading}
        selectedAgentSkill={selectedAgentSkill}
        selectedAgentTitle={selectedAgentTitle}
        agentControlLabel={agentControlLabel}
        agentSuggestionItems={agentSuggestionItems}
        conversationAgentItems={conversationAgentItems}
        onInputChange={setInputValue}
        onSubmit={handleSubmit}
        onPasteFiles={(files) => handleFiles(files)}
        onOuterSuggestionSelect={handleAgentSuggestionSelect}
        onConversationAgentSelect={handleAgentSuggestionSelect}
        onUploadClick={() => fileInputRef.current?.click()}
        onDeepThinkChange={(nextValue) => setDeepThink(Boolean(nextValue))}
      />
    </div>
  );
};
