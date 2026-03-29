import { Outlet } from 'umi';

const UserLayout = () => {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#ffffff',
        padding: '24px 16px',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ width: '100%', maxWidth: 320 }}>
        <Outlet />
      </div>
    </div>
  );
};

export default UserLayout;
