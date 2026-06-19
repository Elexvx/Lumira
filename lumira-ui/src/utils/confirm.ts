import type { ModalFuncProps } from 'antd';
import { modal } from '@/theme/antdFeedbackBridge';
import { resolveBuiltinMessage } from '@/i18n/messages';
import type { ReactNode } from 'react';

type ConfirmActionOptions = {
  title: ReactNode;
  content: ReactNode;
  okText?: ReactNode;
  cancelText?: ReactNode;
  okButtonProps?: ModalFuncProps['okButtonProps'];
  centered?: boolean;
  onOk: () => Promise<void> | void;
  onCancel?: () => Promise<void> | void;
};

export const confirmAction = ({
  title,
  content,
  okText = resolveBuiltinMessage('common.confirm', '确认'),
  cancelText = resolveBuiltinMessage('common.cancel', '取消'),
  okButtonProps,
  centered = true,
  onOk,
  onCancel,
}: ConfirmActionOptions) =>
  modal.confirm({
    title,
    content,
    okText,
    cancelText,
    centered,
    okButtonProps,
    onOk: async () => {
      await onOk();
    },
    onCancel: async () => {
      await onCancel?.();
    },
  });
