import { EditOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Card, Col, DatePicker, Descriptions, Drawer, Empty, Form, Input, Row, Select, Space, Tooltip, Upload, type DescriptionsProps, type FormProps, type UploadProps } from 'antd';
import ImgCrop from 'antd-img-crop';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
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
  editingOpen: boolean;
  onSave: () => void;
  onEditOpenChange: (open: boolean) => void;
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
  editingOpen,
  onSave,
  onEditOpenChange,
  onAvatarBeforeCrop,
  onAvatarUploadRequest,
}: ProfileBasicCardProps) => {
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
  return (
    <>
      <Card
        title="个人信息"
        loading={loading}
        className="saas-profile-page__personal-card"
        style={{ width: '100%' }}
        extra={(
          <Space size={4}>
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
        width={STANDARD_DRAWER_WIDTH}
        destroyOnClose={false}
        onClose={() => onEditOpenChange(false)}
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={() => onEditOpenChange(false)}>取消</Button>
              <Button type="primary" loading={profileSaving} onClick={onSave}>
                保存资料
              </Button>
            </Space>
          </div>
        }
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
              <Col xs={24}>
                <Form.Item name="nickname" label="昵称">
                  <Input placeholder="请输入昵称" />
                </Form.Item>
              </Col>
              {visibleProfileFields.has('realName') ? (
                <Col xs={24}>
                  <Form.Item name="realName" label="姓名">
                    <Input placeholder="请输入姓名" />
                  </Form.Item>
                </Col>
              ) : null}
              {visibleProfileFields.has('birthMonth') ? (
                <Col xs={24}>
                  <Form.Item name="birthMonth" label="出生年月">
                    <DatePicker picker="month" placeholder="请选择出生年月" format="YYYY年MM月" style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              ) : null}
              {visibleProfileFields.has('gender') ? (
                <Col xs={24}>
                  <Form.Item name="gender" label="性别">
                    <Select allowClear placeholder="请选择性别" options={GENDER_OPTIONS} />
                  </Form.Item>
                </Col>
              ) : null}
              {visibleProfileFields.has('region') ? (
                <Col xs={24}>
                  <Form.Item name="region" label="所在地区">
                    <Input placeholder="请输入所在地区" />
                  </Form.Item>
                </Col>
              ) : null}
              {visibleProfileFields.has('idCardNumber') ? (
                <Col xs={24}>
                  <Form.Item name="idCardNumber" label="身份证号码" rules={[{ validator: validateOptionalChinaIdCard }]} normalize={trimString}>
                    <Input placeholder="请输入身份证号码" />
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
