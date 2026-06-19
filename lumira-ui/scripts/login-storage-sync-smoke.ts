import assert from 'node:assert/strict';
import {
  AUTH_TOKEN_STORAGE_KEY,
  createLoginStorageHandler,
  isAuthTokenStorageEvent,
} from '../src/auth/loginRedirect';

const run = () => {
  const redirectCalls: string[] = [];
  const handleStorage = createLoginStorageHandler('/dashboard/home', (target) => {
    redirectCalls.push(target);
  });

  handleStorage({ key: AUTH_TOKEN_STORAGE_KEY, newValue: '{"accessToken":"a"}' });
  assert.equal(redirectCalls.length, 1, 'token storage writes should trigger a login-page redirect');
  assert.equal(
    isAuthTokenStorageEvent({ key: AUTH_TOKEN_STORAGE_KEY, newValue: '{"accessToken":"a"}' }),
    true,
    'token storage writes should be recognized as auth token events',
  );

  handleStorage({ key: AUTH_TOKEN_STORAGE_KEY, newValue: null });
  assert.equal(redirectCalls.length, 1, 'token removals should not trigger a redirect');
  assert.equal(
    isAuthTokenStorageEvent({ key: AUTH_TOKEN_STORAGE_KEY, newValue: null }),
    false,
    'token removals should not be treated as redirectable auth token events',
  );

  handleStorage({ key: 'other-key', newValue: '{"accessToken":"a"}' });
  assert.equal(redirectCalls.length, 1, 'unrelated storage writes should be ignored');

  console.log('login-storage-sync-smoke: ok');
};

run();
