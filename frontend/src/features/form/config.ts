import type { FormProps } from 'antd';
import { useResponsive } from '@/hooks/useResponsive';

export const buildStandardFormProps = (isMobile: boolean, overrides: FormProps = {}): FormProps => ({
  layout: overrides.layout ?? (isMobile ? 'vertical' : 'horizontal'),
  ...overrides,
});

export const useStandardFormProps = (overrides: FormProps = {}): FormProps => {
  const responsive = useResponsive();
  return buildStandardFormProps(responsive.isMobile, overrides);
};
