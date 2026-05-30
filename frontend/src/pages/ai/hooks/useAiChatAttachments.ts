import { useState } from 'react';
import { message } from 'antd';
import { fileService } from '@/services/file';
import type { ChatSession, ComposerAttachment } from '../types';
import { MAX_UPLOAD_FILE_COUNT } from '@/pages/files/fileCenter.utils';
import { isAllowedAiAttachment, AI_ATTACHMENT_EXTENSIONS, mapFileObjectToAttachment, AI_CHAT_ATTACHMENT_BUCKET } from '../utils';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';


export const useAiChatAttachments = () => {
  const [attachmentUploading, setAttachmentUploading] = useState(false);

  const uploadAttachments = async (
    files: File[],
    options: {
      isShareMode: boolean;
      activeSession: ChatSession | null;
      updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;
    }
  ) => {
    const { isShareMode, activeSession, updateSession } = options;
    if (isShareMode || !activeSession) return;

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
          API_OPTS.NO_REDIRECT
        );
        uploadedAttachments.push(mapFileObjectToAttachment(record));
      }

      updateSession(activeSession.id, (session) => ({
        ...session,
        pendingAttachments: [...session.pendingAttachments, ...uploadedAttachments],
      }));
      message.success(`已添加 ${uploadedAttachments.length} 个附件`);
    } catch (error) {
      showErrorMessage(error, '附件上传失败');
    } finally {
      setAttachmentUploading(false);
    }
  };

  const handleRemoveDraftAttachment = (
    fileId: number,
    options: {
      activeSession: ChatSession | null;
      updateSession: (sessionId: string, updater: (session: ChatSession) => ChatSession) => void;
    }
  ) => {
    const { activeSession, updateSession } = options;
    if (!activeSession) return;
    updateSession(activeSession.id, (session) => ({
      ...session,
      pendingAttachments: session.pendingAttachments.filter((attachment) => attachment.fileId !== fileId),
    }));
  };

  return {
    attachmentUploading,
    uploadAttachments,
    handleRemoveDraftAttachment,
  };
};
