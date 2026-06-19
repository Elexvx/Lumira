import type { FormProps } from 'antd';

export const buildStandardFormProps = (overrides: FormProps = {}): FormProps => ({
  layout: overrides.layout ?? 'vertical',
  ...overrides,
});

export const useStandardFormProps = (overrides: FormProps = {}): FormProps => {
  return buildStandardFormProps(overrides);
};
