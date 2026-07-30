export const SESSION_EXPIRED_NOTICE_KEY = 'login-session-expired';
export const SESSION_EXPIRED_NOTICE_DURATION_SECONDS = 4;

type SessionExpiredMessageApi = {
  warning: (config: {
    key: string;
    content: string;
    duration: number;
  }) => unknown;
};

export const showSessionExpiredNotice = (
  messageApi: SessionExpiredMessageApi,
  content: string,
) => {
  messageApi.warning({
    key: SESSION_EXPIRED_NOTICE_KEY,
    content,
    duration: SESSION_EXPIRED_NOTICE_DURATION_SECONDS,
  });
};
