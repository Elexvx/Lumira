import { NotificationOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Col, Empty, Form, Input, List, Row, Space, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { systemService } from '@/services/system';
import type { NotificationRecord } from '@/types/api';

const formatCreatedAt = (value?: string) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
};

const NotificationsPage = () => {
  const [form] = Form.useForm<{ title: string; content: string }>();
  const { initialState } = useInitialStateModel();
  const actionPermission = useActionPermission();
  const canCreate = actionPermission.can('system:notification:write');
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [notifications, setNotifications] = useState<NotificationRecord[]>([]);
  const publishFormProps = useStandardFormProps({
    form,
    initialValues: { title: '', content: '' },
  });

  const tenantId = initialState?.currentTenant?.tenantId;

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const records = await systemService.notifications({ autoRedirectOnUnauthorized: false });
      setNotifications(records);
    } catch (error) {
      message.error('加载通知失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications, tenantId]);

  const handlePublish = async () => {
    if (!canCreate) {
      return;
    }
    setPublishing(true);
    try {
      const values = await form.validateFields();
      await systemService.createNotification(values, { autoRedirectOnUnauthorized: false });
      message.success('通知已发布');
      form.resetFields();
      await loadNotifications();
    } finally {
      setPublishing(false);
    }
  };

  const emptyState = useMemo(
    () => (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无已发布通知"
      />
    ),
    [],
  );

  return (
    <PageContainer
      title="通知中心"
      content="系统公告与站内通知都在这里统一管理，公告插件已收进系统内置能力。"
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="内置公告"
        description="这里直接复用公告数据表，不再需要单独安装公告插件。左侧可以发布新通知，右侧展示当前租户的已发布内容。"
      />
      <Row gutter={[16, 16]}>
        {canCreate ? (
          <Col xs={24} xl={8}>
            <Card title={<Space><PlusOutlined />发布通知</Space>}>
              <Form {...publishFormProps}>
                <Form.Item
                  name="title"
                  label="通知标题"
                  rules={[{ required: true, message: '请输入通知标题' }, { max: 128, message: '标题长度不能超过 128 个字符' }]}
                >
                  <Input placeholder="例如：系统维护公告" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="通知内容"
                  rules={[{ required: true, message: '请输入通知内容' }, { max: 2000, message: '内容长度不能超过 2000 个字符' }]}
                >
                  <Input.TextArea rows={8} placeholder="请输入要展示给租户用户的公告内容" />
                </Form.Item>
                <Button type="primary" block loading={publishing} onClick={() => void handlePublish()}>
                  发布通知
                </Button>
              </Form>
            </Card>
          </Col>
        ) : null}
        <Col xs={24} xl={canCreate ? 16 : 24}>
          <Card
            title={<Space><NotificationOutlined />已发布通知</Space>}
            extra={
              <Button icon={<ReloadOutlined />} onClick={() => void loadNotifications()} loading={loading}>
                刷新
              </Button>
            }
            loading={loading}
          >
            {notifications.length === 0 ? (
              emptyState
            ) : (
              <List
                itemLayout="vertical"
                dataSource={notifications}
                renderItem={(item) => (
                  <List.Item key={item.id} style={{ paddingInline: 0 }}>
                    <Space direction="vertical" size={8} style={{ width: '100%' }}>
                      <Space size={8} wrap>
                        <Typography.Title level={5} style={{ margin: 0 }}>
                          {item.title}
                        </Typography.Title>
                        <Typography.Text type="secondary">{formatCreatedAt(item.createdAt)}</Typography.Text>
                      </Space>
                      <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                        {item.content}
                      </Typography.Paragraph>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default NotificationsPage;
