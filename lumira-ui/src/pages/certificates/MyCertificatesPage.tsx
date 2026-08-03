import { DownloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { downloadMyCertificate, listMyCertificates } from '@/services/certificates/api';
import type { CertificateRecord } from '@/services/certificates/types';
import { saveBlobAsFile } from '@/utils/download';
import { showErrorMessage } from '@/utils/errorMessage';

const statusText: Record<string, string> = {
  ISSUED: '已签发',
  REVOKED: '已撤销',
};

const handleCertificateDownload = async (record: CertificateRecord) => {
  try {
    const blob = await downloadMyCertificate(record.id);
    saveBlobAsFile(blob, `${record.certificateNo}.png`);
  } catch (error) {
    showErrorMessage(error, '证书下载失败');
  }
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
                      onClick={() => void handleCertificateDownload(record)}
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
