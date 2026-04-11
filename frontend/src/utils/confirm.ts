import { Modal, type ModalFuncProps } from 'antd';
import type { ReactNode } from 'react';

type ConfirmActionOptions = {
  title: ReactNode;
  content: ReactNode;
  okText?: ReactNode;
  cancelText?: ReactNode;
  okButtonProps?: ModalFuncProps['okButtonProps'];
  centered?: boolean;
  onOk: () => Promise<void> | void;
};

export const confirmAction = ({
  title,
  content,
  okText = '确认',
  cancelText = '取消',
  okButtonProps,
  centered = true,
  onOk,
}: ConfirmActionOptions) =>
  Modal.confirm({
    title,
    content,
    okText,
    cancelText,
    centered,
    okButtonProps,
    onOk: async () => {
      await onOk();
    },
  });
