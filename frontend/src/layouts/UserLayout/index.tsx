import type { PropsWithChildren } from 'react';
import { Outlet } from 'umi';
import './index.less';

const UserLayout = ({ children }: PropsWithChildren) => (
  <div className="saas-user-layout">
    <div className="saas-user-layout__background" />
    {children ?? <Outlet />}
  </div>
);

export default UserLayout;
