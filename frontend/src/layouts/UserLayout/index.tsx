import type { PropsWithChildren } from 'react';
import { Outlet } from 'umi';

const UserLayout = ({ children }: PropsWithChildren) => children ?? <Outlet />;

export default UserLayout;
