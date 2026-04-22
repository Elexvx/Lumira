import assert from 'node:assert/strict';
import { isCurrentSessionRemovalEvent } from '../src/services/system/onlineUsers';

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
