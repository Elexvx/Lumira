import { Button, Card, Descriptions, Form, Input, Space, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from '@umijs/max';
import { verifyCertificateByNo, verifyCertificateByToken } from '@/services/certificates/api';
import type { CertificatePublicVerifyResult } from '@/services/certificates/types';
import './certificate.css';

const PublicVerifyPage = () => {
  const params = useParams<{ publicToken?: string }>();
  const token = params.publicToken;
  const [form] = Form.useForm();
  const [result, setResult] = useState<CertificatePublicVerifyResult | null>(null);
  const resultText = useMemo(() => ({
    VALID: '有效证书',
    REVOKED: '证书已撤销',
    EXPIRED: '证书已过期',
    NOT_FOUND: '未查询到证书',
    INVALID_CODE: '校验码错误',
  }[result?.result || 'NOT_FOUND']), [result]);

  useEffect(() => {
    if (token) {
      void verifyCertificateByToken(token).then(setResult);
    }
  }, [token]);

  const submit = async () => {
    const values = await form.validateFields();
    setResult(await verifyCertificateByNo(values.certificateNo, values.verificationCode));
  };

  return (
    <div className="certificate-public">
      <Card className="certificate-public__card">
        <Space direction="vertical" size="large" className="certificate-public__content">
          <div>
            <Typography.Title level={2}>证书真伪查验</Typography.Title>
            <Typography.Text type="secondary">请输入证书编号和校验码，或通过证书二维码直接访问。</Typography.Text>
          </div>
          <Form form={form} layout="vertical">
            <Form.Item name="certificateNo" label="证书编号" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="verificationCode" label="校验码" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Button type="primary" block onClick={submit}>查询证书</Button>
          </Form>
          {result ? (
            <Card className={`certificate-public__result is-${result.result.toLowerCase()}`}>
              <Typography.Title level={4}>{resultText}</Typography.Title>
              {result.certificateNo ? (
                <Descriptions
                  column={1}
                  items={[
                    { key: 'no', label: '证书编号', children: result.certificateNo },
                    { key: 'name', label: '获奖人/团队', children: result.recipientName },
                    { key: 'competition', label: '赛事', children: result.competitionTitle },
                    { key: 'project', label: '项目', children: result.projectName },
                    { key: 'award', label: '奖项', children: result.awardName },
                    { key: 'date', label: '发证日期', children: result.issueDate },
                  ]}
                />
              ) : null}
            </Card>
          ) : null}
        </Space>
      </Card>
    </div>
  );
};

export default PublicVerifyPage;
