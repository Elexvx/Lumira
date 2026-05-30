import fs from 'fs/promises';
import path from 'path';

async function processFile(filePath) {
  let content = await fs.readFile(filePath, 'utf-8');
  
  // Delete const defaultCapacityProfiles block in install-platform.mjs
  content = content.replace(/const defaultCapacityProfiles = \{[\s\S]*?    smokeConcurrency: 32,\n  \},\n\};\n/s, '');

  await fs.writeFile(filePath, content, 'utf-8');
  console.log(`Refactored ${path.basename(filePath)}`);
}

processFile(path.join(process.cwd(), 'scripts/install-platform.mjs')).catch(console.error);
