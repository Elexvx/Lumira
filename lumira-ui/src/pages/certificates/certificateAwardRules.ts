import type { CertificateAwardGrant, CertificateAwardRule } from '@/services/certificates/types';

export const validateCertificateAwardRules = (
  rules: CertificateAwardRule[] | undefined,
): string | undefined => {
  if (!rules?.length) {
    return '请至少配置一个奖项规则';
  }
  const normalized = rules
    .map((rule) => ({
      ...rule,
      awardName: rule.awardName?.trim(),
    }))
    .sort((left, right) => left.minRank - right.minRank);
  for (const rule of normalized) {
    if (!rule.awardName) {
      return '请输入奖项名称';
    }
    if (!Number.isInteger(rule.minRank) || !Number.isInteger(rule.maxRank)
      || rule.minRank < 1 || rule.maxRank > 10000 || rule.minRank > rule.maxRank) {
      return `${rule.awardName}的名次范围无效`;
    }
  }
  for (let index = 1; index < normalized.length; index += 1) {
    if (normalized[index].minRank <= normalized[index - 1].maxRank) {
      return `${normalized[index - 1].awardName}与${normalized[index].awardName}的名次范围不能重叠`;
    }
  }
  return undefined;
};

export const selectableAwardGrantIds = (grants: CertificateAwardGrant[]) =>
  grants
    .filter((grant) => grant.status === 'GRANTED' && !grant.certificateRecordId)
    .map((grant) => grant.id);

export const summarizeAwardGrants = (grants: CertificateAwardGrant[]) => ({
  total: grants.length,
  pending: selectableAwardGrantIds(grants).length,
  issued: grants.filter((grant) => grant.status === 'ISSUED' || Boolean(grant.certificateRecordId)).length,
  revoked: grants.filter((grant) => grant.status === 'REVOKED').length,
});

export const haveAwardGrantsChanged = (
  previous: CertificateAwardGrant[],
  next: CertificateAwardGrant[],
) => {
  if (previous.length !== next.length) {
    return true;
  }
  const previousById = new Map(previous.map((grant) => [grant.id, grant]));
  return next.some((grant) => {
    const existing = previousById.get(grant.id);
    return !existing
      || existing.status !== grant.status
      || existing.awardName !== grant.awardName
      || existing.rankNo !== grant.rankNo
      || existing.certificateRecordId !== grant.certificateRecordId;
  });
};
