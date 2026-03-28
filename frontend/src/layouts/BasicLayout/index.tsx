import { ProLayout } from '@ant-design/pro-components';
import { history, Outlet } from 'umi';
import { TopActions } from '../components/TopActions';
import { useResponsive } from '@/hooks/useResponsive';

const BasicLayout = () => {
  const { isMobile } = useResponsive();

  return (
    <ProLayout
      title="SaaS Foundation"
      layout="mix"
      splitMenus
      fixSiderbar
      collapsedButtonRender={isMobile ? false : undefined}
      actionsRender={() => [<TopActions key="top-actions" />]}
      route={{
        path: '/',
        routes: [
          { path: '/dashboard/home', name: '首页' },
          { path: '/system/management', name: '系统管理' },
          { path: '/tenant/overview', name: '租户中心' },
          { path: '/iam/overview', name: '权限中心' },
          { path: '/audit/overview', name: '审计中心' },
        ],
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
      <Outlet />
    </ProLayout>
  );
};

export default BasicLayout;
