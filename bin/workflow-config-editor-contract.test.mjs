import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = fs.readFileSync(
  path.join(repoRoot, 'lumira-ui', 'src', 'pages', 'workflow', 'WorkflowConfigPage.tsx'),
  'utf8',
);

test('workflow editor saves the active business type and ignores stale definition loads', () => {
  assert.match(source, /const activeBusinessType = useRef<WorkflowBusinessType>/);
  assert.match(source, /activeBusinessType\.current = nextBusinessType/);
  assert.match(source, /const saveBusinessType = activeBusinessType\.current/);
  assert.match(source, /saveWorkflowDraft\(saveBusinessType, buildPayload\(\)\)/);
  assert.match(source, /loadGeneration === definitionLoadGeneration\.current/);
});

test('workflow edge payload keeps only business config data', () => {
  assert.match(source, /data: \{ \.\.\.\(edge\.config \|\| \{\}\) \}/);
  assert.doesNotMatch(source, /data: \{ \.\.\.edge \}/);
});
