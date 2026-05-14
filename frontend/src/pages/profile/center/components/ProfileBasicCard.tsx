import { EditOutlined, EyeInvisibleOutlined, EyeOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Card, Col, DatePicker, Descriptions, Drawer, Empty, Form, Input, Row, Select, Space, Tooltip, Upload, type DescriptionsProps, type FormProps, type UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { useState } from 'react';
import { GENDER_OPTIONS } from '@/pages/profile/center/constants';
import type { CurrentUser } from '@/types/api';
import { trimString, validateOptionalChinaIdCard } from '@/utils/validators';

interface ProfileBasicCardProps {
  loading: boolean;
  hasVisibleProfileFields: boolean;
  profileSaving: boolean;
  profileFormProps: FormProps;
  visibleProfileFields: Set<string>;
  currentUser: CurrentUser | null | undefined;
  avatarValue?: string;
  avatarUploading: boolean;
  mobileLockedByVerification?: boolean;
  emailLockedByVerification?: boolean;
  editingOpen: boolean;
  onSave: () => void;
  onEditOpenChange: (open: boolean) => void;
  onAvatarBeforeCrop: (file: File) => boolean;
  onAvatarUploadRequest: UploadProps['customRequest'];
}

const maskMobile = (mobile?: string | null) => {
  if (!mobile) {
    return '-';
  }

  return mobile.length >= 7 ? `${mobile.slice(0, 3)}****${mobile.slice(-4)}` : mobile;
};

const maskEmail = (email?: string | null) => {
  if (!email) {
    return '-';
  }

  const [localPart, domainPart] = email.split('@');
  if (!domainPart) {
    return email;
  }

  if (localPart.length <= 2) {
    return `**@${domainPart}`;
  }

  return `${localPart.slice(0, 2)}***@${domainPart}`;
};

export const ProfileBasicCard = ({
  loading,
  hasVisibleProfileFields,
  profileSaving,
  profileFormProps,
  visibleProfileFields,
  currentUser,
  avatarValue,
  avatarUploading,
  mobileLockedByVerification = false,
  emailLockedByVerification = false,
  editingOpen,
  onSave,
  onEditOpenChange,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
}: ProfileBasicCardProps) => {
  const [showSensitiveInfo, setShowSensitiveInfo] = useState(false);
  const showSensitiveToggle = mobileLockedByVerification || emailLockedByVerification;
  const displayMobile = showSensitiveInfo ? currentUser?.mobile || '-' : maskMobile(currentUser?.mobile);
  const displayEmail = showSensitiveInfo ? currentUser?.email || '-' : maskEmail(currentUser?.email);
  const displayGender = GENDER_OPTIONS.find((item) => item.value === currentUser?.gender)?.label || '-';
  const visibleField = (fieldKey: string) => visibleProfileFields.has(fieldKey);

  const profileItems: DescriptionsProps['items'] = [
    { key: 'username', label: '用户名', children: currentUser?.username || '-' },
  ];

  if (visibleField('nickname')) {
    profileItems.push({ key: 'nickname', label: '昵称', children: currentUser?.nickname || '-' });
  }
  if (visibleField('realName')) {
    profileItems.push({ key: 'realName', label: '姓名', children: currentUser?.realName || '-' });
  }
  if (visibleField('mobile')) {
    profileItems.push({ key: 'mobile', label: '手机号', children: displayMobile });
  }
  if (visibleField('email')) {
    profileItems.push({ key: 'email', label: '邮箱', children: displayEmail });
  }
  if (visibleField('birthMonth')) {
    profileItems.push({ key: 'birthMonth', label: '出生年月', children: currentUser?.birthMonth || '-' });
  }
  if (visibleField('gender')) {
    profileItems.push({ key: 'gender', label: '性别', children: displayGender });
  }
  if (visibleField('region')) {
    profileItems.push({ key: 'region', label: '所在地区', children: currentUser?.region || '-' });
  }
  if (visibleField('idCardNumber')) {
    profileItems.push({ key: 'idCardNumber', label: '身份证号码', children: currentUser?.idCardNumber || '-' });
  }
  if (visibleField('availableTime')) {
    profileItems.push({ key: 'availableTime', label: '可工作时间', children: currentUser?.availableTime || '-' });
  }

  return (
    <>
      <Card
        title="个人信息"
        loading={loading}
        className="saas-profile-page__personal-card"
        style={{ width: '100%' }}
        extra={(
          <Space size={4}>
            {showSensitiveToggle ? (
              <Tooltip title={showSensitiveInfo ? '隐藏敏感信息' : '显示敏感信息'}>
                <Button
                  type="text"
                  shape="circle"
                  aria-label={showSensitiveInfo ? '隐藏敏感信息' : '显示敏感信息'}
                  icon={showSensitiveInfo ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                  onClick={() => setShowSensitiveInfo((current) => !current)}
                />
              </Tooltip>
            ) : null}
            <Tooltip title="编辑资料">
              <Button
                type="text"
                shape="circle"
                aria-label="编辑资料"
                icon={<EditOutlined />}
                disabled={!hasVisibleProfileFields}
                onClick={() => onEditOpenChange(true)}
              />
            </Tooltip>
          </Space>
        )}
      >
        {hasVisibleProfileFields ? (
          <Descriptions className="saas-profile-page__descriptions" colon={false} column={{ xs: 1, sm: 2, lg: 4 }} items={profileItems} />
        ) : (
          <Empty description="当前未开启任何可编辑资料字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>

      <Drawer
        title="编辑个人资料"
        open={editingOpen}
        width={560}
        destroyOnClose={false}
        onClose={() => onEditOpenChange(false)}
        extra={(
          <Space>
            <Button onClick={() => onEditOpenChange(false)}>取消</Button>
            <Button type="primary" loading={profileSaving} onClick={onSave}>
              保存资料
            </Button>
          </Space>
        )}
      >
        <Form {...profileFormProps} layout="vertical">
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
            <Col xs={24}>
              <Form.Item label="用户名">
                <Input value={currentUser?.username || '-'} disabled />
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
                  <Form.Item label="手机号">
                    <Input value={displayMobile} disabled />
                  </Form.Item>
                </Col>
              ) : null}
              {visibleProfileFields.has('email') ? (
                <Col xs={24} md={12}>
                  <Form.Item label="邮箱">
                    <Input value={displayEmail} disabled autoComplete="off" />
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
            <Empty description="当前未开启任何可编辑资料字段" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Form>
      </Drawer>
    </>
  );
};
