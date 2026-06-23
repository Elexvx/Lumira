import type { ReactNode } from 'react';
import type { ResultProps } from 'antd';
import { Card, Result } from 'antd';
import './Exception.css';

interface ExceptionResultProps {
  status: ResultProps['status'];
  title: string;
  subTitle: ReactNode;
  extra: ReactNode;
}

const ExceptionResult = ({ status, title, subTitle, extra }: ExceptionResultProps) => (
  <div className="saas-exception-page-shell">
    <Card className="saas-exception-card" bordered={false}>
      <Result
        className="saas-exception-result"
        status={status}
        title={title}
        subTitle={subTitle}
        extra={extra}
      />
    </Card>
  </div>
);

export default ExceptionResult;
