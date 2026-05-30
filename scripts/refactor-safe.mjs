import fs from 'fs/promises';
import path from 'path';

function removeFunction(content, funcHeader) {
  let startIndex = content.indexOf(funcHeader);
  if (startIndex === -1) return content;
  
  let braceCount = 0;
  let inString = false;
  let stringChar = '';
  let i = startIndex + funcHeader.length;
  
  // Find the first opening brace
  while (i < content.length && content[i] !== '{') {
    i++;
  }
  
  if (i >= content.length) return content;
  braceCount = 1;
  i++;
  
  while (i < content.length && braceCount > 0) {
    const char = content[i];
    
    if (!inString) {
      if (char === "'" || char === '"' || char === '`') {
        inString = true;
        stringChar = char;
      } else if (char === '{') {
        braceCount++;
      } else if (char === '}') {
        braceCount--;
      }
    } else {
      if (char === '\\') {
        i++; // skip escaped char
      } else if (char === stringChar) {
        inString = false;
      }
    }
    i++;
  }
  
  return content.slice(0, startIndex) + content.slice(i);
}

async function processFile(filePath) {
  let content = await fs.readFile(filePath, 'utf-8');
  
  const imports = `import { parseEnvFile, setEnvValue, randomSecret, randomBase64Secret, defaultCapacityProfiles } from './lib/env-utils.mjs';
import { run as execRun, output as execOutput, optionalOutput as execOptionalOutput, createLogger, resolveRepoRoot } from './lib/exec-utils.mjs';
import { waitForHttp, probeHttp } from './lib/http-utils.mjs';`;

  if (!content.includes('./lib/env-utils.mjs')) {
    const importMatch = content.match(/import .* from 'node:.*?';\n/g);
    if (importMatch) {
      const lastImport = importMatch[importMatch.length - 1];
      content = content.replace(lastImport, lastImport + imports + '\n');
    }
  }

  content = content.replace(/const scriptDir = path\.dirname\(fileURLToPath\(import\.meta\.url\)\);\nconst repoRoot = path\.resolve\(scriptDir, '\.\.'\);/, 'const repoRoot = resolveRepoRoot(import.meta.url);\nconst log = createLogger(\'deploy\');');
  
  content = removeFunction(content, 'function log(message) {');
  
  // Wrap run, output, optionalOutput
  content = removeFunction(content, 'function run(command, commandArgs, options = {}) {');
  content = removeFunction(content, 'function output(command, commandArgs) {');
  content = removeFunction(content, 'function optionalOutput(command, commandArgs) {');
  
  const injectWrappers = `
function run(command, commandArgs, options = {}) {
  try {
    return execRun(command, commandArgs, { cwd: repoRoot, ...options });
  } catch (err) {
    process.exit(err.status ?? 1);
  }
}

function output(command, commandArgs) {
  return execOutput(command, commandArgs, { cwd: repoRoot, check: false });
}

function optionalOutput(command, commandArgs) {
  return execOptionalOutput(command, commandArgs, { cwd: repoRoot });
}
`;

  // Insert wrappers right before `function parseEnvFile` or somewhere early
  const injectTarget = 'function randomSecret';
  if (content.includes(injectTarget)) {
    content = content.replace(injectTarget, injectWrappers + '\n' + injectTarget);
  }

  content = removeFunction(content, 'function randomSecret(prefix) {');
  content = removeFunction(content, 'function randomBase64Secret(byteLength = 48) {');
  content = removeFunction(content, 'function parseEnvFile(filePath) {');
  content = removeFunction(content, 'async function probeHttp(url, options = {}) {');
  content = removeFunction(content, 'async function waitForHttp(url, label, options = {}) {');
  content = removeFunction(content, 'export function setEnvValue(');

  if (content.includes('const defaultCapacityProfiles = {')) {
    content = removeFunction(content, 'const defaultCapacityProfiles = {');
    // But since it's a const, our removeFunction which looks for '{' will just remove the object body.
    content = content.replace(/const defaultCapacityProfiles = ;\n*/g, '');
  }

  await fs.writeFile(filePath, content, 'utf-8');
  console.log(`Refactored ${path.basename(filePath)}`);
}

async function main() {
  await processFile(path.join(process.cwd(), 'scripts/deploy-container.mjs'));
  await processFile(path.join(process.cwd(), 'scripts/install-platform.mjs'));
}

main().catch(console.error);
