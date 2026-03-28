import { Outlet } from 'umi';

const UserLayout = () => {
  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#f0f2f5' }}>
      <div style={{ width: 420, padding: 32, background: '#fff', borderRadius: 8 }}>
        <Outlet />
      </div>
    </div>
  );
};

export default UserLayout;
