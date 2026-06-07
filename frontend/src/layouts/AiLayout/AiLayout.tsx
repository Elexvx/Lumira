import { Navigate, Outlet, useAccess, useLocation } from '@umijs/max';

const accessValue = (access: unknown, accessKey: string) =>
  Boolean((access as Record<string, unknown>)[accessKey]);

export const resolveAiLandingPath = (access: unknown) => {
  if (accessValue(access, 'canVisitAiAssistant')) {
    return '/ai/assistant';
  }

  if (accessValue(access, 'canVisitAiKnowledge')) {
    return '/ai/knowledge';
  }

  return '/403';
};

const AiLayout = () => {
  const location = useLocation();
  const access = useAccess();

  if (location.pathname === '/ai') {
    return <Navigate to={resolveAiLandingPath(access)} replace />;
  }

  return <Outlet />;
};

export default AiLayout;
