export type WechatOAuthCallback = {
  code: string;
  state: string;
};

type ConsumeWechatOAuthCallbackOptions = {
  locationSearch?: string;
  locationPathname: string;
  loginAvailable: boolean;
  replaceLocation: (path: string) => void;
};

/**
 * Reads and immediately removes a WeChat OAuth callback from the browser URL.
 * WeChat authorization codes are single-use, so the URL must be consumed
 * before any asynchronous login work can remount the login route.
 */
export const consumeWechatOAuthCallback = ({
  locationSearch,
  locationPathname,
  loginAvailable,
  replaceLocation,
}: ConsumeWechatOAuthCallbackOptions): WechatOAuthCallback | null => {
  if (!loginAvailable) {
    return null;
  }

  const searchParams = new URLSearchParams(locationSearch || '');
  const code = searchParams.get('code')?.trim();
  const state = searchParams.get('state')?.trim();
  if (!code || !state) {
    return null;
  }

  replaceLocation(locationPathname);
  return { code, state };
};
