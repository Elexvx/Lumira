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
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';
import { sanitizeMarkdownInput } from '@/utils/markdownSecurity';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

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
  { key: 'quote', title: t('引用', 'Quote'), icon: <CommentOutlined />, before: '> ', sample: t('引用内容', 'Quoted text'), mode: 'line' },
  { key: 'bold', title: t('加粗', 'Bold'), icon: <BoldOutlined />, before: '**', after: '**', sample: t('加粗文字', 'Bold text') },
  { key: 'italic', title: t('斜体', 'Italic'), icon: <ItalicOutlined />, before: '*', after: '*', sample: t('斜体文字', 'Italic text') },
  { key: 'unordered-list', title: t('无序列表', 'Unordered list'), icon: <UnorderedListOutlined />, before: '- ', sample: t('列表项', 'List item'), mode: 'line' },
  { key: 'ordered-list', title: t('有序列表', 'Ordered list'), icon: <OrderedListOutlined />, before: '1. ', sample: t('列表项', 'List item'), mode: 'line' },
  { key: 'task-list', title: t('任务列表', 'Task list'), icon: <CheckSquareOutlined />, before: '- [ ] ', sample: t('待办项', 'Todo item'), mode: 'line' },
  { key: 'link', title: t('链接', 'Link'), icon: <LinkOutlined />, before: '[', after: '](https://example.com)', sample: t('链接文字', 'Link text') },
  { key: 'image', title: t('图片', 'Image'), icon: <PictureOutlined />, before: '![', after: '](https://example.com/image.png)', sample: t('图片描述', 'Image description') },
  { key: 'code', title: t('代码', 'Code'), icon: <CodeOutlined />, before: '`', after: '`', sample: 'code' },
  {
    key: 'table',
    title: t('表格', 'Table'),
    icon: <TableOutlined />,
    before: t('| 标题 | 内容 |\n| --- | --- |\n| 示例 | 文本 |', '| Header | Content |\n| --- | --- |\n| Example | Text |'),
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

const normalizeAgreementMarkdownValue = (value?: string) => sanitizeMarkdownInput(value ?? '');

const getAgreementMarkdownTextArea = (textAreaRef: RefObject<TextAreaRef | null>) => textAreaRef.current?.resizableTextArea?.textArea;

const updateAgreementMarkdownValue = (
  getTextArea: () => HTMLTextAreaElement | null | undefined,
  onChange: ((value: string) => void) | undefined,
  nextValue: string,
  nextSelectionStart?: number,
  nextSelectionEnd?: number,
) => {
  onChange?.(sanitizeMarkdownInput(nextValue));
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
                  { key: 'paragraph', title: t('段落', 'Paragraph'), icon: null, before: '\n\n', sample: '', mode: 'block' },
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
              {t('编辑', 'Edit')}
            </Button>
            <Button type={mode === 'preview' ? 'primary' : 'default'} onClick={() => setMode('preview')}>
              {t('预览', 'Preview')}
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
            onChange={(event) => onChange?.(sanitizeMarkdownInput(event.target.value))}
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
              <div className="agreement-markdown-editor__preview-empty">{t('预览会显示在这里', 'Preview will appear here')}</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
