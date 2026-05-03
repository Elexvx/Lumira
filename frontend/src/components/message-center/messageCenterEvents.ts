export const MESSAGE_CENTER_REFRESH_EVENT = 'saas-message-center:refresh';

export const notifyMessageCenterRefresh = () => {
  window.dispatchEvent(new Event(MESSAGE_CENTER_REFRESH_EVENT));
};

