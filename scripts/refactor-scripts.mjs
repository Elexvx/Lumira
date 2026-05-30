import fs from 'fs/promises';
import path from 'path';

const deployContainerPath = path.join(process.cwd(), 'scripts/deploy-container.mjs');
const installPlatformPath = path.join(process.cwd(), 'scripts/install-platform.mjs');

async function refactorScript(filePath) {
  let content = await fs.readFile(filePath, 'utf-8');
  
  // Replace imports
  if (!content.includes('env-utils.mjs')) {
    const importReplacement = `import { parseEnvFile, randomSecret, randomBase64Secret } from './lib/env-utils.mjs';
import { run, output, optionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';`;
    
    // Find where to insert (after node: imports)
    const imports = content.match(/import .* from 'node:.*?';\n/g);
    if (imports) {
      const lastImport = imports[imports.length - 1];
      content = content.replace(lastImport, lastImport + importReplacement + '\n');
    }
  }

  // Remove duplicated functions in deploy-container
  const funcsToRemove = [
    'function log\\(message\\) {[^}]*\\}',
    'function run\\(command, commandArgs, options = {}\\) {\\s*const result = spawnSync.*?if \\(result\\.status !== 0\\) {.*?\\}\\s*\\}',
    'function output\\(command, commandArgs\\) {\\s*return spawnSync.*?\\}',
    'function optionalOutput\\(command, commandArgs\\) {\\s*const result = output.*?\\}',
    'function randomSecret\\(prefix\\) {[^}]*\\}',
    'function randomBase64Secret\\(byteLength = 48\\) {[^}]*\\}',
    'function parseEnvFile\\(filePath\\) {[\\s\\S]*?\\n\\}',
    'async function probeHttp\\(url, options = {}\\) {[\\s\\S]*?\\n\\}',
    'async function waitForHttp\\(url, label, options = {}\\) {[\\s\\S]*?throw new Error.*?\\n\\}'
  ];

  for (const func of funcsToRemove) {
    const regex = new RegExp(func, 'gm');
    content = content.replace(regex, '');
  }

  // Use createLogger
  if (!content.includes('const log = createLogger')) {
    content = content.replace(/const scriptDir = path\.dirname\(fileURLToPath\(import\.meta\.url\)\);\nconst repoRoot = path\.resolve\(scriptDir, '\.\.'\);/g, `const repoRoot = resolveRepoRoot(import.meta.url);\nconst log = createLogger('deploy');`);
  }

  await fs.writeFile(filePath, content, 'utf-8');
  console.log(`Refactored ${path.basename(filePath)}`);
}

async function main() {
  await refactorScript(deployContainerPath);
  await refactorScript(installPlatformPath);
}

main().catch(console.error);
