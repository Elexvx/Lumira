import { useLocation } from '@umijs/max';
import CompetitionPage from './CompetitionPage';
import CompetitionRegistrationPage from './CompetitionRegistrationPage';
import CompetitionSettingsPage from './CompetitionSettingsPage';

const CompetitionRouteEntry = () => {
  const location = useLocation();
  if (location.pathname === '/competitions/register') {
    return <CompetitionRegistrationPage />;
  }
  if (/^\/competitions\/[^/]+\/settings$/.test(location.pathname)) {
    return <CompetitionSettingsPage />;
  }
  return <CompetitionPage />;
};

export default CompetitionRouteEntry;
