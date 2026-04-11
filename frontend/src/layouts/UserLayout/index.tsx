import type { PropsWithChildren } from 'react';
import { Outlet } from '@umijs/max';

const UserLayout = ({ children }: PropsWithChildren) => children ?? <Outlet />;

export default UserLayout;
