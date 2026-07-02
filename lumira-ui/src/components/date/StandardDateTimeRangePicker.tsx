import { DatePicker } from 'antd';
import type { GetProps } from 'antd';

type RangePickerProps = GetProps<typeof DatePicker.RangePicker>;

export const STANDARD_DATE_TIME_DISPLAY_FORMAT = 'YYYY-MM-DD HH:mm';
export const STANDARD_TIME_DISPLAY_FORMAT = 'HH:mm';

export type StandardDateTimeRangePickerProps = Omit<RangePickerProps, 'format' | 'showTime'>;

export const StandardDateTimeRangePicker = (props: StandardDateTimeRangePickerProps) => (
  <DatePicker.RangePicker
    {...props}
    showTime={{ format: STANDARD_TIME_DISPLAY_FORMAT }}
    format={STANDARD_DATE_TIME_DISPLAY_FORMAT}
  />
);

