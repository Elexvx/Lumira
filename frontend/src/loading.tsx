import { Spin } from 'antd';

const Loading = () => {
  return (
    <div className="saas-app-loading">
      <Spin spinning size="large" />
    </div>
  );
};

export default Loading;
