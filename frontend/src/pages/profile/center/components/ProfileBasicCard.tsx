import { UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Card, Col, DatePicker, Empty, Form, Input, Row, Select, Space, Tooltip, Upload, type FormProps, type UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import type { CurrentUser } from '@/types/api';
import { GENDER_OPTIONS } from '@/pages/profile/center/constants';
import { trimString, validateOptionalChinaIdCard, validateOptionalChinaMobile } from '@/utils/validators';

interface ProfileBasicCardProps {
  loading: boolean;
  hasVisibleProfileFields: boolean;
  profileSaving: boolean;
  profileFormProps: FormProps;
  visibleProfileFields: Set<string>;
  currentUser: CurrentUser | null | undefined;
  avatarValue?: string;
  avatarUploading: boolean;
  onSave: () => void;
  onAvatarBeforeCrop: (file: File) => boolean;
  onAvatarUploadRequest: UploadProps['customRequest'];
}

export const ProfileBasicCard = ({
  loading,
  hasVisibleProfileFields,
  profileSaving,
  profileFormProps,
  visibleProfileFields,
  currentUser,
  avatarValue,
  avatarUploading,
  onSave,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
}: ProfileBasicCardProps) => (
  <Card
    title="基础资料"
    loading={loading}
    style={{ width: '100%' }}
  >
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Form {...profileFormProps}>
        <Form.Item name="avatarUrl" hidden>
          <Input />
        </Form.Item>

        {visibleProfileFields.has('avatarUrl') ? (
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <Space direction="vertical" align="center" size={12}>
              <ImgCrop rotationSlider aspect={1} modalTitle="裁切头像" beforeCrop={onAvatarBeforeCrop}>
                <Upload accept="image/*" showUploadList={false} customRequest={onAvatarUploadRequest} disabled={avatarUploading}>
                  <Tooltip title="点击头像修改" placement="top">
                    <Avatar size={96} src={avatarValue || currentUser?.avatarUrl || undefined} icon={<UserOutlined />} />
                  </Tooltip>
                </Upload>
              </ImgCrop>
            </Space>
          </div>
        ) : null}

        <Row gutter={[16, 0]}>
          <Col xs={24} md={12}>
            <Form.Item label="用户名">
              <Input value={currentUser?.username || '-'} disabled />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item label="用户ID">
              <Input value={currentUser?.userId ? String(currentUser.userId) : '-'} disabled />
            </Form.Item>
          </Col>
        </Row>

        {hasVisibleProfileFields ? (
          <Row gutter={[16, 0]}>
            <Col xs={24} md={12}>
              <Form.Item name="nickname" label="昵称">
                <Input placeholder="请输入昵称" />
              </Form.Item>
            </Col>
            {visibleProfileFields.has('realName') ? (
              <Col xs={24} md={12}>
                <Form.Item name="realName" label="姓名">
                  <Input placeholder="请输入姓名" />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('mobile') ? (
              <Col xs={24} md={12}>
                <Form.Item name="mobile" label="手机号" rules={[{ validator: validateOptionalChinaMobile }]} normalize={trimString}>
                  <Input placeholder="请输入手机号" />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('email') ? (
              <Col xs={24} md={12}>
                <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入有效邮箱地址' }]}>
                  <Input placeholder="请输入邮箱地址" autoComplete="email" />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('birthMonth') ? (
              <Col xs={24} md={12}>
                <Form.Item name="birthMonth" label="出生年月">
                  <DatePicker picker="month" placeholder="请选择出生年月" format="YYYY年MM月" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('gender') ? (
              <Col xs={24} md={12}>
                <Form.Item name="gender" label="性别">
                  <Select allowClear placeholder="请选择性别" options={GENDER_OPTIONS} />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('region') ? (
              <Col xs={24} md={12}>
                <Form.Item name="region" label="所在地区">
                  <Input placeholder="请输入所在地区" />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('idCardNumber') ? (
              <Col xs={24} md={12}>
                <Form.Item name="idCardNumber" label="身份证号码" rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
                  <Input placeholder="请输入身份证号码" />
                </Form.Item>
              </Col>
            ) : null}
            {visibleProfileFields.has('availableTime') ? (
              <Col xs={24} md={24}>
                <Form.Item name="availableTime" label="可工作时间">
                  <Input.TextArea
                    rows={2}
                    placeholder="请输入可工作时间，如：周一至周五 09:00-18:00"
                    style={{ width: '100%', display: 'block' }}
                  />
                </Form.Item>
              </Col>
            ) : null}
          </Row>
        ) : (
          <Empty description="当前租户未开启任何可编辑资料字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Form>

      {hasVisibleProfileFields ? (
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" loading={profileSaving} onClick={onSave}>
            保存资料
          </Button>
        </div>
      ) : null}
    </Space>
  </Card>
);
