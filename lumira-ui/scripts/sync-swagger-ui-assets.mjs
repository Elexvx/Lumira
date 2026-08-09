import { copyFileSync, mkdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(scriptDirectory, '..');
const swaggerPackageJsonPath = require.resolve('swagger-ui-dist/package.json');
const swaggerRoot = dirname(swaggerPackageJsonPath);
const swaggerPackage = JSON.parse(readFileSync(swaggerPackageJsonPath, 'utf8'));
const targetRoot = join(uiRoot, 'public', 'vendor', 'swagger-ui');
const bootstrapSource = join(uiRoot, 'public', 'swagger-ui-bootstrap.js');
const assetNames = [
  'swagger-ui.css',
  'swagger-ui.css.map',
  'swagger-ui-bundle.js',
  'swagger-ui-bundle.js.map',
  'swagger-ui-standalone-preset.js',
  'swagger-ui-standalone-preset.js.map',
  'LICENSE',
  'NOTICE',
];

mkdirSync(targetRoot, { recursive: true });
for (const assetName of assetNames) {
  copyFileSync(join(swaggerRoot, assetName), join(targetRoot, assetName));
}
copyFileSync(bootstrapSource, join(targetRoot, 'lumira-bootstrap.js'));

process.stdout.write(`Synced Swagger UI ${swaggerPackage.version} assets.\n`);
