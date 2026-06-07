import { describe, expect, it } from 'vitest';
import { resolveAiLandingPath } from '@/layouts/AiLayout/AiLayout';

describe('resolveAiLandingPath', () => {
  it('prefers the assistant page when both AI entry points are available', () => {
    expect(
      resolveAiLandingPath({
        canVisitAiAssistant: true,
        canVisitAiKnowledge: true,
      }),
    ).toBe('/ai/assistant');
  });

  it('falls back to knowledge when assistant access is missing', () => {
    expect(
      resolveAiLandingPath({
        canVisitAiAssistant: false,
        canVisitAiKnowledge: true,
      }),
    ).toBe('/ai/knowledge');
  });
});
