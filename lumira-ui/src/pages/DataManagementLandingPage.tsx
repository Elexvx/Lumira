import { Navigate, useAccess } from '@umijs/max';
import { resolveDataManagementLandingPath } from '@/navigation/routeLanding';

const DataManagementLandingPage = () => {
  const access = useAccess();
  return <Navigate to={resolveDataManagementLandingPath(access)} replace />;
};

export default DataManagementLandingPage;
