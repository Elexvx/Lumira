import { Button, message } from 'antd';
import { PaperClipOutlined, ThunderboltOutlined, AppstoreOutlined, RobotOutlined } from '@ant-design/icons';
import { Suggestion, Sender as XSender } from '@ant-design/x';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { AiEmployeeRecord } from '@/types/api';
import type { ChatSession } from '../types';
import { AI_ATTACHMENT_ACCEPT, AI_ATTACHMENT_EXTENSIONS, isAllowedAiAttachment } from '../utils';
import { renderAttachmentCardList } from './menuBuilders';

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
  
  const selectedEmployeeIds = useMemo(() => selectedEmployees.map((e) => e.id), [selectedEmployees]);
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
      if (!keyword) return conversationAgentItems;
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
          if (files.length) handleFiles(files);
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
            onSubmit={handleSubmit}
            onPasteFile={(files) => handleFiles(Array.from(files))}
            placeholder={readOnly ? '当前为只读分享页面' : activeSession ? '向我提问吧' : '暂无可用对话'}
            header={
              activeSession?.pendingAttachments.length ? (
                <div className="saas-ai-assistant-composer__header">
                  <div className="saas-ai-assistant-composer__attachments">
                    {renderAttachmentCardList(activeSession.pendingAttachments, {
                      removable: !sending && !readOnly,
                      onRemove: onRemoveAttachment,
                      className: 'saas-ai-assistant-file-card-list saas-ai-assistant-file-card-list--pending',
                    })}
                  </div>
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
                          title={selectedEmployees.length ? `当前助手：${selectedEmployees.map((e) => e.nickname?.trim() || e.username).join('、')}` : '选择助手'}
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
