import MDEditor from '@uiw/react-md-editor';
import { Empty } from 'antd';
import type { CSSProperties } from 'react';
import '@uiw/react-md-editor/markdown-editor.css';
import '@uiw/react-markdown-preview/markdown.css';

export interface MarkdownEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  height?: number;
  placeholder?: string;
  style?: CSSProperties;
}

export const MarkdownEditor = ({
  value = '',
  onChange,
  height = 360,
  placeholder,
  style,
}: MarkdownEditorProps) => (
  <div data-color-mode="light" style={style}>
    <MDEditor
      value={value}
      onChange={(next) => onChange?.(next || '')}
      preview="edit"
      height={height}
      textareaProps={{ placeholder }}
    />
  </div>
);

export interface MarkdownViewerProps {
  value?: string;
  className?: string;
  minHeight?: number;
}

export const MarkdownViewer = ({ value, className, minHeight = 180 }: MarkdownViewerProps) => {
  if (!value) {
    return <Empty description="暂无协议内容" />;
  }

  return (
    <div className={className} data-color-mode="light" style={{ minHeight }}>
      <MDEditor.Markdown source={value} />
    </div>
  );
};
