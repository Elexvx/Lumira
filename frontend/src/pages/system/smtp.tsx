import { history, useLocation } from '@umijs/max';
import { useEffect } from 'react';

const SmtpRedirectPage = () => {
  const location = useLocation();

  useEffect(() => {
    history.replace({
      pathname: '/system/verification',
      search: `?${new URLSearchParams({ tab: 'email' }).toString()}`,
    });
  }, [location.pathname]);

  return null;
};

export default SmtpRedirectPage;
