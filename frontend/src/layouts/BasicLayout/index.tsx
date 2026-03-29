import { ProLayout } from '@ant-design/pro-components';
import { history, Outlet } from 'umi';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { TopActions } from '../components/TopActions';
import { useResponsive } from '@/hooks/useResponsive';

const BasicLayout = () => {
  const { isMobile } = useResponsive();
  const { initialState } = useInitialStateModel();
  const routes = initialState?.menuTree ?? [];

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
        routes,
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
