import type { ReactNode, RefObject } from 'react';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { useRef, useState } from 'react';
import { Button, Divider, Input, Tooltip } from 'antd';
import {
  BoldOutlined,
  CheckSquareOutlined,
  CodeOutlined,
  CommentOutlined,
  FontSizeOutlined,
  ItalicOutlined,
  LinkOutlined,
  OrderedListOutlined,
  PictureOutlined,
  ProfileOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import '@ant-design/x-markdown/es/XMarkdown/index.css';
import { XMarkdown } from '@ant-design/x-markdown';
import './AgreementMarkdownEditor.css';

type AgreementMarkdownEditorMode = 'edit' | 'preview';

export type AgreementMarkdownEditorProps = {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
};

type InsertMode = 'wrap' | 'line' | 'block';

type AgreementMarkdownToolbarAction = {
  key: string;
  title: string;
  icon: ReactNode;
  before: string;
  after?: string;
  sample: string;
  mode?: InsertMode;
};

const AGREEMENT_MARKDOWN_TOOLBAR_ACTIONS: readonly AgreementMarkdownToolbarAction[] = [
  { key: 'quote', title: '引用', icon: <CommentOutlined />, before: '> ', sample: '引用内容', mode: 'line' },
  { key: 'bold', title: '加粗', icon: <BoldOutlined />, before: '**', after: '**', sample: '加粗文字' },
  { key: 'italic', title: '斜体', icon: <ItalicOutlined />, before: '*', after: '*', sample: '斜体文字' },
  { key: 'unordered-list', title: '无序列表', icon: <UnorderedListOutlined />, before: '- ', sample: '列表项', mode: 'line' },
  { key: 'ordered-list', title: '有序列表', icon: <OrderedListOutlined />, before: '1. ', sample: '列表项', mode: 'line' },
  { key: 'task-list', title: '任务列表', icon: <CheckSquareOutlined />, before: '- [ ] ', sample: '待办项', mode: 'line' },
  { key: 'link', title: '链接', icon: <LinkOutlined />, before: '[', after: '](https://example.com)', sample: '链接文字' },
  { key: 'image', title: '图片', icon: <PictureOutlined />, before: '![', after: '](https://example.com/image.png)', sample: '图片描述' },
  { key: 'code', title: '代码', icon: <CodeOutlined />, before: '`', after: '`', sample: 'code' },
  {
    key: 'table',
    title: '表格',
    icon: <TableOutlined />,
    before: '| 标题 | 内容 |\n| --- | --- |\n| 示例 | 文本 |',
    sample: '',
    mode: 'block',
  },
] as const;

const AGREEMENT_MARKDOWN_TOOLBAR_LEFT_ACTIONS = AGREEMENT_MARKDOWN_TOOLBAR_ACTIONS.slice(0, 6);
const AGREEMENT_MARKDOWN_TOOLBAR_RIGHT_ACTIONS = AGREEMENT_MARKDOWN_TOOLBAR_ACTIONS.slice(6);
const AGREEMENT_MARKDOWN_TOOLBAR_HEADINGS = {
  heading: <FontSizeOutlined />,
  paragraph: <ProfileOutlined />,
} as const;

const normalizeAgreementMarkdownValue = (value?: string) => value ?? '';

const getAgreementMarkdownTextArea = (textAreaRef: RefObject<TextAreaRef | null>) => textAreaRef.current?.resizableTextArea?.textArea;

const updateAgreementMarkdownValue = (
  getTextArea: () => HTMLTextAreaElement | null | undefined,
  onChange: ((value: string) => void) | undefined,
  nextValue: string,
  nextSelectionStart?: number,
  nextSelectionEnd?: number,
) => {
  onChange?.(nextValue);
  window.setTimeout(() => {
    const textArea = getTextArea();
    textArea?.focus();
    if (typeof nextSelectionStart === 'number' && typeof nextSelectionEnd === 'number') {
      textArea?.setSelectionRange(nextSelectionStart, nextSelectionEnd);
    }
  }, 0);
};

const insertAgreementMarkdown = (
  markdown: string,
  getTextArea: () => HTMLTextAreaElement | null | undefined,
  action: AgreementMarkdownToolbarAction,
  onChange: ((value: string) => void) | undefined,
) => {
  const textArea = getTextArea();
  const start = textArea?.selectionStart ?? markdown.length;
  const end = textArea?.selectionEnd ?? markdown.length;
  const selectedText = markdown.slice(start, end) || action.sample;

  if (action.mode === 'line') {
    const lineStart = markdown.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
    const nextValue = `${markdown.slice(0, lineStart)}${action.before}${markdown.slice(lineStart)}`;
    const selectionOffset = action.before.length;
    updateAgreementMarkdownValue(getTextArea, onChange, nextValue, start + selectionOffset, end + selectionOffset);
    return;
  }

  if (action.mode === 'block') {
    const needsLeadingBreak = start > 0 && markdown[start - 1] !== '\n';
    const needsTrailingBreak = end < markdown.length && markdown[end] !== '\n';
    const blockText = `${needsLeadingBreak ? '\n' : ''}${action.before}${needsTrailingBreak ? '\n' : ''}`;
    const nextValue = `${markdown.slice(0, start)}${blockText}${markdown.slice(end)}`;
    updateAgreementMarkdownValue(getTextArea, onChange, nextValue, start + blockText.length, start + blockText.length);
    return;
  }

  const after = action.after ?? action.before;
  const nextValue = `${markdown.slice(0, start)}${action.before}${selectedText}${after}${markdown.slice(end)}`;
  const nextSelectionStart = start + action.before.length;
  updateAgreementMarkdownValue(getTextArea, onChange, nextValue, nextSelectionStart, nextSelectionStart + selectedText.length);
};

const insertAgreementHeading = (
  markdown: string,
  getTextArea: () => HTMLTextAreaElement | null | undefined,
  onChange: ((value: string) => void) | undefined,
) => {
  const textArea = getTextArea();
  const start = textArea?.selectionStart ?? markdown.length;
  const lineStart = markdown.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
  const heading = '#'.repeat(2) + ' ';
  const nextValue = `${markdown.slice(0, lineStart)}${heading}${markdown.slice(lineStart)}`;
  updateAgreementMarkdownValue(getTextArea, onChange, nextValue, start + heading.length, start + heading.length);
};

export const AgreementMarkdownEditor = ({ value, onChange, placeholder }: AgreementMarkdownEditorProps) => {
  const textAreaRef = useRef<TextAreaRef | null>(null);
  const [mode, setMode] = useState<AgreementMarkdownEditorMode>('edit');
  const markdown = normalizeAgreementMarkdownValue(value);

  const getTextArea = () => getAgreementMarkdownTextArea(textAreaRef);

  return (
    <div className="agreement-markdown-editor">
      <div className="agreement-markdown-editor__toolbar">
        <div className="agreement-markdown-editor__tools">
          <>
            <Button
              size="small"
              icon={AGREEMENT_MARKDOWN_TOOLBAR_HEADINGS.heading}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => insertAgreementHeading(markdown, getTextArea, onChange)}
            />
            <Button
              size="small"
              icon={AGREEMENT_MARKDOWN_TOOLBAR_HEADINGS.paragraph}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() =>
                insertAgreementMarkdown(
                  markdown,
                  getTextArea,
                  { key: 'paragraph', title: '段落', icon: null, before: '\n\n', sample: '', mode: 'block' },
                  onChange,
                )
              }
            />
          </>
          <Divider type="vertical" />
          <>
            {AGREEMENT_MARKDOWN_TOOLBAR_LEFT_ACTIONS.map((action) => (
              <Tooltip title={action.title} key={action.key}>
                <Button size="small" icon={action.icon} onMouseDown={(event) => event.preventDefault()} onClick={() => insertAgreementMarkdown(markdown, getTextArea, action, onChange)} />
              </Tooltip>
            ))}
            <Divider type="vertical" />
            {AGREEMENT_MARKDOWN_TOOLBAR_RIGHT_ACTIONS.map((action) => (
              <Tooltip title={action.title} key={action.key}>
                <Button size="small" icon={action.icon} onMouseDown={(event) => event.preventDefault()} onClick={() => insertAgreementMarkdown(markdown, getTextArea, action, onChange)} />
              </Tooltip>
            ))}
          </>
        </div>
        <div className="agreement-markdown-editor__mode">
          <Button.Group size="small">
            <Button type={mode === 'edit' ? 'primary' : 'default'} onClick={() => setMode('edit')}>
              编辑
            </Button>
            <Button type={mode === 'preview' ? 'primary' : 'default'} onClick={() => setMode('preview')}>
              预览
            </Button>
          </Button.Group>
        </div>
      </div>
      <div className="agreement-markdown-editor__body">
        {mode === 'edit' ? (
          <Input.TextArea
            ref={textAreaRef}
            className="agreement-markdown-editor__input"
            value={markdown}
            onChange={(event) => onChange?.(event.target.value)}
            placeholder={placeholder}
            autoSize={{ minRows: 15, maxRows: 28 }}
          />
        ) : (
          <div className="agreement-markdown-editor__preview">
            {markdown.trim() ? (
              <div className="agreement-markdown-editor__preview-content">
                <XMarkdown content={markdown} openLinksInNewTab escapeRawHtml />
              </div>
            ) : (
              <div className="agreement-markdown-editor__preview-empty">预览会显示在这里</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
