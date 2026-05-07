import MarkdownPreview from '@uiw/react-markdown-preview';
import '@uiw/react-markdown-preview/markdown.css';
import { formatMessage } from '@umijs/max';
import { Modal } from 'antd';

interface AgreementPreviewModalProps {
  open: boolean;
  title: string;
  markdown?: string;
  onClose: () => void;
}

export const AgreementPreviewModal = ({ open, title, markdown, onClose }: AgreementPreviewModalProps) => (
  <Modal
    className="saas-login-page__agreement-modal"
    open={open}
    onCancel={onClose}
    footer={null}
    width={720}
    centered
    title={title}
    destroyOnHidden
  >
    {markdown ? (
      <MarkdownPreview source={markdown} />
    ) : (
      <div style={{ color: 'var(--saas-text-secondary)' }}>
        {formatMessage({ id: 'page.login.agreement.empty', defaultMessage: 'The backend has not configured this agreement yet.' })}
      </div>
    )}
  </Modal>
);
