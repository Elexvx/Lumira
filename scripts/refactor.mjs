import fs from 'fs/promises';
import path from 'path';

async function processFile(filePath) {
  let content = await fs.readFile(filePath, 'utf-8');
  
  // 1. Add imports
  const imports = `import { parseEnvFile, randomSecret, randomBase64Secret, defaultCapacityProfiles } from './lib/env-utils.mjs';
import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';`;

  if (!content.includes('./lib/env-utils.mjs')) {
    const importMatch = content.match(/import .* from 'node:.*?';\n/g);
    if (importMatch) {
      const lastImport = importMatch[importMatch.length - 1];
      content = content.replace(lastImport, lastImport + imports + '\n');
    }
  }

  // 2. Replace setup (scriptDir, repoRoot, log)
  content = content.replace(/const scriptDir = path\.dirname\(fileURLToPath\(import\.meta\.url\)\);\nconst repoRoot = path\.resolve\(scriptDir, '\.\.'\);/, 'const repoRoot = resolveRepoRoot(import.meta.url);\nconst log = createLogger(\'deploy\');');
  
  // 3. Replace log function entirely
  content = content.replace(/function log\(message\) {\n  console\.log\(`\[deploy\] \$\{message\}`\);\n}\n/g, '');

  // 4. Wrap run, output, optionalOutput to inject cwd: repoRoot
  content = content.replace(/function run\(command, commandArgs, options = \{\}\) \{\n  const result = spawnSync.*?if \(result\.status !== 0\) \{\n    process\.exit\(result\.status \?\? 1\);\n  \}\n\}/s, `function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
  }
}`);

  content = content.replace(/function output\(command, commandArgs\) \{\n  return spawnSync.*?\}\n\}/s, `function output(command, commandArgs) {
  return execRun(command, commandArgs, { cwd: repoRoot, check: false, encoding: 'utf8' });
}`);

  content = content.replace(/function optionalOutput\(command, commandArgs\) \{\n  const result = output\(command, commandArgs\);\n  return result\.status === 0 \? result\.stdout\.trim\(\) : '';\n\}/s, `function optionalOutput(command, commandArgs) {
  return execOptionalOutput(command, commandArgs, { cwd: repoRoot });
}`);

  // 5. Delete randomSecret, randomBase64Secret, parseEnvFile
  content = content.replace(/function randomSecret\(prefix\) \{\n  return `\$\{prefix\}-\$\{randomBytes\(24\)\.toString\('hex'\)\}`;\n\}\n/s, '');
  content = content.replace(/function randomBase64Secret\(byteLength = 48\) \{\n  return randomBytes\(byteLength\)\.toString\('base64'\);\n\}\n/s, '');
  content = content.replace(/function parseEnvFile\(filePath\) \{[\s\S]*?\n\}\n/s, '');

  // 6. Delete waitForHttp, probeHttp
  content = content.replace(/async function probeHttp\(url, options = \{\}\) \{[\s\S]*?\n\}\n/s, '');
  content = content.replace(/async function waitForHttp\(url, label, options = \{\}\) \{[\s\S]*?\n\}\n/s, '');

  await fs.writeFile(filePath, content, 'utf-8');
  console.log(`Refactored ${path.basename(filePath)}`);
}

async function main() {
  await processFile(path.join(process.cwd(), 'scripts/deploy-container.mjs'));
  await processFile(path.join(process.cwd(), 'scripts/install-platform.mjs'));
}

main().catch(console.error);
