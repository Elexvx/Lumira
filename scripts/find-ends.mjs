import fs from 'fs';

function findFunctionEnd(content, funcHeader) {
  const startIndex = content.indexOf(funcHeader);
  if (startIndex === -1) return -1;
  let braceCount = 0;
  let inString = false;
  let stringChar = '';
  let i = startIndex;
  
  while (i < content.length && content[i] !== '{') {
    i++;
  }
  
  if (i >= content.length) return -1;
  braceCount = 1;
  i++;
  
  while (i < content.length && braceCount > 0) {
    const char = content[i];
    if (char === '\\') {
      i += 2;
      continue;
    }
    
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
      if (char === stringChar) {
        inString = false;
      }
    }
    i++;
  }
  
  return content.substring(0, i).split('\n').length; // Line number of end brace
}

const file1 = fs.readFileSync('scripts/deploy-container.mjs', 'utf8');
const file2 = fs.readFileSync('scripts/install-platform.mjs', 'utf8');

const funcs1 = ['function log(', 'function run(', 'function output(', 'function optionalOutput(', 'function randomSecret(', 'function randomBase64Secret(', 'function parseEnvFile(', 'async function probeHttp(', 'async function waitForHttp('];
for (const f of funcs1) {
  console.log(`deploy-container.mjs: ${f} ends at line ${findFunctionEnd(file1, f)}`);
}

const funcs2 = ['function log(', 'function run(', 'function output(', 'function parseEnvFile(', 'function setEnvValue(', 'function randomSecret(', 'function randomBase64Secret('];
for (const f of funcs2) {
  console.log(`install-platform.mjs: ${f} ends at line ${findFunctionEnd(file2, f)}`);
}

const pStartIndex = file2.indexOf('const defaultCapacityProfiles = {');
console.log(`install-platform.mjs: const defaultCapacityProfiles ends at line ${findFunctionEnd(file2, 'const defaultCapacityProfiles = {')}`);
