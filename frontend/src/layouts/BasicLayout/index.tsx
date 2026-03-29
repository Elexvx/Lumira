import { ProLayout } from '@ant-design/pro-components';
import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  DatabaseOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useMemo, type ReactNode } from 'react';
import { history, Outlet, useLocation } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import type { MenuNode } from '@/types/api';
import { TopActions } from '../components/TopActions';
import { useResponsive } from '@/hooks/useResponsive';

type LayoutMenuNode = Omit<MenuNode, 'icon' | 'children'> & {
  icon?: ReactNode;
  children?: LayoutMenuNode[];
};

const iconMap: Record<string, ReactNode> = {
  ApartmentOutlined: <ApartmentOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  AuditOutlined: <AuditOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  SettingOutlined: <SettingOutlined />,
  TeamOutlined: <TeamOutlined />,
  UserOutlined: <UserOutlined />,
};

const mapMenuNodes = (nodes: MenuNode[]): LayoutMenuNode[] =>
  nodes.map((node) => ({
    ...node,
    icon: node.icon ? iconMap[node.icon] || <AppstoreOutlined /> : undefined,
    children: node.children?.length ? mapMenuNodes(node.children) : undefined,
  }));

const BasicLayout = () => {
  const { isMobile } = useResponsive();
  const { initialState } = useInitialStateModel();
  const location = useLocation();
  const routes = useMemo(() => mapMenuNodes(initialState?.menuTree ?? []), [initialState?.menuTree]);

  return (
    <ProLayout
      title="宏翔商道"
      logo={<AppstoreOutlined style={{ color: '#fff', fontSize: 18 }} />}
      layout={isMobile ? 'top' : 'mix'}
      navTheme="realDark"
      splitMenus={!isMobile}
      fixedHeader={!isMobile}
      fixSiderbar={!isMobile}
      contentWidth="Fluid"
      collapsed={isMobile ? true : undefined}
      collapsedButtonRender={isMobile ? false : undefined}
      rightContentRender={() => <TopActions />}
      location={location}
      route={{
        path: '/',
        routes,
      }}
      contentStyle={{
        margin: 0,
        padding: isMobile ? 12 : 16,
        minHeight: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
      }}
      menuItemRender={(item, dom) => (
        <a
          onClick={() => {
            if (item.path) {
              history.push(item.path);
            }
          }}
        >
          {dom}
        </a>
      )}
    >
      <div style={{ minHeight: 0, overflow: 'visible' }}>
        <Outlet />
      </div>
    </ProLayout>
  );
};

export default BasicLayout;
