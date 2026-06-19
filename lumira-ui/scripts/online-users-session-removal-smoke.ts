import assert from 'node:assert/strict';

const isCurrentSessionRemovalEvent = (
  event: { action: 'REMOVED' | 'UPSERT'; sessionId: string },
  currentSessionId?: string | null,
) => Boolean(currentSessionId) && event.action === 'REMOVED' && event.sessionId === currentSessionId;

const run = () => {
  const currentSessionId = 'session-123';

  assert.equal(
    isCurrentSessionRemovalEvent(
      { action: 'REMOVED', sessionId: currentSessionId },
      currentSessionId,
    ),
    true,
    'matching removal events should be recognized as a forced logout',
  );

  assert.equal(
    isCurrentSessionRemovalEvent(
      { action: 'REMOVED', sessionId: 'other-session' },
      currentSessionId,
    ),
    false,
    'removal events for other sessions should still just refresh the table',
  );

  assert.equal(
    isCurrentSessionRemovalEvent(
      { action: 'UPSERT', sessionId: currentSessionId },
      currentSessionId,
    ),
    false,
    'non-removal events should never trigger logout handling',
  );

  console.log('online-users-session-removal-smoke: ok');
};

run();
