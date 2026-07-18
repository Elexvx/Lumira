import type { CompetitionConfigItem, CompetitionConfigItemType } from '@/services/competition/types';

export type RegistrationFieldScope = Extract<
  CompetitionConfigItemType,
  'REGISTRATION_FIELD' | 'TEAM_FIELD' | 'MEMBER_FIELD' | 'PROJECT_FIELD'
>;

const normalizeFieldKey = (value?: string) => (value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();

export const isDeprecatedRegistrationContactField = (
  item: Pick<CompetitionConfigItem, 'itemType' | 'itemKey'>,
) => item.itemType === 'REGISTRATION_FIELD' && normalizeFieldKey(item.itemKey) === 'contactname';

export const removeDeprecatedRegistrationContactFields = <T extends Pick<CompetitionConfigItem, 'itemType' | 'itemKey'>>(
  items: T[],
) => items.filter((item) => !isDeprecatedRegistrationContactField(item));

const parseMetadata = (contentJson?: string | null): Record<string, unknown> => {
  if (!contentJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(contentJson);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
};

const isMemberNameField = (item: Pick<CompetitionConfigItem, 'itemKey' | 'title'>) => {
  const key = normalizeFieldKey(item.itemKey);
  const title = (item.title || '').replace(/\s/g, '');
  return key === 'membername'
    || (key === 'name' || key === 'contactname') && (title === '姓名' || title === '成员姓名');
};

/**
 * Keeps legacy competition configurations where a member-name field was saved
 * under TEAM_FIELD from being rendered as a team-level field. The same
 * normalized scope is used by the settings editor and the registration form,
 * and is persisted as MEMBER_FIELD when the settings are saved.
 */
export const resolveRegistrationFieldScope = (item: CompetitionConfigItem): RegistrationFieldScope => {
  const metadata = parseMetadata(item.contentJson);
  const metadataScope = metadata.fieldScope;
  const configuredScope = typeof metadataScope === 'string' && [
    'REGISTRATION_FIELD',
    'TEAM_FIELD',
    'MEMBER_FIELD',
    'PROJECT_FIELD',
  ].includes(metadataScope)
    ? metadataScope as RegistrationFieldScope
    : item.itemType as RegistrationFieldScope;

  if (configuredScope === 'TEAM_FIELD' && isMemberNameField(item)) {
    return 'MEMBER_FIELD';
  }
  return configuredScope;
};
