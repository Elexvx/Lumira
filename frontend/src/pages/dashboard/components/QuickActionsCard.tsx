import { PlusOutlined } from '@ant-design/icons';
import { Button, Col, Row, Typography } from 'antd';
import { ProCard } from '@ant-design/pro-components';

interface QuickActionsCardProps {
  placeholders: string[];
}

const QuickActionsCard = ({ placeholders }: QuickActionsCardProps) => {
  return (
    <ProCard
      className="saas-workbench-card"
      bordered={false}
      boxShadow
      title="快捷开始 / 便捷导航"
      extra={
        <Button type="dashed" icon={<PlusOutlined />} className="saas-workbench-quick-card__add">
          添加
        </Button>
      }
    >
      <Typography.Paragraph type="secondary" className="saas-workbench-quick-card__description">
        这里预留给后续的常用入口、收藏项和高频操作。
      </Typography.Paragraph>
      <div className="saas-workbench-quick-card__grid">
        <Row gutter={[12, 12]}>
          {placeholders.map((placeholder) => (
            <Col key={placeholder} xs={12} sm={6} xl={12}>
              <div className="saas-workbench-quick-card__placeholder">
                <Typography.Text type="secondary">{placeholder}</Typography.Text>
              </div>
            </Col>
          ))}
        </Row>
      </div>
    </ProCard>
  );
};

export default QuickActionsCard;
