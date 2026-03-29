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
import './index.less';

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
      className="saas-basic-layout"
      title="宏翔商道"
      logo={<AppstoreOutlined style={{ fontSize: 16, color: '#1677ff' }} />}
      layout={isMobile ? 'top' : 'side'}
      navTheme="light"
      splitMenus={false}
      fixedHeader
      fixSiderbar={!isMobile}
      contentWidth="Fluid"
      siderWidth={216}
      collapsedButtonRender={isMobile ? false : undefined}
      rightContentRender={() => <TopActions />}
      menuHeaderRender={(logoDom, titleDom) => (
        <div
          className="saas-menu-header"
          onClick={() => {
            history.push('/dashboard/home');
          }}
        >
          {logoDom}
          {titleDom}
        </div>
      )}
      location={location}
      route={{
        path: '/',
        routes,
      }}
      token={{
        bgLayout: '#f5f7fa',
        sider: {
          colorMenuBackground: '#ffffff',
          colorTextMenu: '#2f3640',
          colorTextMenuActive: '#1677ff',
          colorTextMenuSelected: '#1677ff',
          colorBgMenuItemSelected: '#eaf2ff',
          colorBgMenuItemHover: '#f3f8ff',
          colorBgCollapsedButton: '#ffffff',
          colorTextCollapsedButton: '#5b6275',
          colorTextCollapsedButtonHover: '#1677ff',
          colorMenuItemDivider: '#f0f0f0',
        },
        header: {
          colorBgHeader: '#ffffff',
          colorTextMenu: '#5b6275',
          colorTextMenuSecondary: '#5b6275',
          colorTextMenuActive: '#1677ff',
          colorTextMenuSelected: '#1677ff',
          colorBgMenuItemHover: '#f5f9ff',
          colorBgMenuItemSelected: '#eaf2ff',
        },
      }}
      contentStyle={{
        margin: 0,
        padding: isMobile ? 12 : 16,
        minHeight: 0,
        overflow: 'auto',
      }}
      menuItemRender={(item, dom) => (
        <span
          onClick={() => {
            if (item.path) {
              history.push(item.path);
            }
          }}
        >
          {dom}
        </span>
      )}
    >
      <div className="saas-layout-content">
        <Outlet />
      </div>
    </ProLayout>
  );
};

export default BasicLayout;
