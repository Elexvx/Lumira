import type { PropsWithChildren } from 'react';
import { Outlet } from 'umi';
import './index.less';

const UserLayout = ({ children }: PropsWithChildren) => {
  return (
    <div className="saas-user-layout">
      <div className="saas-user-layout__background" />
      <div className="saas-user-layout__grid" />
      <div className="saas-user-layout__orb saas-user-layout__orb--primary" />
      <div className="saas-user-layout__orb saas-user-layout__orb--secondary" />
      <div className="saas-user-layout__content">
        <div className="saas-user-layout__inner">{children ?? <Outlet />}</div>
      </div>
    </div>
  );
};

export default UserLayout;
