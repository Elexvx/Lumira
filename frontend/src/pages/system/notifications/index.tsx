import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Col, Form, Input, Row, Space, message } from 'antd';
import { useState } from 'react';
import { MessageCenterContent } from '@/components/message-center/MessageCenterContent';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { messageService } from '@/services/message';

const NotificationsPage = () => {
  const [form] = Form.useForm<{ title: string; content: string }>();
  const actionPermission = useActionPermission();
  const canCreate =
    actionPermission.can('message:message:write') ||
    actionPermission.can('message:announcement:write') ||
    actionPermission.can('system:notification:write');
  const [publishing, setPublishing] = useState(false);
  const publishFormProps = useStandardFormProps({
    form,
    initialValues: { title: '', content: '' },
  });

  const handlePublish = async () => {
    if (!canCreate) {
      return;
    }
    setPublishing(true);
    try {
      const values = await form.validateFields();
      await messageService.createMessage(
        {
          title: values.title,
          content: values.content,
          targetScope: 'TENANT',
        },
        { autoRedirectOnUnauthorized: false },
      );
      message.success('站内信已发布');
      form.resetFields();
    } finally {
      setPublishing(false);
    }
  };

  return (
    <PageContainer
      title="消息中心"
      content="这里用于调试站内信发布与消息联动，顶部铃铛会同步显示同一套消息中心。"
    >
      <Row gutter={[16, 16]}>
        {canCreate ? (
          <Col xs={24} xl={8}>
            <Card title={<Space>发布站内信</Space>}>
              <Form {...publishFormProps}>
                <Form.Item
                  name="title"
                  label="站内信标题"
                  rules={[{ required: true, message: '请输入站内信标题' }, { max: 128, message: '标题长度不能超过 128 个字符' }]}
                >
                  <Input placeholder="例如：系统维护提醒" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="站内信内容"
                  rules={[{ required: true, message: '请输入站内信内容' }, { max: 2000, message: '内容长度不能超过 2000 个字符' }]}
                >
                  <Input.TextArea rows={8} placeholder="请输入要发送给租户用户的站内信内容" />
                </Form.Item>
                <Button type="primary" block loading={publishing} onClick={() => void handlePublish()}>
                  发布站内信
                </Button>
              </Form>
            </Card>
          </Col>
        ) : null}
        <Col xs={24} xl={canCreate ? 16 : 24}>
          <MessageCenterContent className="saas-message-center-page__content" />
        </Col>
      </Row>
    </PageContainer>
  );
};

export default NotificationsPage;
