import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('./lumira-updater.mjs', import.meta.url), 'utf8');

assert.match(source, /function authorized\(req\) \{/);
assert.match(source, /if \(!token\) \{\s*return false;\s*\}/);
assert.doesNotMatch(source, /if \(!token\) \{\s*return true;\s*\}/);
