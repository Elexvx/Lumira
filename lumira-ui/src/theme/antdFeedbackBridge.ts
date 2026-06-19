import { message as staticMessage, Modal as staticModal, notification as staticNotification } from 'antd';

type MessageApi = Pick<typeof staticMessage, 'destroy' | 'error' | 'info' | 'loading' | 'open' | 'success' | 'warning'>;
type NotificationApi = Pick<typeof staticNotification, 'destroy' | 'error' | 'info' | 'open' | 'success' | 'warning'>;
type ModalConfirmApi = Pick<typeof staticModal, 'confirm'>;

type AntdFeedbackRuntimeApi = {
  message: MessageApi;
  notification: NotificationApi;
  modal: ModalConfirmApi;
};

const fallbackApi: AntdFeedbackRuntimeApi = {
  message: staticMessage,
  notification: staticNotification,
  modal: {
    confirm: staticModal.confirm,
  },
};

let runtimeApi: AntdFeedbackRuntimeApi = fallbackApi;

const createProxy = <T extends object>(getApi: () => T): T =>
  new Proxy({},
    {
      get(_target, property) {
        const currentApi = getApi() as Record<PropertyKey, unknown>;
        const value = currentApi[property];

        if (typeof value === 'function') {
          return value.bind(currentApi);
        }

        return value;
      },
    },
  ) as T;

export const registerAntdFeedbackApi = (nextApi: {
  message?: MessageApi;
  notification?: NotificationApi;
  modal?: Partial<ModalConfirmApi>;
}) => {
  runtimeApi = {
    ...runtimeApi,
    message: nextApi.message ?? runtimeApi.message,
    notification: nextApi.notification ?? runtimeApi.notification,
    modal: {
      ...runtimeApi.modal,
      ...nextApi.modal,
    },
  };
};

export const message: MessageApi = createProxy<MessageApi>(() => runtimeApi.message);
export const notification: NotificationApi = createProxy<NotificationApi>(() => runtimeApi.notification);
export const modal: ModalConfirmApi = createProxy<ModalConfirmApi>(() => runtimeApi.modal);
