import { describe, expect, it } from 'vitest';
import { isPoliceBeianText, POLICE_BEIAN_QUERY_URL, resolvePoliceBeianQueryUrl } from './beian';

describe('beian helpers', () => {
  it('identifies police filing text without confusing it with an ICP filing', () => {
    expect(isPoliceBeianText('苏公网安备32010502011484号')).toBe(true);
    expect(isPoliceBeianText('公安备案 32010502011484')).toBe(true);
    expect(isPoliceBeianText('苏ICP备2025160017号-2')).toBe(false);
  });

  it('builds the official police filing query URL from the record code', () => {
    expect(resolvePoliceBeianQueryUrl('苏公网安备32010502011484号')).toBe(
      `${POLICE_BEIAN_QUERY_URL}?code=32010502011484`,
    );
    expect(resolvePoliceBeianQueryUrl('公安备案')).toBe(POLICE_BEIAN_QUERY_URL);
  });
});
