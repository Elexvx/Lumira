import fs from 'fs/promises';
import path from 'path';

const SRC_DIR = path.join(process.cwd(), 'frontend/src');
const IMPORT_STMT = "import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';\n";

async function walkDir(dir) {
  const files = await fs.readdir(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = await fs.stat(filePath);
    if (stat.isDirectory()) {
      await walkDir(filePath);
    } else if (filePath.endsWith('.ts') || filePath.endsWith('.tsx')) {
      await processFile(filePath);
    }
  }
}

async function processFile(filePath) {
  let content = await fs.readFile(filePath, 'utf-8');
  let changed = false;

  // Replace { autoRedirectOnUnauthorized: false, silent: true }
  if (content.includes('{ autoRedirectOnUnauthorized: false, silent: true }')) {
    content = content.replace(/\{\s*autoRedirectOnUnauthorized:\s*false,\s*silent:\s*true\s*\}/g, 'API_OPTS.SILENT_NO_REDIRECT');
    changed = true;
  }
  
  // Replace { autoRedirectOnUnauthorized: false }
  if (content.includes('{ autoRedirectOnUnauthorized: false }')) {
    content = content.replace(/\{\s*autoRedirectOnUnauthorized:\s*false\s*\}/g, 'API_OPTS.NO_REDIRECT');
    changed = true;
  }

  // Replace error instanceof Error ? error.message : 'xxx'
  // Regex needs to be careful
  const errorMsgRegex = /message\.error\(\s*error\s*instanceof\s*Error\s*(?:&&[^?]+)?\?\s*error\.message\s*:\s*([^)]+)\s*\)/g;
  if (errorMsgRegex.test(content)) {
    content = content.replace(errorMsgRegex, 'showErrorMessage(error, $1)');
    changed = true;
  }

  if (changed) {
    // Add import if not present
    if (!content.includes('from \'@/utils/errorMessage\'')) {
      // Find the last import statement
      const importRegex = /^import .+?;/gm;
      let lastImportIndex = 0;
      let match;
      while ((match = importRegex.exec(content)) !== null) {
        lastImportIndex = match.index + match[0].length;
      }
      
      if (lastImportIndex > 0) {
        content = content.slice(0, lastImportIndex) + '\n' + IMPORT_STMT + content.slice(lastImportIndex);
      } else {
        content = IMPORT_STMT + '\n' + content;
      }
    }
    
    await fs.writeFile(filePath, content, 'utf-8');
    console.log(`Updated: ${filePath.replace(SRC_DIR, '')}`);
  }
}

walkDir(SRC_DIR).then(() => console.log('Done')).catch(console.error);
