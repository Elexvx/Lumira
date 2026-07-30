import { DownloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Empty, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { listMyCertificates } from '@/services/certificates/api';
import type { CertificateRecord } from '@/services/certificates/types';

const statusText: Record<string, string> = {
  ISSUED: '已签发',
  REVOKED: '已撤销',
};

export default function MyCertificatesPage() {
  const [records, setRecords] = useState<CertificateRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void listMyCertificates()
      .then(setRecords)
      .finally(() => setLoading(false));
  }, []);

  return (
    <ManagementPage title="我的证书">
      <ManagementPageBody>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert type="info" showIcon message="这里展示与你本人或所在参赛团队关联的获奖证书。" />
          <Card>
            <Table<CertificateRecord>
              rowKey="id"
              loading={loading}
              dataSource={records}
              locale={{ emptyText: <Empty description="暂无已签发证书" /> }}
              scroll={{ x: 880 }}
              columns={[
                {
                  title: '证书',
                  dataIndex: 'certificateNo',
                  width: 190,
                  render: (value) => (
                    <Space>
                      <SafetyCertificateOutlined />
                      <Typography.Text strong>{value}</Typography.Text>
                    </Space>
                  ),
                },
                { title: '赛事', dataIndex: 'competitionTitle', ellipsis: true },
                { title: '项目', dataIndex: 'projectName', ellipsis: true },
                { title: '奖项', dataIndex: 'awardName', width: 140 },
                { title: '签发日期', dataIndex: 'issueDate', width: 120 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (value) => <Tag color={value === 'ISSUED' ? 'green' : 'red'}>{statusText[value] || value}</Tag>,
                },
                {
                  title: '操作',
                  width: 110,
                  render: (_, record) => (
                    <Button
                      icon={<DownloadOutlined />}
                      disabled={record.status !== 'ISSUED'}
                      href={`/api/v2/aiadc/certificates/mine/${record.id}/download`}
                    >
                      下载
                    </Button>
                  ),
                },
              ]}
            />
          </Card>
        </Space>
      </ManagementPageBody>
    </ManagementPage>
  );
}
