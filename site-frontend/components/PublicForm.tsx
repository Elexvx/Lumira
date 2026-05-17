'use client';

import { useMemo, useState } from 'react';
import { getForm, submitForm, type PublicFormField } from '@/lib/api';

function parseFields(schemaJson?: string): PublicFormField[] {
  if (!schemaJson) return [];
  try {
    const parsed = JSON.parse(schemaJson);
    return Array.isArray(parsed)
      ? parsed.filter((item) => item && typeof item.name === 'string')
      : [];
  } catch {
    return [];
  }
}

function inputType(type?: string) {
  if (type === 'email') return 'email';
  if (type === 'mobile' || type === 'phone' || type === 'tel') return 'tel';
  return 'text';
}

export function PublicForm({ code = 'contact', title }: { code?: string; title?: string }) {
  const [fields, setFields] = useState<PublicFormField[] | null>(null);
  const [formName, setFormName] = useState(title || '在线提交');
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const requiredNames = useMemo(() => new Set((fields || []).filter((field) => field.required).map((field) => field.name)), [fields]);

  async function ensureLoaded() {
    if (fields) return fields;
    const form = await getForm(code);
    const nextFields = parseFields(form?.schemaJson);
    setFormName(title || form?.name || '在线提交');
    setFields(nextFields);
    return nextFields;
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage('');
    const activeFields = await ensureLoaded();
    const missing = activeFields.find((field) => requiredNames.has(field.name) && !values[field.name]?.trim());
    if (missing) {
      setMessage(`请填写${missing.label || missing.name}`);
      return;
    }

    setLoading(true);
    try {
      await submitForm(code, values);
      setValues({});
      setMessage('提交成功，我们会尽快处理。');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '提交失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="public-form" onFocus={ensureLoaded} onMouseEnter={ensureLoaded} onSubmit={handleSubmit}>
      <div className="public-form-heading">
        <span>Form</span>
        <h3>{formName}</h3>
      </div>
      {(fields || []).map((field) => {
        const value = values[field.name] || '';
        const setValue = (next: string) => setValues((current) => ({ ...current, [field.name]: next }));
        if (field.type === 'textarea') {
          return (
            <label key={field.name}>
              <span>{field.label || field.name}{field.required ? ' *' : ''}</span>
              <textarea value={value} placeholder={field.placeholder} rows={5} onChange={(event) => setValue(event.target.value)} />
            </label>
          );
        }
        if (field.type === 'select') {
          return (
            <label key={field.name}>
              <span>{field.label || field.name}{field.required ? ' *' : ''}</span>
              <select value={value} onChange={(event) => setValue(event.target.value)}>
                <option value="">请选择</option>
                {(field.options || []).map((option) => {
                  const next = typeof option === 'string' ? { label: option, value: option } : option;
                  return <option value={next.value} key={next.value}>{next.label}</option>;
                })}
              </select>
            </label>
          );
        }
        return (
          <label key={field.name}>
            <span>{field.label || field.name}{field.required ? ' *' : ''}</span>
            <input type={inputType(field.type)} value={value} placeholder={field.placeholder} onChange={(event) => setValue(event.target.value)} />
          </label>
        );
      })}
      {fields?.length === 0 ? <p className="public-form-empty">表单暂未配置字段。</p> : null}
      <button type="submit" disabled={loading || fields?.length === 0}>{loading ? '提交中...' : '提交'}</button>
      {message ? <p className="public-form-message">{message}</p> : null}
    </form>
  );
}
