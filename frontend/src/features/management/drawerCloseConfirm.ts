import type { DrawerProps } from 'antd';
import { useCallback } from 'react';
import { confirmAction } from '@/utils/confirm';
import { resolveBuiltinMessage } from '@/i18n/messages';

const isDrawerMaskCloseEvent = (event: Parameters<NonNullable<DrawerProps['onClose']>>[0]) => {
  const target = event?.target;
  return target instanceof Element && target.classList.contains('ant-drawer-mask');
};

export const useConfirmableDrawerClose = (onClose?: DrawerProps['onClose']) =>
  useCallback<NonNullable<DrawerProps['onClose']>>(
    (event) => {
      if (!isDrawerMaskCloseEvent(event)) {
        onClose?.(event);
        return;
      }

      confirmAction({
        title: resolveBuiltinMessage('common.drawerCloseConfirm.title', '确认关闭抽屉？'),
        content: resolveBuiltinMessage('common.drawerCloseConfirm.content', '当前抽屉将关闭，未保存的内容可能丢失。'),
        okText: resolveBuiltinMessage('common.drawerCloseConfirm.ok', '关闭'),
        cancelText: resolveBuiltinMessage('common.drawerCloseConfirm.cancel', '继续编辑'),
        okButtonProps: { danger: true },
        onOk: () => {
          onClose?.(event);
        },
      });
    },
    [onClose],
  );
