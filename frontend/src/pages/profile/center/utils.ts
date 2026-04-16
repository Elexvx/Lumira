import type { ProfileSummary } from '@/types/api';

export const buildVisibleProfileFields = (profileFieldSettings: ProfileSummary['profileFieldSettings'] = []) =>
  new Set(profileFieldSettings.filter((item) => item.visible).map((item) => item.fieldKey));
